package org.liuyehcf.chat.server;

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.liuyehcf.chat.protocol.Protocol.Header.APPLY_GROUP_NAME;

/**
 * 服务端PipeLineTask实现类
 * Created by Liuye on 2017/5/29.
 */
public class ServerSessionTask extends AbstractPipeLineTask {

    /**
     * 单例对象
     */
    private ServerConnectionDispatcher serverConnectionDispatcher = ServerConnectionDispatcher.getSingleton();

    private Protocol protocol = new Protocol();

    public ServerSessionTask() {
        serverConnectionDispatcher.getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            checkActive();

            //任一线程做负载均衡
            serverConnectionDispatcher.checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ServerConnectionDispatcher.LOGGER.info("{} is finished", this);
        serverConnectionDispatcher.getPipeLineTasks().remove(this);
    }

    /**
     * 读取信息
     */
    private void readMessage() {
        int readReadyNum;
        try {
            readReadyNum = getReadSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
        }

        if (readReadyNum <= 0) return;

        Set<SelectionKey> selectionKeys = getReadSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            readMessageFromConnection(selectionKey);

            iterator.remove();
        }
    }

    private void readMessageFromConnection(SelectionKey selectionKey) {
        ServerConnection connection = (ServerConnection) selectionKey.attachment();

        MessageReader messageReader = connection.getMessageReader();
        List<Message> messages;
        try {
            messages = messageReader.read(connection);
        } catch (IOException e) {
            ServerConnectionDispatcher.LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
            connection.getBindPipeLineTask().offLine(connection);
            return;
        }

        for (Message message : messages) {
            connection.activeNow();

            ServerConnectionDispatcher.LOGGER.debug("Receive a message {}", protocol.wrap(message));

            if (message.getControl().isSystemMessage()) {
                if (message.getHeader().getParam1().equals(APPLY_GROUP_NAME)) {
                    String groupName = message.getHeader().getParam3();
                    if (!serverConnectionDispatcher.getGroupInfoMap().containsKey(groupName)) {
                        serverConnectionDispatcher.getGroupInfoMap().put(groupName, new ServerGroupInfo(groupName));

                        //刷新群聊列表
                        refreshGroupList();
                    } else {
                        //失败说明冲突，同样会刷新，这里什么都不用做
                    }
                }
            }
            //登录信息
            else if (message.getControl().isLoginInMessage()) {
                connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, message.getHeader().getParam1()));
                connection.setMainConnection(true);

                String account = message.getHeader().getParam1();
                ServerUtils.ASSERT(!serverConnectionDispatcher.getMainConnectionMap().containsKey(account));

                serverConnectionDispatcher.getMainConnectionMap().put(account, connection);

                //发送消息，允许客户端登录
                ServerUtils.sendReplyLoginInMessage(
                        connection,
                        true,
                        account);

                //刷新好友列表
                refreshFriendList();

                //刷新群聊列表
                refreshGroupList();
                //todo 何时deny
            }
            //注销信息
            else if (message.getControl().isLoginOutMessage()) {
                String userName = message.getHeader().getParam1();
                ServerUtils.ASSERT(serverConnectionDispatcher.getMainConnectionMap().containsKey(userName));

                //关闭主界面连接
                connection.getBindPipeLineTask().offLine(connection);

                //找到该主界面对应的会话连接描述符
                ConnectionDescription connectionDescription = new ConnectionDescription(
                        Protocol.SERVER_USER_NAME,
                        userName);

                //获取会话连接
                Connection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(connectionDescription);

                //若为空，则代表该用户没有开启任何会话
                if (sessionConnection != null) {
                    //注销通知
                    loginOutNotify(sessionConnection);

                    //关闭会话连接
                    sessionConnection.getBindPipeLineTask().offLine(sessionConnection);
                }
                //刷新好友列表
                refreshFriendList();
            }
            //是否是新建会话消息
            else if (message.getControl().isOpenSessionMessage()) {
                //群聊
                if (message.getControl().isGroupChat()) {
                    String fromUserName = message.getHeader().getParam1();
                    String groupName = message.getHeader().getParam2();
                    ServerConnectionDispatcher.LOGGER.info("Client {} open a new GroupSession {}", fromUserName, groupName);

                    //该Connection第一次建立，服务端Connection建立时是没有描述符的，因为没有接到任何消息
                    if (connection.getConnectionDescription() == null) {
                        connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                        connection.setMainConnection(false);

                        ServerUtils.ASSERT(!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));
                        serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
                    }

                    //增加一条会话描述符
                    SessionDescription newSessionDescription = new SessionDescription(
                            fromUserName,
                            groupName,
                            true);
                    ServerUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(
                            newSessionDescription));

                    ServerConnectionDispatcher.LOGGER.info("Client {} open a new GroupSession {} successfully", fromUserName, newSessionDescription);

                    ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
                    serverGroupInfo.addConnection(fromUserName, connection);
                    //todo 刷新群聊界面成员列表
                }
                //非群聊
                else {
                    String fromUserName = message.getHeader().getParam1();
                    String toUserName = message.getHeader().getParam2();
                    ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session", fromUserName);

                    //该Connection第一次建立，服务端Connection建立时是没有描述符的，因为没有接到任何消息
                    if (connection.getConnectionDescription() == null) {
                        connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                        connection.setMainConnection(false);

                        ServerUtils.ASSERT(!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));
                        serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
                    }

                    //增加一条会话描述符
                    SessionDescription newSessionDescription = new SessionDescription(
                            fromUserName,
                            toUserName,
                            false);
                    ServerUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(
                            newSessionDescription));

                    ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);

                }
            }
            //客户端要求断开连接
            else if (message.getControl().isCloseSessionMessage()) {
                //群聊
                if (message.getControl().isGroupChat()) {
                    String fromUserName = message.getHeader().getParam1();
                    String groupName = message.getHeader().getParam2();

                    SessionDescription sessionDescription = new SessionDescription(
                            fromUserName,
                            groupName,
                            true);
                    ServerUtils.ASSERT(connection.getConnectionDescription().removeSessionDescription(sessionDescription));
                    ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);

                    ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
                    serverGroupInfo.removeConnection(fromUserName);
                    //todo 刷新群聊界面成员列表
                }
                //非群聊
                else {
                    String fromUserName = message.getHeader().getParam1();
                    String toUserName = message.getHeader().getParam2();

                    SessionDescription sessionDescription = new SessionDescription(
                            fromUserName,
                            toUserName,
                            false);
                    ServerUtils.ASSERT(connection.getConnectionDescription().removeSessionDescription(sessionDescription));
                    ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);

                }
                //该连接没有会话了
                if (connection.getConnectionDescription().getSessionDescriptions().isEmpty()) {
                    connection.getBindPipeLineTask().offLine(connection);
                }
            }
            //一般信息
            else {
                //群聊
                if (message.getControl().isGroupChat()) {
                    String fromUserName = message.getHeader().getParam1();
                    String groupName = message.getHeader().getParam2();

                    ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);

                    serverGroupInfo.offerMessage(connection,message);
                }
                //非群聊
                else {
                    String fromUserName = message.getHeader().getParam1();
                    String toUserName = message.getHeader().getParam2();

                    ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
                    //连接存在
                    if (serverConnectionDispatcher.getSessionConnectionMap().containsKey(toConnectionDescription)) {
                        Connection toConnection = serverConnectionDispatcher.getSessionConnectionMap().get(toConnectionDescription);

                        SessionDescription toSessionDescription = new SessionDescription(
                                toUserName,
                                fromUserName,
                                false);
                        //会话也存在
                        if (toConnection.getConnectionDescription().getSessionDescriptions().contains(toSessionDescription)) {
                            toConnection.offerMessage(message);
                        }
                        //会话不存在
                        else {
                            Connection mainConnection;
                            if ((mainConnection = serverConnectionDispatcher.getMainConnectionMap().get(toUserName)) != null) {

                                //发送一条消息要求客户端代开会话窗口
                                ServerUtils.sendOpenSessionWindowMessage(
                                        mainConnection,
                                        toUserName,
                                        fromUserName,
                                        message.getBody().getContent());
                            } else {
                                ServerUtils.sendNotOnLineMessage(
                                        connection,
                                        fromUserName,
                                        toUserName,
                                        "<" + toUserName + ">已经离线");
                            }
                        }
                    } else {
                        Connection mainConnection;
                        if ((mainConnection = serverConnectionDispatcher.getMainConnectionMap().get(toUserName)) != null) {

                            //发送一条消息要求客户端代开会话窗口
                            ServerUtils.sendOpenSessionWindowMessage(
                                    mainConnection,
                                    toUserName,
                                    fromUserName,
                                    message.getBody().getContent());
                        } else {
                            ServerUtils.sendNotOnLineMessage(
                                    connection,
                                    fromUserName,
                                    toUserName,
                                    "<" + toUserName + ">已经离线");
                        }
                    }
                }

            }
        }
    }

    /**
     * 写入信息
     */
    private void writeMessage() {
        int readyWriteNum;
        try {
            readyWriteNum = getWriteSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed!");
        }

        if (readyWriteNum <= 0) return;

        Set<SelectionKey> selectionKeys = getWriteSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            writeMessageToConnection(selectionKey);

            iterator.remove();
        }
    }

    private void writeMessageToConnection(SelectionKey selectionKey) {
        Connection connection = (Connection) selectionKey.attachment();

        MessageWriter messageWriter = connection.getMessageWriter();

        Message message = connection.pollMessage();

        if (message != null) {
            try {
                messageWriter.write(message, connection);
                ServerConnectionDispatcher.LOGGER.debug("Send a message {}", protocol.wrap(message));
            } catch (IOException e) {
                ServerConnectionDispatcher.LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
                connection.getBindPipeLineTask().offLine(connection);
            }
        }
    }

    /**
     * 检查处于Connection是否处于活跃状态
     * 超过一定时间就强制下线
     */
    //todo 这个方法有问题
    private void checkActive() {
        long currentStamp = System.currentTimeMillis();
        for (Connection connection : getConnections()) {
            if (currentStamp - connection.getRecentActiveTimeStamp() > ServerUtils.MAX_INACTIVE_TIME * 60 * 1000L) {
                //发送消息关闭会话
//                ServerUtils.sendLogOutMessage(
//                        connection,
//                        connection.getConnectionDescription().getSource(),
//                        "占着茅坑不拉屎，你可以滚了!!!"
//                );
            }
        }
    }

    /**
     * 离线的后续处理
     *
     * @param connection
     */
    @Override
    protected void offLinePostProcess(Connection connection) {
        ServerConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);

        ServerConnection serverConnection = (ServerConnection) connection;
        if (serverConnection.isMainConnection()) {
            serverConnectionDispatcher.getMainConnectionMap().remove(connection.getConnectionDescription().getDestination());
        } else {
            serverConnectionDispatcher.getSessionConnectionMap().remove(connection.getConnectionDescription());
        }
    }

    private void refreshFriendList() {
        String listString = serverConnectionDispatcher.getMainConnectionMap().keySet().toString();
        for (Connection connection : serverConnectionDispatcher.getMainConnectionMap().values()) {
            //发送系统消息，要求客户端刷新主界面好友列表
            ServerUtils.sendFlushFriendListMessage(
                    connection,
                    connection.getConnectionDescription().getDestination(),
                    listString);
        }
    }

    private void refreshGroupList() {
        String listString = serverConnectionDispatcher.getGroupInfoMap().keySet().toString();
        for (Connection connection : serverConnectionDispatcher.getMainConnectionMap().values()) {
            //发送系统消息，要求客户端刷新群聊列表
            ServerUtils.sendFlushGroupListMessage(
                    connection,
                    connection.getConnectionDescription().getDestination(),
                    listString);
        }
    }

    private void loginOutNotify(Connection connection) {
        //遍历会话连接的所有会话描述符
        for (SessionDescription sessionDescription : connection.getConnectionDescription().getSessionDescriptions()) {
            String fromUserName = sessionDescription.getFromUserName();
            String toUserName = sessionDescription.getToUserName();

            ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
            Connection toConnection = serverConnectionDispatcher.getSessionConnectionMap().get(toConnectionDescription);

            //connection为null代表已经关闭了
            if (toConnection != null) {
                String systemContent = "["
                        + fromUserName
                        + "]已断开连接";
                ServerUtils.sendLoginOutNotifyMessage(
                        toConnection,
                        toUserName,
                        fromUserName,
                        systemContent);
            }
        }


    }

    //todo
//    private void closeGroupSession(Connection connection, String fromUserName, String groupName) {
//        ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
//
//        serverGroupInfo.removeConnection(connection);
//
//        if (serverGroupInfo.isGroupEmpty()) {
//            serverConnectionDispatcher.getGroupInfoMap().remove(serverGroupInfo.getGroupName());
//        } else {
//            String systemContent = "["
//                    + fromUserName
//                    + "]已断开连接";
//            serverGroupInfo.offerMessage(connection, ServerUtils.sendLogOutMessage(
//                    false,
//                    "",
//                    systemContent
//            ));
//        }
//
//    }
}

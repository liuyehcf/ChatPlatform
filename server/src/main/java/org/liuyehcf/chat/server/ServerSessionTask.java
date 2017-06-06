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

/**
 * 服务端PipeLineTask实现类
 * Created by Liuye on 2017/5/29.
 */
public class ServerSessionTask extends AbstractPipeLineTask {

    /**
     * 单例对象
     */
    private ServerConnectionDispatcher serverConnectionDispatcher = ServerConnectionDispatcher.getSingleton();

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
            //已配置拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {
            connection.activeNow();

            //登录信息
            if (message.getControl().isLoginInMessage()) {
                connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, message.getHeader().getParam1()));
                connection.setMainConnection(true);

                String account = message.getHeader().getParam1();
                ServerUtils.ASSERT(!serverConnectionDispatcher.getMainConnectionMap().containsKey(account));

                serverConnectionDispatcher.getMainConnectionMap().put(account, connection);

                //发送消息，允许客户端登录
                ServerUtils.sendReplyLoginInMessage(
                        connection,
                        true,
                        account,
                        serverConnectionDispatcher.getMainConnectionMap().keySet().toString());

                //刷新好友列表
                refreshFriendList();
                //todo 何时deny
            }
            //注销信息
            else if (message.getControl().isLoginOutMessage()) {
                String userName = message.getHeader().getParam1();
                ServerUtils.ASSERT(serverConnectionDispatcher.getMainConnectionMap().containsKey(userName));

                //关闭主界面连接
                serverConnectionDispatcher.getMainConnectionMap().remove(userName);
                connection.getBindPipeLineTask().offLine(connection);

                //找到该主界面对应的会话连接描述符
                ConnectionDescription connectionDescription = new ConnectionDescription(
                        Protocol.SERVER_USER_NAME,
                        userName);

                //获取会话连接
                Connection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(connectionDescription);

                ServerUtils.ASSERT(sessionConnection != null);

                //注销通知
                loginOutNotify(sessionConnection);

                //关闭会话连接
                serverConnectionDispatcher.getSessionConnectionMap().remove(connectionDescription);
                sessionConnection.getBindPipeLineTask().offLine(sessionConnection);

                //刷新好友列表
                refreshFriendList();
            }
            //是否是新建会话消息
            else if (message.getControl().isOpenSessionMessage()) {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();
                ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session", fromUserName);

                //该Connection第一次建立，服务端Connection建立时是没有描述符的，因为没有接到任何消息
                if (connection.getConnectionDescription() == null) {
                    connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                    connection.setMainConnection(false);
                }

                //增加一条会话描述符
                SessionDescription newSessionDescription = new SessionDescription(fromUserName, toUserName);
                ServerUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(
                        newSessionDescription));

                ServerUtils.ASSERT(!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));

                serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
                ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);

            }
            //客户端要求断开连接
            else if (message.getControl().isCloseSessionMessage()) {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();

                SessionDescription sessionDescription = new SessionDescription(fromUserName, toUserName);
                ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);
                ServerUtils.ASSERT(connection.getConnectionDescription().removeSessionDescription(sessionDescription));

                //该连接没有会话了
                if (connection.getConnectionDescription().getSessionDescriptions().isEmpty()) {
                    connection.getBindPipeLineTask().offLine(connection);
                }
            }
            //私聊
            else {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();

                ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
                if (serverConnectionDispatcher.getSessionConnectionMap().containsKey(toConnectionDescription)) {
                    Connection toConnection = serverConnectionDispatcher.getSessionConnectionMap().get(toConnectionDescription);
                    toConnection.offerMessage(message);
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
                        //todo 此时对方已下线
                        ServerUtils.sendSystemMessage(
                                connection,
                                fromUserName,
                                "<" + toUserName + ">已经离线");
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
            } catch (IOException e) {
                //已配置拦截器，这里不做处理
                return;
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
                ServerUtils.sendCloseSessionMessage(
                        connection,
                        connection.getConnectionDescription().getSource(),
                        "占着茅坑不拉屎，你可以滚了!!!"
                );
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
            ServerUtils.sendLoginInFlushMessage(
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
                ServerUtils.sendCloseSessionMessage(
                        toConnection,
                        toUserName,
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
//            serverGroupInfo.offerMessage(connection, ServerUtils.sendCloseSessionMessage(
//                    false,
//                    "",
//                    systemContent
//            ));
//        }
//
//    }
}

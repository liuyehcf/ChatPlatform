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
                if (serverConnectionDispatcher.getMainConnectionMap().containsKey(account))
                    throw new RuntimeException();
                serverConnectionDispatcher.getMainConnectionMap().put(account, connection);
                //允许客户端登录
                connection.offerMessage(ServerUtils.createReplyLoginInMessage(true, account, serverConnectionDispatcher.getMainConnectionMap().keySet().toString()));

                //刷新好友列表
                for (Connection otherConnection : serverConnectionDispatcher.getMainConnectionMap().values()) {
                    otherConnection.offerMessage(
                            ServerUtils.createLoginInFlushMessage(
                                    otherConnection.getConnectionDescription().getDestination(),
                                    serverConnectionDispatcher.getMainConnectionMap().keySet().toString()));
                }
                //todo 何时deny
            }
            //注销信息
            else if (message.getControl().isLoginOutMessage()) {
                String userName = message.getHeader().getParam1();
                serverConnectionDispatcher.getMainConnectionMap().remove(userName);
                connection.getBindPipeLineTask().offLine(connection);

                //找到该主界面对应的会话连接描述符
                ConnectionDescription connectionDescription = new ConnectionDescription(
                        Protocol.SERVER_USER_NAME,
                        userName);
                Connection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(connectionDescription);

                if (sessionConnection != null) {
                    for (SessionDescription sessionDescription : sessionConnection.getConnectionDescription().getSessionDescriptions()) {
                        closeSession(sessionDescription.getFromUserName(), sessionDescription.getToUserName());
                    }
                    serverConnectionDispatcher.getSessionConnectionMap().remove(connectionDescription);
                    sessionConnection.getBindPipeLineTask().offLine(sessionConnection);
                }
                //todo
                //刷新好友列表
                for (Connection otherConnection : serverConnectionDispatcher.getMainConnectionMap().values()) {
                    otherConnection.offerMessage(
                            ServerUtils.createLoginInFlushMessage(
                                    otherConnection.getConnectionDescription().getDestination(),
                                    serverConnectionDispatcher.getMainConnectionMap().keySet().toString()));
                }
            }
            //是否是新建会话消息
            else if (message.getControl().isOpenSessionMessage()) {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();
                ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session", fromUserName);

                //该Connection第一次建立
                if (connection.getConnectionDescription() == null) {
                    connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                    connection.setMainConnection(false);
                }

                //增加一条会话描述符
                SessionDescription newSessionDescription = new SessionDescription(fromUserName, toUserName);
                connection.getConnectionDescription().addSessionDescription(
                        newSessionDescription
                );

                if (!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription())) {
                    serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
                    ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);

                    String greetContent = fromUserName +
                            "，欢迎进入六爷聊天室!!!";
                    connection.offerMessage(ServerUtils.createSystemMessage(
                            false,
                            fromUserName,
                            greetContent));
                } else {
                    throw new RuntimeException();
                }
            }
            //客户端要求断开连接
            else if (message.getControl().isCloseSessionMessage()) {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();

                SessionDescription sessionDescription = new SessionDescription(fromUserName, toUserName);
                ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);
                connection.getConnectionDescription().removeSessionDescription(sessionDescription);

                closeSession(fromUserName, toUserName);

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
                    //todo 激活对话窗口
                    Connection mainConnection;

                    if ((mainConnection = serverConnectionDispatcher.getMainConnectionMap().get(toUserName)) != null) {
                        mainConnection.offerMessage(
                                ServerUtils.createOpenSessionWindowMessage(
                                        toUserName, fromUserName, message.getBody().getContent()
                                ));
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
    private void checkActive() {
        long currentStamp = System.currentTimeMillis();
        for (Connection connection : getConnections()) {
            if (currentStamp - connection.getRecentActiveTimeStamp() > ServerUtils.MAX_INACTIVE_TIME * 60 * 1000L) {
                connection.offerMessage(ServerUtils.createSystemMessage(
                        true,
                        connection.getConnectionDescription().getSource(),
                        "占着茅坑不拉屎，你可以滚了!!!"
                        )
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


    private void closeSession(String fromUserName, String toUserName) {
        ConnectionDescription connectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
        Connection connection = serverConnectionDispatcher.getSessionConnectionMap().get(connectionDescription);

        //connection为null代表已经关闭了
        if (connection != null) {
            String systemContent = "["
                    + fromUserName
                    + "]已断开连接";
            connection.offerMessage(ServerUtils.createSystemMessage(
                    false,
                    toUserName,
                    systemContent
            ));
        }
    }

    private void closeGroupSession(Connection connection, String fromUserName, String groupName) {
        ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);

        serverGroupInfo.removeConnection(connection);

        if (serverGroupInfo.isGroupEmpty()) {
            serverConnectionDispatcher.getGroupInfoMap().remove(serverGroupInfo.getGroupName());
        } else {
            String systemContent = "["
                    + fromUserName
                    + "]已断开连接";
            serverGroupInfo.offerMessage(connection, ServerUtils.createSystemMessage(
                    false,
                    "",
                    systemContent
            ));
        }

    }
}

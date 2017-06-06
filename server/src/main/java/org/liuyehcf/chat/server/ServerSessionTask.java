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
     * 客户端主界面连接的映射
     * 每个用户只能对应一个Main Connection(主界面对应的Connection)
     */
    private Map<String, Connection> mainConnectionMap;

    /**
     * 用户名到Connection的映射，多个PipeLineTask共享
     */
    private Map<ConnectionDescription, Connection> sessionConnectionMap;

    /**
     * 用户组名到ServerGroupInfo的映射，多个PipeLineTask共享
     */
    private Map<String, ServerGroupInfo> groupInfoMap;

    public ServerSessionTask(
            Map<String, Connection> mainConnectionMap,
            Map<ConnectionDescription, Connection> sessionConnectionMap,
            Map<String, ServerGroupInfo> groupInfoMap) {
        this.mainConnectionMap = mainConnectionMap;
        this.sessionConnectionMap = sessionConnectionMap;
        this.groupInfoMap = groupInfoMap;

        ChatServerDispatcher.getSingleton().getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            checkActive();

            //任一线程做负载均衡
            ChatServerDispatcher.getSingleton().checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ChatServerDispatcher.LOGGER.info("{} is finished", this);
        ChatServerDispatcher.getSingleton().getPipeLineTasks().remove(this);
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

            if (message.getControl().isLoginInMessage()) {
                connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, message.getHeader().getParam1()));
                connection.setMainConnection(true);

                String account = message.getHeader().getParam1();
                if (!mainConnectionMap.containsKey(account)) {
                    mainConnectionMap.put(account, connection);
                    //允许客户端登录
                    connection.offerMessage(ServerUtils.createReplyLoginInMessage(true, account, mainConnectionMap.keySet().toString()));

                    //刷新好友列表
                    for (Connection otherConnection : mainConnectionMap.values()) {
                        otherConnection.offerMessage(
                                ServerUtils.createLoginInFlushMessage(
                                        otherConnection.getConnectionDescription().getDestination(),
                                        mainConnectionMap.keySet().toString()));
                    }

                    //todo 什么时候deny
                } else {
                    //todo
                }
            } else if (message.getControl().isLoginOutMessage()) {
                String user = message.getHeader().getParam1();
                mainConnectionMap.remove(user);
                connection.getBindPipeLineTask().offLine(connection);

                //找到该主界面对应的会话连接描述符
                ConnectionDescription connectionDescription = new ConnectionDescription(
                        Protocol.SERVER_USER_NAME,
                        user);
                //todo
                //刷新好友列表
                for (Connection otherConnection : mainConnectionMap.values()) {
                    otherConnection.offerMessage(
                            ServerUtils.createLoginInFlushMessage(
                                    otherConnection.getConnectionDescription().getDestination(),
                                    mainConnectionMap.keySet().toString()));
                }
            }
            //是否是新建会话消息
            else if (message.getControl().isOpenSessionMessage()) {
                String fromUser = message.getHeader().getParam1();
                String toUser = message.getHeader().getParam2();
                ChatServerDispatcher.LOGGER.info("Client {} open a new Session", fromUser);

                //该Connection第一次建立
                if (connection.getConnectionDescription() == null) {
                    connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUser));
                    connection.setMainConnection(false);
                }

                //增加一条会话描述符
                SessionDescription newSessionDescription = new SessionDescription(fromUser, toUser);
                connection.getConnectionDescription().addSessionDescription(
                        newSessionDescription
                );

                if (!sessionConnectionMap.containsKey(connection.getConnectionDescription())) {
                    sessionConnectionMap.put(connection.getConnectionDescription(), connection);
                    ChatServerDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUser, newSessionDescription);

                    String greetContent = fromUser +
                            "，欢迎进入六爷聊天室!!!";
                    connection.offerMessage(ServerUtils.createSystemMessage(
                            false,
                            fromUser,
                            greetContent));
                } else {
                    throw new RuntimeException();
                }
            }
            //客户端要求断开连接
            else if (message.getControl().isCloseSessionMessage()) {
                String fromUser = message.getHeader().getParam1();
                String toUser = message.getHeader().getParam2();

                SessionDescription sessionDescription = new SessionDescription(fromUser, toUser);
                ChatServerDispatcher.LOGGER.info("The client {} close the session {}", fromUser, sessionDescription);
                connection.getConnectionDescription().removeSessionDescription(sessionDescription);

                closeSession(fromUser, toUser);

                //该连接没有会话了
                if (connection.getConnectionDescription().getSessionDescriptions().isEmpty()) {
                    connection.getBindPipeLineTask().offLine(connection);
                }
            }
            //非群聊
            else {
                String fromUser = message.getHeader().getParam1();
                String toUser = message.getHeader().getParam2();

                ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUser);
                if (sessionConnectionMap.containsKey(toConnectionDescription)) {
                    Connection toConnection = sessionConnectionMap.get(toConnectionDescription);
                    toConnection.offerMessage(message);
                } else {
                    //todo 激活对话窗口
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
        ChatServerDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);

        ServerConnection serverConnection = (ServerConnection) connection;
        if (serverConnection.isMainConnection()) {
            mainConnectionMap.remove(connection.getConnectionDescription().getDestination());
        } else {
            sessionConnectionMap.remove(connection.getConnectionDescription());
        }
    }


    private void closeSession(String fromUser, String toUser) {
        ConnectionDescription connectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUser);
        Connection connection = sessionConnectionMap.get(connectionDescription);

        String systemContent = "["
                + fromUser
                + "]已断开连接";
        connection.offerMessage(ServerUtils.createSystemMessage(
                false,
                toUser,
                systemContent
        ));
    }

    private void closeGroupSession(Connection connection, String fromUser, String groupName) {
        ServerGroupInfo serverGroupInfo = groupInfoMap.get(groupName);

        serverGroupInfo.removeConnection(connection);

        if (serverGroupInfo.isGroupEmpty()) {
            groupInfoMap.remove(serverGroupInfo.getGroupName());
        } else {
            String systemContent = "["
                    + fromUser
                    + "]已断开连接";
            serverGroupInfo.offerMessage(connection, ServerUtils.createSystemMessage(
                    false,
                    "",
                    systemContent
            ));
        }

    }
}

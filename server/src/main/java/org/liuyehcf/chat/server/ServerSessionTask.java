package org.liuyehcf.chat.server;

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
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
    private Map<ConnectionDescription, Connection> connectionMap;

    /**
     * 用户组名到ServerGroupInfo的映射，多个PipeLineTask共享
     */
    private Map<String, ServerGroupInfo> groupInfoMap;

    public ServerSessionTask(
            Map<String, Connection> mainConnectionMap,
            Map<ConnectionDescription, Connection> connectionMap,
            Map<String, ServerGroupInfo> groupInfoMap) {
        this.mainConnectionMap = mainConnectionMap;
        this.connectionMap = connectionMap;
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
        Connection connection = (Connection) selectionKey.attachment();

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

            String source = message.getHeader().getParam1();
            String destination = message.getHeader().getParam2();

            if (message.getControl().isLoginInMessage()) {
                String account = message.getHeader().getParam1();
                if (!mainConnectionMap.containsKey(account)) {
                    mainConnectionMap.put(account, connection);
                    connection.offerMessage(ServerUtils.createReplyLoginInMessage(true, account, mainConnectionMap.keySet().toString()));

                    //todo 什么时候deny
                } else {
                    //todo
                }
            } else if (message.getControl().isLoginOutMessage()) {
                //todo
            }
            //是否是Hello消息
            else if (message.getControl().isHelloMessage()) {
                ChatServerDispatcher.LOGGER.info("Client {} is accessing the server", source);

                connection.setConnectionDescription(new ConnectionDescription(source, destination));

                if (!connectionMap.containsKey(connection.getConnectionDescription())) {
                    connectionMap.put(connection.getConnectionDescription(), connection);
                    ChatServerDispatcher.LOGGER.info("Client {} accesses the server successfully", source);

                    if (isGroupChat(message)) {
                        ChatServerDispatcher.LOGGER.info("This connection is a group chat");

                        connection.setGroupChat(true);
                        String groupName = connection.getConnectionDescription().getDestination();
                        ServerGroupInfo groupConnect;
                        if (groupInfoMap.containsKey(groupName)) {
                            groupConnect = groupInfoMap.get(groupName);
                        } else {
                            groupConnect = new ServerGroupInfo();
                            groupConnect.setGroupName(groupName);
                            groupInfoMap.put(groupName, groupConnect);
                        }
                        groupConnect.addConnection(connection);
                        String greetContent1 = "大家欢迎<"
                                + source
                                + ">进入群聊聊天室!!!";
                        groupConnect.offerMessage(connection, ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent1));

                        String greetContent2 = source +
                                "，欢迎进入群聊聊天室!!!";
                        connection.offerMessage(ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent2));
                    } else {
                        String greetContent = source +
                                "，欢迎进入私人聊天室!!!";
                        connection.offerMessage(ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent));

                    }
                } else {
                    ChatServerDispatcher.LOGGER.info("The name of client {} is already exists", source);

                    String greetContent = source +
                            "名字重复，登录失败";
                    connection.offerMessage(ServerUtils.createSystemMessage(
                            true,
                            source,
                            greetContent));
                }
            }
            //客户端要求断开连接
            else if (message.getControl().isOffLineMessage()) {
                ChatServerDispatcher.LOGGER.info("The client {} request goes offline", source);

                offLine(connection);
            }
            //是否为群聊
            else if (connection.isGroupChat()) {
                String groupName = ((Message) message).getHeader().getParam2();
                ServerGroupInfo groupConnect = groupInfoMap.get(groupName);
                groupConnect.offerMessage(connection, message);
            }
            //非群聊
            else {
                //由于ConnectionDescription的hash与equals是带有方向的，因此必须获取与该用户连接的反向描述符
                ConnectionDescription reverseConnectionDescription = connection.getConnectionDescription().getReverse();
                if (connectionMap.containsKey(reverseConnectionDescription)) {
                    Connection toConnection = connectionMap.get(reverseConnectionDescription);
                    SocketChannel toSocketChannel = toConnection.getSocketChannel();
                    toConnection.offerMessage(message);
                } else {
                    String systemContent = "["
                            + connection.getConnectionDescription().getDestination()
                            + "]未上线";
                    connection.offerMessage(ServerUtils.createSystemMessage(
                            false,
                            source,
                            systemContent
                    ));
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
    public void offLine(Connection connection) {
        ChatServerDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);

        SocketChannel socketChannel = connection.getSocketChannel();

        for (Selector selector : connection.getSelectors()) {
            SelectionKey selectionKey = socketChannel.keyFor(selector);
            if (selectionKey != null) selectionKey.cancel();
        }
        connection.getSelectors().clear();

        if (socketChannel.isConnected()) {
            try {
                socketChannel.finishConnect();
            } catch (IOException e) {
            }
        }

        if (socketChannel.isOpen()) {
            try {
                socketChannel.close();
            } catch (IOException e) {
            }
        }

        connectionMap.remove(connection.getConnectionDescription());

        if (connection.isGroupChat()) {
            ServerGroupInfo groupConnect = groupInfoMap.get(connection.getConnectionDescription().getDestination());
            groupConnect.removeConnection(connection);
            if (groupConnect.isGroupEmpty()) {
                groupInfoMap.remove(groupConnect.getGroupName());
            } else {
                String systemContent = "["
                        + connection.getConnectionDescription().getSource()
                        + "]已断开连接";
                groupConnect.offerMessage(connection, ServerUtils.createSystemMessage(
                        false,
                        connection.getConnectionDescription().getDestination(),
                        systemContent
                ));
            }
        } else {
            //由于ConnectionDescription的hash与equals是带有方向的，因此必须获取与该用户连接的反向描述符
            ConnectionDescription reverseConnectionDescription = connection.getConnectionDescription().getReverse();
            if (connectionMap.containsKey(reverseConnectionDescription)) {
                Connection toConnection = connectionMap.get(reverseConnectionDescription);
                String systemContent = "["
                        + connection.getConnectionDescription().getSource()
                        + "]已断开连接";
                toConnection.offerMessage(ServerUtils.createSystemMessage(
                        false,
                        connection.getConnectionDescription().getDestination(),
                        systemContent
                ));
            }
        }

        getConnections().remove(connection);
        if (getConnectionNum() <= 0)
            getBindThread().interrupt();
    }

    /**
     * 判断是否为群聊
     *
     * @param message
     * @return
     */
    private boolean isGroupChat(Message message) {
        return message.getHeader().getParam2().startsWith("#");
    }
}

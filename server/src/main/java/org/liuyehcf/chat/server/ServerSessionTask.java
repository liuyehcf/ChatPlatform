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

        }
        //具体过程交给拦截器处理
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

}

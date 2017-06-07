package org.liuyehcf.chat.client;

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientSessionTask extends AbstractPipeLineTask {

    /**
     * 单例对象
     */
    private ClientConnectionDispatcher clientConnectionDispatcher = ClientConnectionDispatcher.getSingleton();

    public ClientSessionTask() {
        clientConnectionDispatcher.getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            //负载均衡
            clientConnectionDispatcher.checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ClientConnectionDispatcher.LOGGER.info("{} is finished", this);
        clientConnectionDispatcher.getPipeLineTasks().remove(this);
    }

    private void readMessage() {
        int readyReadNum;
        try {
            readyReadNum = getReadSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
        }

        if (readyReadNum <= 0) return;

        Set<SelectionKey> selectionKeys = getReadSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            readMessageFromConnection(selectionKey);

            iterator.remove();
        }

    }

    private void readMessageFromConnection(SelectionKey selectionKey) {
        ClientSessionConnection connection = (ClientSessionConnection) selectionKey.attachment();

        MessageReader messageReader = connection.getMessageReader();
        List<Message> messages;
        try {
            messages = messageReader.read(connection);
        } catch (IOException e) {
            ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);
            return;
        }

        for (Message message : messages) {
            connection.getSessionWindow(message.getHeader().getParam2()).flushOnWindow(false, message.getControl().isSystemMessage(), message.getDisplayMessageString());
        }
    }

    private void writeMessage() {
        int readyWriteNum;
        try {
            readyWriteNum = getWriteSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
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
        ClientSessionConnection connection = (ClientSessionConnection) selectionKey.attachment();

        Message message = connection.pollMessage();
        if (message != null) {
            MessageWriter messageWriter = connection.getMessageWriter();
            String userName = message.getHeader().getParam1();
            try {
                messageWriter.write(message, connection);

                //如果不是系统消息就打印到会话窗口
                if (!message.getControl().isSystemMessage())
                    connection.getSessionWindow(userName).flushOnWindow(true, false, message.getDisplayMessageString());
                //如果是会话关闭消息
                if (message.getControl().isCloseSessionMessage()) {
                    connection.getBindPipeLineTask().offLine(connection);
                    connection.removeSessionWindow(message.getHeader().getParam1());
                }
            } catch (IOException e) {
                connection.getSessionWindow(userName).flushOnWindow(false, true, "[已失去与服务器的连接]");
                connection.getBindPipeLineTask().offLine(connection);
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
        ClientConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);

        clientConnectionDispatcher.getSessionConnectionMap().remove(connection.getConnectionDescription());


    }
}

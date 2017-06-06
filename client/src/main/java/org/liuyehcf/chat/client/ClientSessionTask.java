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

    public ClientSessionTask() {
        ClientConnectionDispatcher.getSingleton().getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            //负载均衡
            ClientConnectionDispatcher.getSingleton().checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ClientConnectionDispatcher.LOGGER.info("{} is finished", this);
        ClientConnectionDispatcher.getSingleton().getPipeLineTasks().remove(this);
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
            //由于已经添加了拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {

            //服务器告知下线
            if (message.getControl().isCloseSessionMessage()) {
                offLine(connection);
            }

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

            try {
                messageWriter.write(message, connection);
            } catch (IOException e) {
                //由于已经添加了拦截器，这里不做处理
                return;
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
        ClientConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);

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

        ClientConnectionDispatcher.getSingleton().getSessionConnectionMap().remove(connection.getConnectionDescription());

        getConnections().remove(connection);
        if (getConnectionNum() <= 0)
            getBindThread().interrupt();
    }
}

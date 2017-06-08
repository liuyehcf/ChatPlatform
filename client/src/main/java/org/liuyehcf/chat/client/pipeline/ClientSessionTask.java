package org.liuyehcf.chat.client.pipeline;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.SelectionKey;
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
        Connection connection = (Connection) selectionKey.attachment();

        MessageReader messageReader = connection.getMessageReader();
        try {
            messageReader.read(connection);
        } catch (IOException e) {

        }
        //交由拦截器处理
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
        Connection connection = (Connection) selectionKey.attachment();

        Message message = connection.pollMessage();
        if (message != null) {
            MessageWriter messageWriter = connection.getMessageWriter();
            try {
                messageWriter.write(message, connection);
            } catch (IOException e) {

            }
        }
        //交由拦截器处理
    }

    /**
     * 离线的后续处理
     *
     * @param connection
     */
    @Override
    protected void offLinePostProcess(Connection connection) {
        ClientConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);
        if (connection instanceof ClientMainConnection) {
            ClientUtils.ASSERT(clientConnectionDispatcher.getMainConnectionMap().containsKey(connection.getConnectionDescription().getSource()));
            clientConnectionDispatcher.getMainConnectionMap().remove(connection.getConnectionDescription().getSource());
        } else if (connection instanceof ClientSessionConnection) {
            ClientUtils.ASSERT(clientConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));
            clientConnectionDispatcher.getSessionConnectionMap().remove(connection.getConnectionDescription());
        }
    }
}

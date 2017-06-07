package org.liuyehcf.chat.client.pipeline;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.ui.MainWindow;
import org.liuyehcf.chat.client.ui.SessionWindow;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.liuyehcf.chat.protocol.Protocol.Header.*;


/**
 * Created by Liuye on 2017/6/5.
 */
public class ClientMainTask extends AbstractPipeLineTask {

    /**
     * 单例对象
     */
    private ClientConnectionDispatcher clientConnectionDispatcher = ClientConnectionDispatcher.getSingleton();

    public ClientMainTask() {

    }

    @Override
    protected void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ClientConnectionDispatcher.LOGGER.info("{} is finished", this);
        clientConnectionDispatcher.setMainTask(null);
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
        ClientMainConnection connection = (ClientMainConnection) selectionKey.attachment();

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
        ClientMainConnection connection = (ClientMainConnection) selectionKey.attachment();

        Message message = connection.pollMessage();
        if (message != null) {
            MessageWriter messageWriter = connection.getMessageWriter();
            try {
                messageWriter.write(message, connection);
            } catch (IOException e) {

            }
            //交由拦截器处理
        }
    }

    @Override
    protected void offLinePostProcess(Connection connection) {
        ClientConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);
        ClientUtils.ASSERT(clientConnectionDispatcher.getMainWindowMap().containsKey(connection.getConnectionDescription().getSource()));
        clientConnectionDispatcher.getMainWindowMap().remove(connection.getConnectionDescription().getSource());
    }
}

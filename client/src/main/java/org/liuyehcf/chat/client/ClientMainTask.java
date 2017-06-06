package org.liuyehcf.chat.client;

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

import static org.liuyehcf.chat.protocol.Protocol.Header.DENY;
import static org.liuyehcf.chat.protocol.Protocol.Header.FLUSH;
import static org.liuyehcf.chat.protocol.Protocol.Header.PERMIT;


/**
 * Created by Liuye on 2017/6/5.
 */
public class ClientMainTask extends AbstractPipeLineTask {

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
        ClientConnectionDispatcher.getSingleton().setMainTask(null);
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
        List<Message> messages;
        try {
            messages = messageReader.read(connection);
        } catch (IOException e) {
            //由于已经添加了拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {
            //登录消息
            if (message.getControl().isLoginInMessage()) {
                //允许登录
                if (message.getHeader().getParam3().equals(PERMIT)) {
                    String userName = message.getHeader().getParam2();
                    MainWindow mainWindow = ClientConnectionDispatcher.getSingleton().getMainWindowMap().get(userName);

                    //显示主界面
                    mainWindow.setVisible(true);

                    //刷新好友列表
                    mainWindow.flushUserList(ClientUtils.retrieveNames(message.getBody().getContent()));
                } else if (message.getHeader().getParam3().equals(DENY)) {
                    //拒绝登录
                } else if (message.getHeader().getParam3().endsWith(FLUSH)) {
                    String userName = message.getHeader().getParam2();
                    MainWindow mainWindow = ClientConnectionDispatcher.getSingleton().getMainWindowMap().get(userName);

                    //刷新好友列表
                    mainWindow.flushUserList(ClientUtils.retrieveNames(message.getBody().getContent()));
                }
            }
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
        ClientMainConnection connection = (ClientMainConnection) selectionKey.attachment();

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


    @Override
    public void offLine(Connection connection) {

    }
}

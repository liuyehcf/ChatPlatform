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
        List<Message> messages;
        try {
            messages = messageReader.read(connection);
        } catch (IOException e) {
            ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);
            connection.getBindPipeLineTask().offLine(connection);
            return;
        }

        for (Message message : messages) {
            if (message.getControl().isSystemMessage()) {
                if (message.getHeader().getParam1().equals(LOGIN_REPLY)) {
                    //允许登录
                    if (message.getHeader().getParam3().equals(PERMIT)) {
                        String userName = message.getHeader().getParam2();

                        //获取主界面
                        MainWindow mainWindow = clientConnectionDispatcher.getMainWindowMap().get(userName);

                        //显示主界面
                        mainWindow.setVisible(true);
                    } else if (message.getHeader().getParam3().equals(DENY)) {
                        //拒绝登录
                    }
                } else if (message.getHeader().getParam1().equals(FLUSH_FRIEND_LIST)) {
                    String userName = message.getHeader().getParam2();

                    //获取主界面
                    MainWindow mainWindow = clientConnectionDispatcher.getMainWindowMap().get(userName);

                    //刷新好友列表
                    mainWindow.flushUserList(ClientUtils.retrieveNames(message.getBody().getContent()));
                } else if (message.getHeader().getParam1().equals(FLUSH_GROUP_LIST)) {
                    String userName = message.getHeader().getParam2();

                    //获取主界面
                    MainWindow mainWindow = clientConnectionDispatcher.getMainWindowMap().get(userName);

                    //刷新群聊列表
                    mainWindow.flushGroupList(ClientUtils.retrieveNames(message.getBody().getContent()));
                }
            } else if (message.getControl().isLoginInMessage()) {

            } else if (message.getControl().isLoginOutMessage()) {

            } else if (message.getControl().isRegisterMessage()) {

            } else if (message.getControl().isOpenSessionMessage()) {
                String fromUserName = message.getHeader().getParam1();
                String toUserName = message.getHeader().getParam2();
                MainWindow mainWindow = clientConnectionDispatcher.getMainWindowMap().get(fromUserName);
                SessionWindow sessionWindow = mainWindow.createSessionWindow(toUserName);

                Message notSendMessage = ClientUtils.createOpenSessionWindowMessage(
                        toUserName,
                        fromUserName,
                        message.getBody().getContent()
                );
                sessionWindow.flushOnWindow(false, false, notSendMessage.getDisplayMessageString());

            } else if (message.getControl().isCloseSessionMessage()) {

            } else {

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

                if (message.getControl().isSystemMessage()) {

                } else if (message.getControl().isLoginInMessage()) {

                } else if (message.getControl().isLoginOutMessage()) {

                } else if (message.getControl().isRegisterMessage()) {

                } else if (message.getControl().isOpenSessionMessage()) {

                } else if (message.getControl().isCloseSessionMessage()) {

                } else {

                }

                if (message.getControl().isLoginOutMessage()) {
                    connection.getBindPipeLineTask().offLine(connection);
                }

            } catch (IOException e) {
                ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);
                connection.getBindPipeLineTask().offLine(connection);
            }
        }
    }

    @Override
    protected void offLinePostProcess(Connection connection) {
        ClientConnectionDispatcher.LOGGER.info("Connection {} is getOff from {}", connection, this);
        clientConnectionDispatcher.getMainWindowMap().remove(connection.getConnectionDescription().getSource());
    }
}

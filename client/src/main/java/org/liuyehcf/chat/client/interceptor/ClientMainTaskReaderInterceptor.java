package org.liuyehcf.chat.client.interceptor;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.ui.MainWindow;
import org.liuyehcf.chat.client.ui.SessionWindow;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;
import java.util.List;

import static org.liuyehcf.chat.protocol.Protocol.Header.*;
import static org.liuyehcf.chat.protocol.Protocol.Header.FLUSH_GROUP_LIST;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ClientMainTaskReaderInterceptor implements MessageInterceptor {

    /**
     * 单例对象
     */
    private final ClientConnectionDispatcher clientConnectionDispatcher;

    /**
     * 协议
     */
    private Protocol protocol = new Protocol();

    public ClientMainTaskReaderInterceptor(ClientConnectionDispatcher clientConnectionDispatcher) {
        this.clientConnectionDispatcher = clientConnectionDispatcher;
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        List<Message> messages;
        try {
            messages = (List<Message>) messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientMainConnection connection = (ClientMainConnection) proxyMethodInvocation.getArguments()[0];

            for (Message message : messages) {

                if (message.getControl().isSystemMessage()) {
                    processSystemMessage(connection, message);
                } else if (message.getControl().isLoginInMessage()) {
                    processLoginInMessage(connection, message);
                } else if (message.getControl().isLoginOutMessage()) {
                    processLoginOutMessage(connection, message);
                } else if (message.getControl().isOpenSessionMessage()) {
                    processOpenSessionMessage(connection, message);
                } else if (message.getControl().isCloseSessionMessage()) {
                    processCloseSessionMessage(connection, message);
                } else {
                    processNormalMessage(connection, message);
                }
            }
        } catch (IOException e) {
            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientMainConnection connection = (ClientMainConnection) proxyMethodInvocation.getArguments()[0];

            ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);
            connection.getBindPipeLineTask().offLine(connection);
            throw e;
        }

        return messages;
    }

    /**
     * 处理系统消息
     *
     * @param connection
     * @param message
     */
    private void processSystemMessage(ClientMainConnection connection, Message message) {
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
    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginInMessage(ClientMainConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginOutMessage(ClientMainConnection connection, Message message) {
        connection.getBindMainWindow().showMessage(message.getBody().getContent());

        connection.getBindPipeLineTask().offLine(connection);

        ClientSessionConnection sessionConnection = clientConnectionDispatcher.getSessionConnectionMap().get(connection.getConnectionDescription());

        if (sessionConnection != null)
            sessionConnection.getBindPipeLineTask().offLine(sessionConnection);
    }

    /**
     * @param connection
     * @param message
     */
    private void processOpenSessionMessage(ClientMainConnection connection, Message message) {
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

    }

    /**
     * @param connection
     * @param message
     */
    private void processCloseSessionMessage(ClientMainConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processNormalMessage(ClientMainConnection connection, Message message) {

    }
}

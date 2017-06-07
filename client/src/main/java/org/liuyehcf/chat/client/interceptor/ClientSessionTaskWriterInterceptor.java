package org.liuyehcf.chat.client.interceptor;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.connect.SessionDescription;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ClientSessionTaskWriterInterceptor implements MessageInterceptor {

    /**
     * 单例对象
     */
    private final ClientConnectionDispatcher clientConnectionDispatcher;

    /**
     * 协议
     */
    private Protocol protocol = new Protocol();

    public ClientSessionTaskWriterInterceptor(ClientConnectionDispatcher clientConnectionDispatcher) {
        this.clientConnectionDispatcher = clientConnectionDispatcher;
    }


    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        Object result;
        try {
            result = messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientSessionConnection connection = (ClientSessionConnection) proxyMethodInvocation.getArguments()[1];

            Message message = (Message) proxyMethodInvocation.getArguments()[0];

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
        } catch (IOException e) {
            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientSessionConnection connection = (ClientSessionConnection) proxyMethodInvocation.getArguments()[1];
            Message message = (Message) proxyMethodInvocation.getArguments()[0];

            ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);

            if (message.getControl().isGroupChat()) {
                String groupName = message.getHeader().getParam2();
                connection.getGroupSessionWindow(groupName).flushOnWindow(false, true, "[已失去与服务器的连接]");
            } else {
                String toUserName = message.getHeader().getParam2();
                connection.getSessionWindow(toUserName).flushOnWindow(false, true, "[已失去与服务器的连接]");
            }
            connection.getBindPipeLineTask().offLine(connection);
            throw e;
        }
        return result;
    }

    /**
     * 处理系统消息
     *
     * @param connection
     * @param message
     */
    private void processSystemMessage(ClientSessionConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginInMessage(ClientSessionConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginOutMessage(ClientSessionConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processOpenSessionMessage(ClientSessionConnection connection, Message message) {
        if (message.getControl().isGroupChat()) {
            String fromUserName = message.getHeader().getParam1();
            String groupName = message.getHeader().getParam2();
            SessionDescription newSessionDescription = new SessionDescription(
                    fromUserName,
                    groupName,
                    true);
            ClientUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(newSessionDescription));
            ClientConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);
        } else {
            String fromUserName = message.getHeader().getParam1();
            String toUserName = message.getHeader().getParam2();
            SessionDescription newSessionDescription = new SessionDescription(
                    fromUserName,
                    toUserName,
                    false);
            ClientUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(newSessionDescription));
            ClientConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);
        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processCloseSessionMessage(ClientSessionConnection connection, Message message) {
        if (message.getControl().isGroupChat()) {
            String fromUserName = message.getHeader().getParam1();
            String groupName = message.getHeader().getParam2();
            SessionDescription sessionDescription = new SessionDescription(
                    fromUserName,
                    groupName,
                    true);

            //如果启动SessionWindow或者GroupSessionWindows失败时，会直接调用dispose方法，因此会话是不存在的，因此允许删除失败
            connection.getConnectionDescription().removeSessionDescription(sessionDescription);
            ClientConnectionDispatcher.LOGGER.info("Client {} close a Session {} successfully", message.getHeader().getParam1(), sessionDescription);
            connection.removeGroupSessionWindow(groupName);

        } else {
            String fromUserName = message.getHeader().getParam1();
            String toUserName = message.getHeader().getParam2();
            SessionDescription sessionDescription = new SessionDescription(
                    fromUserName,
                    toUserName,
                    false);

            //如果启动SessionWindow或者GroupSessionWindows失败时，会直接调用dispose方法，因此会话是不存在的，因此允许删除失败
            connection.getConnectionDescription().removeSessionDescription(sessionDescription);
            ClientConnectionDispatcher.LOGGER.info("Client {} close a Session {} successfully", message.getHeader().getParam1(), sessionDescription);
            connection.removeSessionWindow(toUserName);
        }
        if (connection.getConnectionDescription().getSessionDescriptions().isEmpty()) {
            connection.getBindPipeLineTask().offLine(connection);
        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processNormalMessage(ClientSessionConnection connection, Message message) {
        if (message.getControl().isGroupChat()) {
            String groupName = message.getHeader().getParam2();
            connection.getGroupSessionWindow(groupName).flushOnWindow(true, false, message.getDisplayMessageString());
        } else {
            String toUserName = message.getHeader().getParam2();
            connection.getSessionWindow(toUserName).flushOnWindow(true, false, message.getDisplayMessageString());
        }
    }
}

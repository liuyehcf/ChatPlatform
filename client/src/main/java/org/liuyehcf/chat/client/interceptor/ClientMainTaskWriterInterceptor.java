package org.liuyehcf.chat.client.interceptor;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ClientMainTaskWriterInterceptor implements MessageInterceptor {

    /**
     * 单例对象
     */
    private final ClientConnectionDispatcher clientConnectionDispatcher;

    /**
     * 协议
     */
    private Protocol protocol = new Protocol();

    public ClientMainTaskWriterInterceptor(ClientConnectionDispatcher clientConnectionDispatcher) {
        this.clientConnectionDispatcher = clientConnectionDispatcher;
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        Object result;
        try {
            result = messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientMainConnection connection = (ClientMainConnection) proxyMethodInvocation.getArguments()[1];

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
            ClientMainConnection connection = (ClientMainConnection) proxyMethodInvocation.getArguments()[1];

            ClientConnectionDispatcher.LOGGER.info("MainConnection {} 已失去与服务器的连接", connection);
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
    private void processSystemMessage(ClientMainConnection connection, Message message) {

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
        connection.getBindPipeLineTask().offLine(connection);
    }

    /**
     * @param connection
     * @param message
     */
    private void processOpenSessionMessage(ClientMainConnection connection, Message message) {

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

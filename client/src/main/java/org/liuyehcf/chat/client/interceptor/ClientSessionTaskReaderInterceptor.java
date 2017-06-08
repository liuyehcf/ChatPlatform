package org.liuyehcf.chat.client.interceptor;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;
import java.util.List;

import static org.liuyehcf.chat.protocol.Protocol.Header.FLUSH_GROUP_SESSION_USER_LIST;
import static org.liuyehcf.chat.protocol.Protocol.Header.LOGIN_OUT_NOTIFY;
import static org.liuyehcf.chat.protocol.Protocol.Header.NOT_ONLINE;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ClientSessionTaskReaderInterceptor implements MessageInterceptor {

    /**
     * 单例对象
     */
    private final ClientConnectionDispatcher clientConnectionDispatcher;

    /**
     * 协议
     */
    private Protocol protocol = new Protocol();

    public ClientSessionTaskReaderInterceptor(ClientConnectionDispatcher clientConnectionDispatcher) {
        this.clientConnectionDispatcher = clientConnectionDispatcher;
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        List<Message> messages;
        try {
            messages = (List<Message>) messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientSessionConnection connection = (ClientSessionConnection) proxyMethodInvocation.getArguments()[0];

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
            ClientSessionConnection connection = (ClientSessionConnection) proxyMethodInvocation.getArguments()[0];

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
    private void processSystemMessage(ClientSessionConnection connection, Message message) {
        if (message.getHeader().getParam1().equals(LOGIN_OUT_NOTIFY)) {
            //参见ServerUtils.sendLoginOutNotifyMessage方法
            String toUserName = message.getHeader().getParam3();
            connection.getSessionWindow(toUserName).flushOnWindow(false, true, message.getDisplayMessageString());
        } else if (message.getHeader().getParam1().equals(NOT_ONLINE)) {
            //参见ServerUtils.sendNotOnLineMessage方法
            String toUserName = message.getHeader().getParam3();
            connection.getSessionWindow(toUserName).flushOnWindow(false, true, message.getDisplayMessageString());
        } else if (message.getHeader().getParam1().equals(FLUSH_GROUP_SESSION_USER_LIST)) {
            //参见ServerUtils.createFlushGroupSessionUserListMessage以及ServerGroupInfo.offerMessage方法
            String groupName = message.getHeader().getParam3();
            connection.getGroupSessionWindow(groupName).flushGroupSessionUserList(ClientUtils.retrieveNames(message.getBody().getContent()));
        }
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

    }

    /**
     * @param connection
     * @param message
     */
    private void processCloseSessionMessage(ClientSessionConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processNormalMessage(ClientSessionConnection connection, Message message) {
        if (message.getControl().isGroupChat()) {
            String groupName = message.getHeader().getParam3();
            connection.getGroupSessionWindow(groupName)
                    .flushOnWindow(false, false, message.getDisplayMessageString());
        } else {
            String fromUserName = message.getHeader().getParam1();
            connection.getSessionWindow(fromUserName)
                    .flushOnWindow(false, false, message.getDisplayMessageString());
        }
    }
}

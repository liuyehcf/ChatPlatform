package org.liuyehcf.chat.server.interceptor;

import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.server.connection.ServerConnection;
import org.liuyehcf.chat.server.ServerConnectionDispatcher;
import org.liuyehcf.chat.server.utils.ServerGroupInfo;
import org.liuyehcf.chat.server.utils.ServerUtils;

import java.io.IOException;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ServerMessageWriterInterceptor implements MessageInterceptor {
    /**
     * 单例对象，这里不能直接调用静态方法进行初始化，会造成死循环
     */
    private final ServerConnectionDispatcher serverConnectionDispatcher;

    /**
     * 协议
     */
    private Protocol protocol = new Protocol();

    public ServerMessageWriterInterceptor(ServerConnectionDispatcher serverConnectionDispatcher) {
        this.serverConnectionDispatcher = serverConnectionDispatcher;
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        Object result;
        try {
            result = messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ServerConnection connection = (ServerConnection) proxyMethodInvocation.getArguments()[1];

            Message message = (Message) proxyMethodInvocation.getArguments()[0];

            ServerConnectionDispatcher.LOGGER.debug("Send a message {}", protocol.wrap(message));

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
            ServerConnection connection = (ServerConnection) proxyMethodInvocation.getArguments()[1];
            ServerConnectionDispatcher.LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
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
    private void processSystemMessage(ServerConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginInMessage(ServerConnection connection, Message message) {

    }

    /**
     * @param mainConnection
     * @param message
     */
    private void processLoginOutMessage(ServerConnection mainConnection, Message message) {
        ServerUtils.ASSERT(mainConnection.isMainConnection());

        ServerConnection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(mainConnection.getConnectionDescription());

        mainConnection.getBindPipeLineTask().offLine(mainConnection);

        //可能有些连接就没有会话存在
        if (sessionConnection != null) {
            sessionConnection.getBindPipeLineTask().offLine(sessionConnection);

            String userName = sessionConnection.getConnectionDescription().getDestination();

            for (ServerGroupInfo serverGroupInfo : serverConnectionDispatcher.getGroupInfoMap().values()) {
                if (serverGroupInfo.getGroupSessionConnectionMap().containsKey(userName)) {
                    serverGroupInfo.removeConnection(userName);
                }

                //刷新群成员列表
                serverGroupInfo.offerMessage(
                        null,
                        ServerUtils.createFlushGroupSessionUserListMessage(
                                serverGroupInfo.getGroupSessionConnectionMap().keySet().toString()
                        ));
            }
        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processOpenSessionMessage(ServerConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processCloseSessionMessage(ServerConnection connection, Message message) {

    }

    /**
     * @param connection
     * @param message
     */
    private void processNormalMessage(ServerConnection connection, Message message) {

    }
}

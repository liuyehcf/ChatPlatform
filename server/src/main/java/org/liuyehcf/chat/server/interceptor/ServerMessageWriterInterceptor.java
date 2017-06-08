package org.liuyehcf.chat.server.interceptor;

import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.server.connection.ServerConnection;
import org.liuyehcf.chat.server.ServerConnectionDispatcher;
import org.liuyehcf.chat.server.utils.ServerGroupInfo;
import org.liuyehcf.chat.server.utils.ServerUtils;

import java.io.IOException;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ServerMessageWriterInterceptor extends AbstractServerMessageInterceptor {

    public ServerMessageWriterInterceptor(ServerConnectionDispatcher serverConnectionDispatcher) {
        super(serverConnectionDispatcher);
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
            } else if (message.getControl().isLogInMessage()) {
                processLogInMessage(connection, message);
            } else if (message.getControl().isLogOutMessage()) {
                processLogOutMessage(connection, message);
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
    private void processLogInMessage(ServerConnection connection, Message message) {

    }

    /**
     * @param mainConnection
     * @param message
     */
    private void processLogOutMessage(ServerConnection mainConnection, Message message) {
        ServerUtils.ASSERT(mainConnection.isMainConnection());

        mainConnection.getBindPipeLineTask().offLine(mainConnection);

        ServerConnection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(mainConnection.getConnectionDescription());

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

        //刷新好友列表
        refreshFriendList();
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

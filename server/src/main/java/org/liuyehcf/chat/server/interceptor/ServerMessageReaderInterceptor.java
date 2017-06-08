package org.liuyehcf.chat.server.interceptor;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.connect.ConnectionDescription;
import org.liuyehcf.chat.connect.SessionDescription;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.server.connection.ServerConnection;
import org.liuyehcf.chat.server.ServerConnectionDispatcher;
import org.liuyehcf.chat.server.utils.ServerGroupInfo;
import org.liuyehcf.chat.server.utils.ServerUtils;

import java.io.IOException;
import java.util.List;

import static org.liuyehcf.chat.protocol.Protocol.Header.APPLY_GROUP_NAME;

/**
 * Created by Liuye on 2017/6/7.
 */
public class ServerMessageReaderInterceptor extends AbstractServerMessageInterceptor {

    public ServerMessageReaderInterceptor(ServerConnectionDispatcher serverConnectionDispatcher) {
        super(serverConnectionDispatcher);
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        List<Message> messages;
        try {
            messages = (List<Message>) messageInvocation.process();

            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ServerConnection connection = (ServerConnection) proxyMethodInvocation.getArguments()[0];

            for (Message message : messages) {
                connection.activeNow();

                ServerConnectionDispatcher.LOGGER.debug("Receive a message {}", protocol.wrap(message));

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
            ServerConnection connection = (ServerConnection) proxyMethodInvocation.getArguments()[0];

            ServerConnectionDispatcher.LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
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
    private void processSystemMessage(ServerConnection connection, Message message) {
        if (message.getHeader().getParam1().equals(APPLY_GROUP_NAME)) {
            String groupName = message.getHeader().getParam3();
            if (!serverConnectionDispatcher.getGroupInfoMap().containsKey(groupName)) {
                serverConnectionDispatcher.getGroupInfoMap().put(groupName, new ServerGroupInfo(groupName));

                //刷新群聊列表
                refreshGroupList();
            } else {
                //失败说明冲突，同样会刷新，这里什么都不用做
            }
        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginInMessage(ServerConnection connection, Message message) {
        connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, message.getHeader().getParam1()));
        connection.setMainConnection(true);

        String account = message.getHeader().getParam1();
        ServerUtils.ASSERT(!serverConnectionDispatcher.getMainConnectionMap().containsKey(account));

        serverConnectionDispatcher.getMainConnectionMap().put(account, connection);

        //发送消息，允许客户端登录
        ServerUtils.sendReplyLoginInMessage(
                connection,
                true,
                account);

        //刷新好友列表
        refreshFriendList();

        //刷新群聊列表
        refreshGroupList();
        //todo 何时deny
    }

    /**
     * @param connection
     * @param message
     */
    private void processLoginOutMessage(ServerConnection connection, Message message) {
        String userName = message.getHeader().getParam1();
        ServerUtils.ASSERT(serverConnectionDispatcher.getMainConnectionMap().containsKey(userName));

        //关闭主界面连接
        connection.getBindPipeLineTask().offLine(connection);

        //找到该主界面对应的会话连接描述符
        ConnectionDescription connectionDescription = new ConnectionDescription(
                Protocol.SERVER_USER_NAME,
                userName);

        //获取会话连接
        Connection sessionConnection = serverConnectionDispatcher.getSessionConnectionMap().get(connectionDescription);

        //若为空，则代表该用户没有开启任何会话
        if (sessionConnection != null) {
            //注销通知
            loginOutNotify(sessionConnection);

            //关闭会话连接
            sessionConnection.getBindPipeLineTask().offLine(sessionConnection);
        }
        //刷新好友列表
        refreshFriendList();
    }

    /**
     * @param connection
     * @param message
     */
    private void processOpenSessionMessage(ServerConnection connection, Message message) {
        //群聊
        if (message.getControl().isGroupChat()) {
            String fromUserName = message.getHeader().getParam1();
            String groupName = message.getHeader().getParam2();
            ServerConnectionDispatcher.LOGGER.info("Client {} open a new GroupSession {}", fromUserName, groupName);

            //该Connection第一次建立，服务端Connection建立时是没有描述符的，因为没有接到任何消息
            if (connection.getConnectionDescription() == null) {
                connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                connection.setMainConnection(false);

                ServerUtils.ASSERT(!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));
                serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
            }

            //增加一条会话描述符
            SessionDescription newSessionDescription = new SessionDescription(
                    fromUserName,
                    groupName,
                    true);
            ServerUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(
                    newSessionDescription));

            ServerConnectionDispatcher.LOGGER.info("Client {} open a new GroupSession {} successfully", fromUserName, newSessionDescription);

            ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
            serverGroupInfo.addConnection(fromUserName, connection);

            //刷新群成员列表
            serverGroupInfo.offerMessage(
                    null,
                    ServerUtils.createFlushGroupSessionUserListMessage(
                            serverGroupInfo.getGroupSessionConnectionMap().keySet().toString()
                    ));
        }
        //非群聊
        else {
            String fromUserName = message.getHeader().getParam1();
            String toUserName = message.getHeader().getParam2();
            ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session", fromUserName);

            //该Connection第一次建立，服务端Connection建立时是没有描述符的，因为没有接到任何消息
            if (connection.getConnectionDescription() == null) {
                connection.setConnectionDescription(new ConnectionDescription(Protocol.SERVER_USER_NAME, fromUserName));
                connection.setMainConnection(false);

                ServerUtils.ASSERT(!serverConnectionDispatcher.getSessionConnectionMap().containsKey(connection.getConnectionDescription()));
                serverConnectionDispatcher.getSessionConnectionMap().put(connection.getConnectionDescription(), connection);
            }

            //增加一条会话描述符
            SessionDescription newSessionDescription = new SessionDescription(
                    fromUserName,
                    toUserName,
                    false);
            ServerUtils.ASSERT(connection.getConnectionDescription().addSessionDescription(
                    newSessionDescription));

            ServerConnectionDispatcher.LOGGER.info("Client {} open a new Session {} successfully", fromUserName, newSessionDescription);

        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processCloseSessionMessage(ServerConnection connection, Message message) {
        //群聊
        if (message.getControl().isGroupChat()) {
            String fromUserName = message.getHeader().getParam1();
            String groupName = message.getHeader().getParam2();

            SessionDescription sessionDescription = new SessionDescription(
                    fromUserName,
                    groupName,
                    true);
            ServerUtils.ASSERT(connection.getConnectionDescription().removeSessionDescription(sessionDescription));
            ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);

            ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
            serverGroupInfo.removeConnection(fromUserName);

            //刷新群成员列表
            serverGroupInfo.offerMessage(
                    null,
                    ServerUtils.createFlushGroupSessionUserListMessage(
                            serverGroupInfo.getGroupSessionConnectionMap().keySet().toString()
                    ));
        }
        //非群聊
        else {
            String fromUserName = message.getHeader().getParam1();
            String toUserName = message.getHeader().getParam2();

            SessionDescription sessionDescription = new SessionDescription(
                    fromUserName,
                    toUserName,
                    false);
            ServerUtils.ASSERT(connection.getConnectionDescription().removeSessionDescription(sessionDescription));
            ServerConnectionDispatcher.LOGGER.info("The client {} close the session {}", fromUserName, sessionDescription);

        }
        //该连接没有会话了
        if (connection.getConnectionDescription().getSessionDescriptions().isEmpty()) {
            connection.getBindPipeLineTask().offLine(connection);
        }
    }

    /**
     * @param connection
     * @param message
     */
    private void processNormalMessage(ServerConnection connection, Message message) {
        //群聊
        if (message.getControl().isGroupChat()) {
            String groupName = message.getHeader().getParam2();

            ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);

            serverGroupInfo.offerMessage(connection, message);
        }
        //非群聊
        else {
            String fromUserName = message.getHeader().getParam1();
            String toUserName = message.getHeader().getParam2();

            ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
            //连接存在
            if (serverConnectionDispatcher.getSessionConnectionMap().containsKey(toConnectionDescription)) {
                Connection toConnection = serverConnectionDispatcher.getSessionConnectionMap().get(toConnectionDescription);

                SessionDescription toSessionDescription = new SessionDescription(
                        toUserName,
                        fromUserName,
                        false);
                //会话也存在
                if (toConnection.getConnectionDescription().getSessionDescriptions().contains(toSessionDescription)) {
                    toConnection.offerMessage(message);
                }
                //会话不存在
                else {
                    Connection mainConnection;
                    if ((mainConnection = serverConnectionDispatcher.getMainConnectionMap().get(toUserName)) != null) {

                        //发送一条消息要求客户端代开会话窗口
                        ServerUtils.sendOpenSessionWindowMessage(
                                mainConnection,
                                toUserName,
                                fromUserName,
                                message.getBody().getContent());
                    } else {
                        ServerUtils.sendNotOnLineMessage(
                                connection,
                                fromUserName,
                                toUserName,
                                "<" + toUserName + ">已经离线");
                    }
                }
            } else {
                Connection mainConnection;
                if ((mainConnection = serverConnectionDispatcher.getMainConnectionMap().get(toUserName)) != null) {

                    //发送一条消息要求客户端代开会话窗口
                    ServerUtils.sendOpenSessionWindowMessage(
                            mainConnection,
                            toUserName,
                            fromUserName,
                            message.getBody().getContent());
                } else {
                    ServerUtils.sendNotOnLineMessage(
                            connection,
                            fromUserName,
                            toUserName,
                            "<" + toUserName + ">已经离线");
                }
            }
        }
    }

}

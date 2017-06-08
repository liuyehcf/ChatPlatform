package org.liuyehcf.chat.server.interceptor;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.connect.ConnectionDescription;
import org.liuyehcf.chat.connect.SessionDescription;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.server.ServerConnectionDispatcher;
import org.liuyehcf.chat.server.utils.ServerGroupInfo;
import org.liuyehcf.chat.server.utils.ServerUtils;

import java.io.IOException;

/**
 * Created by Liuye on 2017/6/8.
 */
public class AbstractServerMessageInterceptor implements MessageInterceptor {
    /**
     * 单例对象，这里不能直接调用静态方法进行初始化，会造成死循环
     */
    protected final ServerConnectionDispatcher serverConnectionDispatcher;

    /**
     * 协议
     */
    protected Protocol protocol = new Protocol();

    AbstractServerMessageInterceptor(ServerConnectionDispatcher serverConnectionDispatcher) {
        this.serverConnectionDispatcher = serverConnectionDispatcher;
    }

    @Override
    public Object intercept(MessageInvocation messageInvocation) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 刷新群聊列表
     */
    protected void refreshGroupList() {
        String listString = serverConnectionDispatcher.getGroupInfoMap().keySet().toString();
        for (Connection connection : serverConnectionDispatcher.getMainConnectionMap().values()) {
            //发送系统消息，要求客户端刷新群聊列表
            ServerUtils.sendFlushGroupListMessage(
                    connection,
                    connection.getConnectionDescription().getDestination(),
                    listString);
        }
    }

    /**
     * 刷新好友列表
     */
    protected void refreshFriendList() {
        String listString = serverConnectionDispatcher.getMainConnectionMap().keySet().toString();
        for (Connection connection : serverConnectionDispatcher.getMainConnectionMap().values()) {
            //发送系统消息，要求客户端刷新主界面好友列表
            ServerUtils.sendFlushFriendListMessage(
                    connection,
                    connection.getConnectionDescription().getDestination(),
                    listString);
        }
    }

    /**
     * 离线通知
     *
     * @param connection
     */
    protected void loginOutNotify(Connection connection) {
        //遍历会话连接的所有会话描述符
        for (SessionDescription sessionDescription : connection.getConnectionDescription().getSessionDescriptions()) {
            if (sessionDescription.isGroupChat()) {
                String fromUserName = sessionDescription.getFromUserName();
                String groupName = sessionDescription.getToUserName();

                ServerGroupInfo serverGroupInfo = serverConnectionDispatcher.getGroupInfoMap().get(groupName);
                serverGroupInfo.removeConnection(fromUserName);

                //刷新群成员列表
                serverGroupInfo.offerMessage(
                        null,
                        ServerUtils.createFlushGroupSessionUserListMessage(
                                serverGroupInfo.getGroupSessionConnectionMap().keySet().toString()
                        ));
            } else {
                String fromUserName = sessionDescription.getFromUserName();
                String toUserName = sessionDescription.getToUserName();

                ConnectionDescription toConnectionDescription = new ConnectionDescription(Protocol.SERVER_USER_NAME, toUserName);
                Connection toConnection = serverConnectionDispatcher.getSessionConnectionMap().get(toConnectionDescription);

                //connection为null代表已经关闭了
                if (toConnection != null) {
                    String systemContent = "["
                            + fromUserName
                            + "]已断开连接";
                    ServerUtils.sendLoginOutNotifyMessage(
                            toConnection,
                            toUserName,
                            fromUserName,
                            systemContent);
                }
            }
        }
    }
}

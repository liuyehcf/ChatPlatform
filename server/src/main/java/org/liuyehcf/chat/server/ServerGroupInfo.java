package org.liuyehcf.chat.server;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by HCF on 2017/5/30.
 */
public class ServerGroupInfo {

    /**
     * 群聊名
     */
    private final String groupName;

    /**
     * 当前群聊的Connection映射
     */
    private Map<String, Connection> groupSessionConnectionMap;

    public String getGroupName() {
        return groupName;
    }

    public ServerGroupInfo(String groupName) {
        this.groupName = groupName;
        groupSessionConnectionMap = new ConcurrentHashMap<String, Connection>();
    }

    public void addConnection(String userName, Connection connection) {
        ServerUtils.ASSERT(!groupSessionConnectionMap.containsKey(userName));
        groupSessionConnectionMap.put(userName, connection);
    }

    public void removeConnection(String userName) {
        ServerUtils.ASSERT(groupSessionConnectionMap.containsKey(userName));
        groupSessionConnectionMap.remove(userName);

    }

    public Map<String, Connection> getGroupSessionConnectionMap() {
        return groupSessionConnectionMap;
    }

    public void offerMessage(Connection fromConnection, Message message) {
        for (Connection toConnection : groupSessionConnectionMap.values()) {
            if (toConnection != fromConnection) {
                toConnection.offerMessage(getModifiedMessage(toConnection, message));
            }
        }
    }

    /**
     * 改造Message
     * param1：信息发送者
     * param2：connection代表的用户
     * param3：组名
     *
     * @param connection
     * @param message
     * @return
     */
    private Message getModifiedMessage(Connection connection, Message message) {
        Message modifiedMessage = message.getClonedMessage();
        modifiedMessage.getHeader().setParam2(connection.getConnectionDescription().getSource());
        modifiedMessage.getHeader().setParam3(groupName);
        return modifiedMessage;
    }
}

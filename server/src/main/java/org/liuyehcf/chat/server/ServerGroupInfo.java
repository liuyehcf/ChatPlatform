package org.liuyehcf.chat.server;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;

import java.util.*;

/**
 * Created by HCF on 2017/5/30.
 */
public class ServerGroupInfo {

    /**
     * 群聊名
     */
    private String groupName;

    /**
     * 当前群聊的Connection集合
     */
    private Set<Connection> connections;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ServerGroupInfo() {
        connections = new HashSet<Connection>();
    }

    public boolean addConnection(Connection connection) {
        synchronized (connections) {
            return connections.add(connection);
        }
    }

    public boolean removeConnection(Connection connection) {
        synchronized (connections) {
            return connections.remove(connection);
        }
    }

    public boolean isGroupEmpty() {
        synchronized (connections) {
            return connections.isEmpty();
        }
    }

    public void offerMessage(Connection fromConnection, Message message) {
        synchronized (connections) {
            for (Connection toConnection : connections) {
                if (toConnection != fromConnection) {
                    toConnection.offerMessage(getModifiedMessage(toConnection, message));
                }
            }
        }
    }

    private Message getModifiedMessage(Connection connection, Message message) {
        Message modifiedMessage = message.getClonedMessage();
        modifiedMessage.getHeader().setParam2(connection.getConnectionDescription().getSource());
        return modifiedMessage;
    }
}

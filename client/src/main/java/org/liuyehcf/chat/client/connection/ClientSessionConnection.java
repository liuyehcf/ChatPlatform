package org.liuyehcf.chat.client.connection;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.ui.GroupSessionWindow;
import org.liuyehcf.chat.client.ui.SessionWindow;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientSessionConnection extends Connection {
    /**
     * 绑定在该连接上的SessionWindow
     */
    private Map<String, SessionWindow> sessionWindowMap;

    /**
     * 绑定在该连接上的GroupSessionWindow
     */
    private Map<String, GroupSessionWindow> groupSessionWindowMap;

    /**
     * 构造函数，允许抛出异常，交给ChatWindows处理
     *
     * @param source
     * @param destination
     * @param messageReaderFactory
     * @param messageWriterFactory
     * @param inetSocketAddress
     * @throws IOException
     */
    public ClientSessionConnection(String source,
                                   String destination,
                                   MessageReaderFactory messageReaderFactory,
                                   MessageWriterFactory messageWriterFactory,
                                   InetSocketAddress inetSocketAddress) throws IOException {

        super(messageReaderFactory, messageWriterFactory);
        setConnectionDescription(new ConnectionDescription(source, destination));

        socketChannel = SocketChannel.open();
        socketChannel.connect(inetSocketAddress);

        sessionWindowMap = new ConcurrentHashMap<String, SessionWindow>();
        groupSessionWindowMap = new ConcurrentHashMap<String, GroupSessionWindow>();

        //ClientUtils.ASSERT(!ClientConnectionDispatcher.getSingleton().getSessionConnectionMap().containsKey(getConnectionDescription()));
        ClientConnectionDispatcher.getSingleton().getSessionConnectionMap().put(getConnectionDescription(), this);
    }

    public void addSessionWindow(String userName, SessionWindow sessionWindow) {
        ClientUtils.ASSERT(!sessionWindowMap.containsKey(userName));
        sessionWindowMap.put(userName, sessionWindow);
    }

    public void removeSessionWindow(String userName) {
        ClientUtils.ASSERT(sessionWindowMap.containsKey(userName));
        sessionWindowMap.remove(userName);
    }

    public SessionWindow getSessionWindow(String userName) {
        return sessionWindowMap.get(userName);
    }


    public void addGroupSessionWindow(String groupName, GroupSessionWindow groupSessionWindow) {
        ClientUtils.ASSERT(!groupSessionWindowMap.containsKey(groupName));
        groupSessionWindowMap.put(groupName, groupSessionWindow);
    }

    public void removeGroupSessionWindow(String groupName) {
        ClientUtils.ASSERT(groupSessionWindowMap.containsKey(groupName));
        groupSessionWindowMap.remove(groupName);
    }

    public GroupSessionWindow getGroupSessionWindow(String groupName) {
        return groupSessionWindowMap.get(groupName);
    }

}

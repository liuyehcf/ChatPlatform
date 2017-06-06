package org.liuyehcf.chat.client;

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientSessionConnection extends Connection {
    /**
     * 绑定在该连接上的SessionWindow
     */
    private Map<String, SessionWindow> chatWindowMap;

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

        chatWindowMap = new HashMap<String, SessionWindow>();

        ClientConnectionDispatcher.getSingleton().getSessionConnectionMap().put(getConnectionDescription(), this);
    }

    public void addSessionWindow(String userName, SessionWindow sessionWindow) {
        if (chatWindowMap.containsKey(userName))
            throw new RuntimeException();
        chatWindowMap.put(userName, sessionWindow);
    }

    public void removeSessionWindow(String userName) {
        if (!chatWindowMap.containsKey(userName))
            throw new RuntimeException();
        chatWindowMap.remove(userName);
    }

    public SessionWindow getSessionWindow(String userName) {
        return chatWindowMap.get(userName);
    }
}

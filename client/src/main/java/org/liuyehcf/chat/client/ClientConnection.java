package org.liuyehcf.chat.client;

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientConnection extends Connection {

    /**
     * 信息头
     */
    private Protocol.Header header;

    /**
     * 聊天界面
     */
    private final ChatWindow bindChatWindow;

    public ChatWindow getBindChatWindow() {
        return bindChatWindow;
    }

    public Protocol.Header getHeader() {
        return header;
    }

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
    public ClientConnection(String source,
                            String destination,
                            MessageReaderFactory messageReaderFactory,
                            MessageWriterFactory messageWriterFactory,
                            InetSocketAddress inetSocketAddress,
                            ChatWindow bindChatWindow) throws IOException {

        super(source, destination, messageReaderFactory, messageWriterFactory);

        this.bindChatWindow = bindChatWindow;

        socketChannel = SocketChannel.open();
        socketChannel.connect(inetSocketAddress);

        header = new Protocol.Header();

        header.setParam1(source);
        header.setParam2(destination);

        ClientConnectionDispatcher.getSingleton().getConnectionMap().put(getConnectionDescription(), this);
    }

}

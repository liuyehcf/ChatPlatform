package org.liuyehcf.chat.server.connection;

/**
 * Created by Liuye on 2017/5/29.
 */

import org.liuyehcf.chat.connect.*;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.nio.channels.SocketChannel;


/**
 * 每个客户端连接对应一个Connection，用于封装一些组件
 */
public class ServerConnection extends Connection {

    /**
     * 是否是主界面的连接
     */
    private boolean isMainConnection;

    /**
     * 是否被拒绝(服务端连接上线，只有主连接会被真正拒绝)
     */
    private boolean isRefused;

    public boolean isMainConnection() {
        return isMainConnection;
    }

    public boolean isRefused() {
        return isRefused;
    }

    public void setRefused(boolean refused) {
        isRefused = refused;
    }

    public void setMainConnection(boolean mainConnection) {
        isMainConnection = mainConnection;
    }

    public ServerConnection(MessageReaderFactory messageReaderFactory,
                            MessageWriterFactory messageWriterFactory,
                            SocketChannel socketChannel) {
        super(messageReaderFactory, messageWriterFactory);

        this.socketChannel = socketChannel;
    }
}

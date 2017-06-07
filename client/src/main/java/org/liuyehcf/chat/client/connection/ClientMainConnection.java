package org.liuyehcf.chat.client.connection;

import org.liuyehcf.chat.client.ui.MainWindow;
import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.connect.ConnectionDescription;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Created by Liuye on 2017/6/5.
 */
public class ClientMainConnection extends Connection {
    /**
     * 绑定的列表窗口
     */
    private final MainWindow bindMainWindow;

    public MainWindow getBindMainWindow() {
        return bindMainWindow;
    }

    public ClientMainConnection(String source,
                                String destination,
                                MessageReaderFactory messageReaderFactory,
                                MessageWriterFactory messageWriterFactory,
                                InetSocketAddress inetSocketAddress,
                                MainWindow bindMainWindow) throws IOException {
        super(messageReaderFactory,
                messageWriterFactory);
        setConnectionDescription(new ConnectionDescription(source, destination));

        this.bindMainWindow = bindMainWindow;

        socketChannel = SocketChannel.open();
        socketChannel.connect(inetSocketAddress);
    }
}

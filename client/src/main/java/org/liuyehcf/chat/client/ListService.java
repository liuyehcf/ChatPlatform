package org.liuyehcf.chat.client;

import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Created by Liuye on 2017/6/5.
 */
public class ListService extends Service {

    /**
     * 信息头
     */
    private Protocol.Header header;

    /**
     * 绑定的列表窗口
     */
    private ListWindow bindListWindow;

    public ListWindow getBindListWindow() {
        return bindListWindow;
    }

    public ListService(String source,
                       String destination,
                       InetSocketAddress inetSocketAddress,
                       ListWindow bindListWindow) throws IOException {
        //todo 为ListService配置工厂

        super(source,
                destination,
                DefaultMessageReaderProxyFactory.Builder(),
                DefaultMessageWriterProxyFactory.Builder());

        this.bindListWindow = bindListWindow;

        socketChannel = SocketChannel.open();
        socketChannel.connect(inetSocketAddress);

        header = new Protocol.Header();

        header.setParam1(source);
        header.setParam2(destination);
    }
}

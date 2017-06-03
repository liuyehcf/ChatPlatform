package org.liuyehcf.chat.server;

/**
 * Created by Liuye on 2017/5/29.
 */

import org.liuyehcf.chat.service.*;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;
import java.nio.channels.SocketChannel;


/**
 * 每个客户端连接对应一个Service，用于封装一些组件
 */
public class ServerService extends Service {

    public ServerService(String fromUserName,
                         String toUserName,
                         MessageReaderFactory messageReaderFactory,
                         MessageWriterFactory messageWriterFactory,
                         SocketChannel socketChannel) {
        super(fromUserName, toUserName, messageReaderFactory, messageWriterFactory);

        this.socketChannel = socketChannel;
    }

}

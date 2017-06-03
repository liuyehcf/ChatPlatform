package org.liuyehcf.chat.writer;

import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by Liuye on 2017/5/29.
 */
public class MessageWriterImpl implements MessageWriter {
    /**
     * 一级缓存
     */
    private ByteBuffer byteBuffer;

    /**
     * 协议
     */
    private Protocol protocol;

    /**
     * 一级缓存大小
     */
    private static final int BUFFER_SIZE = 1024;

    public MessageWriterImpl(Protocol protocol) {
        this.protocol = protocol;
        byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    @Override
    public void write(Message message, Service service) throws IOException {
        SocketChannel socketChannel = service.getSocketChannel();
        String messageString = protocol.wrap(message);

        byte[] messageBytes;
        try {
            messageBytes = messageString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("UTF-8编码失败");
        }
        byteBuffer.clear();
        for (int i = 1; ; i++) {
            boolean breakFlag = false;
            //首先将messageBytes的内容存入byeBuffer中，i代表需要分几次存入
            if (messageBytes.length <= BUFFER_SIZE * i) {
                int count = messageBytes.length - (i - 1) * BUFFER_SIZE;
                byteBuffer.put(messageBytes, (i - 1) * BUFFER_SIZE, count);
                breakFlag = true;
            } else {
                byteBuffer.put(messageBytes, (i - 1) * BUFFER_SIZE, BUFFER_SIZE);
            }

            byteBuffer.flip();

            //可能会抛出异常，直到byteBuffer数据被全部写入channel
            while (socketChannel.write(byteBuffer) > 0) ;
            //要是此时socketChannel不处于write-ready状态，那么可能导致byteBuffer丢失，直接抛出一个异常
            if (byteBuffer.hasRemaining())
                throw new IOException();

            byteBuffer.clear();

            if (breakFlag) {
                break;
            }
        }
    }
}

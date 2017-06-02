package org.liuyehcf.chat.reader;

import org.liuyehcf.chat.common.Service;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by Liuye on 2017/5/29.
 */
public class MessageReaderImpl implements MessageReader {

    /**
     * 已经读取的信息
     */
    private Queue<Message> messageQueue;

    /**
     * 二级缓存
     */
    private MessageBuffer messageBuffer;

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


    public MessageReaderImpl(Protocol protocol) {
        this.protocol = protocol;
        messageQueue = new LinkedList<Message>();
        messageBuffer = new MessageBuffer();
        byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    @Override
    public List<Message> read(Service service) throws IOException {
        SocketChannel socketChannel = service.getSocketChannel();
        byteBuffer.clear();
        while (socketChannel.read(byteBuffer) > 0) {
            //缓存到messageBuffer中
            messageBuffer.buffer(byteBuffer);

            int endIndexOfMessage;
            while ((endIndexOfMessage = protocol.findEndIndexOfMessage(messageBuffer)) != -1) {
                Message message = protocol.parse(messageBuffer.getBufferedString());
                messageBuffer.shiftLeft(endIndexOfMessage + 1);
                messageQueue.add(message);
            }
            byteBuffer.clear();
        }

        List<Message> messages = new ArrayList<Message>();
        while (!messageQueue.isEmpty()) {
            messages.add(messageQueue.poll());
        }

        return messages;
    }
}

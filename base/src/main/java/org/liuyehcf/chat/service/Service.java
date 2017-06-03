package org.liuyehcf.chat.service;

import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriter;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by HCF on 2017/5/31.
 */
public abstract class Service {
    /**
     * 连接描述符
     */
    private ServiceDescription serviceDescription;

    /**
     * 是否为群聊
     */
    private boolean isGroupChat;

    /**
     * 套接字信道
     */
    protected SocketChannel socketChannel;

    /**
     * 消息读取器工厂
     */
    private MessageReaderFactory messageReaderFactory;

    /**
     * 消息写入器工厂
     */
    private MessageWriterFactory messageWriterFactory;

    /**
     * 消息读取器
     */
    private MessageReader messageReader;

    /**
     * 消息写入器
     */
    private MessageWriter messageWriter;

    /**
     * 等待被写入该Service关联Channel的Message
     */
    private Queue<Message> writeMessages;

    /**
     * 当期Service所关联的PipeLineTask
     */
    private PipeLineTask pipeLineTask;

    /**
     * 当前Service下socketChannel所关联的Selector
     */
    private List<Selector> selectors;

    /**
     * 状态
     */
    private volatile int status;

    /**
     * 最近活跃时间戳
     */
    private long recentActiveTimeStamp;

    /**
     * 正常状态
     */
    private static final int NORMAL = 0;

    /**
     * 非正常状态，此时不在接受消息，等待被关闭
     */
    private static final int CANCEL = -1;

    public void setServiceDescription(ServiceDescription serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public ServiceDescription getServiceDescription() {
        return serviceDescription;
    }

    public boolean isGroupChat() {
        return isGroupChat;
    }

    public void setGroupChat(boolean groupChat) {
        isGroupChat = groupChat;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public MessageReader getMessageReader() {
        return messageReader;
    }

    public MessageWriter getMessageWriter() {
        return messageWriter;
    }

    public void bindPipeLineTask(PipeLineTask pipeLineTask) {
        this.pipeLineTask = pipeLineTask;
    }

    public PipeLineTask getBindPipeLineTask() {
        return pipeLineTask;
    }

    public List<Selector> getSelectors() {
        return selectors;
    }

    public long getRecentActiveTimeStamp() {
        return recentActiveTimeStamp;
    }

    public Queue<Message> getWriteMessages() {
        return writeMessages;
    }

    public Service(
            String fromUserName,
            String toUserName,
            MessageReaderFactory messageReaderFactory,
            MessageWriterFactory messageWriterFactory) {
        serviceDescription = new ServiceDescription(fromUserName, toUserName);
        this.messageReaderFactory = messageReaderFactory;
        this.messageWriterFactory = messageWriterFactory;

        messageReader = this.messageReaderFactory.build();
        messageWriter = this.messageWriterFactory.build();

        writeMessages = new LinkedList<Message>();
        selectors = new ArrayList<Selector>();

        recentActiveTimeStamp = System.currentTimeMillis();
        status = NORMAL;
    }

    /**
     * 向消息队列增加消息
     *
     * @param message
     */
    public void offerMessage(Message message) {
        if (status == NORMAL)
            writeMessages.offer(message);
    }


    /**
     * 从消息队列返回一条消息
     *
     * @return
     */
    public Message pollMessage() {
        return writeMessages.isEmpty() ? null : writeMessages.poll();
    }

    public void registerSelector(Selector selector, int ops) {
        try {
            this.socketChannel.configureBlocking(false);
            this.socketChannel.register(selector, ops, this);
            this.selectors.add(selector);
        } catch (IOException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("SocketChannel注册失败");
        }
    }

    /**
     * 设置为终止状态，不允许接受新的message
     */
    public void cancel() {
        status = CANCEL;
    }

    /**
     * 更新时间戳
     */
    public void activeNow() {
        recentActiveTimeStamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return getServiceDescription().toString();
    }
}

package org.liuyehcf.chat.client;

import org.liuyehcf.chat.common.*;
import org.liuyehcf.chat.pipe.AbstractPipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.TextMessage;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.writer.MessageWriter;

import javax.swing.*;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientPipeLineTask extends AbstractPipeLineTask {

    public ClientPipeLineTask() {
        ChatClientDispatcher.getSingleton().getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            /*
             * 负载均衡
             */
            ChatClientDispatcher.getSingleton().checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ChatClientDispatcher.LOGGER.debug("This pipeLineTask {} is finished", this);
        ChatClientDispatcher.getSingleton().getPipeLineTasks().remove(this);
    }

    private void readMessage() {
        int readyReadNum;
        try {
            readyReadNum = getReadSelector().selectNow();
        } catch (IOException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("调用selectNow失败");
        }

        if (readyReadNum <= 0) return;

        Set<SelectionKey> selectionKeys = getReadSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            readMessageFromService(selectionKey);

            iterator.remove();
        }

    }

    private void readMessageFromService(SelectionKey selectionKey) {
        ClientService service = (ClientService) selectionKey.attachment();

        MessageReader messageReader = service.getMessageReader();
        List<Message> messages;
        try {
            messages = messageReader.read(service);
        } catch (IOException e) {
            //由于已经添加了拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {
            //服务器告知下线
            if (((TextMessage) message).getTextControl().isOffLineMessage()) {
                offLine(service);
            }
            service.flushOnWindow(false, message.getDisplayMessageString());
        }
    }

    private void writeMessage() {
        int readyWriteNum;
        try {
            readyWriteNum = getWriteSelector().selectNow();
        } catch (IOException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("selectNow失败");
        }

        if (readyWriteNum <= 0) return;

        Set<SelectionKey> selectionKeys = getWriteSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            writeMessageToService(selectionKey);

            iterator.remove();
        }
    }

    private void writeMessageToService(SelectionKey selectionKey) {
        ClientService service = (ClientService) selectionKey.attachment();

        Message message = service.pollMessage();
        if (message != null) {
            MessageWriter messageWriter = service.getMessageWriter();

            try {
                messageWriter.write(message, service);
            } catch (IOException e) {
                //由于已经添加了拦截器，这里不做处理
                return;
            }
        }
    }

    /**
     * 离线的后续处理
     *
     * @param service
     */
    @Override
    public void offLine(Service service) {
        SocketChannel socketChannel = service.getSocketChannel();

        for (Selector selector : service.getSelectors()) {
            SelectionKey selectionKey = socketChannel.keyFor(selector);
            if (selectionKey != null) selectionKey.cancel();
        }
        service.getSelectors().clear();

        if (socketChannel.isConnected()) {
            try {
                socketChannel.finishConnect();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }

        if (socketChannel.isOpen()) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }

        ChatClientDispatcher.getSingleton().getServiceMap().remove(service.getServiceDescription());

        getServices().remove(service);
        if (getServiceNum() <= 0)
            getBindThread().interrupt();
    }
}

package org.liuyehcf.chat.client;

import org.liuyehcf.chat.pipe.AbstractSingleServicePipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * Created by Liuye on 2017/6/5.
 */
public class ListServicePipeLineTask extends AbstractSingleServicePipeLineTask {

    public ListServicePipeLineTask() {

    }


    @Override
    protected void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ChatClientDispatcher.LOGGER.info("{} is finished", this);
        ChatClientDispatcher.getSingleton().getPipeLineTasks().remove(this);
    }


    private void readMessage() {
        int readyReadNum;
        try {
            readyReadNum = getReadSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
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
        ListService service = (ListService) selectionKey.attachment();

        MessageReader messageReader = service.getMessageReader();
        List<Message> messages;
        try {
            messages = messageReader.read(service);
        } catch (IOException e) {
            //由于已经添加了拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {
            if (message.getControl().isLoginInMessage()) {
                if (message.getBody().getContent().equals("permit")) {
                    ChatClientDispatcher.getSingleton().getBindListWindow().setVisible(true);
                }
            }
        }
    }

    private void writeMessage() {
        int readyWriteNum;
        try {
            readyWriteNum = getWriteSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
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
        ListService service = (ListService) selectionKey.attachment();

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


    @Override
    public void offLine(Service service) {

    }
}

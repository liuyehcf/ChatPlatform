package org.liuyehcf.chat.protocol;

import org.liuyehcf.chat.reader.MessageBuffer;

/**
 * Created by Liuye on 2017/5/29.
 */
public interface Protocol {
    /**
     * 将Message封装成传输格式
     *
     * @param message
     * @return
     */
    String wrap(Message message);

    /**
     * 将messageString转换成Message
     *
     * @param messageString
     */
    Message parse(String messageString);

    /**
     * 在给定MessageBuffer中查找，看是否存在Message的尾
     * 返回-1不存在
     *
     * @param messageBuffer
     * @return
     */
    int findEndIndexOfMessage(MessageBuffer messageBuffer);
}

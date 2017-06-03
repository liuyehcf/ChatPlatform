package org.liuyehcf.chat.writer;

import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.protocol.Message;

import java.io.IOException;

/**
 * Created by Liuye on 2017/5/29.
 */
public interface MessageWriter {
    /**
     * 将指定Message中的信息写入指定信道，允许抛出异常，交给外界处理
     *
     * @param message
     * @param service
     * @throws IOException
     */
    void write(Message message, Service service) throws IOException;
}

package org.liuyehcf.chat.writer;

/**
 * Created by Liuye on 2017/5/31.
 */
public interface MessageWriterFactory {
    /**
     * 创建MessageWriter
     *
     * @return
     */
    MessageWriter build();
}

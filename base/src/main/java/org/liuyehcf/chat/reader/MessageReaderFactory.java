package org.liuyehcf.chat.reader;

/**
 * Created by Liuye on 2017/5/31.
 */
public interface MessageReaderFactory {
    /**
     * 生产MessageReader
     *
     * @return
     */
    MessageReader build();
}

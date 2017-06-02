package org.liuyehcf.chat.reader;

import org.liuyehcf.chat.common.Service;
import org.liuyehcf.chat.protocol.Message;

import java.io.IOException;
import java.util.List;

/**
 * Created by Liuye on 2017/5/29.
 */
public interface MessageReader {
    /**
     * 从信道中读取数据，如果已经有Message解析好了，则返回，允许抛出异常，交给外界处理
     * 必须返回所有，否则每次一有数据准备好，如果只取一个Message，那么会导致Message堆积在这里
     *
     * @param service
     * @return
     * @throws IOException
     */
    List<Message> read(Service service) throws IOException;
}

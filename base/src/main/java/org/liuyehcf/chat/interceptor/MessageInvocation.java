package org.liuyehcf.chat.interceptor;

import java.io.IOException;

/**
 * Created by Liuye on 2017/5/31.
 */
public interface MessageInvocation {
    /**
     * 方法调用，用于传递拦截器连
     *
     * @return
     */
    Object process() throws IOException;
}

package org.liuyehcf.chat.interceptor;

import java.io.IOException;

/**
 * Created by Liuye on 2017/5/31.
 */
public interface MessageInterceptor {
    /**
     * 拦截
     *
     * @param messageInvocation
     * @return
     */
    Object intercept(MessageInvocation messageInvocation) throws IOException;
}

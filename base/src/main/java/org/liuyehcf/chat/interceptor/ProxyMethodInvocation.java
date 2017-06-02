package org.liuyehcf.chat.interceptor;

import java.lang.reflect.Method;

/**
 * Created by Liuye on 2017/5/31.
 */
public interface ProxyMethodInvocation extends MessageInvocation {
    /**
     * 获取方法
     *
     * @return
     */
    Method getMethod();

    /**
     * 获取目标对象
     *
     * @return
     */
    Object getTarget();

    /**
     * 获取参数
     *
     * @return
     */
    Object[] getArguments();
}

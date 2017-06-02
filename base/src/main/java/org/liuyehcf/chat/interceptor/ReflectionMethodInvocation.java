package org.liuyehcf.chat.interceptor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by Liuye on 2017/5/31.
 */
public class ReflectionMethodInvocation implements ProxyMethodInvocation {
    /**
     * 方法反射对象
     */
    private Method method;

    /**
     * 目标对象
     */
    private Object target;

    /**
     * 方法参数
     */
    private Object[] args;

    /**
     * 当前指向的拦截器
     */
    private int currentInterceptorIndex = -1;

    /**
     * 拦截器链
     */
    private List<MessageInterceptor> interceptors;

    public ReflectionMethodInvocation(
            Method method,
            Object target,
            Object[] args,
            List<MessageInterceptor> interceptors) {
        this.method = method;
        this.target = target;
        this.args = args;
        this.interceptors = interceptors;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object process() throws IOException {
        if (++currentInterceptorIndex == interceptors.size()) {
            try {
                return getMethod().invoke(getTarget(), getArguments());
            } catch (InvocationTargetException e) {
                throw (IOException) e.getTargetException();
            } catch (Exception e) {
                return null;
            }
        }

        MessageInterceptor messageInterceptor = interceptors.get(currentInterceptorIndex);
        return messageInterceptor.intercept(this);
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public Object[] getArguments() {
        return args;
    }
}

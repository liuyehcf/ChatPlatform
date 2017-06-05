package org.liuyehcf.chat.writer;

import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.interceptor.ReflectionMethodInvocation;
import org.liuyehcf.chat.protocol.Protocol;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Liuye on 2017/5/31.
 */
public class DefaultMessageWriterProxyFactory implements MessageWriterFactory {
    /**
     * 文本协议
     */
    private static final Protocol DEFAULT_PROTOCOL = new Protocol();

    /**
     * 拦截器链
     */
    private List<MessageInterceptor> interceptorChain;

    private DefaultMessageWriterProxyFactory() {
        interceptorChain = new ArrayList<MessageInterceptor>();
    }

    /**
     * 建造者模式
     *
     * @return
     */
    public static DefaultMessageWriterProxyFactory Builder() {
        return new DefaultMessageWriterProxyFactory();
    }

    public DefaultMessageWriterProxyFactory addInterceptor(MessageInterceptor interceptor) {
        interceptorChain.add(interceptor);
        return this;
    }

    @Override
    public MessageWriter build() {
        MessageWriter messageWriter = new MessageWriterImpl(DEFAULT_PROTOCOL);
        return (MessageWriter) Proxy.newProxyInstance(
                messageWriter.getClass().getClassLoader(),
                messageWriter.getClass().getInterfaces(),
                new JdkDynamicProxy(messageWriter)
        );
    }

    private class JdkDynamicProxy implements InvocationHandler {
        private Object target;

        public JdkDynamicProxy(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //todo 这个对象极度重要，每次调用都生成一个invocation来传递调用连，这个对象包含了本次调用所需要的所有信息
            //todo 如果不这样，那么Interceptor和Invocation接口将变得很复杂(很多参数)
            ProxyMethodInvocation invocation = new ReflectionMethodInvocation(
                    method,
                    this.target,
                    args,
                    interceptorChain);
            return invocation.process();
        }
    }
}

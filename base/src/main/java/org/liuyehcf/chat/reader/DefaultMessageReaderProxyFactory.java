package org.liuyehcf.chat.reader;

import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.interceptor.ReflectionMethodInvocation;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.protocol.TextProtocol;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Liuye on 2017/5/31.
 */
public class DefaultMessageReaderProxyFactory implements MessageReaderFactory {
    /**
     * 文本协议
     */
    private static final Protocol DEFAULT_PROTOCOL = new TextProtocol();

    /**
     * 拦截器链
     */
    private List<MessageInterceptor> interceptorChain;

    private DefaultMessageReaderProxyFactory() {
        interceptorChain = new ArrayList<MessageInterceptor>();
    }

    /**
     * 建造者模式
     *
     * @return
     */
    public static DefaultMessageReaderProxyFactory Builder() {
        return new DefaultMessageReaderProxyFactory();
    }

    public DefaultMessageReaderProxyFactory addInterceptor(MessageInterceptor interceptor) {
        interceptorChain.add(interceptor);
        return this;
    }

    /**
     * 创建实例的方法
     *
     * @return
     */
    @Override
    public MessageReader build() {
        MessageReader messageReader = new MessageReaderImpl(DEFAULT_PROTOCOL);
        return (MessageReader) Proxy.newProxyInstance(
                messageReader.getClass().getClassLoader(),
                messageReader.getClass().getInterfaces(),
                new JdkDynamicProxy(messageReader)
        );
    }

    /**
     * 代理类，织入拦截器
     */
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

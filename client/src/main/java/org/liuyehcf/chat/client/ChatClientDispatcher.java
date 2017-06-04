package org.liuyehcf.chat.client;


import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.service.ServiceDescription;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.TextMessage;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Liuye on 2017/5/29.
 */
public class ChatClientDispatcher {
    /**
     * 日志
     */
    static Logger LOGGER = LoggerFactory.getLogger(ChatClientDispatcher.class);

    /**
     * 该类唯一作用是使得ChatClientDispatcher的单例延迟加载
     * 访问该类的其他静态变量，不会导致单例的初始化
     */
    private static class LazyInitializeSingleton {
        private static ChatClientDispatcher chatClientDispatcher = new ChatClientDispatcher();
    }

    public static ChatClientDispatcher getSingleton() {
        return LazyInitializeSingleton.chatClientDispatcher;
    }

    /**
     * 保存着所有的ServerPipeLineTask，用于客户端负载均衡
     */
    private List<PipeLineTask> pipeLineTasks;

    /**
     * 做负载均衡时，仅用一个线程来做即可，即利用tryLock方法
     */
    private ReentrantLock loadBalancingLock;

    /**
     * 消息读取器工厂
     */
    private MessageReaderFactory messageReaderFactory;

    /**
     * 消息写入器工厂
     */
    private MessageWriterFactory messageWriterFactory;

    /**
     * 线程池
     */
    private ExecutorService executorService;

    /**
     * Service描述符到Service的映射，多个PipeLineTask共享
     */
    private Map<ServiceDescription, Service> serviceMap;

    /**
     * 下一次做负载均衡的时刻，取自System.currentTimeMillis()
     */
    private long nextLoadBalancingTimeStamp = 0;

    public List<PipeLineTask> getPipeLineTasks() {
        return pipeLineTasks;
    }

    public ReentrantLock getLoadBalancingLock() {
        return loadBalancingLock;
    }

    public MessageReaderFactory getMessageReaderFactory() {
        return messageReaderFactory;
    }

    public MessageWriterFactory getMessageWriterFactory() {
        return messageWriterFactory;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Map<ServiceDescription, Service> getServiceMap() {
        return serviceMap;
    }

    private ChatClientDispatcher() {
        pipeLineTasks = new LinkedList<PipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        messageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ClientMessageReadeInterceptor());

        messageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ClientMessageWriteInterceptor());

        executorService = Executors.newCachedThreadPool();

        serviceMap = new ConcurrentHashMap<ServiceDescription, Service>();

    }

    public void dispatch(Service service) {
        //如果当前时刻小于做负载均衡的约定时刻，那么直接返回，不需要排队通过该安全点
        if (System.currentTimeMillis() < nextLoadBalancingTimeStamp) {
            doDispatcher(service);
        } else {
            //如果需要做负载均衡，则必须加锁，保证负载均衡时所有线程处于安全点
            try {
                loadBalancingLock.lock();
                doDispatcher(service);
            } finally {
                loadBalancingLock.unlock();
            }
        }
    }

    private void doDispatcher(Service service) {
        if (pipeLineTasks.isEmpty() ||
                getIdlePipeLineTask().getServiceNum() >= ClientUtils.MAX_CONNECTION_PER_TASK) {
            if (pipeLineTasks.size() >= ClientUtils.MAX_THREAD_NUM) {
                LOGGER.info("Client is overload");
                //todo 客户端负载过高,直接拒绝新连接
                ChatWindow chatWindow = ((ClientService) service).getBindChatWindow();
                ((ClientService) service).getBindChatWindow().flushOnWindow(false, true, "客户端负载过大，当前连接已被拒绝，请关闭本窗口，稍后尝试连接");

            } else {
                PipeLineTask newPipeLineTask = new ClientPipeLineTask();
                LOGGER.info("Add a new connection to {}", newPipeLineTask);

                newPipeLineTask.registerService(service);

                executorService.execute(newPipeLineTask);
            }
        } else {
            PipeLineTask pipeLineTask = getIdlePipeLineTask();
            LOGGER.info("Add a new connection to an existing {}", pipeLineTask);

            pipeLineTask.registerService(service);
        }
    }

    /**
     * 获取相对最空闲的任务
     *
     * @return
     */
    public PipeLineTask getIdlePipeLineTask() {
        PipeLineTask idlePipeLineTask = null;
        int minSize = Integer.MAX_VALUE;
        for (PipeLineTask pipeLineTask : pipeLineTasks) {
            if (pipeLineTask.getServiceNum() < minSize) {
                minSize = pipeLineTask.getServiceNum();
                idlePipeLineTask = pipeLineTask;
            }
        }
        return idlePipeLineTask;
    }


    /**
     * 负载均衡
     */
    public void checkLoadBalancing() {
        //如果当前时刻小于做负载均衡的约定时刻，那么直接返回，不需要排队通过该安全点
        if (System.currentTimeMillis() < nextLoadBalancingTimeStamp)
            return;

        if (loadBalancingLock.tryLock()) {
            //尝试获取锁，如果成功，那么即由当前线程来做检查
            LOGGER.info("{} is doing the load balancing!", Thread.currentThread());
            try {
                /*
                 * 自旋等待所有其他线程进入安全点
                 * 至少有pipeLineTasks.size()-1个线程处于排队状态时才能通过
                 * 可能调用dispatch方法的线程在排队，也可能不在排队(阻塞监听)
                 */
                while (loadBalancingLock.getQueueLength() < pipeLineTasks.size() - 1) ;

                int totalServiceNum = 0;
                for (PipeLineTask pipeLineTask : pipeLineTasks) {
                    totalServiceNum += pipeLineTask.getServiceNum();
                }

                /*
                 * 计算当前负载因子
                 */
                double curLoadFactory = (double) totalServiceNum / (double) pipeLineTasks.size() / (double) ClientUtils.MAX_CONNECTION_PER_TASK;

                if (curLoadFactory <= ClientUtils.LOAD_FACTORY_THRESHOLD)
                    doLoadBalancing(totalServiceNum);

                nextLoadBalancingTimeStamp = System.currentTimeMillis() + ClientUtils.LOAD_BALANCE_FREQUENCY * 60 * 1000;
            } finally {
                loadBalancingLock.unlock();
            }
        } else {
            //正在做负载均衡，等待负载均衡完成，或者排队通过
            queuedForLoadBalancing();
        }
    }

    private void doLoadBalancing(int totalServiceNum) {
        int remainTaskNum = (int) Math.ceil((double) totalServiceNum / (ClientUtils.LOAD_FACTORY_BALANCED * ClientUtils.MAX_CONNECTION_PER_TASK));
        if (remainTaskNum >= pipeLineTasks.size() || remainTaskNum < 1) {
            return;
        }

        LOGGER.info("start load balancing");
        List<PipeLineTask> dropPipeLineTasks = new ArrayList<PipeLineTask>();

        //将连接数量最小的task取出，剩下的pipeLineTasks就是保留下来的
        while (pipeLineTasks.size() > remainTaskNum) {
            PipeLineTask idlePipeLineTask = getIdlePipeLineTask();
            pipeLineTasks.remove(idlePipeLineTask);
            dropPipeLineTasks.add(idlePipeLineTask);
        }

        for (PipeLineTask pipeLineTask : dropPipeLineTasks) {
            for (Service service : pipeLineTask.getServices()) {
                //从原PipeLineTask中移除
                pipeLineTask.removeService(service);

                //注册到新的PipeLineTask中
                getIdlePipeLineTask().registerService(service);
            }
        }
    }

    private void queuedForLoadBalancing() {
        try {
            loadBalancingLock.lock();
        } finally {
            loadBalancingLock.unlock();
        }
    }


    /**
     * 消息读取后处理器，仅处理异常
     */
    private static class ClientMessageReadeInterceptor implements MessageInterceptor {

        @Override
        public Object intercept(MessageInvocation messageInvocation) throws IOException {
            List<Message> messages;
            try {
                messages = (List<Message>) messageInvocation.process();
            } catch (IOException e) {
                ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
                ClientService service = (ClientService) proxyMethodInvocation.getArguments()[0];
                service.getBindChatWindow().flushOnWindow(false, true, "[已失去与服务器的连接]");
                service.getBindPipeLineTask().offLine(service);
                throw e;
            }
            return messages;
        }
    }

    /**
     * 消息写入后处理器
     */
    private static class ClientMessageWriteInterceptor implements MessageInterceptor {

        @Override
        public Object intercept(MessageInvocation messageInvocation) throws IOException {
            Object result;
            try {
                result = messageInvocation.process();
            } catch (IOException e) {
                ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
                ClientService service = (ClientService) proxyMethodInvocation.getArguments()[1];
                service.getBindChatWindow().flushOnWindow(false, true, "[已失去与服务器的连接]");
                service.getBindPipeLineTask().offLine(service);
                throw e;
            }
            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            ClientService service = (ClientService) proxyMethodInvocation.getArguments()[1];
            TextMessage message = (TextMessage) proxyMethodInvocation.getArguments()[0];
            if (!message.getTextControl().isHelloMessage() && !message.getTextControl().isOffLineMessage())
                service.getBindChatWindow().flushOnWindow(true, false, message.getDisplayMessageString());
            if (message.getTextControl().isOffLineMessage()) {
                service.getBindPipeLineTask().offLine(service);
            }
            return result;
        }
    }
}





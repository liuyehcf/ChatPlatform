package org.liuyehcf.chat.server;

import org.liuyehcf.chat.pipe.MultiServicePipeLineTask;
import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.service.ServiceDescription;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by HCF on 2017/6/2.
 */
public class ChatServerDispatcher {
    /**
     * 日志
     */
    static Logger LOGGER = LoggerFactory.getLogger(ChatServerDispatcher.class);

    /**
     * 该类唯一作用是使得ChatServerDispatcher的单例延迟加载
     * 访问该类的其他静态变量，不会导致单例的初始化
     */
    private static class LazyInitializeSingleton {
        private static ChatServerDispatcher chatServerDispatcher = new ChatServerDispatcher();
    }

    public static ChatServerDispatcher getSingleton() {
        return LazyInitializeSingleton.chatServerDispatcher;
    }


    /**
     * 保存着所有的ServerPipeLineTask，用于服务端负载均衡
     */
    private List<MultiServicePipeLineTask> pipeLineTasks;

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
     * 主界面连接映射
     */
    private Map<String, Service> listServiceMap;

    /**
     * Service描述符到Service的映射
     */
    private Map<ServiceDescription, Service> serviceMap;

    /**
     * 用户组名到GroupService的映射
     */
    private Map<String, GroupService> groupServiceMap;

    /**
     * 下一次做负载均衡的时刻，取自System.currentTimeMillis()
     */
    private long nextLoadBalancingTimeStamp = 0;

    public List<MultiServicePipeLineTask> getPipeLineTasks() {
        return pipeLineTasks;
    }

    private ChatServerDispatcher() {
        pipeLineTasks = new LinkedList<MultiServicePipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        executorService = Executors.newCachedThreadPool();

        messageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ServerMessageReadeInterceptor());

        messageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ServerMessageWriteInterceptor());

        listServiceMap = new ConcurrentHashMap<String, Service>();
        serviceMap = new ConcurrentHashMap<ServiceDescription, Service>();
        groupServiceMap = new ConcurrentHashMap<String, GroupService>();
    }

    /**
     * 将监听到的任务分配给PipeLineTask
     * 该任务必须加负载均衡锁，必须保证在做负载均衡时，该函数不能执行
     * 由于新连接必进频率非常低，因此加锁性能不会有太大起伏
     */
    public void dispatch(SocketChannel socketChannel) {
        //如果当前时刻小于做负载均衡的约定时刻，那么直接返回，不需要排队通过该安全点
        if (System.currentTimeMillis() < nextLoadBalancingTimeStamp) {
            doDispatcher(socketChannel);
        } else {
            //如果需要做负载均衡，则必须加锁，保证负载均衡时所有线程处于安全点
            try {
                loadBalancingLock.lock();
                doDispatcher(socketChannel);
            } finally {
                loadBalancingLock.unlock();
            }
        }
    }

    private void doDispatcher(SocketChannel socketChannel) {
        if (pipeLineTasks.isEmpty() ||
                getIdlePipeLineTask().getServiceNum() >= ServerUtils.MAX_CONNECTION_PER_TASK) {
            if (pipeLineTasks.size() >= ServerUtils.MAX_THREAD_NUM) {
                LOGGER.info("Server is overload");

                PipeLineTask pipeLineTask = getIdlePipeLineTask();
                //该链接描述符的建立是在第一次接受消息时，此时只是截获SocketChannel，无法得知发送发的具体信息
                Service newService = new ServerService(
                        "",
                        "",
                        messageReaderFactory,
                        messageWriterFactory,
                        socketChannel);

                pipeLineTask.registerService(newService);
                newService.offerMessage(ServerUtils.createSystemMessage(true, "", "服务器负载过高，请稍后尝试登陆"));
                //该链接不再接受任何消息
                newService.cancel();
            } else {
                PipeLineTask newPipeLineTask = new ServerPipeLineTask(listServiceMap, serviceMap, groupServiceMap);
                LOGGER.info("Add a new connection to a new {}", newPipeLineTask);

                //该链接描述符的建立是在第一次接受消息时，此时只是截获SocketChannel，无法得知发送发的具体信息
                Service newService = new ServerService(
                        "",
                        "",
                        messageReaderFactory,
                        messageWriterFactory,
                        socketChannel);

                newPipeLineTask.registerService(newService);

                executorService.execute(newPipeLineTask);
            }
        } else {
            PipeLineTask pipeLineTask = getIdlePipeLineTask();
            LOGGER.info("Add a new connection to an existing {}", pipeLineTask);

            //该链接描述符的建立是在第一次接受消息时，此时只是截获SocketChannel，无法得知发送发的具体信息
            Service newService = new ServerService(
                    "",
                    "",
                    messageReaderFactory,
                    messageWriterFactory,
                    socketChannel);

            pipeLineTask.registerService(newService);
        }
    }

    /**
     * 获取相对最空闲的任务
     *
     * @return
     */
    public MultiServicePipeLineTask getIdlePipeLineTask() {
        MultiServicePipeLineTask idlePipeLineTask = null;
        int minSize = Integer.MAX_VALUE;
        for (MultiServicePipeLineTask pipeLineTask : pipeLineTasks) {
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

        //如果需要做负载均衡，则尝试获取锁
        if (loadBalancingLock.tryLock()) {
            //如果成功，那么即由当前线程来做检查
            LOGGER.info("{} is doing the load balancing!", Thread.currentThread());
            try {
                /*
                 * 自旋等待所有其他线程进入安全点
                 * 至少有pipeLineTasks.size()-1个线程处于排队状态时才能通过
                 * 可能主线程也在排队，也可能不在排队(阻塞监听)
                 */
                while (loadBalancingLock.getQueueLength() < pipeLineTasks.size() - 1) ;

                int totalServiceNum = 0;
                for (MultiServicePipeLineTask pipeLineTask : pipeLineTasks) {
                    totalServiceNum += pipeLineTask.getServiceNum();
                }

                /*
                 * 计算当前负载因子
                 */
                double curLoadFactory = (double) totalServiceNum / (double) pipeLineTasks.size() / (double) ServerUtils.MAX_CONNECTION_PER_TASK;

                if (curLoadFactory <= ServerUtils.LOAD_FACTORY_THRESHOLD)
                    doLoadBalancing(totalServiceNum);

                nextLoadBalancingTimeStamp = System.currentTimeMillis() + ServerUtils.LOAD_BALANCE_FREQUENCY * 60 * 1000;
            } finally {
                loadBalancingLock.unlock();
            }
        } else {
            //当前可能有其他线程正在做负载均衡，等待负载均衡完成
            queuedForLoadBalancing();
        }
    }

    private void doLoadBalancing(int totalServiceNum) {
        int remainTaskNum = (int) Math.ceil((double) totalServiceNum / (ServerUtils.LOAD_FACTORY_BALANCED * ServerUtils.MAX_CONNECTION_PER_TASK));
        if (remainTaskNum >= pipeLineTasks.size() || remainTaskNum < 1) {
            return;
        }

        LOGGER.info("start load balancing");
        List<MultiServicePipeLineTask> dropPipeLineTasks = new ArrayList<MultiServicePipeLineTask>();

        //将连接数量最小的task取出，剩下的pipeLineTasks就是保留下来的
        while (pipeLineTasks.size() > remainTaskNum) {
            MultiServicePipeLineTask idlePipeLineTask = getIdlePipeLineTask();
            pipeLineTasks.remove(idlePipeLineTask);
            dropPipeLineTasks.add(idlePipeLineTask);
        }

        for (MultiServicePipeLineTask pipeLineTask : dropPipeLineTasks) {
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

    public void stop() {
        //首先给所有活跃用户发送离线消息
        for (MultiServicePipeLineTask pipeLineTask : pipeLineTasks) {
            for (Service service : pipeLineTask.getServices()) {
                service.offerMessage(
                        ServerUtils.createSystemMessage(
                                true,
                                service.getServiceDescription().getSource(),
                                "[很抱歉通知您，服务器已关闭]"
                        ));
                service.cancel();
            }
        }

        boolean canStop = false;
        while (!canStop) {
            try {
                loadBalancingLock.lock();

                /*
                 * 该方法只有ChatServerListener所处的线程才会调用，因此必须所有PipeLineTask都位于阻塞状态才行
                 * 这一点与负载均衡不同，负载均衡时由PipeLineTask中的任意一个线程来做的
                 */
                while (loadBalancingLock.getQueueLength() < pipeLineTasks.size()) ;

                for (MultiServicePipeLineTask pipeLineTask : pipeLineTasks) {
                    for (Service service : pipeLineTask.getServices()) {
                        if (service.getWriteMessages().isEmpty()) {
                            pipeLineTask.offLine(service);
                        }
                        //否则等待剩余消息发送完毕
                    }
                }

                if (pipeLineTasks.isEmpty())
                    canStop = true;

            } finally {
                loadBalancingLock.unlock();
            }

            //尚不能结束，则等待剩余Message发送完毕
            if (!canStop) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {

                }
            }
        }
    }

    /**
     * 读取器后处理器，只处理异常
     */
    private static class ServerMessageReadeInterceptor implements MessageInterceptor {
        /**
         * 协议
         */
        private Protocol protocol = new Protocol();

        @Override
        public Object intercept(MessageInvocation messageInvocation) throws IOException {
            List<Message> messages;
            try {
                messages = (List<Message>) messageInvocation.process();
            } catch (IOException e) {
                LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
                ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
                Service service = (Service) proxyMethodInvocation.getArguments()[0];
                service.getBindPipeLineTask().offLine(service);
                throw e;
            }
            for (Message message : messages)
                LOGGER.debug("Receive a message {}", protocol.wrap(message));
            return messages;
        }
    }

    /**
     * 离线后处理器
     */
    private static class ServerMessageWriteInterceptor implements MessageInterceptor {
        /**
         * 协议
         */
        private Protocol protocol = new Protocol();

        @Override
        public Object intercept(MessageInvocation messageInvocation) throws IOException {
            Object result;
            try {
                result = messageInvocation.process();
            } catch (IOException e) {
                LOGGER.info("The server is disconnected from the client due to the abnormal offline of client");
                ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
                Service service = (Service) proxyMethodInvocation.getArguments()[1];
                service.getBindPipeLineTask().offLine(service);
                throw e;
            }
            ProxyMethodInvocation proxyMethodInvocation = (ProxyMethodInvocation) messageInvocation;
            Message message = (Message) proxyMethodInvocation.getArguments()[0];
            LOGGER.debug("Send a message {}", protocol.wrap(message));
            if (message.getControl().isOffLineMessage()) {
                Service service = (Service) proxyMethodInvocation.getArguments()[1];
                service.getBindPipeLineTask().offLine(service);
            }
            return result;
        }
    }


}

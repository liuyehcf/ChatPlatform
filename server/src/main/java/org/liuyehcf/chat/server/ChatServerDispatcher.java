package org.liuyehcf.chat.server;

import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.service.ServiceDescription;
import org.liuyehcf.chat.interceptor.MessageInterceptor;
import org.liuyehcf.chat.interceptor.MessageInvocation;
import org.liuyehcf.chat.interceptor.ProxyMethodInvocation;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.protocol.TextMessage;
import org.liuyehcf.chat.protocol.TextProtocol;
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
     * 用户名到Service的映射
     */
    private Map<ServiceDescription, Service> serviceMap;

    /**
     * 用户组名到GroupService的映射
     */
    private Map<String, GroupService> groupServiceMap;

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

    public Map<String, GroupService> getGroupServiceMap() {
        return groupServiceMap;
    }

    private ChatServerDispatcher() {
        pipeLineTasks = new LinkedList<PipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        executorService = Executors.newCachedThreadPool();

        messageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ServerMessageReadeInterceptor());

        messageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ServerMessageWriteInterceptor());

        serviceMap = new ConcurrentHashMap<ServiceDescription, Service>();
        groupServiceMap = new ConcurrentHashMap<String, GroupService>();
    }

    /**
     * 将监听到的任务分配给PipeLineTask
     * 该任务必须加负载均衡锁，必须保证在做负载均衡时，该函数不能执行
     */
    public void dispatch(SocketChannel socketChannel) {
        try {
            loadBalancingLock.lock();

            if (pipeLineTasks.isEmpty() ||
                    getIdlePipeLineTask().getServiceNum() >= ServerUtils.MAX_CONNECTION_PER_TASK) {
                if (pipeLineTasks.size() >= ServerUtils.MAX_THREAD_NUM) {
                    LOGGER.info("Server is overload");
                    //todo 服务器负载过高,发送一条系统消息后断开连接

                    PipeLineTask pipeLineTask = getIdlePipeLineTask();
                    Service newService = new ServerService(
                            "",
                            "",
                            messageReaderFactory,
                            messageWriterFactory,
                            socketChannel);

                    pipeLineTask.registerService(newService);
                    newService.offerMessage(ServerUtils.createSystemMessage(true, "", "服务器负载过高，请稍后尝试登陆"));
                    newService.cancel();
                } else {
                    PipeLineTask newPipeLineTask = new ServerPipeLineTask(serviceMap, groupServiceMap);
                    LOGGER.info("Add a new connection to a new {}", newPipeLineTask);

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

                Service newService = new ServerService(
                        "",
                        "",
                        messageReaderFactory,
                        messageWriterFactory,
                        socketChannel);

                pipeLineTask.registerService(newService);
            }
        } finally {
            loadBalancingLock.unlock();
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
        //尝试获取锁，如果成功，那么即由当前线程来做检查
        if (loadBalancingLock.tryLock()) {
            try {
                /*
                 * 自旋等待所有其他线程进入安全点
                 * 至少有pipeLineTasks.size()-1个线程处于排队状态时才能通过
                 * 可能主线程也在排队，也可能不在排队(阻塞监听)
                 */
                while (loadBalancingLock.getQueueLength() < pipeLineTasks.size() - 1) ;

                int totalServiceNum = 0;
                for (PipeLineTask pipeLineTask : pipeLineTasks) {
                    totalServiceNum += pipeLineTask.getServiceNum();
                }
                double curLoadFactory = (double) totalServiceNum / (double) pipeLineTasks.size() / (double) ServerUtils.MAX_CONNECTION_PER_TASK;

                if (curLoadFactory <= ServerUtils.LOAD_FACTORY_THRESHOLD)
                    doLoadBalancing(totalServiceNum);
            } finally {
                loadBalancingLock.unlock();
            }
        } else {
            //正在做负载均衡，等待负载均衡完成，或者排队通过
            queuedForLoadBalancing();
        }
    }

    private void doLoadBalancing(int totalServiceNum) {
        int remainTaskNum = (int) Math.ceil((double) totalServiceNum / (ServerUtils.LOAD_FACTORY_BALANCED * ServerUtils.MAX_CONNECTION_PER_TASK));
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
                pipeLineTask.removeService(service);

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
        for (PipeLineTask pipeLineTask : pipeLineTasks) {
            for (Service service : pipeLineTask.getServices()) {
                service.offerMessage(
                        ServerUtils.createSystemMessage(
                                true,
                                service.getServiceDescription().getFromUserName(),
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

                for (PipeLineTask pipeLineTask : pipeLineTasks) {
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
        private Protocol protocol = new TextProtocol();

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
        private Protocol protocol = new TextProtocol();

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
            TextMessage message = (TextMessage) proxyMethodInvocation.getArguments()[0];
            LOGGER.debug("Send a message {}", protocol.wrap(message));
            if (message.getTextControl().isOffLineMessage()) {
                Service service = (Service) proxyMethodInvocation.getArguments()[1];
                service.getBindPipeLineTask().offLine(service);
            }
            return result;
        }
    }


}

package org.liuyehcf.chat.client;


import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.interceptor.ClientMainTaskReaderInterceptor;
import org.liuyehcf.chat.client.interceptor.ClientMainTaskWriterInterceptor;
import org.liuyehcf.chat.client.interceptor.ClientSessionTaskReaderInterceptor;
import org.liuyehcf.chat.client.interceptor.ClientSessionTaskWriterInterceptor;
import org.liuyehcf.chat.client.pipeline.ClientSessionTask;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.connect.ConnectionDescription;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Protocol;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
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
public class ClientConnectionDispatcher {
    /**
     * 日志
     */
    public static Logger LOGGER = LoggerFactory.getLogger(ClientConnectionDispatcher.class);

    /**
     * 该类唯一作用是使得ChatClientDispatcher的单例延迟加载
     * 访问该类的其他静态变量，不会导致单例的初始化
     */
    private static class LazyInitializeSingleton {
        private static ClientConnectionDispatcher clientConnectionDispatcher = new ClientConnectionDispatcher();
    }

    public static ClientConnectionDispatcher getSingleton() {
        return LazyInitializeSingleton.clientConnectionDispatcher;
    }

    /**
     * 保存着所有的ClientPipeLineTask，用于客户端负载均衡
     */
    private List<PipeLineTask> pipeLineTasks;

    /**
     * 做负载均衡时，仅用一个线程来做即可，即利用tryLock方法
     */
    private ReentrantLock loadBalancingLock;

    /**
     * 主界面连接消息读取器工厂
     */
    private MessageReaderFactory mainTaskMessageReaderFactory;

    /**
     * 会话连接消息读取工厂
     */
    private MessageReaderFactory sessionTaskMessageReaderFactory;

    /**
     * 主界面连接消息写入器工厂
     */
    private MessageWriterFactory mainTaskMessageWriterFactory;

    /**
     * 会话连接消息写入器工厂
     */
    private MessageWriterFactory sessionTaskMessageWriterFactory;

    /**
     * 线程池
     */
    private ExecutorService executorService;

    /**
     * 列表界面
     */
    private Map<String, ClientMainConnection> mainConnectionMap;

    /**
     * Connection描述符到Connection的映射，多个PipeLineTask共享
     */
    private Map<ConnectionDescription, ClientSessionConnection> sessionConnectionMap;

    /**
     * 下一次做负载均衡的时刻，取自System.currentTimeMillis()
     */
    private long nextLoadBalancingTimeStamp = 0;

    public List<PipeLineTask> getPipeLineTasks() {
        return pipeLineTasks;
    }

    public Map<String, ClientMainConnection> getMainConnectionMap() {
        return mainConnectionMap;
    }

    public Map<ConnectionDescription, ClientSessionConnection> getSessionConnectionMap() {
        return sessionConnectionMap;
    }

    public MessageReaderFactory getMainTaskMessageReaderFactory() {
        return mainTaskMessageReaderFactory;
    }

    public MessageReaderFactory getSessionTaskMessageReaderFactory() {
        return sessionTaskMessageReaderFactory;
    }

    public MessageWriterFactory getMainTaskMessageWriterFactory() {
        return mainTaskMessageWriterFactory;
    }

    public MessageWriterFactory getSessionTaskMessageWriterFactory() {
        return sessionTaskMessageWriterFactory;
    }

    private ClientConnectionDispatcher() {
        mainConnectionMap = new ConcurrentHashMap<String, ClientMainConnection>();
        pipeLineTasks = new LinkedList<PipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        mainTaskMessageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ClientMainTaskReaderInterceptor(this));

        sessionTaskMessageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ClientSessionTaskReaderInterceptor(this));

        mainTaskMessageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ClientMainTaskWriterInterceptor(this));

        sessionTaskMessageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ClientSessionTaskWriterInterceptor(this));

        executorService = Executors.newCachedThreadPool();

        sessionConnectionMap = new ConcurrentHashMap<ConnectionDescription, ClientSessionConnection>();
    }

    /**
     * 目前，一个用户的所有会话都绑定到一个SessionConnection当中
     * 如果已存在，则返回该会话，如果不存在，新建
     *
     * @param account
     * @param serverHost
     * @param serverPort
     * @return
     */
    public ClientSessionConnection getSessionConnection(String account, String serverHost, Integer serverPort) {
        ConnectionDescription connectionDescription = new ConnectionDescription(account, Protocol.SERVER_USER_NAME);
        if (sessionConnectionMap.containsKey(connectionDescription)) {
            return sessionConnectionMap.get(connectionDescription);
        } else {
            return (ClientSessionConnection) createAndDispatch(
                    account,
                    new InetSocketAddress(serverHost, serverPort),
                    false,
                    "");
        }
    }

    /**
     * 生成Connection并且分配到PipeLineTask中去
     *
     * @param account
     * @param inetSocketAddress
     * @param isMainConnection
     * @param password
     * @return
     */
    public Connection createAndDispatch(String account,
                                        InetSocketAddress inetSocketAddress,
                                        boolean isMainConnection,
                                        String password) {
        //如果当前时刻小于做负载均衡的约定时刻，那么直接返回，不需要排队通过该安全点
        if (System.currentTimeMillis() < nextLoadBalancingTimeStamp) {
            return doCreateAndDispatch(account,
                    inetSocketAddress,
                    isMainConnection,
                    password);
        } else {
            //如果需要做负载均衡，则必须加锁，保证负载均衡时所有线程处于安全点
            try {
                loadBalancingLock.lock();
                return doCreateAndDispatch(account,
                        inetSocketAddress,
                        isMainConnection,
                        password);
            } finally {
                loadBalancingLock.unlock();
            }
        }
    }

    private Connection doCreateAndDispatch(String account,
                                           InetSocketAddress inetSocketAddress,
                                           boolean isMainConnection,
                                           String password) {
        //需要新建PipeLineTask
        if (pipeLineTasks.isEmpty() ||
                getIdlePipeLineTask().getConnectionNum() >= ClientUtils.MAX_CONNECTION_PER_TASK) {

            if (isMainConnection) {
                //若当前PipeLineTask数量已达上限
                if (pipeLineTasks.size() >= ClientUtils.MAX_THREAD_NUM) {
                    LOGGER.info("Refuse to create a MainConnection!");
                    return null;
                } else {
                    try {
                        ClientMainConnection newMainConnection = new ClientMainConnection(
                                account,
                                Protocol.SERVER_USER_NAME,
                                ClientConnectionDispatcher.getSingleton().getMainTaskMessageReaderFactory(),
                                ClientConnectionDispatcher.getSingleton().getMainTaskMessageWriterFactory(),
                                inetSocketAddress);

                        PipeLineTask newPipeLineTask = new ClientSessionTask();
                        LOGGER.info("Create a new PipeLineTask {}", newPipeLineTask);
                        executorService.execute(newPipeLineTask);

                        LOGGER.info("Add a new MainConnection to a new {}", newPipeLineTask);
                        newPipeLineTask.registerConnection(newMainConnection);

                        return newMainConnection;
                    } catch (IOException e) {
                        return null;
                    }
                }
            } else {
                try {
                    ClientSessionConnection newConnection = new ClientSessionConnection(
                            account,
                            Protocol.SERVER_USER_NAME,
                            ClientConnectionDispatcher.getSingleton().getSessionTaskMessageReaderFactory(),
                            ClientConnectionDispatcher.getSingleton().getSessionTaskMessageWriterFactory(),
                            inetSocketAddress);

                    PipeLineTask newPipeLineTask = new ClientSessionTask();
                    LOGGER.info("Create a new PipeLineTask {}", newPipeLineTask);
                    executorService.execute(newPipeLineTask);

                    LOGGER.info("Add a new SessionConnection to a new {}", newPipeLineTask);
                    newPipeLineTask.registerConnection(newConnection);

                    return newConnection;
                } catch (IOException e) {
                    return null;
                }
            }
        } else {
            if (isMainConnection) {
                try {
                    ClientMainConnection newMainConnection = new ClientMainConnection(
                            account,
                            Protocol.SERVER_USER_NAME,
                            ClientConnectionDispatcher.getSingleton().getMainTaskMessageReaderFactory(),
                            ClientConnectionDispatcher.getSingleton().getMainTaskMessageWriterFactory(),
                            inetSocketAddress);

                    PipeLineTask pipeLineTask = getIdlePipeLineTask();

                    LOGGER.info("Add a new MainConnection to an existing {}", pipeLineTask);
                    pipeLineTask.registerConnection(newMainConnection);

                    return newMainConnection;
                } catch (IOException e) {
                    return null;
                }
            } else {
                try {
                    ClientSessionConnection newConnection = new ClientSessionConnection(
                            account,
                            Protocol.SERVER_USER_NAME,
                            ClientConnectionDispatcher.getSingleton().getSessionTaskMessageReaderFactory(),
                            ClientConnectionDispatcher.getSingleton().getSessionTaskMessageWriterFactory(),
                            inetSocketAddress);

                    PipeLineTask pipeLineTask = getIdlePipeLineTask();

                    LOGGER.info("Add a new SessionConnection to an existing {}", pipeLineTask);
                    pipeLineTask.registerConnection(newConnection);

                    return newConnection;
                } catch (IOException e) {
                    return null;
                }
            }
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
            if (pipeLineTask.getConnectionNum() < minSize) {
                minSize = pipeLineTask.getConnectionNum();
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

                int totalConnectionNum = 0;
                for (PipeLineTask pipeLineTask : pipeLineTasks) {
                    totalConnectionNum += pipeLineTask.getConnectionNum();
                }

                /*
                 * 计算当前负载因子
                 */
                double curLoadFactory = (double) totalConnectionNum / (double) pipeLineTasks.size() / (double) ClientUtils.MAX_CONNECTION_PER_TASK;

                if (curLoadFactory <= ClientUtils.LOAD_FACTORY_THRESHOLD)
                    doLoadBalancing(totalConnectionNum);

                nextLoadBalancingTimeStamp = System.currentTimeMillis() + ClientUtils.LOAD_BALANCE_FREQUENCY * 60 * 1000;
            } finally {
                loadBalancingLock.unlock();
            }
        } else {
            //正在做负载均衡，等待负载均衡完成，或者排队通过
            queuedForLoadBalancing();
        }
    }

    private void doLoadBalancing(int totalConnectionNum) {
        int remainTaskNum = (int) Math.ceil((double) totalConnectionNum / (ClientUtils.LOAD_FACTORY_BALANCED * ClientUtils.MAX_CONNECTION_PER_TASK));
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
            for (Connection connection : pipeLineTask.getConnections()) {
                //从原PipeLineTask中移除
                pipeLineTask.removeConnection(connection);

                //注册到新的PipeLineTask中
                getIdlePipeLineTask().registerConnection(connection);
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
}





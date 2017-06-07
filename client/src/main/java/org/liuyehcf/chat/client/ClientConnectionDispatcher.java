package org.liuyehcf.chat.client;


import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.connection.ClientSessionConnection;
import org.liuyehcf.chat.client.pipeline.ClientMainTask;
import org.liuyehcf.chat.client.pipeline.ClientSessionTask;
import org.liuyehcf.chat.client.ui.MainWindow;
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
     * 列表界面
     */
    private Map<String, MainWindow> mainWindowMap;

    /**
     * 主界面线程，一个主界面线程可能管理多个主界面
     */
    private PipeLineTask mainTask;

    /**
     * 保存着所有的ClientPipeLineTask，用于客户端负载均衡
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
     * Connection描述符到Connection的映射，多个PipeLineTask共享
     */
    private Map<ConnectionDescription, ClientSessionConnection> sessionConnectionMap;

    /**
     * 下一次做负载均衡的时刻，取自System.currentTimeMillis()
     */
    private long nextLoadBalancingTimeStamp = 0;

    public Map<String, MainWindow> getMainWindowMap() {
        return mainWindowMap;
    }

    public void setMainTask(PipeLineTask mainTask) {
        this.mainTask = mainTask;
    }

    public List<PipeLineTask> getPipeLineTasks() {
        return pipeLineTasks;
    }

    public Map<ConnectionDescription, ClientSessionConnection> getSessionConnectionMap() {
        return sessionConnectionMap;
    }

    private ClientConnectionDispatcher() {
        mainWindowMap = new ConcurrentHashMap<String, MainWindow>();
        pipeLineTasks = new LinkedList<PipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        messageReaderFactory = DefaultMessageReaderProxyFactory.Builder();

        messageWriterFactory = DefaultMessageWriterProxyFactory.Builder();

        executorService = Executors.newCachedThreadPool();

        sessionConnectionMap = new ConcurrentHashMap<ConnectionDescription, ClientSessionConnection>();
    }

    /**
     * 目前，一个用户的所有会话都绑定到一个SessionConnection当中
     * 如果已存在，则返回该会话，如果不存在，新建
     *
     * @param source
     * @param serverHost
     * @param serverPort
     * @return
     */
    public ClientSessionConnection getSessionConnection(String source, String serverHost, Integer serverPort) {
        ConnectionDescription connectionDescription = new ConnectionDescription(source, Protocol.SERVER_USER_NAME);
        if (sessionConnectionMap.containsKey(connectionDescription)) {
            return sessionConnectionMap.get(connectionDescription);
        } else {
            try {
                ClientSessionConnection newConnection = new ClientSessionConnection(
                        source,
                        Protocol.SERVER_USER_NAME,
                        DefaultMessageReaderProxyFactory.Builder(),
                        DefaultMessageWriterProxyFactory.Builder(),
                        new InetSocketAddress(serverHost, serverPort)
                );
                dispatchSessionConnection(newConnection);
                sessionConnectionMap.put(connectionDescription, newConnection);
                return newConnection;
            } catch (IOException e) {
                return null;
            }
        }
    }

    //todo 如果一个线程管理多个主界面，那么也是需要加锁的哦
    public void dispatcherMainConnection(ClientMainConnection connection, String account, String password) {
        if (mainTask == null) {
            mainTask = new ClientMainTask();
            executorService.execute(mainTask);
            LOGGER.info("Start the Main Task {}", mainTask);
        }

        if (mainTask.getConnectionNum() >= ClientUtils.MAX_MAIN_WINDOW_PER_MAIN_TASK) {
            mainTask.registerConnection(connection);
            ClientUtils.sendLoginOutMessage(connection, account);
            connection.cancel();
            //注销操作等消息发送成功后再执行
        } else {
            mainTask.registerConnection(connection);
            ClientUtils.sendLoginInMessage(connection, account, password);
        }
    }

    public void dispatchSessionConnection(ClientSessionConnection connection) {
        //如果当前时刻小于做负载均衡的约定时刻，那么直接返回，不需要排队通过该安全点
        if (System.currentTimeMillis() < nextLoadBalancingTimeStamp) {
            doDispatchSessionConnection(connection);
        } else {
            //如果需要做负载均衡，则必须加锁，保证负载均衡时所有线程处于安全点
            try {
                loadBalancingLock.lock();
                doDispatchSessionConnection(connection);
            } finally {
                loadBalancingLock.unlock();
            }
        }
    }

    private void doDispatchSessionConnection(ClientSessionConnection connection) {
        if (pipeLineTasks.isEmpty() ||
                getIdlePipeLineTask().getConnectionNum() >= ClientUtils.MAX_CONNECTION_PER_TASK) {
            if (pipeLineTasks.size() >= ClientUtils.MAX_THREAD_NUM) {
                LOGGER.info("Client is overload");
                //todo 客户端负载过高,直接拒绝新连接
//                SessionWindow chatWindow = connection.getBindChatWindow();
//                ((ClientSessionConnection) connection).getBindChatWindow().flushOnWindow(false, true, "客户端负载过大，当前连接已被拒绝，请关闭本窗口，稍后尝试连接");

            } else {
                PipeLineTask newPipeLineTask = new ClientSessionTask();
                LOGGER.info("Add a new connection to {}", newPipeLineTask);

                newPipeLineTask.registerConnection(connection);

                executorService.execute(newPipeLineTask);
            }
        } else {
            PipeLineTask pipeLineTask = getIdlePipeLineTask();
            LOGGER.info("Add a new connection to an existing {}", pipeLineTask);

            pipeLineTask.registerConnection(connection);
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





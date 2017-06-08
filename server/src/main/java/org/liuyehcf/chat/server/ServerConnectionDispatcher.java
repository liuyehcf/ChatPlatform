package org.liuyehcf.chat.server;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.connect.ConnectionDescription;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.reader.DefaultMessageReaderProxyFactory;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.server.connection.ServerConnection;
import org.liuyehcf.chat.server.interceptor.ServerMessageReaderInterceptor;
import org.liuyehcf.chat.server.interceptor.ServerMessageWriterInterceptor;
import org.liuyehcf.chat.server.pipeline.ServerSessionTask;
import org.liuyehcf.chat.server.utils.ServerGroupInfo;
import org.liuyehcf.chat.server.utils.ServerUtils;
import org.liuyehcf.chat.writer.DefaultMessageWriterProxyFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by HCF on 2017/6/2.
 */
public class ServerConnectionDispatcher {
    /**
     * 日志
     */
    public static Logger LOGGER = LoggerFactory.getLogger(ServerConnectionDispatcher.class);

    /**
     * 该类唯一作用是使得ChatServerDispatcher的单例延迟加载
     * 访问该类的其他静态变量，不会导致单例的初始化
     */
    private static class LazyInitializeSingleton {
        private static ServerConnectionDispatcher serverConnectionDispatcher = new ServerConnectionDispatcher();
    }

    public static ServerConnectionDispatcher getSingleton() {
        return LazyInitializeSingleton.serverConnectionDispatcher;
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
     * 主界面连接映射
     */
    private Map<String, ServerConnection> mainConnectionMap;

    /**
     * Connection描述符到Connection的映射
     */
    private Map<ConnectionDescription, ServerConnection> sessionConnectionMap;

    /**
     * 用户组名到ServerGroupInfo的映射
     */
    private Map<String, ServerGroupInfo> groupInfoMap;

    /**
     * 下一次做负载均衡的时刻，取自System.currentTimeMillis()
     */
    private long nextLoadBalancingTimeStamp = 0;

    public List<PipeLineTask> getPipeLineTasks() {
        return pipeLineTasks;
    }

    public Map<String, ServerConnection> getMainConnectionMap() {
        return mainConnectionMap;
    }

    public Map<ConnectionDescription, ServerConnection> getSessionConnectionMap() {
        return sessionConnectionMap;
    }

    public Map<String, ServerGroupInfo> getGroupInfoMap() {
        return groupInfoMap;
    }

    private ServerConnectionDispatcher() {
        pipeLineTasks = new LinkedList<PipeLineTask>();

        loadBalancingLock = new ReentrantLock(true);

        executorService = Executors.newCachedThreadPool();

        messageReaderFactory = DefaultMessageReaderProxyFactory.Builder()
                .addInterceptor(new ServerMessageReaderInterceptor(this));

        messageWriterFactory = DefaultMessageWriterProxyFactory.Builder()
                .addInterceptor(new ServerMessageWriterInterceptor(this));

        mainConnectionMap = new ConcurrentHashMap<String, ServerConnection>();


        sessionConnectionMap = new ConcurrentHashMap<ConnectionDescription, ServerConnection>();
        groupInfoMap = new ConcurrentHashMap<String, ServerGroupInfo>();
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

    //todo 如果是主链接，不拒绝，会话连接，则拒绝，这个方法也有问题
    private void doDispatcher(SocketChannel socketChannel) {
        if (pipeLineTasks.isEmpty() ||
                getIdlePipeLineTask().getConnectionNum() >= ServerUtils.MAX_CONNECTION_PER_TASK) {
            if (pipeLineTasks.size() >= ServerUtils.MAX_THREAD_NUM) {
                LOGGER.info("Server is overload");

                PipeLineTask pipeLineTask = getIdlePipeLineTask();

                Connection newConnection = new ServerConnection(
                        messageReaderFactory,
                        messageWriterFactory,
                        socketChannel);

                pipeLineTask.registerConnection(newConnection);
                //发送系统消息关闭会话
                //todo ServerUtils.sendLogOutMessage(newConnection, "", "", "服务器负载过高，请稍后尝试登陆");
                //该链接不再接受任何消息
                newConnection.cancel();
            } else {
                PipeLineTask newPipeLineTask = new ServerSessionTask();
                LOGGER.info("Add a new connection to a new {}", newPipeLineTask);

                Connection newConnection = new ServerConnection(
                        messageReaderFactory,
                        messageWriterFactory,
                        socketChannel);

                newPipeLineTask.registerConnection(newConnection);

                executorService.execute(newPipeLineTask);
            }
        } else {
            PipeLineTask pipeLineTask = getIdlePipeLineTask();
            LOGGER.info("Add a new connection to an existing {}", pipeLineTask);

            Connection newConnection = new ServerConnection(
                    messageReaderFactory,
                    messageWriterFactory,
                    socketChannel);

            pipeLineTask.registerConnection(newConnection);
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

                int totalConnectionNum = 0;
                for (PipeLineTask pipeLineTask : pipeLineTasks) {
                    totalConnectionNum += pipeLineTask.getConnectionNum();
                }

                /*
                 * 计算当前负载因子
                 */
                double curLoadFactory = (double) totalConnectionNum / (double) pipeLineTasks.size() / (double) ServerUtils.MAX_CONNECTION_PER_TASK;

                if (curLoadFactory <= ServerUtils.LOAD_FACTORY_THRESHOLD)
                    doLoadBalancing(totalConnectionNum);

                nextLoadBalancingTimeStamp = System.currentTimeMillis() + ServerUtils.LOAD_BALANCE_FREQUENCY * 60 * 1000;
            } finally {
                loadBalancingLock.unlock();
            }
        } else {
            //当前可能有其他线程正在做负载均衡，等待负载均衡完成
            queuedForLoadBalancing();
        }
    }

    private void doLoadBalancing(int totalConnectionNum) {
        int remainTaskNum = (int) Math.ceil((double) totalConnectionNum / (ServerUtils.LOAD_FACTORY_BALANCED * ServerUtils.MAX_CONNECTION_PER_TASK));
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

    /**
     * 关闭服务器
     * 关闭时是由监听线程调用的，此时监听线程已经退出while循环，不会再有新连接了
     */
    public void stop() {
        //首先给所有活跃用户发送离线消息
        for (PipeLineTask pipeLineTask : pipeLineTasks) {
            for (Connection connection : pipeLineTask.getConnections()) {

                ServerConnection serverConnection = (ServerConnection) connection;
                if (serverConnection.isMainConnection()) {
                    String userName = serverConnection.getConnectionDescription().getDestination();
                    //发送强制下线消息，真正下线操作在写入信息之后处理
                    ServerUtils.sendForceLoginOutMessage(serverConnection, userName, "非常抱歉，服务器已暂停服务");
                    serverConnection.cancel();
                }
            }
        }
    }
}

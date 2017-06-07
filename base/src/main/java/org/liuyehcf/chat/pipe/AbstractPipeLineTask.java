package org.liuyehcf.chat.pipe;

import org.liuyehcf.chat.connect.Connection;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Liuye on 2017/6/5.
 */
public abstract class AbstractPipeLineTask implements PipeLineTask {
    /**
     * pipeLineTask计数，用于toString方法
     * 为什么要AtomicInteger，因为初始化可能在多个线程进行，该变量若是int类型，递增不是线程安全的
     */
    private static AtomicInteger pipeLineTaskCnt = new AtomicInteger();

    /**
     * PipeLineTask的id
     */
    private final int id;

    /**
     * 绑定的线程
     */
    private Thread bindThread;

    /**
     * 当前任务管理的所有Connect
     */
    private Set<Connection> localConnections;

    /**
     * 读选择器
     */
    private Selector readSelector;

    /**
     * 写选择器
     */
    private Selector writeSelector;


    @Override
    final public Thread getBindThread() {
        return bindThread;
    }

    @Override
    final public Selector getReadSelector() {
        return readSelector;
    }

    @Override
    final public Selector getWriteSelector() {
        return writeSelector;
    }


    @Override
    final public void registerConnection(Connection connection) {
        connection.getSelectors().clear();
        connection.bindPipeLineTask(this);

        connection.registerSelector(getReadSelector(), SelectionKey.OP_READ);
        connection.registerSelector(getWriteSelector(), SelectionKey.OP_WRITE);
        getConnections().add(connection);
    }

    @Override
    final public void offLine(Connection connection) {
        SocketChannel socketChannel = connection.getSocketChannel();

        for (Selector selector : connection.getSelectors()) {
            SelectionKey selectionKey = socketChannel.keyFor(selector);
            if (selectionKey != null) selectionKey.cancel();
        }
        connection.getSelectors().clear();

        if (socketChannel.isConnected()) {
            try {
                socketChannel.finishConnect();
            } catch (IOException e) {
            }
        }

        if (socketChannel.isOpen()) {
            try {
                socketChannel.socket().close();
                socketChannel.close();
            } catch (IOException e) {
            }
        }

        getConnections().remove(connection);

        if (getConnections().isEmpty()) {
            getBindThread().interrupt();
        }

        offLinePostProcess(connection);
    }

    /**
     * 下线的后续处理，交给子类实现
     */
    protected abstract void offLinePostProcess(Connection connection);


    @Override
    final public void removeConnection(Connection connection) {
        List<Selector> selectors = connection.getSelectors();
        for (Selector selector : selectors) {
            SelectionKey selectionKey = connection.getSocketChannel().keyFor(selector);
            if (selectionKey != null) {
                selectionKey.cancel();
            }
        }
        connection.getSelectors().clear();

        getConnections().remove(connection);

        if (getConnections().isEmpty()) {
            getBindThread().interrupt();
        }
    }

    @Override
    final public Set<Connection> getConnections() {
        return localConnections;
    }

    @Override
    final public int getConnectionNum() {
        return localConnections.size();
    }

    public AbstractPipeLineTask() {
        this.id = pipeLineTaskCnt.incrementAndGet();
        this.localConnections = new HashSet<Connection>();
        try {
            readSelector = Selector.open();
            writeSelector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("read selector init failed!");
        }
    }

    @Override
    final public void run() {
        bindThread = Thread.currentThread();
        start();
    }

    /**
     * 在run中执行的方法，交给子类实现
     */
    protected abstract void start();

    @Override
    final public String toString() {
        return "PipeLineTask{" + this.id + "}";
    }
}

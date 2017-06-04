package org.liuyehcf.chat.pipe;

import org.liuyehcf.chat.service.Service;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Liuye on 2017/6/1.
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
     * 当前任务管理的所有Service
     */
    private Set<Service> localServices;

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

    public AbstractPipeLineTask() {
        this.id = pipeLineTaskCnt.incrementAndGet();
        this.localServices = new HashSet<Service>();
        try {
            readSelector = Selector.open();
            writeSelector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("read selector init failed!");
        }
    }

    @Override
    final public void registerService(Service service) {
        service.getSelectors().clear();
        service.bindPipeLineTask(this);

        service.registerSelector(getReadSelector(), SelectionKey.OP_READ);
        service.registerSelector(getWriteSelector(), SelectionKey.OP_WRITE);

        getServices().add(service);
    }

    @Override
    final public void removeService(Service service) {
        List<Selector> selectors = service.getSelectors();
        for (Selector selector : selectors) {
            SelectionKey selectionKey = service.getSocketChannel().keyFor(selector);
            if (selectionKey != null) {
                selectionKey.cancel();
            }
        }
        service.getSelectors().clear();

        getServices().remove(service);

        if (getServices().isEmpty()) {
            getBindThread().interrupt();
        }
    }

    @Override
    final public Set<Service> getServices() {
        return localServices;
    }

    @Override
    final public int getServiceNum() {
        return localServices.size();
    }

    @Override
    public abstract void offLine(Service service);

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

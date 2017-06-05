package org.liuyehcf.chat.client;

import org.liuyehcf.chat.service.Service;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * Created by Liuye on 2017/6/5.
 */
public class ListTask implements Runnable {

    /**
     * 绑定的线程
     */
    private Thread bindThread;

    /**
     * 绑定的连接
     */
    private ListService bindListService;

    /**
     * 读选择器
     */
    private Selector readSelector;

    /**
     * 写选择器
     */
    private Selector writeSelector;

    public Thread getBindThread() {
        return bindThread;
    }

    public ListService getBindListService() {
        return bindListService;
    }

    public void bindListService(ListService bindListService) {
        this.bindListService = bindListService;
    }

    public Selector getReadSelector() {
        return readSelector;
    }

    public Selector getWriteSelector() {
        return writeSelector;
    }

    public ListTask(){
        try {
            readSelector = Selector.open();
            writeSelector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("read selector init failed!");
        }
    }

    public void registerService(Service service) {
        service.getSelectors().clear();
        service.bindPipeLineTask(this);

        service.registerSelector(getReadSelector(), SelectionKey.OP_READ);
        service.registerSelector(getWriteSelector(), SelectionKey.OP_WRITE);

        getServices().add(service);
    }

    @Override
    public void run() {

    }
}

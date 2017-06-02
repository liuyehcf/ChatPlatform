package org.liuyehcf.chat.pipe;


import org.liuyehcf.chat.common.Service;

import java.nio.channels.Selector;
import java.util.Set;

/**
 * Created by HCF on 2017/5/31.
 */
public interface PipeLineTask extends Runnable {

    /**
     * 返回该任务绑定的线程
     *
     * @return
     */
    Thread getBindThread();

    /**
     * 返回读选择器
     *
     * @return
     */
    Selector getReadSelector();

    /**
     * 返回写选择器
     *
     * @return
     */
    Selector getWriteSelector();

    /**
     * 将Service注册到当前PipeLineTask中
     */
    void registerService(Service service);

    /**
     * 移除Service，用于服务端做负载均衡
     */
    void removeService(Service service);

    /**
     * 返回当前PipeLineTask管理的Service
     *
     * @return
     */
    Set<Service> getServices();

    /**
     * 返回当前PipeLine处理的Service数量
     *
     * @return
     */
    int getServiceNum();

    /**
     * 让指定Service断开连接
     */
    void offLine(Service service);
}

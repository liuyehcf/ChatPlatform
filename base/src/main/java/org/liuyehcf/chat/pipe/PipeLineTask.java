package org.liuyehcf.chat.pipe;


import org.liuyehcf.chat.service.Service;

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
     * 让指定Service断开连接
     */
    void offLine(Service service);
}

package org.liuyehcf.chat.pipe;


import org.liuyehcf.chat.connect.Connection;

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
     * 将Connection注册到当前PipeLineTask中
     */
    void registerConnection(Connection connection);

    /**
     * 让指定Connection断开连接
     */
    void offLine(Connection connection);

    /**
     * 移除Connection，与offLine不同，不会关闭Connection，仅仅将其移出当前PipeLine的管理
     */
    void removeConnection(Connection connection);

    /**
     * 返回当前PipeLineTask管理的Connection
     *
     * @return
     */
    Set<Connection> getConnections();

    /**
     * 返回当前PipeLine处理的Connection数量
     *
     * @return
     */
    int getConnectionNum();
}

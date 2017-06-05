package org.liuyehcf.chat.pipe;

import org.liuyehcf.chat.service.Service;

import java.nio.channels.Pipe;
import java.util.Set;

/**
 * Created by Liuye on 2017/6/5.
 */
public interface MultiServicePipeLineTask extends PipeLineTask {
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
}

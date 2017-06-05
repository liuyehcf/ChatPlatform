package org.liuyehcf.chat.pipe;

import org.liuyehcf.chat.service.Service;

/**
 * Created by Liuye on 2017/6/5.
 */
public interface SingleServicePipeLineTask extends PipeLineTask {
    /**
     * 返回该单连接任务绑定的Service
     *
     * @return
     */
    Service getBindService();
}

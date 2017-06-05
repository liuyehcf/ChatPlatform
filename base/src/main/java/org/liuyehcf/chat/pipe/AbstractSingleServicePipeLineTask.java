package org.liuyehcf.chat.pipe;

import org.liuyehcf.chat.service.Service;

/**
 * Created by Liuye on 2017/6/5.
 */
public abstract class AbstractSingleServicePipeLineTask extends AbstractPipeLineTask implements SingleServicePipeLineTask {

    /**
     * 绑定的连接
     */
    private Service bindService;

    @Override
    public final void registerService(Service service) {
        super.registerService(service);
        bindService = service;
    }

    @Override
    public final Service getBindService() {
        return bindService;
    }
}

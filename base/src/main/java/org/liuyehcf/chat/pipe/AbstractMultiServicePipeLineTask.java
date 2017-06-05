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
public abstract class AbstractMultiServicePipeLineTask extends AbstractPipeLineTask implements MultiServicePipeLineTask {

    /**
     * 当前任务管理的所有Service
     */
    private Set<Service> localServices;


    public AbstractMultiServicePipeLineTask() {
        this.localServices = new HashSet<Service>();
    }

    @Override
    final public void registerService(Service service) {
        super.registerService(service);
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

}

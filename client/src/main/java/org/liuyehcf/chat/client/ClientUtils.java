package org.liuyehcf.chat.client;

import org.liuyehcf.chat.service.Service;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

/**
 * Created by Liuye on 2017/6/2.
 */
class ClientUtils {

    /**
     * 每个线程管理的channel数量的最大值
     */
    static final int MAX_CONNECTION_PER_TASK = 3;

    /**
     * 最大线程数量
     */
    static final int MAX_THREAD_NUM = 4;

    /**
     * 当每个任务管理的连接数量与每个任务可管理最大连接数量之比
     * 若小于该值，可以减少线程数量，将连接进行重新分配
     */
    static final double LOAD_FACTORY_THRESHOLD = 0.5;

    /**
     * 负载均衡需要达到的平均现有连接数与最大连接数之比
     */
    static final double LOAD_FACTORY_BALANCED = 0.8;

    /**
     * 做负载均衡的频率，1分钟一次
     */
    static final int LOAD_BALANCE_FREQUENCY = 1;

    static void sendSystemMessage(Service service, boolean isHelloMessage, boolean isOffLineMessage) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(((ClientService) service).getHeader());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setHelloMessage(isHelloMessage);
        message.getControl().setOffLineMessage(isOffLineMessage);

        service.offerMessage(message);
    }

    static void sendNormalMessage(Service service, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(((ClientService) service).getHeader());
        message.setBody(new Protocol.Body());

        message.getBody().setContent(content);

        service.offerMessage(message);
    }
}

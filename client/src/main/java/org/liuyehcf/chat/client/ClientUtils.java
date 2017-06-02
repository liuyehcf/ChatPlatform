package org.liuyehcf.chat.client;

import org.liuyehcf.chat.common.Service;
import org.liuyehcf.chat.protocol.TextMessage;
import org.liuyehcf.chat.protocol.TextProtocol;

/**
 * Created by Liuye on 2017/6/2.
 */
class ClientUtils {

    /**
     * 每个线程管理的channel数量的最大值
     */
    static final int MAX_CONNECTION_PER_TASK = 2;

    /**
     * 最大线程数量
     */
    static final int MAX_THREAD_NUM = 3;

    /**
     * 当每个任务管理的连接数量与每个任务可管理最大连接数量之比
     * 若小于该值，可以减少线程数量，将连接进行重新分配
     */
    static final double LOAD_FACTORY_THRESHOLD = 0.5;

    /**
     * 负载均衡需要达到的平均现有连接数与最大连接数之比
     */
    static final double LOAD_FACTORY_BALANCED = 0.8;


    static void sendSystemMessage(Service service, boolean isHelloMessage, boolean isOffLineMessage) {
        TextMessage message = new TextMessage();

        message.setTextControl(new TextProtocol.TextControl());
        message.setTextHeader(((ClientService) service).getTextHeader());
        message.setTextBody(new TextProtocol.TextBody());

        message.getTextControl().setHelloMessage(isHelloMessage);
        message.getTextControl().setOffLineMessage(isOffLineMessage);

        service.offerMessage(message);
    }

    static void sendNormalMessage(Service service, String content) {
        TextMessage message = new TextMessage();

        message.setTextControl(new TextProtocol.TextControl());
        message.setTextHeader(((ClientService) service).getTextHeader());
        message.setTextBody(new TextProtocol.TextBody());

        message.getTextBody().setContent(content);

        service.offerMessage(message);
    }
}

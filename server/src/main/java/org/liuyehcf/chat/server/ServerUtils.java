package org.liuyehcf.chat.server;


import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.TextMessage;
import org.liuyehcf.chat.protocol.TextProtocol;


/**
 * Created by Liuye on 2017/6/1.
 */
class ServerUtils {

    /**
     * 每个线程管理的channel数量的最大值
     */
    static final int MAX_CONNECTION_PER_TASK = 2;

    /**
     * 允许最长不活跃时间，单位分钟
     */
    static final int MAX_INACTIVE_TIME = 100;

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


    /**
     * 创建一条系统消息
     *
     * @param notifyUserName
     * @param systemContent
     * @return
     */
    static Message createSystemMessage(boolean isOffLine, String notifyUserName, String systemContent) {
        TextMessage message = new TextMessage();

        message.setTextControl(new TextProtocol.TextControl());
        message.setTextHeader(new TextProtocol.TextHeader());
        message.setTextBody(new TextProtocol.TextBody());

        message.getTextControl().setOffLineMessage(isOffLine);

        message.getTextHeader().setFromUserName(TextProtocol.SERVER_USER_NAME);
        message.getTextHeader().setToUserName(notifyUserName);

        message.getTextBody().setContent(systemContent);

        return message;
    }

}

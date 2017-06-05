package org.liuyehcf.chat.server;


import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;


/**
 * Created by Liuye on 2017/6/1.
 */
class ServerUtils {

    /**
     * 每个线程管理的channel数量的最大值
     */
    static final int MAX_CONNECTION_PER_TASK = 3;

    /**
     * 允许最长不活跃时间，单位分钟
     */
    static final int MAX_INACTIVE_TIME = 100;

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

    /**
     * 创建一条系统消息
     *
     * @param notifyUserName
     * @param systemContent
     * @return
     */
    static Message createSystemMessage(boolean isOffLine, String notifyUserName, String systemContent) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setOffLineMessage(isOffLine);

        message.getControl().setSystemMessage(true);
        message.getHeader().setParam1(Protocol.SERVER_USER_NAME);
        message.getHeader().setParam2(notifyUserName);

        message.getBody().setContent(systemContent);

        return message;
    }

    static Message createReplyLoginInMessage(boolean isPermit, String source, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setLoginInMessage(true);

        message.getHeader().setParam1(Protocol.SERVER_USER_NAME);
        message.getHeader().setParam2(source);

        message.getBody().setContent(isPermit ? "permit" : "deny");
        if (isPermit) {
            message.getBody().setContent(content);
        }

        return message;
    }

}

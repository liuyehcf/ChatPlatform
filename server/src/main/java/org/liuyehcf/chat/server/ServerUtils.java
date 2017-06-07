package org.liuyehcf.chat.server;


import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import static org.liuyehcf.chat.protocol.Protocol.Header.DENY;
import static org.liuyehcf.chat.protocol.Protocol.Header.FLUSH;
import static org.liuyehcf.chat.protocol.Protocol.Header.PERMIT;


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
     * 非在线消息
     *
     * @param connection
     * @param toUserName
     * @param content
     */
    static void sendNotOnLineMessage(Connection connection, String toUserName, String notOnLineUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1("NOT_ONLINE");
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(notOnLineUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }


    /**
     * 创建一条系统消息
     *
     * @param toUserName
     * @param content
     * @return
     */
    static void sendLoginOutNotifyMessage(Connection connection, String toUserName, String closingUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1("LOGIN_OUT_NOTIFY");
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(closingUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }

    /**
     * 登录回复消息
     *
     * @param isPermit
     * @param toUserName
     * @param content
     * @return
     */
    static void sendReplyLoginInMessage(Connection connection, boolean isPermit, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setLoginInMessage(true);

        message.getHeader().setParam1(Protocol.SERVER_USER_NAME);
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(isPermit ? PERMIT : DENY);

        if (isPermit) {
            message.getBody().setContent(content);
        }

        connection.offerMessage(message);
    }


    /**
     * 上线刷新列表消息
     *
     * @param toUserName
     * @param content
     * @return
     */
    static void sendLoginInFlushMessage(Connection connection, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setLoginInMessage(true);

        message.getHeader().setParam1(Protocol.SERVER_USER_NAME);
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(FLUSH);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }


    /**
     * 该条信息的含义为：希望fromUserName用户，开启一个fromUserName到toUserName的会话
     *
     * @param fromUserName
     * @param toUserName
     * @param content
     * @return
     */
    static void sendOpenSessionWindowMessage(Connection connection, String fromUserName, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setOpenSessionMessage(true);

        message.getHeader().setParam1(fromUserName);
        message.getHeader().setParam2(toUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }


    static void ASSERT(boolean flag) {
        if (!flag) throw new RuntimeException();
    }
}

package org.liuyehcf.chat.server.utils;


import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import static org.liuyehcf.chat.protocol.Protocol.Header.*;

/**
 * Created by Liuye on 2017/6/1.
 */
public class ServerUtils {

    /**
     * 每个线程管理的channel数量的最大值
     */
    public static final int MAX_CONNECTION_PER_TASK = 3;

    /**
     * 允许最长不活跃时间，单位分钟
     */
    public static final int MAX_INACTIVE_TIME = 100;

    /**
     * 最大线程数量
     */
    public static final int MAX_THREAD_NUM = 4;

    /**
     * 当每个任务管理的连接数量与每个任务可管理最大连接数量之比
     * 若小于该值，可以减少线程数量，将连接进行重新分配
     */
    public static final double LOAD_FACTORY_THRESHOLD = 0.5;

    /**
     * 负载均衡需要达到的平均现有连接数与最大连接数之比
     */
    public static final double LOAD_FACTORY_BALANCED = 0.8;

    /**
     * 做负载均衡的频率，1分钟一次
     */
    public static final int LOAD_BALANCE_FREQUENCY = 1;


    /**
     * 登录回复消息
     *
     * @param toUserName
     * @return
     */
    public static void sendReplyLoginInMessage(Connection connection, boolean isPermit, String toUserName) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(LOGIN_REPLY);
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(isPermit ? PERMIT : DENY);

        connection.offerMessage(message);
    }


    /**
     * 上线刷新列表消息
     *
     * @param toUserName
     * @param content
     * @return
     */
    public static void sendFlushFriendListMessage(Connection connection, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(FLUSH_FRIEND_LIST);
        message.getHeader().setParam2(toUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }

    public static void sendFlushGroupListMessage(Connection connection, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(FLUSH_GROUP_LIST);
        message.getHeader().setParam2(toUserName);

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
    public static void sendOpenSessionWindowMessage(Connection connection, String fromUserName, String toUserName, String content) {
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


    /**
     * 提示客户端，该客户端所连的客户端还没上线
     *
     * @param connection
     * @param toUserName
     * @param content
     */
    public static void sendNotOnLineMessage(Connection connection, String toUserName, String notOnLineUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(NOT_ONLINE);
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(notOnLineUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }


    /**
     * 提示客户端，该客户端所连接的另一个客户端已下线
     *
     * @param toUserName
     * @param content
     * @return
     */
    public static void sendLoginOutNotifyMessage(Connection connection, String toUserName, String closingUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(LOGIN_OUT_NOTIFY);
        message.getHeader().setParam2(toUserName);
        message.getHeader().setParam3(closingUserName);

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }

    public static Message createFlushGroupSessionUserListMessage(String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(FLUSH_GROUP_SESSION_USER_LIST);

        message.getBody().setContent(content);

        return message;
    }


    public static void ASSERT(boolean flag) {
        if (!flag) throw new RuntimeException();
    }
}

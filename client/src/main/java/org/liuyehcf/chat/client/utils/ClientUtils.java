package org.liuyehcf.chat.client.utils;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.liuyehcf.chat.protocol.Protocol.Header.*;


/**
 * Created by Liuye on 2017/6/2.
 */
public class ClientUtils {
    /**
     * 每个线程管理的channel数量的最大值
     */
    public static final int MAX_CONNECTION_PER_TASK = 2;

    /**
     * 最大线程数量
     */
    public static final int MAX_THREAD_NUM = 3;

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
     * 登录
     *
     * @param connection
     * @param account
     * @param password
     */
    public static void sendLogInMessage(Connection connection, String account, String password) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setLogInMessage(true);

        message.getHeader().setParam1(account);
        message.getHeader().setParam2(password);

        connection.offerMessage(message);
    }

    /**
     * 注销
     *
     * @param connection
     * @param account
     */
    public static void sendLogOutMessage(Connection connection, String account) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setLogOutMessage(true);

        message.getHeader().setParam1(account);
        message.getHeader().setParam2(Protocol.SERVER_USER_NAME);

        connection.offerMessage(message);
    }

    /**
     * 打开会话
     *
     * @param connection
     * @param isGroupSession
     * @param header
     */
    public static void sendOpenSessionMessage(Connection connection, boolean isGroupSession, Protocol.Header header) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getControl().setOpenSessionMessage(true);
        message.getControl().setGroupChat(isGroupSession);

        connection.offerMessage(message);
    }

    /**
     * 关闭会话
     *
     * @param connection
     * @param isGroupSession
     * @param header
     */
    public static void sendCloseSessionMessage(Connection connection, boolean isGroupSession, Protocol.Header header) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getControl().setGroupChat(isGroupSession);
        message.getControl().setCloseSessionMessage(true);

        connection.offerMessage(message);
    }

    /**
     * 正常消息
     *
     * @param connection
     * @param isGroup
     * @param header
     * @param content
     */
    public static void sendNormalMessage(Connection connection, boolean isGroup, Protocol.Header header, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getBody().setContent(content);
        message.getControl().setGroupChat(isGroup);

        connection.offerMessage(message);
    }

    /**
     * 创建一条用于显示的Message，当Session被动打开时，需要显示这个方法创建的Message
     *
     * @param fromUserName
     * @param toUserName
     * @param content
     * @return
     */
    public static Message createOpenSessionWindowMessage(String fromUserName, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getHeader().setParam1(fromUserName);
        message.getHeader().setParam2(toUserName);

        message.getBody().setContent(content);
        return message;
    }

    public static void sendApplyGroupNameMessage(Connection connection, String fromUserName, String groupName) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);

        message.getHeader().setParam1(APPLY_GROUP_NAME);
        message.getHeader().setParam2(fromUserName);
        message.getHeader().setParam3(groupName);

        connection.offerMessage(message);
    }


    /**
     * 从服务器返回的在线人员信息数据中解析成在线人员列表
     *
     * @param s
     * @return
     */
    public static List<String> retrieveNames(String s) {
        s = s.replaceAll("[\\[\\] ]", "");
        String[] names = s.split(",");
        return new ArrayList<String>(Arrays.asList(names));
    }

    public static void ASSERT(boolean flag) {
        if (!flag) throw new RuntimeException();
    }
}

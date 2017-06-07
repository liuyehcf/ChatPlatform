package org.liuyehcf.chat.client;

import org.liuyehcf.chat.connect.Connection;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Liuye on 2017/6/2.
 */
class ClientUtils {
    /**
     * 主界面线程管理的主界面最大数量
     */
    static final int MAX_MAIN_WINDOW_PER_MAIN_TASK = 6;

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

    /**
     * 做负载均衡的频率，1分钟一次
     */
    static final int LOAD_BALANCE_FREQUENCY = 1;

    static void sendOpenSessionMessage(Connection connection, Protocol.Header header) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setOpenSessionMessage(true);

        connection.offerMessage(message);
    }

    static void sendCloseSessionMessage(Connection connection, boolean isGroupSession, Protocol.Header header) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setGroupChat(isGroupSession);
        message.getControl().setCloseSessionMessage(true);

        connection.offerMessage(message);
    }

    static void sendNormalMessage(Connection connection, Protocol.Header header, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(header);
        message.setBody(new Protocol.Body());

        message.getBody().setContent(content);

        connection.offerMessage(message);
    }

    static void sendLoginInMessage(Connection connection, String account, String password) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setLoginInMessage(true);

        message.getHeader().setParam1(account);
        message.getHeader().setParam2(password);

        connection.offerMessage(message);
    }

    static void sendLoginOutMessage(Connection connection, String account) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getControl().setSystemMessage(true);
        message.getControl().setLoginOutMessage(true);

        message.getHeader().setParam1(account);
        message.getHeader().setParam2(Protocol.SERVER_USER_NAME);

        connection.offerMessage(message);
    }

    static Message createOpenSessionWindowMessage(String fromUserName, String toUserName, String content) {
        Message message = new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        message.getHeader().setParam1(fromUserName);
        message.getHeader().setParam2(toUserName);

        message.getBody().setContent(content);
        return message;
    }

    static List<String> retrieveNames(String s) {
        s = s.replaceAll("[\\[\\] ]", "");
        String[] names = s.split(",");
        return new ArrayList<String>(Arrays.asList(names));
    }

    static void ASSERT(boolean flag) {
        if (!flag) throw new RuntimeException();
    }
}

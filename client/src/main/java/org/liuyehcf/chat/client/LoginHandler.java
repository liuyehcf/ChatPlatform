package org.liuyehcf.chat.client;

/**
 * Created by Liuye on 2017/6/2.
 */
public interface LoginHandler {
    /**
     * 登录成功时的回调函数
     */
    void onSucceed();

    /**
     * 登录失败时的回调函数
     */
    void onFailure();
}

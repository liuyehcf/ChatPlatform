package org.liuyehcf.chat.handler;

/**
 * Created by HCF on 2017/6/3.
 */
public interface WindowHandler {
    /**
     * 登录成功时的回调函数
     */
    void onSucceed();

    /**
     * 登录失败时的回调函数
     */
    void onFailure();
}

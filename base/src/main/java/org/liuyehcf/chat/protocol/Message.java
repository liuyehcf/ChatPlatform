package org.liuyehcf.chat.protocol;

/**
 * Created by Liuye on 2017/5/29.
 */
public interface Message {
    /**
     * 返回用于显示的String格式
     *
     * @return
     */
    String getDisplayMessageString();

    /**
     * 获取一份拷贝的Message
     * 至于何种程度的拷贝由子类定义
     *
     * @return
     */
    Message getClonedMessage();
}

package org.liuyehcf.chat.protocol;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Liuye on 2017/5/29.
 */
public class Message {
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 控制信息
     */
    private Protocol.Control control;

    /**
     * 信息头
     */
    private Protocol.Header header;

    /**
     * 信息内容
     */
    private Protocol.Body body;


    public Protocol.Control getControl() {
        return control;
    }

    public void setControl(Protocol.Control control) {
        this.control = control;
    }

    public Protocol.Header getHeader() {
        return header;
    }

    public void setHeader(Protocol.Header header) {
        this.header = header;
    }

    public Protocol.Body getBody() {
        return body;
    }

    public void setBody(Protocol.Body body) {
        this.body = body;
    }

    public Message() {
    }

    public String getDisplayMessageString() {
        StringBuilder sb = new StringBuilder();

        sb.append(SIMPLE_DATE_FORMAT.format(new Date()))
                .append("   " + getHeader().getParam1())
                .append("\n")
                .append(getBody().getContent())
                .append("\n");
        return sb.toString();
    }

    public Message getClonedMessage() {
        Message message = new Message();
        message.setControl(this.getControl());
        message.setHeader(this.getHeader());
        message.setBody(this.getBody());

        return message;
    }
}

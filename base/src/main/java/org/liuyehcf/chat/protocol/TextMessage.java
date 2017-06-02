package org.liuyehcf.chat.protocol;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Liuye on 2017/5/29.
 */
public class TextMessage implements Message {
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 控制信息
     */
    private TextProtocol.TextControl textControl;

    /**
     * 信息头
     */
    private TextProtocol.TextHeader textHeader;

    /**
     * 信息内容
     */
    private TextProtocol.TextBody textBody;


    public TextProtocol.TextControl getTextControl() {
        return textControl;
    }

    public void setTextControl(TextProtocol.TextControl textControl) {
        this.textControl = textControl;
    }

    public TextProtocol.TextHeader getTextHeader() {
        return textHeader;
    }

    public void setTextHeader(TextProtocol.TextHeader textHeader) {
        this.textHeader = textHeader;
    }

    public TextProtocol.TextBody getTextBody() {
        return textBody;
    }

    public void setTextBody(TextProtocol.TextBody textBody) {
        this.textBody = textBody;
    }

    public TextMessage() {
    }

    @Override
    public String getDisplayMessageString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%-20s", getTextHeader().getFromUserName()))
                .append(SIMPLE_DATE_FORMAT.format(new Date()))
                .append("\n")
                .append(getTextBody().getContent())
                .append("\n");
        return sb.toString();
    }

    @Override
    public Message getClonedMessage() {
        TextMessage message = new TextMessage();
        message.setTextControl(this.getTextControl());
        message.setTextHeader(new TextProtocol.TextHeader());
        message.setTextBody(this.getTextBody());

        message.getTextHeader().setFromUserName(this.getTextHeader().getFromUserName());
        message.getTextHeader().setToUserName(this.getTextHeader().getToUserName());
        return message;
    }
}

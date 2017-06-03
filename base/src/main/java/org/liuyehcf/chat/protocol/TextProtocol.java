package org.liuyehcf.chat.protocol;

import org.liuyehcf.chat.reader.MessageBuffer;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Liuye on 2017/5/29.
 */
public class TextProtocol implements Protocol {

    /**
     * 文本后缀
     */
    private static final String MESSAGE_PREFIX = "$#$";

    /**
     * 服务器名
     */
    public static final String SERVER_USER_NAME = "SYSTEM";

    /**
     * 控制信息
     */
    public static final class TextControl {
        /**
         * 系统消息前缀字符串
         */
        private static final String CONTROL_PREFIX = "<";

        /**
         * 系统消息后缀字符串
         */
        private static final String CONTROL_SUFFIX = ">";

        /**
         * 系统消息匹配正则表达式
         */
        private static final String CONTROL_REGEX = "<([01]+?)>";
        private static final Pattern CONTROL_PATTERN = Pattern.compile(CONTROL_REGEX);

        private boolean isSystemMessage;

        private boolean isHelloMessage;

        private boolean isOffLineMessage;

        public boolean isSystemMessage() {
            return isSystemMessage;
        }

        public void setSystemMessage(boolean systemMessage) {
            isSystemMessage = systemMessage;
        }

        public boolean isHelloMessage() {
            return isHelloMessage;
        }

        public void setHelloMessage(boolean helloMessage) {
            isHelloMessage = helloMessage;
        }

        public boolean isOffLineMessage() {
            return isOffLineMessage;
        }

        public void setOffLineMessage(boolean offLineMessage) {
            isOffLineMessage = offLineMessage;
        }

        public String getControlString() {
            return CONTROL_PREFIX
                    + (isSystemMessage ? "1" : "0")
                    + (isHelloMessage ? "1" : "0")
                    + (isOffLineMessage ? "1" : "0")
                    + CONTROL_SUFFIX;
        }

        public static TextControl parse(String messageString) {
            Matcher m = CONTROL_PATTERN.matcher(messageString);
            TextControl textControl = new TextControl();
            if (m.find()) {
                String controlString = m.group(1);
                textControl.setSystemMessage(controlString.charAt(0) == '1');
                textControl.setHelloMessage(controlString.charAt(1) == '1');
                textControl.setOffLineMessage(controlString.charAt(2) == '1');
            }
            return textControl;
        }
    }

    /**
     * 信息头
     */
    public static final class TextHeader {
        /**
         * 消息头前缀字符串
         */
        private static final char HEADER_PREFIX = '[';

        /**
         * 消息头后缀字符串
         */
        private static final char HEADER_SUFFIX = ']';

        /**
         * 消息头匹配正则表达式
         */
        private static final String HEADER_REGEX = "\\[fromUserName:(.*?),toUserName:(.*?)\\]";
        private static final Pattern HEADER_PATTERN = Pattern.compile(HEADER_REGEX);

        /**
         * 消源用户
         */
        private String fromUserName;

        /**
         * 信宿用户
         */
        private String toUserName;

        public String getFromUserName() {
            return fromUserName;
        }

        public void setFromUserName(String fromUserName) {
            this.fromUserName = fromUserName;
        }

        public String getToUserName() {
            return toUserName;
        }

        public void setToUserName(String toUserName) {
            this.toUserName = toUserName;
        }

        public TextHeader() {
            fromUserName = "";
            toUserName = "";
        }

        public String getHeaderString() {
            return HEADER_PREFIX +
                    "fromUserName:" + fromUserName + "," +
                    "toUserName:" + toUserName
                    + HEADER_SUFFIX;
        }

        /**
         * 从字符串中解析出消息头
         *
         * @param messageString
         * @return
         */
        public static TextHeader parse(String messageString) {
            Matcher matcher = HEADER_PATTERN.matcher(messageString);
            TextHeader textHeader = new TextHeader();
            if (matcher.find()) {
                textHeader.setFromUserName(matcher.group(1));
                textHeader.setToUserName(matcher.group(2));
            }
            return textHeader;
        }
    }

    /**
     * 信息内容
     */
    public static final class TextBody {
        /**
         * 消息内容前缀字符串
         */
        private static final char BODY_PREFIX = '{';

        /**
         * 消息内容后缀字符串
         */
        private static final char BODY_SUFFIX = '}';

        /**
         * 消息内容匹配正则表达式
         */
        private static final String BODY_REGEX = "\\{content:(.*?)\\}";
        private static final Pattern BODY_PATTERN = Pattern.compile(BODY_REGEX);

        /**
         * 内容
         */
        private String content;

        public TextBody() {
            content = "";
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getBodyString() {
            return BODY_PREFIX +
                    "content:" + content +
                    BODY_SUFFIX;
        }

        /**
         * 从字符串中解析出消息内容
         *
         * @param messageString
         * @return
         */
        public static TextBody parse(String messageString) {
            Matcher matcher = BODY_PATTERN.matcher(messageString);
            TextBody textBody = new TextBody();
            if (matcher.find()) {
                textBody.setContent(matcher.group(1));
            }
            return textBody;
        }
    }

    public TextProtocol() {

    }

    @Override
    public String wrap(Message message) {
        TextMessage textMessage = (TextMessage) message;
        return textMessage.getTextControl().getControlString()
                + textMessage.getTextHeader().getHeaderString()
                + textMessage.getTextBody().getBodyString() + MESSAGE_PREFIX;
    }

    @Override
    public Message parse(String messageString) {
        TextMessage message = new TextMessage();

        message.setTextControl(TextControl.parse(messageString));
        message.setTextHeader(TextHeader.parse(messageString));
        message.setTextBody(TextBody.parse(messageString));

        return message;
    }

    @Override
    public int findEndIndexOfMessage(MessageBuffer messageBuffer) {
        byte[] endBytes = getEndBytes();
        int[] pai = getPai();

        int k = 0;
        for (int i = 0; i < messageBuffer.getPosition(); i++) {
            while (k > 0 && endBytes[k - 1 + 1] != messageBuffer.getByte(i))
                k = pai[k - 1];
            if (endBytes[k - 1 + 1] == messageBuffer.getByte(i))
                k++;
            if (k == pai.length)
                return i;

        }
        return -1;
    }

    /**
     * KMP算法数组
     */
    private int[] pai;

    /**
     * 文本结尾字符串UTF-8编码后的字节数组
     */
    private byte[] endBytes;

    private int[] getPai() {
        if (pai == null) {
            synchronized (this) {
                if (pai == null) {
                    getEndBytes();
                    pai = new int[endBytes.length];
                    pai[0] = 0;
                    int k = 0;
                    for (int i = 1; i < endBytes.length; i++) {
                        while (k > 0 && endBytes[k - 1 + 1] != endBytes[i]) {
                            k = pai[k - 1];
                        }
                        if (endBytes[k - 1 + 1] == endBytes[i])
                            k++;
                        pai[i] = k;
                    }
                }
            }
        }
        return pai;
    }

    private byte[] getEndBytes() {
        if (endBytes == null) {
            synchronized (this) {
                if (endBytes == null) {
                    try {
                        endBytes = TextProtocol.MESSAGE_PREFIX.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("String encoding failed!");
                    }
                }
            }
        }
        return endBytes;
    }
}

package org.liuyehcf.chat.protocol;

import org.liuyehcf.chat.reader.MessageBuffer;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Liuye on 2017/5/29.
 */
public class Protocol {

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
    public static final class Control {
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

        public static Control parse(String messageString) {
            Matcher m = CONTROL_PATTERN.matcher(messageString);
            Control control = new Control();
            if (m.find()) {
                String controlString = m.group(1);
                control.setSystemMessage(controlString.charAt(0) == '1');
                control.setHelloMessage(controlString.charAt(1) == '1');
                control.setOffLineMessage(controlString.charAt(2) == '1');
            }
            return control;
        }
    }

    /**
     * 信息头
     */
    public static final class Header {
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
        private static final String HEADER_REGEX = "\\[param1:(.*?),param2:(.*?)\\]";
        private static final Pattern HEADER_PATTERN = Pattern.compile(HEADER_REGEX);

        /**
         * 消源用户
         */
        private String param1;

        /**
         * 信宿用户
         */
        private String param2;

        public String getParam1() {
            return param1;
        }

        public void setParam1(String param1) {
            this.param1 = param1;
        }

        public String getParam2() {
            return param2;
        }

        public void setParam2(String param2) {
            this.param2 = param2;
        }

        public Header() {
            param1 = "";
            param2 = "";
        }

        public String getHeaderString() {
            return HEADER_PREFIX +
                    "param1:" + param1 + "," +
                    "param2:" + param2
                    + HEADER_SUFFIX;
        }

        /**
         * 从字符串中解析出消息头
         *
         * @param messageString
         * @return
         */
        public static Header parse(String messageString) {
            Matcher matcher = HEADER_PATTERN.matcher(messageString);
            Header header = new Header();
            if (matcher.find()) {
                header.setParam1(matcher.group(1));
                header.setParam2(matcher.group(2));
            }
            return header;
        }
    }

    /**
     * 信息内容
     */
    public static final class Body {
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

        public Body() {
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
        public static Body parse(String messageString) {
            Matcher matcher = BODY_PATTERN.matcher(messageString);
            Body body = new Body();
            if (matcher.find()) {
                body.setContent(matcher.group(1));
            }
            return body;
        }
    }

    public Protocol() {

    }

    public String wrap(Message message) {
        return message.getControl().getControlString()
                + message.getHeader().getHeaderString()
                + message.getBody().getBodyString() + MESSAGE_PREFIX;
    }

    public Message parse(String messageString) {
        Message message = new Message();

        message.setControl(Control.parse(messageString));
        message.setHeader(Header.parse(messageString));
        message.setBody(Body.parse(messageString));

        return message;
    }

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
                        endBytes = MESSAGE_PREFIX.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("String encoding failed!");
                    }
                }
            }
        }
        return endBytes;
    }
}

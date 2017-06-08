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

        /**
         * 是否为系统消息，各种各样的提示消息
         */
        private boolean isSystemMessage;

        /**
         * 是否登录
         */
        private boolean isLogInMessage;

        /**
         * 是否注销
         */
        private boolean isLogOutMessage;

        /**
         * 是否注册
         */
        private boolean isRegisterMessage;

        /**
         * 是否为hello，会话连接时发送的消息
         */
        private boolean isOpenSessionMessage;

        /**
         * 是否为离线消息，当前对话关闭时发送的消息
         */
        private boolean isCloseSessionMessage;

        /**
         * 是否为群聊
         */
        private boolean isGroupChat;

        public boolean isSystemMessage() {
            return isSystemMessage;
        }

        public void setSystemMessage(boolean systemMessage) {
            isSystemMessage = systemMessage;
        }

        public boolean isLogInMessage() {
            return isLogInMessage;
        }

        public void setLogInMessage(boolean logInMessage) {
            isLogInMessage = logInMessage;
        }

        public boolean isLogOutMessage() {
            return isLogOutMessage;
        }

        public void setLogOutMessage(boolean logOutMessage) {
            isLogOutMessage = logOutMessage;
        }

        public boolean isRegisterMessage() {
            return isRegisterMessage;
        }

        public void setRegisterMessage(boolean registerMessage) {
            isRegisterMessage = registerMessage;
        }

        public boolean isOpenSessionMessage() {
            return isOpenSessionMessage;
        }

        public void setOpenSessionMessage(boolean openSessionMessage) {
            isOpenSessionMessage = openSessionMessage;
        }

        public boolean isCloseSessionMessage() {
            return isCloseSessionMessage;
        }

        public void setCloseSessionMessage(boolean closeSessionMessage) {
            isCloseSessionMessage = closeSessionMessage;
        }

        public boolean isGroupChat() {
            return isGroupChat;
        }

        public void setGroupChat(boolean groupChat) {
            isGroupChat = groupChat;
        }

        public String getControlString() {
            return CONTROL_PREFIX
                    + (isSystemMessage ? "1" : "0")
                    + (isLogInMessage ? "1" : "0")
                    + (isLogOutMessage ? "1" : "0")
                    + (isRegisterMessage ? "1" : "0")
                    + (isOpenSessionMessage ? "1" : "0")
                    + (isCloseSessionMessage ? "1" : "0")
                    + (isGroupChat ? "1" : "0")
                    + CONTROL_SUFFIX;
        }

        public static Control parse(String messageString) {
            Matcher m = CONTROL_PATTERN.matcher(messageString);
            Control control = new Control();
            if (m.find()) {
                String controlString = m.group(1);
                control.setSystemMessage(controlString.charAt(0) == '1');
                control.setLogInMessage(controlString.charAt(1) == '1');
                control.setLogOutMessage(controlString.charAt(2) == '1');
                control.setRegisterMessage(controlString.charAt(3) == '1');
                control.setOpenSessionMessage(controlString.charAt(4) == '1');
                control.setCloseSessionMessage(controlString.charAt(5) == '1');
                control.setGroupChat(controlString.charAt(6) == '1');
            } else {
                throw new RuntimeException("parse MessageControl failed!");
            }
            return control;
        }
    }

    /**
     * 信息头
     */
    public static final class Header {

        public static final String PERMIT = "PERMIT";

        public static final String DENY = "DENY";

        public static final String LOGIN_REPLY = "LOGIN_REPLY";

        public static final String APPLY_GROUP_NAME = "APPLY_GROUP_NAME";

        public static final String FLUSH_FRIEND_LIST = "FLUSH_FRIEND_LIST";

        public static final String FLUSH_GROUP_LIST = "FLUSH_GROUP_LIST";

        public static final String FLUSH_GROUP_SESSION_USER_LIST = "FLUSH_GROUP_SESSION_USER_LIST";

        public static final String NOT_ONLINE = "NOT_ONLINE";

        public static final String OPEN_SESSION_WINDOW = "OPEN_SESSION_WINDOW";

        public static final String LOGIN_OUT_NOTIFY = "LOGIN_OUT_NOTIFY";

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
        private static final String HEADER_REGEX = "\\[param1:(.*?),param2:(.*?),param3:(.*?),param4:(.*?)\\]";
        private static final Pattern HEADER_PATTERN = Pattern.compile(HEADER_REGEX);

        /**
         * 参数1
         */
        private String param1 = "";

        /**
         * 参数2
         */
        private String param2 = "";

        /**
         * 参数3
         */
        private String param3 = "";

        /**
         * 参数4
         */
        private String param4 = "";

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

        public String getParam3() {
            return param3;
        }

        public void setParam3(String param3) {
            this.param3 = param3;
        }

        public String getParam4() {
            return param4;
        }

        public void setParam4(String param4) {
            this.param4 = param4;
        }

        public Header() {
            param1 = "";
            param2 = "";
        }

        public String getHeaderString() {
            return HEADER_PREFIX
                    + "param1:" + param1 + ","
                    + "param2:" + param2 + ","
                    + "param3:" + param3 + ","
                    + "param4:" + param4
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
                header.setParam3(matcher.group(3));
                header.setParam4(matcher.group(4));
            } else {
                throw new RuntimeException("parse MessageHeader failed!");
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
        private String content = "";

        public Body() {
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
            } else {
                throw new RuntimeException("parse MessageBody failed!");
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

package org.liuyehcf.chat.connect;

/**
 * Created by Liuye on 2017/6/6.
 */
public class SessionDescription {
    private final String fromUserName;

    private final String toUserName;

    private final boolean isGroupChat;

    public String getFromUserName() {
        return fromUserName;
    }

    public String getToUserName() {
        return toUserName;
    }

    public boolean isGroupChat() {
        return isGroupChat;
    }

    public SessionDescription(String fromUserName, String toUserName, boolean isGroupChat) {
        this.fromUserName = fromUserName;
        this.toUserName = toUserName;
        this.isGroupChat = isGroupChat;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) throw new NullPointerException();
        if (!(obj instanceof SessionDescription)) throw new RuntimeException("类型不匹配");
        SessionDescription sessionDescription = (SessionDescription) obj;
        return sessionDescription.toString().equals(this.toString());
    }

    @Override
    public String toString() {
        return fromUserName
                + ":" + toUserName
                + ":" + (isGroupChat ? "GROUP" : "SINGLE");
    }
}

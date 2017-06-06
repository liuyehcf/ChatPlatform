package org.liuyehcf.chat.connect;

/**
 * Created by Liuye on 2017/6/6.
 */
public class SessionDescription {
    private final String fromUser;

    private final String toUser;

    public String getFromUser() {
        return fromUser;
    }

    public String getToUser() {
        return toUser;
    }

    public SessionDescription(String fromUser, String toUser) {
        this.fromUser = fromUser;
        this.toUser = toUser;
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
        return fromUser + ":" + toUser;
    }
}

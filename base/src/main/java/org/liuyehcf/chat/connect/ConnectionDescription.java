package org.liuyehcf.chat.connect;

/**
 * Created by Liuye on 2017/6/2.
 */
public class ConnectionDescription {
    /**
     * 信源
     */
    private final String source;

    /**
     * 信宿
     */
    private final String destination;

    public ConnectionDescription(String source, String destination) {
        this.source = source;
        this.destination = destination;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    /**
     * 返回与当前连接描述符相反的连接描述符
     *
     * @return
     */
    public ConnectionDescription getReverse() {
        return new ConnectionDescription(destination, source);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) throw new NullPointerException();
        if (!(obj instanceof ConnectionDescription)) throw new RuntimeException("类型不匹配");
        ConnectionDescription connectionDescription = (ConnectionDescription) obj;
        return source.equals(connectionDescription.getSource())
                && destination.equals(connectionDescription.getDestination());
    }

    @Override
    public String toString() {
        return source + ":" + destination;
    }
}

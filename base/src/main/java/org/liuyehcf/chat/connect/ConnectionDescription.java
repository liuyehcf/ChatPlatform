package org.liuyehcf.chat.connect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 类型：文本、视屏、音频等
     */
    private final String type;

    /**
     * Session描述符列表
     */
    private Set<SessionDescription> sessionDescriptions;

    /**
     * 文本类型
     */
    private static final String TEXT = "TEXT";

    /**
     * 默认文本类型的构造函数
     *
     * @param source
     * @param destination
     */
    public ConnectionDescription(String source, String destination) {
        this.source = source;
        this.destination = destination;
        this.type = TEXT;
        sessionDescriptions = new HashSet<SessionDescription>();
    }

    /**
     * 指定类型的构造函数，目前暂时没用，待扩展
     *
     * @param source
     * @param destination
     * @param type
     */
    private ConnectionDescription(String source, String destination, String type) {
        this.source = source;
        this.destination = destination;
        this.type = type;
        sessionDescriptions = new HashSet<SessionDescription>();
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getType() {
        return type;
    }

    public boolean addSessionDescription(SessionDescription sessionDescription) {
        return sessionDescriptions.add(sessionDescription);
    }

    public boolean removeSessionDescription(SessionDescription sessionDescription) {
        return sessionDescriptions.remove(sessionDescription);
    }


    public Set<SessionDescription> getSessionDescriptions() {
        return sessionDescriptions;
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
        return connectionDescription.toString().equals(this.toString());
    }

    @Override
    public String toString() {
        return source + ":" + destination + ":" + type;
    }
}

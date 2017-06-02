package org.liuyehcf.chat.common;

/**
 * Created by Liuye on 2017/6/2.
 */
public class ServiceDescription {
    private final String fromUserName;

    private final String toUserName;

    public ServiceDescription(String fromUserName, String toUserName) {
        this.fromUserName = fromUserName;
        this.toUserName = toUserName;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public String getToUserName() {
        return toUserName;
    }

    public ServiceDescription getReverse() {
        return new ServiceDescription(toUserName, fromUserName);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) throw new NullPointerException();
        if (!(obj instanceof ServiceDescription)) throw new RuntimeException("类型不匹配");
        ServiceDescription serviceDescription = (ServiceDescription) obj;
        return fromUserName.equals(serviceDescription.getFromUserName())
                && toUserName.equals(serviceDescription.getToUserName());
    }

    @Override
    public String toString() {
        return fromUserName + ":" + toUserName;
    }
}

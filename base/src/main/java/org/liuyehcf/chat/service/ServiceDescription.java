package org.liuyehcf.chat.service;

/**
 * Created by Liuye on 2017/6/2.
 */
public class ServiceDescription {
    private final String source;

    private final String destination;

    public ServiceDescription(String source, String destination) {
        this.source = source;
        this.destination = destination;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public ServiceDescription getReverse() {
        return new ServiceDescription(destination, source);
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
        return source.equals(serviceDescription.getSource())
                && destination.equals(serviceDescription.getDestination());
    }

    @Override
    public String toString() {
        return source + ":" + destination;
    }
}

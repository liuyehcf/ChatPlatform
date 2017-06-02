package org.liuyehcf.chat.server;

import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.common.Service;
import org.liuyehcf.chat.protocol.TextMessage;

import java.util.*;

/**
 * Created by HCF on 2017/5/30.
 */
public class GroupService {

    /**
     * 群聊名
     */
    private String groupName;

    /**
     * Service集合
     */
    private Set<Service> services;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public GroupService() {
        services = new HashSet<Service>();
    }

    public boolean addService(Service service) {
        synchronized (services) {
            return services.add(service);
        }
    }

    public boolean removeService(Service service) {
        synchronized (services) {
            return services.remove(service);
        }
    }

    public boolean isGroupEmpty() {
        synchronized (services) {
            return services.isEmpty();
        }
    }

    public void offerMessage(Service fromService, Message message) {
        synchronized (services) {
            for (Service toService : services) {
                if (toService != fromService) {
                    toService.offerMessage(getModifiedMessage(toService, message));
                }
            }
        }
    }

    private Message getModifiedMessage(Service service, Message message) {
        TextMessage textMessage = (TextMessage) message.getClonedMessage();
        textMessage.getTextHeader().setToUserName(service.getServiceDescription().getFromUserName());
        return textMessage;
    }
}

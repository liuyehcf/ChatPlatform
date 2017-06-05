package org.liuyehcf.chat.server;

import org.liuyehcf.chat.service.*;
import org.liuyehcf.chat.pipe.AbstractMultiServicePipeLineTask;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.reader.MessageReader;
import org.liuyehcf.chat.writer.MessageWriter;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 服务端PipeLineTask实现类
 * Created by Liuye on 2017/5/29.
 */
public class ServerPipeLineTask extends AbstractMultiServicePipeLineTask {

    /**
     * 客户端主界面连接的映射
     */
    private Map<String, Service> listServiceMap;


    /**
     * 用户名到Service的映射，多个PipeLineTask共享
     */
    private Map<ServiceDescription, Service> serviceMap;

    /**
     * 用户组名到GroupService的映射，多个PipeLineTask共享
     */
    private Map<String, GroupService> groupServiceMap;

    public ServerPipeLineTask(
            Map<String, Service> listServiceMap,
            Map<ServiceDescription, Service> serviceMap,
            Map<String, GroupService> groupServiceMap) {
        this.listServiceMap = listServiceMap;
        this.serviceMap = serviceMap;
        this.groupServiceMap = groupServiceMap;


        ChatServerDispatcher.getSingleton().getPipeLineTasks().add(this);
    }

    @Override
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {

            readMessage();

            writeMessage();

            checkActive();

            //任一线程做负载均衡
            ChatServerDispatcher.getSingleton().checkLoadBalancing();

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                getBindThread().interrupt();
            }
        }
        ChatServerDispatcher.LOGGER.info("{} is finished", this);
        ChatServerDispatcher.getSingleton().getPipeLineTasks().remove(this);
    }

    /**
     * 读取信息
     */
    private void readMessage() {
        int readReadyNum;
        try {
            readReadyNum = getReadSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed");
        }

        if (readReadyNum <= 0) return;

        Set<SelectionKey> selectionKeys = getReadSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            readMessageFromService(selectionKey);

            iterator.remove();
        }
    }

    private void readMessageFromService(SelectionKey selectionKey) {
        Service service = (Service) selectionKey.attachment();

        MessageReader messageReader = service.getMessageReader();
        List<Message> messages;
        try {
            messages = messageReader.read(service);
        } catch (IOException e) {
            //已配置拦截器，这里不做处理
            return;
        }

        for (Message message : messages) {
            service.activeNow();

            String source = message.getHeader().getParam1();
            String destination = message.getHeader().getParam2();

            if (message.getControl().isLoginInMessage()) {
                String account = message.getHeader().getParam1();
                if (!listServiceMap.containsKey(account)) {
                    listServiceMap.put(account, service);
                    service.offerMessage(ServerUtils.createReplyLoginInMessage(true, account));
                    //todo 什么时候deny
                } else {
                    //todo
                }
            } else if (message.getControl().isLoginOutMessage()) {
                //todo
            }
            //是否是Hello消息
            else if (message.getControl().isHelloMessage()) {
                ChatServerDispatcher.LOGGER.info("Client {} is accessing the server", source);

                service.setServiceDescription(new ServiceDescription(source, destination));

                if (!serviceMap.containsKey(service.getServiceDescription())) {
                    serviceMap.put(service.getServiceDescription(), service);
                    ChatServerDispatcher.LOGGER.info("Client {} accesses the server successfully", source);

                    if (isGroupChat(message)) {
                        ChatServerDispatcher.LOGGER.info("This connection is a group chat");

                        service.setGroupChat(true);
                        String groupName = service.getServiceDescription().getDestination();
                        GroupService groupService;
                        if (groupServiceMap.containsKey(groupName)) {
                            groupService = groupServiceMap.get(groupName);
                        } else {
                            groupService = new GroupService();
                            groupService.setGroupName(groupName);
                            groupServiceMap.put(groupName, groupService);
                        }
                        groupService.addService(service);
                        String greetContent1 = "大家欢迎<"
                                + source
                                + ">进入群聊聊天室!!!";
                        groupService.offerMessage(service, ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent1));

                        String greetContent2 = source +
                                "，欢迎进入群聊聊天室!!!";
                        service.offerMessage(ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent2));
                    } else {
                        String greetContent = source +
                                "，欢迎进入私人聊天室!!!";
                        service.offerMessage(ServerUtils.createSystemMessage(
                                false,
                                source,
                                greetContent));

                    }
                } else {
                    ChatServerDispatcher.LOGGER.info("The name of client {} is already exists", source);

                    String greetContent = source +
                            "名字重复，登录失败";
                    service.offerMessage(ServerUtils.createSystemMessage(
                            true,
                            source,
                            greetContent));
                }
            }
            //客户端要求断开连接
            else if (message.getControl().isOffLineMessage()) {
                ChatServerDispatcher.LOGGER.info("The client {} request goes offline", source);

                offLine(service);
            }
            //是否为群聊
            else if (service.isGroupChat()) {
                String groupName = ((Message) message).getHeader().getParam2();
                GroupService groupService = groupServiceMap.get(groupName);
                groupService.offerMessage(service, message);
            }
            //非群聊
            else {
                //由于ServiceDescription的hash与equals是带有方向的，因此必须获取与该用户连接的反向描述符
                ServiceDescription reverseServiceDescription = service.getServiceDescription().getReverse();
                if (serviceMap.containsKey(reverseServiceDescription)) {
                    Service toService = serviceMap.get(reverseServiceDescription);
                    SocketChannel toSocketChannel = toService.getSocketChannel();
                    toService.offerMessage(message);
                } else {
                    String systemContent = "["
                            + service.getServiceDescription().getDestination()
                            + "]未上线";
                    service.offerMessage(ServerUtils.createSystemMessage(
                            false,
                            source,
                            systemContent
                    ));
                }
            }
        }
    }

    /**
     * 写入信息
     */
    private void writeMessage() {
        int readyWriteNum;
        try {
            readyWriteNum = getWriteSelector().selectNow();
        } catch (IOException e) {
            throw new RuntimeException("selectNow invoke failed!");
        }

        if (readyWriteNum <= 0) return;

        Set<SelectionKey> selectionKeys = getWriteSelector().selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();

            writeMessageToService(selectionKey);

            iterator.remove();
        }
    }

    private void writeMessageToService(SelectionKey selectionKey) {
        Service service = (Service) selectionKey.attachment();

        MessageWriter messageWriter = service.getMessageWriter();

        Message message = service.pollMessage();

        if (message != null) {
            try {
                messageWriter.write(message, service);
            } catch (IOException e) {
                //已配置拦截器，这里不做处理
                return;
            }
        }
    }

    /**
     * 检查处于Service是否处于活跃状态
     * 超过一定时间就强制下线
     */
    private void checkActive() {
        long currentStamp = System.currentTimeMillis();
        for (Service service : getServices()) {
            if (currentStamp - service.getRecentActiveTimeStamp() > ServerUtils.MAX_INACTIVE_TIME * 60 * 1000L) {
                service.offerMessage(ServerUtils.createSystemMessage(
                        true,
                        service.getServiceDescription().getSource(),
                        "占着茅坑不拉屎，你可以滚了!!!"
                        )
                );
            }
        }
    }


    /**
     * 离线的后续处理
     *
     * @param service
     */
    @Override
    public void offLine(Service service) {
        ChatServerDispatcher.LOGGER.info("Service {} is getOff from {}", service, this);

        SocketChannel socketChannel = service.getSocketChannel();

        for (Selector selector : service.getSelectors()) {
            SelectionKey selectionKey = socketChannel.keyFor(selector);
            if (selectionKey != null) selectionKey.cancel();
        }
        service.getSelectors().clear();

        if (socketChannel.isConnected()) {
            try {
                socketChannel.finishConnect();
            } catch (IOException e) {
            }
        }

        if (socketChannel.isOpen()) {
            try {
                socketChannel.close();
            } catch (IOException e) {
            }
        }

        serviceMap.remove(service.getServiceDescription());

        if (service.isGroupChat()) {
            GroupService groupService = groupServiceMap.get(service.getServiceDescription().getDestination());
            groupService.removeService(service);
            if (groupService.isGroupEmpty()) {
                groupServiceMap.remove(groupService.getGroupName());
            } else {
                String systemContent = "["
                        + service.getServiceDescription().getSource()
                        + "]已断开连接";
                groupService.offerMessage(service, ServerUtils.createSystemMessage(
                        false,
                        service.getServiceDescription().getDestination(),
                        systemContent
                ));
            }
        } else {
            //由于ServiceDescription的hash与equals是带有方向的，因此必须获取与该用户连接的反向描述符
            ServiceDescription reverseServiceDescription = service.getServiceDescription().getReverse();
            if (serviceMap.containsKey(reverseServiceDescription)) {
                Service toService = serviceMap.get(reverseServiceDescription);
                String systemContent = "["
                        + service.getServiceDescription().getSource()
                        + "]已断开连接";
                toService.offerMessage(ServerUtils.createSystemMessage(
                        false,
                        service.getServiceDescription().getDestination(),
                        systemContent
                ));
            }
        }

        getServices().remove(service);
        if (getServiceNum() <= 0)
            getBindThread().interrupt();
    }

    /**
     * 判断是否为群聊
     *
     * @param message
     * @return
     */
    private boolean isGroupChat(Message message) {
        return message.getHeader().getParam2().startsWith("#");
    }
}

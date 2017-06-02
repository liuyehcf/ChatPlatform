package org.liuyehcf.chat.server;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Liuye on 2017/5/29.
 */
public class ChatServerListener {

    /**
     * 服务器域名或ip
     */
    private static String serverHost;

    /**
     * 服务器端口号
     */
    private static int serverPort;

    /**
     * 服务端监听channel
     */
    private static ServerSocketChannel serverSocketChannel;


    private static void init() {

        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(serverHost, serverPort));
        } catch (IOException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("聊天服务器启动失败");
        }

    }

    private static void start() {
        init();
        ChatServerDispatcher.LOGGER.debug("The server starts");

        while (!Thread.currentThread().isInterrupted()) {

            SocketChannel socketChannel = listen();

            ChatServerDispatcher.LOGGER.debug("Listen to new connections");

            ChatServerDispatcher.getSingleton().dispatch(socketChannel);

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                Thread.currentThread().interrupt();
            }
        }
    }

    private static SocketChannel listen() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = serverSocketChannel.accept();
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
        return socketChannel;
    }


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("请输入服务器地址:");
        serverHost = scanner.next();

        System.out.println("请输入服务器端口:");
        serverPort = scanner.nextInt();

        ChatServerListener.start();
    }
}



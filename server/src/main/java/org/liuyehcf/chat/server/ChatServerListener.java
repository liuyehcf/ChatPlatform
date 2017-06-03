package org.liuyehcf.chat.server;


import org.liuyehcf.chat.handler.WindowHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 服务端监听器
 * Created by Liuye on 2017/5/29.
 */
public class ChatServerListener {

    /**
     * 服务器域名或ip
     */
    private String serverHost;

    /**
     * 服务器端口号
     */
    private int serverPort;

    /**
     * 服务端监听channel
     */
    private ServerSocketChannel serverSocketChannel;

    /**
     * 回调
     */
    private WindowHandler handler;


    public ChatServerListener(String serverHost, int serverPort, WindowHandler handler) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.handler = handler;

        start();
    }


    private void init() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(serverHost, serverPort));
            handler.onSucceed();
        } catch (IOException e) {
            e.printStackTrace(System.out);
            handler.onFailure();
            System.exit(0);
        }
    }

    private void start() {
        init();
        ChatServerDispatcher.LOGGER.info("The server starts");

        while (!Thread.currentThread().isInterrupted()) {

            SocketChannel socketChannel = listen();

            ChatServerDispatcher.LOGGER.info("Listen to new connections");

            ChatServerDispatcher.getSingleton().dispatch(socketChannel);

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                Thread.currentThread().interrupt();
            }
        }
    }

    private SocketChannel listen() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = serverSocketChannel.accept();
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
        return socketChannel;
    }
}



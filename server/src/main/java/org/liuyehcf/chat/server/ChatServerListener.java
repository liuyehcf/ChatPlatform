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


    private boolean init() {
        boolean initSuccessful;
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(serverHost, serverPort));
            handler.onSucceed();
            initSuccessful = true;
        } catch (IOException e) {
            e.printStackTrace(System.out);
            handler.onFailure();
            initSuccessful = false;
        }
        return initSuccessful;
    }

    private void start() {
        if (!init()) {
            ChatServerDispatcher.LOGGER.info("The server starts failed!");
            return;
        }
        ChatServerDispatcher.LOGGER.info("The server starts successfully!");

        while (!Thread.currentThread().isInterrupted()) {

            SocketChannel socketChannel = listen();

            //由于当前线程可能被中断，listen()方法会阻塞直到检测到新的连接或者被中断，被中断时返回null，并且此时中断标志位未被置位置
            if (socketChannel == null) continue;

            ChatServerDispatcher.LOGGER.info("Listen to new connections");

            ChatServerDispatcher.getSingleton().dispatch(socketChannel);

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                //重置中断现场，sleep内部会吃掉中断标志
                Thread.currentThread().interrupt();
            }
        }

        //终止服务器
        ChatServerDispatcher.getSingleton().stop();
    }

    private SocketChannel listen() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = serverSocketChannel.accept();
        } catch (IOException e) {
            ChatServerDispatcher.LOGGER.info("The ServerListener thread is interrupted");
        }
        return socketChannel;
    }
}



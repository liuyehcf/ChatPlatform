package org.liuyehcf.chat.server.boot;

import org.liuyehcf.chat.handler.WindowHandler;
import org.liuyehcf.chat.server.pipeline.ServerConnectionListener;

/**
 * Created by Liuye on 2017/9/25.
 */
public class CmdMain {
    public static void main(String[] args){
        try{
            String serverHost=args[0];
            Integer serverPort=Integer.parseInt(args[1]);

            Thread listenThread = new Thread(new ServerConnectionListener(
                    serverHost,
                    serverPort,
                    new WindowHandler() {
                        //注意，以下两个回调是由listenThread这个线程来做的
                        @Override
                        public void onSuccessful() {

                        }

                        @Override
                        public void onFailure() {

                        }
                    }));

            listenThread.start();
        }catch(Throwable e){
            System.err.println("Param wrong!");
        }
    }
}

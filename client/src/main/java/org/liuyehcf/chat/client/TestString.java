package org.liuyehcf.chat.client;

import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.Protocol;

/**
 * Created by Liuye on 2017/6/6.
 */
public class TestString {
    public static void main(String[] args){
        Message message=new Message();

        message.setControl(new Protocol.Control());
        message.setHeader(new Protocol.Header());
        message.setBody(new Protocol.Body());

        System.out.println(new Protocol().wrap(message));
        System.out.println(message.getHeader().getHeaderString());
    }
}

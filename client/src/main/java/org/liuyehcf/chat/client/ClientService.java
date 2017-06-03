package org.liuyehcf.chat.client;

import org.liuyehcf.chat.common.*;
import org.liuyehcf.chat.protocol.Message;
import org.liuyehcf.chat.protocol.TextProtocol;
import org.liuyehcf.chat.reader.MessageReaderFactory;
import org.liuyehcf.chat.writer.MessageWriterFactory;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;

/**
 * Created by HCF on 2017/5/30.
 */
public class ClientService extends Service {

    /**
     * 信息头
     */
    private TextProtocol.TextHeader textHeader;

    /**
     * 聊天界面
     */
    private final ChatWindow bindChatWindow;

    public ChatWindow getBindChatWindow() {
        return bindChatWindow;
    }

    public TextProtocol.TextHeader getTextHeader() {
        return textHeader;
    }

    /**
     * 构造函数，允许抛出异常，交给ChatWindows处理
     *
     * @param fromUserName
     * @param toUserName
     * @param messageReaderFactory
     * @param messageWriterFactory
     * @param inetSocketAddress
     * @throws IOException
     */
    public ClientService(String fromUserName,
                         String toUserName,
                         MessageReaderFactory messageReaderFactory,
                         MessageWriterFactory messageWriterFactory,
                         InetSocketAddress inetSocketAddress,
                         ChatWindow bindChatWindow) throws IOException {

        super(fromUserName, toUserName, messageReaderFactory, messageWriterFactory);

        this.bindChatWindow = bindChatWindow;

        socketChannel = SocketChannel.open();
        socketChannel.connect(inetSocketAddress);

        textHeader = new TextProtocol.TextHeader();

        textHeader.setFromUserName(fromUserName);
        textHeader.setToUserName(toUserName);

        ChatClientDispatcher.getSingleton().getServiceMap().put(getServiceDescription(), this);
    }

    public void flushOnWindow(boolean isSend, String content) {
        JTextPane textPane = getBindChatWindow().getTextPane();
        if (isSend) {
            StyledDocument styledDocument = textPane.getStyledDocument();

            MutableAttributeSet mutableAttributeSet = new SimpleAttributeSet();
            StyleConstants.setAlignment(mutableAttributeSet, StyleConstants.ALIGN_RIGHT);
            ChatWindow.setParagraphAttributes(textPane, mutableAttributeSet, false);

            StyleConstants.setForeground(mutableAttributeSet, Color.red);
            try {
                styledDocument.insertString(styledDocument.getLength(), content + "\n", mutableAttributeSet);
            } catch (BadLocationException e) {

            }
        } else {
            StyledDocument styledDocument = textPane.getStyledDocument();

            MutableAttributeSet mutableAttributeSet = new SimpleAttributeSet();
            StyleConstants.setAlignment(mutableAttributeSet, StyleConstants.ALIGN_LEFT);
            ChatWindow.setParagraphAttributes(textPane, mutableAttributeSet, false);

            StyleConstants.setForeground(mutableAttributeSet, Color.black);
            try {
                styledDocument.insertString(styledDocument.getLength(), content + "\n", mutableAttributeSet);
            } catch (BadLocationException e) {

            }
        }
    }
}

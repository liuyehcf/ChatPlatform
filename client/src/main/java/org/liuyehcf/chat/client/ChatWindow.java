package org.liuyehcf.chat.client;

import org.liuyehcf.chat.common.Service;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by Liuye on 2017/6/2.
 */
public class ChatWindow {
    /**
     * 聊天界面
     */
    private JFrame frame;

    /**
     * 服务器主机名或IP
     */
    private String serverHost;

    /**
     * 服务器端口
     */
    private int serverPort;

    /**
     * 信源名字
     */
    private String fromUserName;

    /**
     * 信宿名字
     */
    private String toUserName;

    /**
     * 登录回调
     */
    private LoginHandler handler;

    /**
     * 连接
     */
    private Service service;

    /**
     * 文本域
     */
    private JTextPane textPane;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);

    public JTextPane getTextPane() {
        return textPane;
    }

    public ChatWindow(String serverHost, Integer serverPort, String fromUserName, String toUserName, LoginHandler handler) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.fromUserName = fromUserName;
        this.toUserName = toUserName;
        this.handler = handler;

        initWindow();

        connect();
    }

    /**
     * 设置JTextPane的对齐方式
     *
     * @param editor
     * @param attr
     * @param replace
     */
    static final void setParagraphAttributes(JEditorPane editor,
                                       AttributeSet attr,
                                       boolean replace) {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        StyledDocument doc = getStyledDocument(editor);
        doc.setParagraphAttributes(start, end - start, attr, replace);
    }

    static final StyledDocument getStyledDocument(JEditorPane editorPane) {
        Document document = editorPane.getDocument();
        if (document instanceof StyledDocument) {
            return (StyledDocument) document;
        }
        throw new IllegalArgumentException("document must be StyledDocument");
    }


    private void initWindow() {
        frame = new JFrame();

        /*
         * 滚动条显示文本框
         */
        textPane = new JTextPane();
        textPane.setFont(GLOBAL_FONT);
        textPane.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBounds(25, 25, 800, 600);

        /*
         * 设置一个输入文本框以及一个按钮
         */
        JPanel panel = new JPanel();
        panel.setLayout(null);//这行必须，否则会很诡异

        //输入框
        JTextField textField = new JTextField();
        textField.setBounds(25, 650, 700, 100);
        textField.setBorder(new LineBorder(new Color(127, 157, 185), 1, false));
        textField.setFont(GLOBAL_FONT);
        panel.add(textField);

        //按钮
        JButton button = new JButton();
        button.setText("SEND");
        button.setBounds(725, 650, 100, 100);
        button.setFont(GLOBAL_FONT);
        panel.add(button);
        button.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String content = textField.getText();
                if (content == null || content.equals("")) return;
                ClientUtils.sendNormalMessage(service, content);
                textField.setText("");
            }
        });

        frame.add(scrollPane);
        frame.add(panel);

        //设置大小
        frame.setSize(875, 825);
        frame.setLocation(300, 100);

        /*
         * 设置窗口监听器
         */
        frame.addWindowListener(new MyWindowListener());

        /*
         * 这里的关闭策略选择DISPOSE_ON_CLOSE，这样子窗口关了，父窗口不会关
         */
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    private void connect() {
        try {
            service = new ClientService(
                    fromUserName,
                    toUserName,
                    ChatClientDispatcher.getSingleton().getMessageReaderFactory(),
                    ChatClientDispatcher.getSingleton().getMessageWriterFactory(),
                    new InetSocketAddress(serverHost, serverPort),
                    this
            );
        } catch (IOException e) {
            handler.onFailure();
            System.exit(0);
        }

        ChatClientDispatcher.getSingleton().dispatch(service);

        ClientUtils.sendSystemMessage(service, true, false);
    }

    private final class MyWindowListener implements WindowListener {
        @Override
        public void windowOpened(WindowEvent e) {

        }

        @Override
        public void windowClosing(WindowEvent e) {

        }

        @Override
        public void windowClosed(WindowEvent e) {
            ClientUtils.sendSystemMessage(
                    service,
                    false,
                    true);
        }

        @Override
        public void windowIconified(WindowEvent e) {

        }

        @Override
        public void windowDeiconified(WindowEvent e) {

        }

        @Override
        public void windowActivated(WindowEvent e) {

        }

        @Override
        public void windowDeactivated(WindowEvent e) {

        }
    }


    public static void main(String[] args){
        new ChatWindow("1",1,"1","1",null);
    }
}
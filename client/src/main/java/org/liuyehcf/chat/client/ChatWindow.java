package org.liuyehcf.chat.client;

import org.liuyehcf.chat.handler.WindowHandler;
import org.liuyehcf.chat.protocol.Protocol;
import sun.applet.Main;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

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
    private String fromUser;

    /**
     * 信宿名字
     */
    private String toUser;

    /**
     * 此会话信息头
     */
    private Protocol.Header header;

    /**
     * 登录回调
     */
    private WindowHandler handler;

    /**
     * 该会话关联的主界面
     */
    private final MainWindow mainWindow;

    /**
     * 当前会话绑定的连接，该链接可能绑定了多个会话
     */
    private ClientSessionConnection bindConnection;

    /**
     * 滚动框区域
     */
    private JScrollPane scrollPane;

    /**
     * 文本域
     */
    private JTextPane textPane;

    /**
     * 输入框
     */
    private JTextField textField;

    /**
     * 发送按钮
     */
    private JButton button;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);

    public Protocol.Header getHeader() {
        return header;
    }

    public ClientSessionConnection getBindConnection() {
        return bindConnection;
    }

    public ChatWindow(MainWindow mainWindow, String serverHost, Integer serverPort, String fromUser, String toUser, WindowHandler handler) {
        this.mainWindow = mainWindow;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.handler = handler;

        this.header = new Protocol.Header();
        this.header.setParam1(fromUser);
        this.header.setParam2(toUser);

        initWindow();

        connect();
    }


    private void initWindow() {
        frame = new JFrame();

        //滚动条显示文本框
        textPane = new JTextPane();
        textPane.setFont(GLOBAL_FONT);
        textPane.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBounds(25, 25, 800, 600);


        //设置一个输入文本框以及一个按钮
        JPanel panel = new JPanel();
        panel.setLayout(null);//这行必须，否则会很诡异

        //输入框
        textField = new JTextField();
        textField.setBounds(25, 650, 700, 100);
        textField.setBorder(new LineBorder(new Color(127, 157, 185), 1, false));
        textField.setFont(GLOBAL_FONT);
        textField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                //响应回车键
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    button.doClick();
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
        panel.add(textField);

        //按钮
        button = new JButton();
        button.setText("SEND");
        button.setBounds(725, 650, 100, 100);
        button.setFont(GLOBAL_FONT);
        panel.add(button);
        button.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String content = textField.getText();
                if (content == null || content.equals("")) return;
                ClientUtils.sendNormalMessage(bindConnection, header, content);
                textField.setText("");
            }
        });

        frame.add(scrollPane);
        frame.add(panel);

        //设置大小
        frame.setSize(875, 825);
        frame.setLocation(300, 100);

        //设置窗口监听器
        frame.addWindowListener(new MyWindowListener());

        //这里的关闭策略选择DISPOSE_ON_CLOSE，这样子窗口关了，父窗口不会关
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    private void connect() {
        //公用一条连接即可
        bindConnection = (ClientSessionConnection) ClientConnectionDispatcher.getSingleton()
                .getSessionConnection(fromUser, serverHost, serverPort);

        if (bindConnection != null) {
            ClientConnectionDispatcher.getSingleton().dispatchSessionConnection(bindConnection);
            ClientUtils.sendSessionHelloMessage(bindConnection, header);
            bindConnection.addSessionWindow(fromUser, this);
        } else {
            handler.onFailure();
            frame.dispose();
        }
    }

    public void flushOnWindow(boolean isSent, boolean isSystem, String content) {

        StyledDocument styledDocument = textPane.getStyledDocument();

        int startPos = styledDocument.getLength();

        SimpleAttributeSet simpleAttributeSet = new SimpleAttributeSet();

        if (isSent)
            StyleConstants.setAlignment(simpleAttributeSet, StyleConstants.ALIGN_RIGHT);
        else
            StyleConstants.setAlignment(simpleAttributeSet, StyleConstants.ALIGN_LEFT);

        if (isSystem)
            StyleConstants.setForeground(simpleAttributeSet, Color.red);
        else
            StyleConstants.setForeground(simpleAttributeSet, Color.black);

        StyleConstants.setFontSize(simpleAttributeSet, 20);
        StyleConstants.setBold(simpleAttributeSet, true);

        try {
            styledDocument.insertString(styledDocument.getLength(), content + "\n", simpleAttributeSet);
        } catch (Exception e) {

        }

        int endPos = styledDocument.getLength();

        /*
         * 虽然在执行StyledDocument#insertString的时候已经指定了同样的格式，但是这里还得再刷新一下
         */
        styledDocument.setParagraphAttributes(startPos, endPos - startPos, simpleAttributeSet, true);

        //滚动到最下方
        JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
        scrollBar.setValue(scrollBar.getMaximum());
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
            ClientUtils.sendSessionOffLineMessage(bindConnection, header);
            mainWindow.removeChatWindow(ChatWindow.this);
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
}
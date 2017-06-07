package org.liuyehcf.chat.client;

import org.liuyehcf.chat.handler.WindowHandler;
import org.liuyehcf.chat.protocol.Protocol;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by Liuye on 2017/6/7.
 */
public class GroupSessionWindow extends JFrame implements TreeSelectionListener {
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
    private String groupName;

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
    private final MainWindow bindMainWindow;

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

    /**
     * 群聊成员树
     */
    private JTree tree;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);

    public Protocol.Header getHeader() {
        return header;
    }

    public ClientSessionConnection getBindConnection() {
        return bindConnection;
    }

    public GroupSessionWindow(MainWindow bindMainWindow, String serverHost, Integer serverPort, String fromUserName, String groupName, WindowHandler handler) {
        this.bindMainWindow = bindMainWindow;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.fromUserName = fromUserName;
        this.groupName = groupName;
        this.handler = handler;

        this.header = new Protocol.Header();
        this.header.setParam1(this.fromUserName);
        this.header.setParam2(this.groupName);

        initWindow();

        this.bindMainWindow.addGroupSessionWindow(this.groupName, this);
    }


    private void initWindow() {
        //成员列表
        tree = new JTree();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("群内成员");
        DefaultTreeModel model = new DefaultTreeModel(root);

        //设置数据模型
        tree.setModel(model);

        //添加事件
        tree.addTreeSelectionListener(this);

        //滚动面板
        JScrollPane jScrollPane = new JScrollPane(tree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        //添加树到滚动面板
        jScrollPane.getViewport().add(tree);

        //添加滚动面板到窗口中
        this.getContentPane().add(jScrollPane);

        jScrollPane.setBounds(25, 25, 100, 600);

        //滚动条显示文本框
        textPane = new JTextPane();
        textPane.setFont(GLOBAL_FONT);
        textPane.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBounds(125, 25, 700, 600);


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
                ClientUtils.sendNormalMessage(bindConnection, true, header, content);
                textField.setText("");
            }
        });

        this.add(scrollPane);
        this.add(panel);

        //设置标题
        this.setTitle("群聊<" + groupName + ">");

        //设置大小
        this.setSize(875, 825);
        this.setLocation(300, 100);

        //设置窗口监听器
        this.addWindowListener(new MyWindowListener());

        //这里的关闭策略选择DISPOSE_ON_CLOSE，这样子窗口关了，父窗口不会关
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setVisible(true);
    }

    public void connect() {
        //共用一条连接即可
        bindConnection = ClientConnectionDispatcher.getSingleton()
                .getSessionConnection(fromUserName, serverHost, serverPort);

        if (bindConnection != null) {
            ClientUtils.sendOpenSessionMessage(bindConnection, true, header);
            bindConnection.addGroupSessionWindow(groupName, this);
        } else {
            handler.onFailure();
            this.dispose();
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

    @Override
    public void valueChanged(TreeSelectionEvent e) {

    }

    private final class MyWindowListener implements WindowListener {
        @Override
        public void windowOpened(WindowEvent e) {

        }

        @Override
        public void windowClosing(WindowEvent e) {
            ClientUtils.sendCloseSessionMessage(bindConnection, true, header);
            bindMainWindow.removeGroupSessionWindow(groupName);
            //GroupSessionWindows关联的SessionConnection在发送消息后调用remove方法
        }

        @Override
        public void windowClosed(WindowEvent e) {

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

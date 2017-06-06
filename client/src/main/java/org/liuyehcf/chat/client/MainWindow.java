package org.liuyehcf.chat.client;

import org.liuyehcf.chat.handler.WindowHandler;
import org.liuyehcf.chat.protocol.Protocol;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Liuye on 2017/6/5.
 */
public class MainWindow extends JFrame implements TreeSelectionListener {
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
    private String account;

    /**
     * 信宿名字
     */
    private String password;

    /**
     * 关联的连接
     */
    private ClientMainConnection bindMainConnection;

    /**
     * 会话窗口界面
     */
    private Set<SessionWindow> sessionWindows;

    /**
     * 登录回调
     */
    private WindowHandler handler;

    /**
     * 树组件
     */
    private JTree jTree;

    /**
     * 树根节点
     */
    private DefaultMutableTreeNode rootList;

    /**
     * 一级节点，在线好友
     */
    private DefaultMutableTreeNode onlineList;

    /**
     * 一级节点，离线好友
     */
    private DefaultMutableTreeNode offlineList;

    private Set<String> onlineFriends;

    public MainWindow(String serverHost,
                      Integer serverPort,
                      String account,
                      String password,
                      WindowHandler handler) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.account = account;
        this.password = password;
        this.handler = handler;

        sessionWindows = new HashSet<SessionWindow>();

        if (ClientConnectionDispatcher.getSingleton().getMainWindowMap().containsKey(account)) {
            throw new RuntimeException();//todo
        }
        ClientConnectionDispatcher.getSingleton().getMainWindowMap().put(account, this);

        init();
    }

    private void init() {
        jTree = new JTree();

        //树节点的相关数据
        rootList = new DefaultMutableTreeNode("好友");

        onlineList = new DefaultMutableTreeNode("在线好友");
        offlineList = new DefaultMutableTreeNode("离线好友");


        rootList.add(onlineList);
        rootList.add(offlineList);

        //设置数据模型
        jTree.setModel(new DefaultTreeModel(rootList));

        //添加事件
        jTree.addTreeSelectionListener(this);

        //滚动面板
        JScrollPane jScrollPane = new JScrollPane(jTree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        //添加树到滚动面板
        jScrollPane.getViewport().add(jTree);

        //添加滚动面板到窗口中
        this.getContentPane().add(jScrollPane);

        this.setTitle("六爷聊天系统");

        //自动调整大小
        this.pack();

        //增加窗口监听器
        this.addWindowListener(new MyWindowListener());

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }


    /**
     * 每一个MainWindow对应一个ClientMainConnection
     */
    public void connect() {
        try {
            bindMainConnection = new ClientMainConnection(
                    account,
                    Protocol.SERVER_USER_NAME,
                    new InetSocketAddress(serverHost, serverPort),
                    this
            );
        } catch (Exception e) {
            //如果抛出异常，那么MainWindow启动失败，执行失败回调
            handler.onFailure();
            //关闭主界面
            this.dispose();
            return;
        }

        ClientConnectionDispatcher.getSingleton().dispatcherMainConnection(bindMainConnection, account, password);
        handler.onSuccessful();
    }


    public SessionWindow createSessionWindow(String fromUser, String toUser) {
        if (!fromUser.equals(account)) throw new RuntimeException();
        SessionWindow newSessionWindow = new SessionWindow(
                this,
                serverHost,
                serverPort,
                fromUser,
                toUser,
                new WindowHandler() {
                    @Override
                    public void onSuccessful() {
                        //todo
                    }

                    @Override
                    public void onFailure() {
                        //todo
                    }
                });
        newSessionWindow.connect();
        return newSessionWindow;
    }


    @Override
    public void valueChanged(TreeSelectionEvent e) {
        //获取选择的节点
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) jTree
                .getLastSelectedPathComponent();
        if (onlineFriends.contains(node.getUserObject()))
            createSessionWindow(account, (String) node.getUserObject());
    }


    public void flushUserList(List<String> s) {
        onlineFriends = new HashSet<String>(s);
        onlineList.removeAllChildren();
        for (String user : s) {
            if (!user.equals(account)) {
                onlineList.add(new DefaultMutableTreeNode(user));
            }
        }

        //展开节点
        for (int i = 0; i < jTree.getRowCount(); i++)
            jTree.expandRow(i);
    }

    public void addSessionWindow(SessionWindow sessionWindow) {
        sessionWindows.add(sessionWindow);
    }

    public void removeSessionWindow(SessionWindow sessionWindow) {
        sessionWindows.remove(sessionWindow);
    }


    private final class MyWindowListener implements WindowListener {
        @Override
        public void windowOpened(WindowEvent e) {

        }

        @Override
        public void windowClosing(WindowEvent e) {
            //需要关闭当前主界面对应的所有会话连接
            for (SessionWindow sessionWindow : sessionWindows) {
                sessionWindow.dispose();
            }
            ClientUtils.sendLoginOutMessage(bindMainConnection, account);
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

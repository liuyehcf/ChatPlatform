package org.liuyehcf.chat.client;

import org.liuyehcf.chat.handler.WindowHandler;
import org.liuyehcf.chat.pipe.PipeLineTask;
import org.liuyehcf.chat.protocol.Protocol;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.net.InetSocketAddress;
import java.util.List;

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
    private ClientMainConnection mainConnection;

    /**
     * 主界面所绑定的主界面线程，一个主界面线程可能管理多个主界面窗口
     */
    private PipeLineTask bindMainTask;

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

        if (ClientConnectionDispatcher.getSingleton().getMainWindowMap().containsKey(account)) {
            throw new RuntimeException();//todo
        }
        ClientConnectionDispatcher.getSingleton().getMainWindowMap().put(account, this);

        init();
        connect();
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

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }


    private void connect() {
        try {
            mainConnection = new ClientMainConnection(
                    account,
                    Protocol.SERVER_USER_NAME,
                    new InetSocketAddress(serverHost, serverPort),
                    this
            );
            handler.onSucceed();
        } catch (Exception e) {
            handler.onFailure();
            //todo 如何只关闭当前ChatWindow
            this.dispose();
            return;
        }

        ClientConnectionDispatcher.getSingleton().dispatcherMainConnection(mainConnection, account, password);
    }


    @Override
    public void valueChanged(TreeSelectionEvent e) {
        //获取选择的节点

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) jTree
                .getLastSelectedPathComponent();

        if (node.getLevel() == 2) {
            new ChatWindow(serverHost, serverPort, account, (String) node.getUserObject(), new WindowHandler() {
                @Override
                public void onSucceed() {
                    //todo
                }

                @Override
                public void onFailure() {
                    //todo
                }
            });
        }
    }


    public void flushUserList(List<String> s) {
        onlineList.removeAllChildren();
        for (String user : s) {
            if (!user.equals(account)) {
                onlineList.add(new DefaultMutableTreeNode(user));
            }
        }
    }


    private MainWindow() {
        init();
        onlineList.add(new DefaultMutableTreeNode("hehe"));
        onlineList.add(new DefaultMutableTreeNode("haha"));
    }

    public static void main(String[] args) {

        //Windows风格
        //String lookAndFeel = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";

        //Windows Classic风格
        //String lookAndFeel = "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel";

        //系统当前风格
        String lookAndFeel = UIManager.getSystemLookAndFeelClassName();

        try {
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception e) {
            e.printStackTrace();
        }

        MainWindow demo = new MainWindow();

        demo.setVisible(true);
    }
}

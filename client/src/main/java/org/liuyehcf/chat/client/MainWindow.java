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
    protected JTree jTree;

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

        DefaultMutableTreeNode rootList = new DefaultMutableTreeNode("好友");

        DefaultMutableTreeNode onlineList = new DefaultMutableTreeNode("在线好友");

        DefaultMutableTreeNode offlineList = new DefaultMutableTreeNode("离线好友");


        onlineList.add(new DefaultMutableTreeNode("好友1"));

        onlineList.add(new DefaultMutableTreeNode("好友2"));

        offlineList.add(new DefaultMutableTreeNode("好友3"));

        offlineList.add(new DefaultMutableTreeNode("好友4"));

        rootList.add(onlineList);

        rootList.add(offlineList);


        //树的数据模型

        DefaultTreeModel model = new DefaultTreeModel(rootList);

        //设置数据模型

        jTree.setModel(model);

        // 展开所有树

//        for (int i = 0; i < jTree.getRowCount(); i++)
//
//            jTree.expandRow(i);

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

        this.setTitle("JTree的事件例子");

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

        if (node.getLevel() == 0) {

            //显示提示信息

//            JOptionPane.showMessageDialog(null,
//
//                    node.getUserObject() + ": 共" + node.getChildCount() + "个国家");

        } else if (node.getLevel() == 1) {

//            //显示提示信息
//
//            JOptionPane.showMessageDialog(null,
//
//                    node.getUserObject() + ": 共" + node.getChildCount() + "名名将");

        } else if (node.getLevel() == 2) {

            //显示提示信息

            JOptionPane.showMessageDialog(null, node.getParent() + "名将: " +

                    node.getUserObject());

        }

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

        } catch (ClassNotFoundException e) {

            e.printStackTrace();

        } catch (InstantiationException e) {

            e.printStackTrace();

        } catch (IllegalAccessException e) {

            e.printStackTrace();

        } catch (UnsupportedLookAndFeelException e) {

            e.printStackTrace();

        }

        //MainWindow demo = new MainWindow();

        //demo.setVisible(true);

    }
}

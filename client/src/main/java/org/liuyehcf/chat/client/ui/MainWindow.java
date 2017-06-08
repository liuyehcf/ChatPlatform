package org.liuyehcf.chat.client.ui;

import org.liuyehcf.chat.client.ClientConnectionDispatcher;
import org.liuyehcf.chat.client.connection.ClientMainConnection;
import org.liuyehcf.chat.client.utils.ClientUtils;
import org.liuyehcf.chat.handler.WindowHandler;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
     * [对方名称--会话窗口]的映射
     */
    private Map<String, SessionWindow> sessionWindowMap;

    /**
     * 群聊窗口界面
     * [群聊名称--群聊会话窗口]的映射
     */
    private Map<String, GroupSessionWindow> groupSessionWindowMap;

    /**
     * 组
     */
    private Set<String> groupNames;

    /**
     * 登录回调
     */
    private WindowHandler handler;

    /**
     * 树组件
     */
    private JTree tree;

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
    private DefaultMutableTreeNode groupList;

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

        sessionWindowMap = new ConcurrentHashMap<String, SessionWindow>();
        groupSessionWindowMap = new ConcurrentHashMap<String, GroupSessionWindow>();
        groupNames = new HashSet<String>();

        init();
    }

    private void init() {
        tree = new JTree();

        //树节点的相关数据
        rootList = new DefaultMutableTreeNode("好友");

        onlineList = new DefaultMutableTreeNode("在线好友");
        groupList = new DefaultMutableTreeNode("群聊<点击添加>");

        rootList.add(new DefaultMutableTreeNode("<" + account + ">"));
        rootList.add(onlineList);
        rootList.add(groupList);

        //设置数据模型
        tree.setModel(new DefaultTreeModel(rootList));

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

        this.setTitle("六爷聊天系统");

        //自动调整大小
        //this.pack();
        this.setSize(400, 600);

        //增加窗口监听器
        this.addWindowListener(new MyWindowListener());

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }


    /**
     * 每一个MainWindow对应一个ClientMainConnection
     */
    public void connect() {
        bindMainConnection = (ClientMainConnection) ClientConnectionDispatcher.getSingleton().createAndDispatch(
                account,
                new InetSocketAddress(serverHost, serverPort),
                true,
                password);


        if (bindMainConnection == null) {
            //如果抛出异常或者达到上限，那么MainWindow启动失败，执行失败回调
            handler.onFailure();
            //关闭主界面
            this.dispose();
        } else {
            bindMainConnection.setBindMainWindow(this);

            ClientUtils.ASSERT(!ClientConnectionDispatcher.getSingleton().getMainConnectionMap().containsKey(account));

            ClientConnectionDispatcher.getSingleton().getMainConnectionMap().put(account, bindMainConnection);

            ClientUtils.sendLogInMessage(bindMainConnection, account, password);
            handler.onSuccessful();
        }
    }


    public SessionWindow createSessionWindow(String toUserName) {
        SessionWindow newSessionWindow = new SessionWindow(
                this,
                serverHost,
                serverPort,
                account,
                toUserName,
                new WindowHandler() {
                    @Override
                    public void onSuccessful() {
                    }

                    @Override
                    public void onFailure() {
                    }
                });
        newSessionWindow.connect();
        return newSessionWindow;
    }

    public GroupSessionWindow createGroupSessionWindow(String groupName) {
        GroupSessionWindow newGroupSessionWindow = new GroupSessionWindow(
                this,
                serverHost,
                serverPort,
                account,
                groupName,
                new WindowHandler() {
                    @Override
                    public void onSuccessful() {
                    }

                    @Override
                    public void onFailure() {
                    }
                });
        newGroupSessionWindow.connect();
        return newGroupSessionWindow;
    }


    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (!bindMainConnection.isActive()) {
            JOptionPane.showMessageDialog(null,
                    "您已断线，请关闭此窗口");
            return;
        }

        //获取选择的节点
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree
                .getLastSelectedPathComponent();

        //点击了好友列表
        if (node != null && node.getParent() != null
                && ((DefaultMutableTreeNode) node.getParent()).getUserObject().equals("在线好友")
                && node.getUserObject() != null) {

            if (sessionWindowMap.containsKey(node.getUserObject())) {
                //置于最前端
                sessionWindowMap.get(node.getUserObject()).toFront();
            } else {
                //开启一个会话窗口
                createSessionWindow((String) node.getUserObject());
            }

        }
        //点击了群聊列表
        else if (node != null && node.getParent() != null
                && ((DefaultMutableTreeNode) node.getParent()).getUserObject().equals("群聊<点击添加>")
                && node.getUserObject() != null) {
            if (groupSessionWindowMap.containsKey(node.getUserObject())) {
                //置于最前端
                groupSessionWindowMap.get(node.getUserObject()).toFront();
            } else {
                //开启一个群聊会话窗口
                createGroupSessionWindow((String) node.getUserObject());
            }
        }
        //点击了添加群聊
        else if (node != null && node.getUserObject().equals("群聊<点击添加>")) {

            String groupName = JOptionPane.showInputDialog("请输入群聊名").replaceAll(" ", "");

            if (groupName == null || groupName.equals("")) return;

            while (groupNames.contains(groupName)) {
                groupName = JOptionPane.showInputDialog("该名称已被占用，请重新输入").replaceAll(" ", "");
            }
            ClientUtils.sendApplyGroupNameMessage(bindMainConnection, account, groupName);
        }
    }


    public void flushUserList(List<String> userNames) {
        onlineList.removeAllChildren();
        for (String userName : userNames) {
            if (!userName.equals(account)) {
                onlineList.add(new DefaultMutableTreeNode(userName));
            }
        }

        //刷新
        ((DefaultTreeModel) tree.getModel()).reload();

        //展开节点
        for (int i = 0; i < tree.getRowCount(); i++)
            tree.expandRow(i);
    }

    public void flushGroupList(List<String> groupNames) {
        this.groupNames = new HashSet<String>(groupNames);
        groupList.removeAllChildren();
        for (String groupName : groupNames) {
            groupList.add(new DefaultMutableTreeNode(groupName));
        }

        //刷新
        ((DefaultTreeModel) tree.getModel()).reload();

        //展开节点
        for (int i = 0; i < tree.getRowCount(); i++)
            tree.expandRow(i);
    }

    public void showMessage(String content) {
        JOptionPane.showMessageDialog(null,
                content);
    }

    public void addSessionWindow(String userName, SessionWindow sessionWindow) {
        ClientUtils.ASSERT(!sessionWindowMap.containsKey(userName));
        sessionWindowMap.put(userName, sessionWindow);
    }

    public void removeSessionWindow(String userName) {
        ClientUtils.ASSERT(sessionWindowMap.containsKey(userName));
        sessionWindowMap.remove(userName);
    }

    public void addGroupSessionWindow(String groupName, GroupSessionWindow groupSessionWindow) {
        ClientUtils.ASSERT(!groupSessionWindowMap.containsKey(groupName));
        groupSessionWindowMap.put(groupName, groupSessionWindow);
    }

    public void removeGroupSessionWindow(String groupName) {
        ClientUtils.ASSERT(groupSessionWindowMap.containsKey(groupName));
        groupSessionWindowMap.remove(groupName);
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
            //需要关闭当前主界面对应的所有会话连接
            for (SessionWindow sessionWindow : sessionWindowMap.values()) {
                sessionWindow.dispose();
            }

            for (GroupSessionWindow groupSessionWindow : groupSessionWindowMap.values()) {
                groupSessionWindow.dispose();
            }

            if (bindMainConnection.isActive()) {
                ClientUtils.sendLogOutMessage(bindMainConnection, account);
                bindMainConnection.cancel();
            }
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

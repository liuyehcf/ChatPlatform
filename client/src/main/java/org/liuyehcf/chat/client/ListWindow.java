package org.liuyehcf.chat.client;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Created by Liuye on 2017/6/5.
 */
public class ListWindow extends JFrame implements TreeSelectionListener {

    /**
     * 树组件
     */
    protected JTree jTree;

    public ListWindow() {
        init();
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

        ListWindow demo = new ListWindow();

        demo.setVisible(true);

    }
}

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
import java.sql.Time;
import java.util.concurrent.TimeUnit;

/**
 * Created by Liuye on 2017/6/3.
 */
public class Test3 {

    /**
     * 聊天界面
     */
    private JFrame frame;


    private JTextPane textPane;

    private boolean flag;

    private JButton button;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);


    public Test3() {


        initWindow();

    }

    private void initWindow() {
        frame = new JFrame();


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
        textField.setHorizontalAlignment(SwingConstants.RIGHT);

        //按钮
        JButton button = new JButton();
        button.setText("SEND");
        button.setBounds(725, 650, 100, 100);
        button.setFont(GLOBAL_FONT);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (flag) {


                    StyledDocument d = textPane.getStyledDocument();

                    MutableAttributeSet attr = new SimpleAttributeSet();
                    StyleConstants.setAlignment(attr, StyleConstants.ALIGN_RIGHT);
                    setParagraphAttributes(textPane, attr, false);

                    StyleConstants.setForeground(attr, Color.red);
                    try {
                        d.insertString(d.getLength(), "好呀\n红色右\n", attr);
                    } catch (Exception e1) {

                    }
                } else {
                    StyledDocument d = textPane.getStyledDocument();

                    MutableAttributeSet attr = new SimpleAttributeSet();
                    StyleConstants.setAlignment(attr, StyleConstants.ALIGN_LEFT);
                    setParagraphAttributes(textPane, attr, false);

                    StyleConstants.setForeground(attr, Color.red);
                    try {
                        d.insertString(d.getLength(), "不好\n黑色左\n", attr);
                    } catch (Exception e1) {

                    }
                }
                flag = !flag;
            }
        });
        panel.add(button);


        this.button = new JButton();
        this.button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (flag) {


                    StyledDocument d = textPane.getStyledDocument();

                    MutableAttributeSet attr = new SimpleAttributeSet();
                    StyleConstants.setAlignment(attr, StyleConstants.ALIGN_RIGHT);
                    setParagraphAttributes(textPane, attr, false);

                    StyleConstants.setForeground(attr, Color.red);
                    try {
                        d.insertString(d.getLength(), "好呀\n红色右\n", attr);
                    } catch (Exception e1) {

                    }
                } else {
                    StyledDocument d = textPane.getStyledDocument();

                    MutableAttributeSet attr = new SimpleAttributeSet();
                    StyleConstants.setAlignment(attr, StyleConstants.ALIGN_LEFT);
                    setParagraphAttributes(textPane, attr, false);

                    StyleConstants.setForeground(attr, Color.red);
                    try {
                        d.insertString(d.getLength(), "不好\n黑色左\n", attr);
                    } catch (Exception e1) {

                    }
                }
                flag = !flag;
            }
        });

        panel.add(this.button);

        frame.add(scrollPane);
        frame.add(panel);

        //设置大小
        frame.setSize(875, 825);
        frame.setLocation(300, 100);


        /*
         * 这里的关闭策略选择DISPOSE_ON_CLOSE，这样子窗口关了，父窗口不会关
         */
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }


    public static final void setParagraphAttributes(JEditorPane editor,
                                                    AttributeSet attr, boolean replace) {
        int p0 = editor.getSelectionStart();
        int p1 = editor.getSelectionEnd();
        StyledDocument doc = getStyledDocument(editor);
        doc.setParagraphAttributes(p0, p1 - p0, attr, replace);
    }

    protected static final StyledDocument getStyledDocument(JEditorPane e) {
        Document d = e.getDocument();
        if (d instanceof StyledDocument) {
            return (StyledDocument) d;
        }
        throw new IllegalArgumentException("document must be StyledDocument");
    }

    public static void main(String[] args) {
        Test3 t = new Test3();


        while (true) {
            t.button.doClick();

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {

            }
        }
    }
}

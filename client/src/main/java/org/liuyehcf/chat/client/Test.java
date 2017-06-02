package org.liuyehcf.chat.client;

/**
 * Created by Liuye on 2017/6/2.
 */
import java.awt.*;
import javax.swing.*;
public class Test extends JFrame {
    JMenuBar jb;
    JTextArea ja;
    JScrollPane jsp;
    public void setImage() {
        jb = new JMenuBar();
        this.setJMenuBar(jb);
        ja = new JTextArea();
        jsp = new JScrollPane(ja);
        this.setSize(600, 400);
        this.setLayout(new BorderLayout());
        this.add(jsp);
        //this.setVisible(true);

    }

    public static void main(String[] args) {
        Test a = new Test();
        a.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        a.setImage();

        JFrame frame=new JFrame();

        frame.add(a);
        frame.setSize(1000,1000);
        frame.setVisible(true);
    }
}
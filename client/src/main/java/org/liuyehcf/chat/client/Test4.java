package org.liuyehcf.chat.client;

/**
 * Created by Liuye on 2017/6/3.
 */
import java.awt.BorderLayout;

import java.awt.Container;

import javax.swing.JFrame;

import javax.swing.JLabel;

import javax.swing.JScrollPane;

import javax.swing.JTextPane;

import javax.swing.UIManager;

import javax.swing.text.BadLocationException;

import javax.swing.text.DefaultStyledDocument;

import javax.swing.text.SimpleAttributeSet;

import javax.swing.text.Style;

import javax.swing.text.StyleConstants;

import javax.swing.text.StyleContext;

import javax.swing.text.StyledDocument;


public class Test4 {

//TODO 修改代码

    private static String message = "<p align='left'>In the beginning, there was COBOL, </p>"

            + "<p align='center'>then there was FORTRAN, </p>"

            + "<p align='right'>then there was BASIC, ... </p>"

            + "and now there is Java.\n";


    public static void main(String[] args) {

        String title = (args.length == 0 ? "JTextPane Example" : args[0]);


        JFrame frame = new JFrame(title);

        Container content = frame.getContentPane();


        StyleContext context = new StyleContext();

        StyledDocument document = new DefaultStyledDocument(context);


        Style style = context.getStyle(StyleContext.DEFAULT_STYLE);

        StyleConstants.setAlignment(style, StyleConstants.ALIGN_LEFT);// 左对齐


        try {

            document.insertString(document.getLength(), message, style);

        }

        catch (BadLocationException badLocationException) {

            System.err.println("Oops");

        }


        SimpleAttributeSet attributes = new SimpleAttributeSet();

        StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_RIGHT);//右对齐

        document.setParagraphAttributes(0, document.getLength(), style, true);

        try {//为什么没有右对齐呀

            document.insertString(document.getLength(), "Hello Java",

                    attributes);

        }

        catch (BadLocationException badLocationException) {

        }


        JTextPane textarea = new JTextPane();

//TODO 新增代码开始

        textarea.setContentType("text/html");

        textarea.setText(message);

//TODO 新增代码结束


        textarea.setEditable(false);

//TODO 被注释代码

/*

textarea.setBackground(UIManager.getColor("label.background"));

textarea.setFont(UIManager.getFont("label"));

textarea.setDocument(document);

*/


        JScrollPane textAreascrollPane = new JScrollPane(textarea);

        content.add(textAreascrollPane, BorderLayout.CENTER);

        JLabel AliasNameLabel = new JLabel("Local Name: ");

        content.add(AliasNameLabel, BorderLayout.SOUTH);

        frame.setSize(300, 200);

        frame.setVisible(true);

        frame.setLocationRelativeTo(null);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }

}
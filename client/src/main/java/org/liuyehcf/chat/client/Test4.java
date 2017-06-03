package org.liuyehcf.chat.client;

/**
 * Created by Liuye on 2017/6/3.
 */
import java.awt.*;

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

    public Test4()
    {
        String text = "To refer to locations within the sequence, the " +
                "coordinates used are the location between two " +
                "characters.\nAs the diagram below shows, a location " +
                "in a text document can be referred to as a position, " +
                "or an offset. This position is zero-based.";

        SimpleAttributeSet aSet = new SimpleAttributeSet();
        StyleConstants.setForeground(aSet, Color.blue);
        StyleConstants.setBackground(aSet, Color.orange);
        StyleConstants.setFontFamily(aSet, "lucida bright italic");
        StyleConstants.setFontSize(aSet, 18);

        SimpleAttributeSet bSet = new SimpleAttributeSet();
        StyleConstants.setAlignment(bSet, StyleConstants.ALIGN_CENTER);
        StyleConstants.setUnderline(bSet, true);
        StyleConstants.setFontFamily(bSet, "lucida typewriter bold");
        StyleConstants.setFontSize(bSet, 24);

        JTextPane textPane = new JTextPane();
        textPane.setText(text);
        StyledDocument doc = textPane.getStyledDocument();
        doc.setCharacterAttributes(105, doc.getLength()-105, aSet, false);
        doc.setParagraphAttributes(0, 104, bSet, false);

        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new JScrollPane(textPane));
        f.setSize(400,400);
        f.setLocation(200,200);
        f.setVisible(true);
    }

    public static void main(String[] args)
    {
        new Test4();
    }

}
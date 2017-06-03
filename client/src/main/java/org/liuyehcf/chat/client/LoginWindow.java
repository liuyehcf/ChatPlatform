package org.liuyehcf.chat.client;

import org.liuyehcf.chat.handler.WindowHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by Liuye on 2017/6/2.
 */
public class LoginWindow {
    /**
     * 聊天界面
     */
    private JFrame frame;

    private static final int LABEL_WIDTH = 150;

    private static final int COMPONENT_HEIGHT = 35;

    private static final int FIELD_WIDTH = 250;

    private static final int BLANK = 10;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);

    public LoginWindow() {
        initWindow();
    }


    private void initWindow() {

        frame = new JFrame();
        frame.setSize(450, 350);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        /* 创建面板，这个类似于 HTML 的 div 标签
         * 我们可以创建多个面板并在 JFrame 中指定位置
         * 面板中我们可以添加文本字段，按钮及其他组件。
         */
        JPanel panel = new JPanel();
        // 添加面板
        frame.add(panel);
        /*
         * 调用用户定义的方法并添加组件到面板
         */
        placeComponents(panel);

        // 设置界面可见
        frame.setVisible(true);
    }

    private void placeComponents(JPanel panel) {
        panel.setLayout(null);

        JLabel hostLabel = new JLabel("Host");
        hostLabel.setBounds(BLANK, BLANK, LABEL_WIDTH, COMPONENT_HEIGHT);
        hostLabel.setFont(GLOBAL_FONT);
        panel.add(hostLabel);

        JLabel portLabel = new JLabel("Port");
        portLabel.setBounds(BLANK, BLANK * 2 + COMPONENT_HEIGHT, LABEL_WIDTH, COMPONENT_HEIGHT);
        portLabel.setFont(GLOBAL_FONT);
        panel.add(portLabel);

        JLabel localId = new JLabel("LocalId");
        localId.setBounds(BLANK, BLANK * 3 + COMPONENT_HEIGHT * 2, LABEL_WIDTH, COMPONENT_HEIGHT);
        localId.setFont(GLOBAL_FONT);
        panel.add(localId);

        JLabel remoteId = new JLabel("RemoteId");
        remoteId.setBounds(BLANK, BLANK * 4 + COMPONENT_HEIGHT * 3, LABEL_WIDTH, COMPONENT_HEIGHT);
        remoteId.setFont(GLOBAL_FONT);
        panel.add(remoteId);


        JTextField hostField = new JTextField();
        hostField.setBounds(BLANK + LABEL_WIDTH, BLANK, FIELD_WIDTH, COMPONENT_HEIGHT);
        hostField.setFont(GLOBAL_FONT);
        panel.add(hostField);

        JTextField portField = new JTextField();
        portField.setBounds(BLANK + LABEL_WIDTH, BLANK * 2 + COMPONENT_HEIGHT, FIELD_WIDTH, COMPONENT_HEIGHT);
        portField.setFont(GLOBAL_FONT);
        panel.add(portField);

        JTextField localIdField = new JTextField();
        localIdField.setBounds(BLANK + LABEL_WIDTH, BLANK * 3 + COMPONENT_HEIGHT * 2, FIELD_WIDTH, COMPONENT_HEIGHT);
        localIdField.setFont(GLOBAL_FONT);
        panel.add(localIdField);

        JTextField remoteIdField = new JTextField();
        remoteIdField.setBounds(BLANK + LABEL_WIDTH, BLANK * 4 + COMPONENT_HEIGHT * 3, FIELD_WIDTH, COMPONENT_HEIGHT);
        remoteIdField.setFont(GLOBAL_FONT);
        panel.add(remoteIdField);

        JLabel systemLabel = new JLabel("Welcome to Liuye's ChatPlatform");
        systemLabel.setBounds(BLANK, BLANK * 6 + COMPONENT_HEIGHT * 5, LABEL_WIDTH + FIELD_WIDTH, COMPONENT_HEIGHT);
        systemLabel.setFont(GLOBAL_FONT);
        panel.add(systemLabel);

        JButton connectButton = new JButton("Connect");
        connectButton.setBounds(BLANK, BLANK * 5 + COMPONENT_HEIGHT * 4, LABEL_WIDTH + FIELD_WIDTH, COMPONENT_HEIGHT);
        connectButton.setFont(GLOBAL_FONT);
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String serverHost;
                Integer serverPort;
                String localId;
                String remoteId;
                try {
                    serverHost = hostField.getText();
                    serverPort = Integer.parseInt(portField.getText());
                    localId = localIdField.getText();
                    remoteId = remoteIdField.getText();
                } catch (Throwable e) {
                    systemLabel.setText("SYSTEM: WRONG INPUT!");
                    return;
                }
                new ChatWindow(
                        serverHost,
                        serverPort,
                        localId,
                        remoteId,
                        new WindowHandler() {
                            @Override
                            public void onSucceed() {
                                systemLabel.setText("Connection succeed!");
                            }

                            @Override
                            public void onFailure() {
                                systemLabel.setText("Connection failed!");
                            }
                        }
                );
            }
        });
        panel.add(connectButton);
    }


    public static void main(String[] args) {
        new LoginWindow();
    }
}

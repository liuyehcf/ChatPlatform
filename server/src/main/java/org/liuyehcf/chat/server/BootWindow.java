package org.liuyehcf.chat.server;

import org.liuyehcf.chat.handler.WindowHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.TimeUnit;

/**
 * Created by HCF on 2017/6/3.
 */
public class BootWindow {
    /**
     * 聊天界面
     */
    private JFrame frame;

    private static final int LABEL_WIDTH = 150;

    private static final int COMPONENT_HEIGHT = 35;

    private static final int FIELD_WIDTH = 250;

    private static final int BLANK = 10;

    private static final Font GLOBAL_FONT = new Font("alias", Font.BOLD, 20);

    /**
     * 监听线程
     */
    private Thread listenThread;

    public BootWindow() {
        initWindow();
    }

    private void initWindow() {

        frame = new JFrame();
        frame.setSize(450, 315);
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


        JTextField hostField = new JTextField();
        hostField.setBounds(BLANK + LABEL_WIDTH, BLANK, FIELD_WIDTH, COMPONENT_HEIGHT);
        hostField.setFont(GLOBAL_FONT);
        panel.add(hostField);

        JTextField portField = new JTextField();
        portField.setBounds(BLANK + LABEL_WIDTH, BLANK * 2 + COMPONENT_HEIGHT, FIELD_WIDTH, COMPONENT_HEIGHT);
        portField.setFont(GLOBAL_FONT);
        panel.add(portField);


        JLabel systemLabel = new JLabel("Welcome to Liuye's ChatPlatform");
        systemLabel.setBounds(BLANK, BLANK * 7 + COMPONENT_HEIGHT * 4, LABEL_WIDTH + FIELD_WIDTH, COMPONENT_HEIGHT);
        systemLabel.setFont(GLOBAL_FONT);
        panel.add(systemLabel);

        JButton bootButton = new JButton("Boot");
        bootButton.setBounds(BLANK, BLANK * 5 + COMPONENT_HEIGHT * 2, LABEL_WIDTH + FIELD_WIDTH, COMPONENT_HEIGHT);
        bootButton.setFont(GLOBAL_FONT);
        bootButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String serverHost;
                Integer serverPort;
                try {
                    serverHost = hostField.getText();
                    serverPort = Integer.parseInt(portField.getText());
                } catch (Throwable e) {
                    systemLabel.setText("SYSTEM: WRONG INPUT!");
                    return;
                }

                if (listenThread != null) {
                    systemLabel.setText("SYSTEM: Server is already started!");
                    return;
                }

                listenThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        new ServerConnectionListener(
                                serverHost,
                                serverPort,
                                new WindowHandler() {
                                    //注意，以下两个回调是由listenThread这个线程来做的
                                    @Override
                                    public void onSuccessful() {
                                        systemLabel.setText("Boot succeed!");
                                    }

                                    @Override
                                    public void onFailure() {
                                        systemLabel.setText("Boot failed!");
                                        listenThread = null;
                                    }
                                });
                    }
                });

                listenThread.start();
            }
        });

        JButton stopButton = new JButton("Stop");
        stopButton.setBounds(BLANK, BLANK * 6 + COMPONENT_HEIGHT * 3, LABEL_WIDTH + FIELD_WIDTH, COMPONENT_HEIGHT);
        stopButton.setFont(GLOBAL_FONT);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (listenThread == null) {
                    systemLabel.setText("SYSTEM: Server hasn't start yet!");
                } else if (!listenThread.isAlive()) {
                    systemLabel.setText("SYSTEM: Server is already stopped!");
                } else {
                    listenThread.interrupt();
                    systemLabel.setText("SYSTEM: Server is stopping!");
                    while (listenThread.isAlive()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {

                        }
                    }
                    systemLabel.setText("SYSTEM: Server stopped successfully!");
                    listenThread = null;
                }
            }
        });

        panel.add(bootButton);
        panel.add(stopButton);
    }

    public static void main(String[] args) {
        new BootWindow();
    }
}

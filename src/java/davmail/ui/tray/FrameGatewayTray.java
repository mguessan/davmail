package davmail.ui.tray;

import davmail.Settings;
import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.ui.AboutFrame;
import davmail.ui.SettingsFrame;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.log4j.lf5.LF5Appender;
import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.viewer.LogBrokerMonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Failover GUI for Java 1.5 without SWT
 */
public class FrameGatewayTray implements DavGatewayTrayInterface {
    protected FrameGatewayTray() {
    }

    protected static JFrame mainFrame;
    protected static AboutFrame aboutFrame;
    protected static SettingsFrame settingsFrame;
    protected static LogBrokerMonitor logBrokerMonitor;
    private static JEditorPane errorArea;
    private static JLabel errorLabel;
    private static JEditorPane messageArea;
    private static Image image;
    private static Image image2;
    private static Image inactiveImage;
    private boolean isActive = true;

    public Image getFrameIcon() {
        return image;
    }

    public void switchIcon() {
        isActive = true;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (mainFrame.getIconImage().equals(image)) {
                    mainFrame.setIconImage(image2);
                } else {
                    mainFrame.setIconImage(image);
                }
            }
        });
    }

    public void resetIcon() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                mainFrame.setIconImage(image);
            }
        });
    }

    public void inactiveIcon() {
        isActive = false;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                mainFrame.setIconImage(inactiveImage);
            }
        });
    }

    public boolean isActive() {
        return isActive;
    }

    public void displayMessage(final String message, final Level level) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (mainFrame != null) {
                    if (level.equals(Level.INFO)) {
                        errorLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
                        errorArea.setText(message);
                    } else if (level.equals(Level.WARN)) {
                        errorLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
                        errorArea.setText(message);
                    } else if (level.equals(Level.ERROR)) {
                        errorLabel.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
                        errorArea.setText(message);
                    }
                    messageArea.setText(message);
                }
            }
        });
    }

    public void about() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                aboutFrame.update();
                aboutFrame.setVisible(true);
            }
        });
    }

    public void preferences() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                settingsFrame.reload();
                settingsFrame.setVisible(true);
            }
        });
    }

    public void showLogs() {
        Logger rootLogger = Logger.getRootLogger();
        LF5Appender lf5Appender = (LF5Appender) rootLogger.getAppender("LF5Appender");
        if (lf5Appender == null) {
            logBrokerMonitor = new LogBrokerMonitor(LogLevel.getLog4JLevels()) {
                @Override
                protected void closeAfterConfirm() {
                    hide();
                }
            };
            lf5Appender = new LF5Appender(logBrokerMonitor);
            lf5Appender.setName("LF5Appender");
            rootLogger.addAppender(lf5Appender);
        }
        lf5Appender.getLogBrokerMonitor().show();
    }

    public void init() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

    protected void buildMenu() {
        // create a popup menu
        JMenu menu = new JMenu(BundleMessage.format("UI_DAVMAIL_GATEWAY"));
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        mainFrame.setJMenuBar(menuBar);

        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener aboutListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                about();
            }
        };
        // create menu item for the default action
        JMenuItem aboutItem = new JMenuItem(BundleMessage.format("UI_ABOUT"));
        aboutItem.addActionListener(aboutListener);
        menu.add(aboutItem);


        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener settingsListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                preferences();
            }
        };
        // create menu item for the default action
        JMenuItem defaultItem = new JMenuItem(BundleMessage.format("UI_SETTINGS"));
        defaultItem.addActionListener(settingsListener);
        menu.add(defaultItem);

        JMenuItem logItem = new JMenuItem(BundleMessage.format("UI_SHOW_LOGS"));
        logItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showLogs();
            }
        });
        menu.add(logItem);

        // create an action exitListener to listen for exit action executed on the tray icon
        ActionListener exitListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                DavGateway.stop();
                // dispose frames
                settingsFrame.dispose();
                aboutFrame.dispose();
                if (logBrokerMonitor != null) {
                    logBrokerMonitor.dispose();
                }
                } catch (Exception exc) {
                    DavGatewayTray.error(exc);
                }
                // make sure we do exit
                System.exit(0);
            }
        };
        // create menu item for the exit action
        JMenuItem exitItem = new JMenuItem(BundleMessage.format("UI_EXIT"));
        exitItem.addActionListener(exitListener);
        menu.add(exitItem);
    }

    protected void createAndShowGUI() {
        // set native look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_SET_SYSTEM_LOOK_AND_FEEL"), e);
        }

        image = DavGatewayTray.loadImage("tray.png");
        image2 = DavGatewayTray.loadImage(AwtGatewayTray.TRAY2_PNG);
        inactiveImage = DavGatewayTray.loadImage(AwtGatewayTray.TRAYINACTIVE_PNG);

        mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setTitle(BundleMessage.format("UI_DAVMAIL_GATEWAY"));
        mainFrame.setIconImage(image);

        JPanel errorPanel = new JPanel();
        errorPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_LAST_MESSAGE")));
        errorPanel.setLayout(new BoxLayout(errorPanel, BoxLayout.X_AXIS));
        errorArea = new JTextPane();
        errorArea.setEditable(false);
        errorArea.setBackground(mainFrame.getBackground());
        errorLabel = new JLabel();
        errorPanel.add(errorLabel);
        errorPanel.add(errorArea);

        JPanel messagePanel = new JPanel();
        messagePanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_LAST_LOG")));
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.X_AXIS));

        messageArea = new JTextPane();
        messageArea.setText(BundleMessage.format("LOG_STARTING_DAVMAIL"));
        messageArea.setEditable(false);
        messageArea.setBackground(mainFrame.getBackground());
        messagePanel.add(messageArea);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(errorPanel);
        mainPanel.add(messagePanel);
        mainFrame.add(mainPanel);

        aboutFrame = new AboutFrame();
        settingsFrame = new SettingsFrame();
        buildMenu();

        mainFrame.setMinimumSize(new Dimension(400, 180));
        mainFrame.pack();
        // workaround MacOSX
        if (mainFrame.getSize().width < 400 || mainFrame.getSize().height < 180) {
            mainFrame.setSize(Math.max(mainFrame.getSize().width, 400),
                    Math.max(mainFrame.getSize().height, 180));
        }
        // center frame
        mainFrame.setLocation(mainFrame.getToolkit().getScreenSize().width / 2 -
                mainFrame.getSize().width / 2,
                mainFrame.getToolkit().getScreenSize().height / 2 -
                        mainFrame.getSize().height / 2);
        mainFrame.setVisible(true);

        // display settings frame on first start
        if (Settings.isFirstStart()) {
            settingsFrame.setVisible(true);
        }
    }
}

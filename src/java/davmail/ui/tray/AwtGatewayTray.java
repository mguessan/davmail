package davmail.ui.tray;

import davmail.Settings;
import davmail.BundleMessage;
import davmail.ui.AboutFrame;
import davmail.ui.SettingsFrame;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.apache.log4j.lf5.LF5Appender;
import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.viewer.LogBrokerMonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Tray icon handler based on java 1.6
 */
public class AwtGatewayTray implements DavGatewayTrayInterface {
    protected static final String TRAY2_PNG = "tray2.png";
    protected static final String TRAY_PNG = "tray.png";
    protected static final String TRAYINACTIVE_PNG = "trayinactive.png";

    protected AwtGatewayTray() {
    }

    static AboutFrame aboutFrame;
    static SettingsFrame settingsFrame;

    private static TrayIcon trayIcon;
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
                if (trayIcon.getImage() == image) {
                    trayIcon.setImage(image2);
                } else {
                    trayIcon.setImage(image);
                }
            }
        });
    }

    public void resetIcon() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                trayIcon.setImage(image);
            }
        });
    }

    public void inactiveIcon() {
        isActive = false;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                trayIcon.setImage(inactiveImage);
            }
        });
    }

    public boolean isActive() {
        return isActive;
    }

    public void displayMessage(final String message, final Priority priority) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (trayIcon != null) {
                    TrayIcon.MessageType messageType = null;
                    if (priority == Priority.INFO) {
                        messageType = TrayIcon.MessageType.INFO;
                    } else if (priority == Priority.WARN) {
                        messageType = TrayIcon.MessageType.WARNING;
                    } else if (priority == Priority.ERROR) {
                        messageType = TrayIcon.MessageType.ERROR;
                    }
                    if (messageType != null) {
                        trayIcon.displayMessage(BundleMessage.format("UI_DAVMAIL_GATEWAY"), message, messageType);
                    }
                    trayIcon.setToolTip(BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\n' + message);
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

    public void init() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }


    protected void createAndShowGUI() {
        // set native look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_SET_SYSTEM_LOOK_AND_FEEL"), e);
        }

        // get the SystemTray instance
        SystemTray tray = SystemTray.getSystemTray();
        image = DavGatewayTray.loadImage(TRAY_PNG);
        image2 = DavGatewayTray.loadImage(TRAY2_PNG);
        inactiveImage = DavGatewayTray.loadImage(TRAYINACTIVE_PNG);

        // create a popup menu
        PopupMenu popup = new PopupMenu();

        aboutFrame = new AboutFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener aboutListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                about();
            }
        };
        // create menu item for the default action
        MenuItem aboutItem = new MenuItem(BundleMessage.format("UI_ABOUT"));
        aboutItem.addActionListener(aboutListener);
        popup.add(aboutItem);

        settingsFrame = new SettingsFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener settingsListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                preferences();
            }
        };
        // create menu item for the default action
        MenuItem defaultItem = new MenuItem(BundleMessage.format("UI_SETTINGS"));
        defaultItem.addActionListener(settingsListener);
        popup.add(defaultItem);

        MenuItem logItem = new MenuItem(BundleMessage.format("UI_SHOW_LOGS"));
        logItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Logger rootLogger = Logger.getRootLogger();
                LF5Appender lf5Appender = (LF5Appender) rootLogger.getAppender("LF5Appender");
                if (lf5Appender == null) {
                    lf5Appender = new LF5Appender(new LogBrokerMonitor(LogLevel.getLog4JLevels()) {
                        @Override protected void closeAfterConfirm() {
                            hide();
                        }
                    });
                    lf5Appender.setName("LF5Appender");
                    rootLogger.addAppender(lf5Appender);
                }
                lf5Appender.getLogBrokerMonitor().show();
            }
        });
        popup.add(logItem);

        // create an action exitListener to listen for exit action executed on the tray icon
        ActionListener exitListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SystemTray.getSystemTray().remove(trayIcon);
                //noinspection CallToSystemExit
                System.exit(0);
            }
        };
        // create menu item for the exit action
        MenuItem exitItem = new MenuItem(BundleMessage.format("UI_EXIT"));
        exitItem.addActionListener(exitListener);
        popup.add(exitItem);

        /// ... add other items
        // construct a TrayIcon
        trayIcon = new TrayIcon(image, BundleMessage.format("UI_DAVMAIL_GATEWAY"), popup);
        // set the TrayIcon properties
        trayIcon.addActionListener(settingsListener);
        // ...
        // add the tray image
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_CREATE_TRAY"), e);
        }

        // display settings frame on first start
        if (Settings.isFirstStart()) {
            settingsFrame.setVisible(true);
        }
    }
}

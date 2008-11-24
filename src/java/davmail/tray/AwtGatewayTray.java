package davmail.tray;

import davmail.Settings;
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
    protected AwtGatewayTray() {
    }

    private static TrayIcon trayIcon = null;
    private static Image image = null;
    private static Image image2 = null;
    private static Image inactiveImage = null;
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
                    trayIcon.displayMessage("DavMail gateway", message, messageType);
                }
                trayIcon.setToolTip("DavMail gateway \n" + message);
            }
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
            DavGatewayTray.warn("Unable to set system look and feel", e);
        }

        // get the SystemTray instance
        SystemTray tray = SystemTray.getSystemTray();
        image = DavGatewayTray.loadImage("tray.png");
        image2 = DavGatewayTray.loadImage("tray.png");
        inactiveImage = DavGatewayTray.loadImage("trayinactive.png");

        // create a popup menu
        PopupMenu popup = new PopupMenu();

        final AboutFrame aboutFrame = new AboutFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener aboutListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                aboutFrame.update();
                aboutFrame.setVisible(true);
            }
        };
        // create menu item for the default action
        MenuItem aboutItem = new MenuItem("About...");
        aboutItem.addActionListener(aboutListener);
        popup.add(aboutItem);

        final SettingsFrame settingsFrame = new SettingsFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener settingsListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                settingsFrame.reload();
                settingsFrame.setVisible(true);
            }
        };
        // create menu item for the default action
        MenuItem defaultItem = new MenuItem("Settings...");
        defaultItem.addActionListener(settingsListener);
        popup.add(defaultItem);

        MenuItem logItem = new MenuItem("Show logs...");
        logItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Logger rootLogger = Logger.getRootLogger();
                LF5Appender lf5Appender = (LF5Appender) rootLogger.getAppender("LF5Appender");
                if (lf5Appender == null) {
                    lf5Appender = new LF5Appender(new LogBrokerMonitor(LogLevel.getLog4JLevels()) {
                        protected void closeAfterConfirm() {
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
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(exitListener);
        popup.add(exitItem);

        /// ... add other items
        // construct a TrayIcon
        trayIcon = new TrayIcon(image, "DavMail Gateway", popup);
        // set the TrayIcon properties
        trayIcon.addActionListener(settingsListener);
        // ...
        // add the tray image
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            DavGatewayTray.warn("Unable to create tray", e);
        }

        // display settings frame on first start
        if (Settings.isFirstStart()) {
            settingsFrame.setVisible(true);
        }
    }
}

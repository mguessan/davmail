package davmail;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.apache.log4j.lf5.LF5Appender;
import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.viewer.LogBrokerMonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

/**
 * Tray icon handler
 */
public class DavGatewayTray {
    protected static final Logger logger = Logger.getLogger("davmail");

    // lock for synchronized block
    protected static final Object lock = new Object();

    protected static TrayIcon trayIcon = null;
    protected static Image image = null;
    protected static Image image2 = null;

    public static void switchIcon() {
        try {
            synchronized (lock) {
                if (trayIcon.getImage() == image) {
                    trayIcon.setImage(image2);
                } else {
                    trayIcon.setImage(image);
                }
            }
        } catch (NoClassDefFoundError e) {
            // ignore, jdk <= 1.6
        }

    }

    public static void resetIcon() {
        try {
            synchronized (lock) {
                trayIcon.setImage(image);
            }
        } catch (NoClassDefFoundError e) {
            // ignore, jdk <= 1.6
        }
    }

    protected static void displayMessage(String message, Priority priority) {
        synchronized (lock) {
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
            logger.log(priority, message);
        }

    }

    public static void debug(String message) {
        displayMessage(message, Priority.DEBUG);
    }

    public static void info(String message) {
        displayMessage(message, Priority.INFO);
    }

    public static void warn(String message) {
        displayMessage(message, Priority.WARN);
    }

    public static void error(String message) {
        displayMessage(message, Priority.ERROR);
    }

    public static void debug(String message, Exception e) {
        debug(message + " " + e + " " + e.getMessage());
    }

    public static void info(String message, Exception e) {
        info(message + " " + e + " " + e.getMessage());
    }

    public static void warn(String message, Exception e) {
        warn(message + " " + e + " " + e.getMessage());
    }

    public static void error(String message, Exception e) {
        error(message + " " + e + " " + e.getMessage());
    }

    public static void init() {
        try {
            if (SystemTray.isSupported()) {
                // set native look and feel
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
                catch (Exception e) {
                    // ignore
                }

                // get the SystemTray instance
                SystemTray tray = SystemTray.getSystemTray();
                // load an image
                ClassLoader classloader = DavGatewayTray.class.getClassLoader();
                URL imageUrl = classloader.getResource("tray.png");
                image = Toolkit.getDefaultToolkit().getImage(imageUrl);
                URL imageUrl2 = classloader.getResource("tray2.png");
                image2 = Toolkit.getDefaultToolkit().getImage(imageUrl2);
                // create a popup menu
                PopupMenu popup = new PopupMenu();
                final SettingsFrame settingsFrame = new SettingsFrame();
                // create an action settingsListener to listen for settings action executed on the tray icon
                ActionListener settingsListener = new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        settingsFrame.setVisible(true);
                    }
                };
                // create menu item for the default action
                MenuItem defaultItem = new MenuItem("Settings...");
                defaultItem.addActionListener(settingsListener);
                popup.add(defaultItem);

                MenuItem logItem = new MenuItem("Logs...");
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
                    System.err.println(e);
                }
            }

        } catch (NoClassDefFoundError e) {
            DavGatewayTray.warn("JDK 1.6 needed for system tray support");
        }

    }
}

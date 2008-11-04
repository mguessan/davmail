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
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.io.IOException;

/**
 * Tray icon handler based on java 1.6
 */
public class AwtGatewayTray implements DavGatewayTrayInterface {
    protected AwtGatewayTray() {
    }

    // LOCK for synchronized block
    protected static final Object LOCK = new Object();

    private static TrayIcon trayIcon = null;
    private static Image image = null;
    private static Image image2 = null;

    public Image getFrameIcon() {
        return image;
    }

    public void switchIcon() {
        synchronized (LOCK) {
            if (trayIcon.getImage() == image) {
                trayIcon.setImage(image2);
            } else {
                trayIcon.setImage(image);
            }
        }
    }

    public void resetIcon() {
        synchronized (LOCK) {
            trayIcon.setImage(image);
        }
    }

    public void displayMessage(String message, Priority priority) {
        synchronized (LOCK) {
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

    }

    public void init() {
        // set native look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            DavGatewayTray.warn("Unable to set system look and feel", e);
        }

        // get the SystemTray instance
        SystemTray tray = SystemTray.getSystemTray();
        // load an image
        ClassLoader classloader = DavGatewayTray.class.getClassLoader();
        try {
            URL imageUrl = classloader.getResource("tray.png");
            image = ImageIO.read(imageUrl);
        } catch (IOException e) {
            DavGatewayTray.warn("Unable to load image", e);
        }

        try {
            URL imageUrl2 = classloader.getResource("tray2.png");
            image2 = ImageIO.read(imageUrl2);
        } catch (IOException e) {
            DavGatewayTray.warn("Unable to load image", e);
        }

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

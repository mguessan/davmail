/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
    ActionListener settingsListener;

    static TrayIcon trayIcon;
    private static Image image;
    private static Image image2;
    private static Image inactiveImage;
    static LogBrokerMonitor logBrokerMonitor;
    private boolean isActive = true;

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    public Image getFrameIcon() {
        return image;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public void switchIcon() {
        isActive = true;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (trayIcon.getImage().equals(image)) {
                    trayIcon.setImage(image2);
                } else {
                    trayIcon.setImage(image);
                }
            }
        });
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void resetIcon() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                trayIcon.setImage(image);
            }
        });
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void inactiveIcon() {
        isActive = false;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                trayIcon.setImage(inactiveImage);
            }
        });
    }

    /**
     * Check if current tray status is inactive (network down).
     *
     * @return true if inactive
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Display balloon message for log level.
     *
     * @param message text message
     * @param level   log level
     */
    public void displayMessage(final String message, final Level level) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (trayIcon != null) {
                    TrayIcon.MessageType messageType = null;
                    if (level.equals(Level.INFO)) {
                        messageType = TrayIcon.MessageType.INFO;
                    } else if (level.equals(Level.WARN)) {
                        messageType = TrayIcon.MessageType.WARNING;
                    } else if (level.equals(Level.ERROR)) {
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

    /**
     * Open about window
     */
    public void about() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                aboutFrame.update();
                aboutFrame.setVisible(true);
            }
        });
    }

    /**
     * Open settings window
     */
    public void preferences() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                settingsFrame.reload();
                settingsFrame.setVisible(true);
            }
        });
    }

    /**
     * Create tray icon and register frame listeners.
     */
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
        settingsListener = new ActionListener() {
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
        });
        popup.add(logItem);

        // create an action exitListener to listen for exit action executed on the tray icon
        ActionListener exitListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    DavGateway.stop();
                    SystemTray.getSystemTray().remove(trayIcon);

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

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

import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.Settings;
import davmail.ui.AboutFrame;
import davmail.ui.SettingsFrame;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.lf5.LF5Appender;
import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.viewer.LogBrokerMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.internal.gtk.OS;
import org.eclipse.swt.widgets.*;

import javax.swing.*;
import java.io.IOException;
import java.net.URL;

/**
 * Tray icon handler based on SWT
 */
public class SwtGatewayTray implements DavGatewayTrayInterface {
    protected SwtGatewayTray() {
    }

    SettingsFrame settingsFrame;
    AboutFrame aboutFrame;

    private static TrayItem trayItem;
    private static java.awt.Image awtImage;
    private static Image image;
    private static Image image2;
    private static Image inactiveImage;
    private static Display display;
    private static Shell shell;
    private LogBrokerMonitor logBrokerMonitor;
    private boolean isActive = true;
    private boolean isReady;

    private final Thread mainThread = Thread.currentThread();

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    public java.awt.Image getFrameIcon() {
        return awtImage;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public void switchIcon() {
        isActive = true;
        display.syncExec(new Runnable() {
            public void run() {
                if (trayItem.getImage().equals(image)) {
                    trayItem.setImage(image2);
                } else {
                    trayItem.setImage(image);
                }
            }
        });

    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void resetIcon() {
        display.syncExec(new Runnable() {
            public void run() {
                trayItem.setImage(image);
            }
        });
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void inactiveIcon() {
        isActive = false;
        display.syncExec(new Runnable() {
            public void run() {
                trayItem.setImage(inactiveImage);
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
     * Log and display balloon message according to log level.
     *
     * @param message text message
     * @param level   log level
     */
    public void displayMessage(final String message, final Level level) {
        if (trayItem != null) {
            display.asyncExec(new Runnable() {
                public void run() {
                    int messageType = 0;
                    if (level.equals(Level.INFO)) {
                        messageType = SWT.ICON_INFORMATION;
                    } else if (level.equals(Level.WARN)) {
                        messageType = SWT.ICON_WARNING;
                    } else if (level.equals(Level.ERROR)) {
                        messageType = SWT.ICON_ERROR;
                    }
                    if (messageType != 0) {
                        final ToolTip toolTip = new ToolTip(shell, SWT.BALLOON | messageType);
                        toolTip.setText(BundleMessage.format("UI_DAVMAIL_GATEWAY"));
                        toolTip.setMessage(message);
                        trayItem.setToolTip(toolTip);
                        toolTip.setVisible(true);
                    }
                    trayItem.setToolTipText(BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\n' + message);
                }
            });
        }
    }

    /**
     * Load image with current class loader.
     *
     * @param fileName image resource file name
     * @return image
     */
    public static Image loadSwtImage(String fileName) {
        Image result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            result = new Image(display, imageUrl.openStream());
        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }

    /**
     * Create tray icon and register frame listeners.
     */
    public void init() {
        // register error handler to avoid application crash on concurrent X access from SWT and AWT
        try {
            OS.gdk_error_trap_push();
        } catch (NoClassDefFoundError e) {
            // ignore
        }
        final String systemLookAndFeelClassName = UIManager.getSystemLookAndFeelClassName();
        try {
            // workaround for bug when SWT and AWT both try to access Gtk
            if (systemLookAndFeelClassName.indexOf("gtk") >= 0) {
                System.setProperty("swing.defaultlaf", UIManager.getCrossPlatformLookAndFeelClassName());
            } else {
                System.setProperty("swing.defaultlaf", systemLookAndFeelClassName);
            }
        } catch (Exception e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_SET_LOOK_AND_FEEL"));
        }

        new Thread("SWT") {
            @Override
            public void run() {
                try {
                    display = new Display();
                    shell = new Shell(display);

                    final Tray tray = display.getSystemTray();
                    if (tray != null) {

                        trayItem = new TrayItem(tray, SWT.NONE);
                        trayItem.setToolTipText(BundleMessage.format("UI_DAVMAIL_GATEWAY"));

                        awtImage = DavGatewayTray.loadImage(AwtGatewayTray.TRAY_PNG);
                        image = loadSwtImage(AwtGatewayTray.TRAY_PNG);
                        image2 = loadSwtImage(AwtGatewayTray.TRAY_ACTIVE_PNG);
                        inactiveImage = loadSwtImage(AwtGatewayTray.TRAY_INACTIVE_PNG);

                        trayItem.setImage(image);

                        // create a popup menu
                        final Menu popup = new Menu(shell, SWT.POP_UP);
                        trayItem.addListener(SWT.MenuDetect, new Listener() {
                            public void handleEvent(Event event) {
                                display.asyncExec(
                                        new Runnable() {
                                            public void run() {
                                                popup.setVisible(true);
                                            }
                                        });
                            }
                        });

                        MenuItem aboutItem = new MenuItem(popup, SWT.PUSH);
                        aboutItem.setText(BundleMessage.format("UI_ABOUT"));
                        aboutItem.addListener(SWT.Selection, new Listener() {
                            public void handleEvent(Event event) {
                                display.asyncExec(
                                        new Runnable() {
                                            public void run() {
                                                if (aboutFrame == null) {
                                                    aboutFrame = new AboutFrame();
                                                }
                                                aboutFrame.update();
                                                aboutFrame.setVisible(true);
                                            }
                                        });
                            }
                        });

                        trayItem.addListener(SWT.DefaultSelection, new Listener() {
                            public void handleEvent(Event event) {
                                display.asyncExec(
                                        new Runnable() {
                                            public void run() {
                                                // create frame on first call
                                                if (settingsFrame == null) {
                                                    settingsFrame = new SettingsFrame();
                                                }
                                                settingsFrame.reload();
                                                settingsFrame.setVisible(true);
                                                // workaround for focus on first open
                                                settingsFrame.setVisible(true);
                                            }
                                        });
                            }
                        });

                        // create menu item for the default action
                        MenuItem defaultItem = new MenuItem(popup, SWT.PUSH);
                        defaultItem.setText(BundleMessage.format("UI_SETTINGS"));
                        defaultItem.addListener(SWT.Selection, new Listener() {
                            public void handleEvent(Event event) {
                                display.asyncExec(
                                        new Runnable() {
                                            public void run() {
                                                // create frame on first call
                                                if (settingsFrame == null) {
                                                    settingsFrame = new SettingsFrame();
                                                }
                                                settingsFrame.reload();
                                                settingsFrame.setVisible(true);
                                                // workaround for focus on first open
                                                settingsFrame.setVisible(true);
                                            }
                                        });
                            }
                        });

                        MenuItem logItem = new MenuItem(popup, SWT.PUSH);
                        logItem.setText(BundleMessage.format("UI_SHOW_LOGS"));
                        logItem.addListener(SWT.Selection, new Listener() {
                            public void handleEvent(Event event) {
                                display.asyncExec(
                                        new Runnable() {
                                            public void run() {

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
                            }
                        });

                        MenuItem exitItem = new MenuItem(popup, SWT.PUSH);
                        exitItem.setText(BundleMessage.format("UI_EXIT"));
                        exitItem.addListener(SWT.Selection, new Listener() {
                            public void handleEvent(Event event) {
                                DavGateway.stop();
                                shell.dispose();
                            }
                        });

                        // display settings frame on first start
                        if (Settings.isFirstStart()) {
                            // create frame on first call
                            if (settingsFrame == null) {
                                settingsFrame = new SettingsFrame();
                            }
                            settingsFrame.setVisible(true);
                        }

                        synchronized (mainThread) {
                            // ready
                            isReady = true;
                            mainThread.notifyAll();
                        }

                        while (!shell.isDisposed()) {
                            if (!display.readAndDispatch()) {
                                display.sleep();
                            }
                        }

                        if (trayItem != null) {
                            trayItem.dispose();
                            trayItem = null;
                        }

                        if (image != null) {
                            image.dispose();
                        }
                        if (image2 != null) {
                            image2.dispose();
                        }
                        try {
                            if (!display.isDisposed()) {
                                display.dispose();
                            }
                        } catch (Exception e) {
                            // already disposed
                        }
                        // dispose AWT frames
                        if (settingsFrame != null) {
                            settingsFrame.dispose();
                        }
                        if (aboutFrame != null) {
                            aboutFrame.dispose();
                        }
                        if (logBrokerMonitor != null) {
                            logBrokerMonitor.dispose();
                        }
                    }
                } catch (Exception exc) {
                    DavGatewayTray.error(exc);
                }
                // make sure we do exit
                System.exit(0);
            }
        }.start();
        while (true) {
            // wait for SWT init
            try {
                synchronized (mainThread) {
                    if (isReady) {
                        break;
                    }
                    mainThread.wait(1000);
                }
            } catch (InterruptedException e) {
                DavGatewayTray.error(new BundleMessage("LOG_ERROR_WAITING_FOR_SWT_INIT"), e);
            }
        }
    }

}

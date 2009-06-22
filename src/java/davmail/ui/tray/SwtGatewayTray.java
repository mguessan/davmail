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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
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

    private static TrayItem trayItem;
    private static java.awt.Image awtImage;
    private static Image image;
    private static Image image2;
    private static Image inactiveImage;
    private static Display display;
    private static Shell shell;
    private static LogBrokerMonitor logBrokerMonitor;
    private boolean isActive = true;
    private boolean isReady;

    private final Thread mainThread = Thread.currentThread();

    public java.awt.Image getFrameIcon() {
        return awtImage;
    }

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

    public void resetIcon() {
        display.syncExec(new Runnable() {
            public void run() {
                trayItem.setImage(image);
            }
        });
    }

    public void inactiveIcon() {
        isActive = false;
        display.syncExec(new Runnable() {
            public void run() {
                trayItem.setImage(inactiveImage);
            }
        });
    }

    public boolean isActive() {
        return isActive;
    }

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

    public void init() {
        // set native look and feel
        try {
            String lafClassName = UIManager.getSystemLookAndFeelClassName();
            // workaround for bug when SWT and AWT both try to access Gtk
            if (lafClassName.indexOf("gtk") > 0) {
                lafClassName = UIManager.getCrossPlatformLookAndFeelClassName();
            }
            UIManager.setLookAndFeel(lafClassName);
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

                        awtImage = DavGatewayTray.loadImage("tray.png");
                        image = loadSwtImage("tray.png");
                        image2 = loadSwtImage(AwtGatewayTray.TRAY2_PNG);
                        inactiveImage = loadSwtImage(AwtGatewayTray.TRAYINACTIVE_PNG);

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
                        final AboutFrame aboutFrame = new AboutFrame();
                        aboutItem.addListener(SWT.Selection, new Listener() {
                            public void handleEvent(Event event) {
                                SwingUtilities.invokeLater(
                                        new Runnable() {
                                            public void run() {
                                                aboutFrame.update();
                                                aboutFrame.setVisible(true);
                                            }
                                        });
                            }
                        });

                        final SettingsFrame settingsFrame = new SettingsFrame();
                        trayItem.addListener(SWT.DefaultSelection, new Listener() {
                            public void handleEvent(Event event) {
                                SwingUtilities.invokeLater(
                                        new Runnable() {
                                            public void run() {
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
                                SwingUtilities.invokeLater(
                                        new Runnable() {
                                            public void run() {
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
                                SwingUtilities.invokeLater(
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
                        display.dispose();
                        // dispose AWT frames
                        settingsFrame.dispose();
                        aboutFrame.dispose();
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

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
import davmail.Settings;
import davmail.exchange.NetworkDownException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.lf5.LF5Appender;
import org.apache.log4j.lf5.LogLevel;
import org.apache.log4j.lf5.viewer.LogBrokerMonitor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;


/**
 * Tray icon handler
 */
public final class DavGatewayTray {
    private static final Logger LOGGER = Logger.getLogger("davmail");
    private static final long ICON_SWITCH_MINIMUM_DELAY = 250;
    private static long lastIconSwitch;

    private DavGatewayTray() {
    }

    static DavGatewayTrayInterface davGatewayTray;

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    public static java.util.List<Image> getFrameIcons() {
        java.util.List<Image> icons = null;
        if (davGatewayTray != null) {
            icons = davGatewayTray.getFrameIcons();
        }
        return icons;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public static void switchIcon() {
        if (davGatewayTray != null && !Settings.getBooleanProperty("davmail.disableTrayActivitySwitch")) {
            if (System.currentTimeMillis() - lastIconSwitch > ICON_SWITCH_MINIMUM_DELAY) {
                davGatewayTray.switchIcon();
                lastIconSwitch = System.currentTimeMillis();
            }
        }
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public static void resetIcon() {
        if (davGatewayTray != null && isActive()) {
            davGatewayTray.resetIcon();
        }
    }

    /**
     * Check if current tray status is inactive (network down).
     *
     * @return true if inactive
     */
    public static boolean isActive() {
        return davGatewayTray == null || davGatewayTray.isActive();
    }

    /**
     * Log and display balloon message according to log level.
     *
     * @param message text message
     * @param level   log level
     */
    private static void displayMessage(BundleMessage message, Level level) {
        LOGGER.log(level, message.formatLog());
        if (davGatewayTray != null && !Settings.getBooleanProperty("davmail.disableGuiNotifications")) {
            davGatewayTray.displayMessage(message.format(), level);
        }
    }

    /**
     * Log and display balloon message and exception according to log level.
     *
     * @param message text message
     * @param e       exception
     * @param level   log level
     */
    private static void displayMessage(BundleMessage message, Exception e, Level level) {
        if (e instanceof NetworkDownException) {
            LOGGER.log(level, BundleMessage.getExceptionLogMessage(message, e));
        } else {
            LOGGER.log(level, BundleMessage.getExceptionLogMessage(message, e), e);
        }
        if (davGatewayTray != null && !Settings.getBooleanProperty("davmail.disableGuiNotifications")
                && (!(e instanceof NetworkDownException))) {
            davGatewayTray.displayMessage(BundleMessage.getExceptionMessage(message, e), level);
        }
        if (davGatewayTray != null && e instanceof NetworkDownException) {
            davGatewayTray.inactiveIcon();
        }
    }

    /**
     * Log message at level DEBUG.
     *
     * @param message bundle message
     */
    public static void debug(BundleMessage message) {
        displayMessage(message, Level.DEBUG);
    }

    /**
     * Log message at level INFO.
     *
     * @param message bundle message
     */
    public static void info(BundleMessage message) {
        displayMessage(message, Level.INFO);
    }

    /**
     * Log message at level WARN.
     *
     * @param message bundle message
     */
    public static void warn(BundleMessage message) {
        displayMessage(message, Level.WARN);
    }

    /**
     * Log exception at level WARN.
     *
     * @param e exception
     */
    public static void warn(Exception e) {
        displayMessage(null, e, Level.WARN);
    }

    /**
     * Log message at level ERROR.
     *
     * @param message bundle message
     */
    public static void error(BundleMessage message) {
        displayMessage(message, Level.ERROR);
    }

    /**
     * Log exception at level WARN for NetworkDownException,
     * ERROR for other exceptions.
     *
     * @param e exception
     */
    public static void log(Exception e) {
        // only warn on network down
        if (e instanceof NetworkDownException) {
            warn(e);
        } else {
            error(e);
        }
    }

    /**
     * Log exception at level ERROR.
     *
     * @param e exception
     */
    public static void error(Exception e) {
        displayMessage(null, e, Level.ERROR);
    }

    /**
     * Log message and exception at level DEBUG.
     *
     * @param message bundle message
     * @param e       exception
     */
    public static void debug(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.DEBUG);
    }

    /**
     * Log message and exception at level WARN.
     *
     * @param message bundle message
     * @param e       exception
     */
    public static void warn(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.WARN);
    }

    /**
     * Log message and exception at level ERROR.
     *
     * @param message bundle message
     * @param e       exception
     */
    public static void error(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.ERROR);
    }

    /**
     * Create tray icon and register frame listeners.
     */
    public static void init(boolean notray) {
        String currentDesktop = System.getenv("XDG_CURRENT_DESKTOP");
        String javaVersion = System.getProperty("java.version");
        String arch = System.getProperty("sun.arch.data.model");
        LOGGER.debug("OS Name: " + System.getProperty("os.name") +
                " Java version: " + javaVersion + ((arch != null) ? ' ' + arch : "") +
                " System tray " + (SystemTray.isSupported() ? "" : "not ") + "supported " +
                ((currentDesktop == null) ? "" : "Current Desktop: " + currentDesktop)
        );

        if (Settings.isLinux()) {
            // enable anti aliasing on linux
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
        }

        if (!Settings.getBooleanProperty("davmail.server")) {
            if ("GNOME-Classic:GNOME".equals(currentDesktop) || "ubuntu:GNOME".equals(currentDesktop)) {
                LOGGER.info("System tray is not supported on Gnome, will switch to frame mode");
            } else if (!notray) {
                if ("Unity".equals(currentDesktop)) {
                    LOGGER.info("Detected Unity desktop, please follow instructions at " +
                            "http://davmail.sourceforge.net/linuxsetup.html to restore normal systray " +
                            "or run DavMail in server mode");
                }
                if (Settings.O365_INTERACTIVE.equals(Settings.getProperty("davmail.mode"))) {
                    LOGGER.info("O365Interactive is not compatible with SWT, do not try to create SWT tray");
                } else {
                    // first try to load SWT before with Java AWT
                    ClassLoader classloader = DavGatewayTray.class.getClassLoader();
                    try {
                        // trigger ClassNotFoundException
                        classloader.loadClass("org.eclipse.swt.SWT");
                        // SWT available, create tray
                        davGatewayTray = new SwtGatewayTray();
                        davGatewayTray.init();
                    } catch (ClassNotFoundException e) {
                        DavGatewayTray.info(new BundleMessage("LOG_SWT_NOT_AVAILABLE"));
                    } catch (Throwable e) {
                        DavGatewayTray.info(new BundleMessage("LOG_SWT_NOT_AVAILABLE"));
                        davGatewayTray = null;
                    }
                }
                // try java6 tray support, except on Linux
                if (davGatewayTray == null /*&& !isLinux()*/) {
                    try {
                        if (SystemTray.isSupported()) {
                            if (isOSX()) {
                                davGatewayTray = new OSXAwtGatewayTray();
                            } else {
                                davGatewayTray = new AwtGatewayTray();
                            }
                            davGatewayTray.init();
                        }
                    } catch (NoClassDefFoundError e) {
                        DavGatewayTray.info(new BundleMessage("LOG_SYSTEM_TRAY_NOT_AVAILABLE"));
                    }
                }
            }

            if (davGatewayTray == null) {
                if (isOSX()) {
                    // MacOS
                    davGatewayTray = new OSXFrameGatewayTray();
                } else {
                    davGatewayTray = new FrameGatewayTray();
                }
                davGatewayTray.init();
            }
        }
    }

    /**
     * Test if running on OSX
     *
     * @return true on Mac OS X
     */
    public static boolean isOSX() {
        return System.getProperty("os.name").toLowerCase().startsWith("mac os x");
    }

    /**
     * Test if running on Windows
     *
     * @return true on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    /**
     * Test if running on Linux
     *
     * @return true on Linux
     */
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().startsWith("linux");
    }

    /**
     * Load image with current class loader.
     *
     * @param fileName image resource file name
     * @return image
     */
    public static BufferedImage loadImage(String fileName) {
        BufferedImage result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            if (imageUrl == null) {
                throw new IOException("Missing resource: " + fileName);
            }
            result = ImageIO.read(imageUrl);
        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }

    public static BufferedImage adjustTrayIcon(BufferedImage image) {
        Color backgroundColor = null;
        String backgroundColorString = Settings.getProperty("davmail.trayBackgroundColor");

        String xdgCurrentDesktop = System.getenv("XDG_CURRENT_DESKTOP");

        boolean isKDE = "KDE".equals(xdgCurrentDesktop);
        boolean isXFCE = "XFCE".equals(xdgCurrentDesktop);
        boolean isUnity = "Unity".equals(xdgCurrentDesktop);
        boolean isCinnamon = "X-Cinnamon".equals(xdgCurrentDesktop);

        if (backgroundColorString == null || backgroundColorString.length() == 0) {
            // define color for default theme
            if (isKDE) {
                backgroundColorString = "#DDF6E8";
            }
            if (isUnity) {
                backgroundColorString = "#4D4B45";
            }
            if (isXFCE) {
                backgroundColorString = "#E8E8E7";
            }
            if (isCinnamon) {
                backgroundColorString = "#2E2E2E";
            }
        }

        int imageType = BufferedImage.TYPE_INT_ARGB;
        if (backgroundColorString != null && backgroundColorString.length() == 7
                && backgroundColorString.startsWith("#")) {
            int red = Integer.parseInt(backgroundColorString.substring(1, 3), 16);
            int green = Integer.parseInt(backgroundColorString.substring(3, 5), 16);
            int blue = Integer.parseInt(backgroundColorString.substring(5, 7), 16);
            backgroundColor = new Color(red, green, blue);
            imageType = BufferedImage.TYPE_INT_RGB;
        }

        if (backgroundColor != null || isKDE || isUnity || isXFCE) {
            int width = image.getWidth();
            int height = image.getHeight();
            int x = 0;
            int y = 0;
            if (isKDE || isXFCE) {
                width = 22;
                height = 22;
                x = 3;
                y = 3;
            } else if (isUnity) {
                width = 22;
                height = 24;
                x = 4;
                y = 4;
            } else if (isCinnamon) {
                width = 24;
                height = 24;
                x = 4;
                y = 4;
            }
            BufferedImage bufferedImage = new BufferedImage(width, height, imageType);
            Graphics2D graphics = bufferedImage.createGraphics();
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(image, x, y, null);
            graphics.dispose();
            return bufferedImage;
        } else {
            return image;
        }
    }


    /**
     * Dispose application tray icon
     */
    public static void dispose() {
        if (davGatewayTray != null) {
            davGatewayTray.dispose();
        }
    }

    /**
     * Open logging window.
     */
    public static void showLogs() {
        Logger rootLogger = Logger.getRootLogger();
        LF5Appender lf5Appender = (LF5Appender) rootLogger.getAppender("LF5Appender");
        if (lf5Appender == null) {
            LogBrokerMonitor logBrokerMonitor = new LogBrokerMonitor(LogLevel.getLog4JLevels()) {
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
}

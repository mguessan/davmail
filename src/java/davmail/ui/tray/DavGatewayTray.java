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
import davmail.exchange.NetworkDownException;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import javax.imageio.ImageIO;
import java.awt.*;
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
    public static Image getFrameIcon() {
        Image icon = null;
        if (davGatewayTray != null) {
            icon = davGatewayTray.getFrameIcon();
        }
        return icon;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public static void switchIcon() {
        if (davGatewayTray != null) {
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
        if (davGatewayTray != null) {
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
        if (davGatewayTray != null
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
    public static void init() {
        if (!Settings.getBooleanProperty("davmail.server")) {
            // first try to load SWT before with Java before 1.7
            if ("1.7".compareTo(System.getProperty("java.specification.version")) > 0) {
                ClassLoader classloader = DavGatewayTray.class.getClassLoader();
                try {
                    // trigger ClassNotFoundException
                    classloader.loadClass("org.eclipse.swt.SWT");
                    // SWT available, create tray
                    davGatewayTray = new SwtGatewayTray();
                    davGatewayTray.init();
                } catch (ClassNotFoundException e) {
                    DavGatewayTray.info(new BundleMessage("LOG_SWT_NOT_AVAILABLE"));
                }
            }
            // try java6 tray support
            if (davGatewayTray == null) {
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
    private static boolean isOSX() {
        return System.getProperty("os.name").toLowerCase().startsWith("mac os x");
    }

    /**
     * Load image with current class loader.
     *
     * @param fileName image resource file name
     * @return image
     */
    public static Image loadImage(String fileName) {
        Image result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            result = ImageIO.read(imageUrl);
        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }

}

package davmail.tray;

import org.apache.log4j.Priority;

import java.awt.SystemTray;

import davmail.tray.AwtGatewayTray;
import davmail.tray.DavGatewayTrayInterface;
import davmail.tray.SwtGatewayTray;


/**
 * Tray icon handler
 */
public class DavGatewayTray {
    protected DavGatewayTray() {
    }

    protected static DavGatewayTrayInterface davGatewayTray;

    public static void switchIcon() {
        if (davGatewayTray != null) { 
        davGatewayTray.switchIcon();
        }
    }

    public static void resetIcon() {
        if (davGatewayTray != null) { 
        davGatewayTray.resetIcon();
        }
    }

    protected static void displayMessage(String message, Priority priority) {
        if (davGatewayTray != null) { 
        davGatewayTray.displayMessage(message, priority);
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
        ClassLoader classloader = DavGatewayTray.class.getClassLoader();
        // first try to load SWT
        try {
            // trigger ClassNotFoundException
            classloader.loadClass("org.eclipse.swt.SWT");
            // SWT available, create tray
            davGatewayTray = new SwtGatewayTray();
            davGatewayTray.init();
        } catch (ClassNotFoundException e) {
            DavGatewayTray.info("SWT not available, fallback to JDK 1.6 system tray support");
        }

        // try java6 tray support
        if (davGatewayTray == null) {
            try {
                if (SystemTray.isSupported()) {
                    davGatewayTray = new AwtGatewayTray();
                    davGatewayTray.init();
                }
            } catch (NoClassDefFoundError e) {
                DavGatewayTray.info("JDK 1.6 needed for system tray support");
            }
        }
        if (davGatewayTray == null) {
            DavGatewayTray.warn("No system tray support found (tried SWT and native java)");
        }
    }
}

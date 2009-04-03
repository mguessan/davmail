package davmail.ui.tray;

import davmail.Settings;
import davmail.ui.tray.OSXFrameGatewayTray;
import davmail.exchange.NetworkDownException;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.URL;


/**
 * Tray icon handler
 */
public class DavGatewayTray {
    protected static final Logger LOGGER = Logger.getLogger("davmail");

    protected DavGatewayTray() {
    }

    static DavGatewayTrayInterface davGatewayTray;

    public static Image getFrameIcon() {
        Image icon = null;
        if (davGatewayTray != null) {
            icon = davGatewayTray.getFrameIcon();
        }
        return icon;
    }

    public static void switchIcon() {
        if (davGatewayTray != null) {
            davGatewayTray.switchIcon();
        }
    }

    public static void resetIcon() {
        if (davGatewayTray != null && isActive()) {
            davGatewayTray.resetIcon();
        }
    }

    public static boolean isActive() {
        return davGatewayTray == null || davGatewayTray.isActive();
    }

    protected static void displayMessage(String message, Priority priority) {
        LOGGER.log(priority, message);
        if (davGatewayTray != null) {
            davGatewayTray.displayMessage(message, priority);
        }
    }

    protected static void displayMessage(String message, Exception e, Priority priority) {
        LOGGER.log(priority, message, e);
        if (davGatewayTray != null
                && (!(e instanceof NetworkDownException) || isActive())) {
            StringBuilder buffer = new StringBuilder();
            if (message != null) {
                buffer.append(message).append(" ");
            }
            if (e.getMessage() != null) {
                buffer.append(e.getMessage());
            } else {
                buffer.append(e.toString());
            }
            davGatewayTray.displayMessage(buffer.toString(), priority);
        }
        if (davGatewayTray != null && e instanceof NetworkDownException) {
            davGatewayTray.inactiveIcon();
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

    public static void error(Exception e) {
        displayMessage(null, e, Priority.ERROR);
    }

    public static void debug(String message, Exception e) {
        displayMessage(message, e, Priority.DEBUG);
    }

    public static void info(String message, Exception e) {
        displayMessage(message, e, Priority.INFO);
    }

    public static void warn(String message, Exception e) {
        displayMessage(message, e, Priority.WARN);
    }

    public static void error(String message, Exception e) {
        displayMessage(message, e, Priority.ERROR);
    }

    public static void init() {
        if (!Settings.getBooleanProperty("davmail.server")) {
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
                        if (isOSX()) {
                            davGatewayTray = new OSXAwtGatewayTray();
                        } else {
                            davGatewayTray = new AwtGatewayTray();
                        }
                        davGatewayTray.init();
                    }
                } catch (NoClassDefFoundError e) {
                    DavGatewayTray.info("JDK 1.6 needed for system tray support");
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
    protected static boolean isOSX() {
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
            DavGatewayTray.warn("Unable to load image", e);
        }
        return result;
    }
}

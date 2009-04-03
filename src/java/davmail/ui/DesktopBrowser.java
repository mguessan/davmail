package davmail.ui;

import davmail.ui.tray.DavGatewayTray;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Open default browser.
 */
public final class DesktopBrowser {
    private DesktopBrowser() {
    }

    public static void browse(URI location) {
        try {
            // trigger ClassNotFoundException
            ClassLoader classloader = AboutFrame.class.getClassLoader();
            classloader.loadClass("java.awt.Desktop");

            // Open link in default browser
            AwtDesktopBrowser.browse(location);
        } catch (ClassNotFoundException e) {
            DavGatewayTray.debug("Java 6 Desktop class not available");
            // failover for MacOSX
            if (System.getProperty("os.name").toLowerCase().startsWith("mac os x")) {
                try {
                    OSXDesktopBrowser.browse(location);
                } catch (Exception e2) {
                    DavGatewayTray.error("Unable to open link", e2);
                }
            } else {
                // failover : try SWT
                try {
                    // trigger ClassNotFoundException
                    ClassLoader classloader = AboutFrame.class.getClassLoader();
                    classloader.loadClass("org.eclipse.swt.program.Program");
                    SwtDesktopBrowser.browse(location);
                } catch (ClassNotFoundException e2) {
                    DavGatewayTray.error("Open link not supported (tried AWT Desktop and SWT Program");
                } catch (Exception e2) {
                    DavGatewayTray.error("Unable to open link", e2);
                }
            }
        } catch (Exception e) {
            DavGatewayTray.error("Unable to open link", e);
        }
    }

    public static void browse(String location) {
        try {
            DesktopBrowser.browse(new URI(location));
        } catch (URISyntaxException e) {
            DavGatewayTray.error("Unable to open link", e);
        }
    }

}

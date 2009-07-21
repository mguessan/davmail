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
package davmail.ui;

import davmail.ui.tray.DavGatewayTray;
import davmail.BundleMessage;

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
            DavGatewayTray.debug(new BundleMessage("LOG_JAVA6_DESKTOP_UNAVAILABLE"));
            // failover for MacOSX
            if (System.getProperty("os.name").toLowerCase().startsWith("mac os x")) {
                try {
                    OSXDesktopBrowser.browse(location);
                } catch (Exception e2) {
                    DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_OPEN_LINK"), e2);
                }
            } else {
                // failover : try SWT
                try {
                    // trigger ClassNotFoundException
                    ClassLoader classloader = AboutFrame.class.getClassLoader();
                    classloader.loadClass("org.eclipse.swt.program.Program");
                    SwtDesktopBrowser.browse(location);
                } catch (ClassNotFoundException e2) {
                    DavGatewayTray.error(new BundleMessage("LOG_OPEN_LINK_NOT_SUPPORTED"));
                } catch (Exception e2) {
                    DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_OPEN_LINK"), e2);
                }
            }
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_OPEN_LINK"), e);
        }
    }

    public static void browse(String location) {
        try {
            DesktopBrowser.browse(new URI(location));
        } catch (URISyntaxException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_OPEN_LINK"), e);
        }
    }

}

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
import org.apache.log4j.Level;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Extended Awt tray with OSX extensions.
 */
public class OSXAwtGatewayTray extends AwtGatewayTray implements OSXTrayInterface {
    protected static final String OSX_TRAY_ACTIVE_PNG = "osxtray2.png";
    protected static final String OSX_TRAY_PNG = "osxtray.png";
    protected static final String OSX_TRAY_INACTIVE_PNG = "osxtrayinactive.png";

    @Override
    protected void loadIcons() {
        image = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(OSX_TRAY_PNG));
        activeImage = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(OSX_TRAY_ACTIVE_PNG));
        inactiveImage = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(OSX_TRAY_INACTIVE_PNG));

        frameIcons = new ArrayList<>();
        frameIcons.add(DavGatewayTray.loadImage(AwtGatewayTray.TRAY128_PNG));
        frameIcons.add(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_PNG));
    }


    @Override
    protected void createAndShowGUI() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        super.createAndShowGUI();
        trayIcon.removeActionListener(settingsListener);
        try {
            new OSXHandler(this);
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_OSXADAPTER"), e);
        }
    }

    @Override
    public void displayMessage(final String message, final Level level) {
        super.displayMessage(message, level);
    }

    protected Image getImageForIcon(Icon icon) {
        BufferedImage bufferedimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = bufferedimage.getGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return bufferedimage;
    }
}

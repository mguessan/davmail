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
import davmail.ui.OSXAdapter;
import info.growl.Growl;
import info.growl.GrowlException;
import info.growl.GrowlUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

/**
 * Extended Awt tray with OSX extensions.
 */
@SuppressWarnings("Since15")
public class OSXAwtGatewayTray extends AwtGatewayTray {
    protected static final String OSX_TRAY_ACTIVE_PNG = "osxtray2.png";
    protected static final String OSX_TRAY_PNG = "osxtray.png";
    protected static final String OSX_TRAY_INACTIVE_PNG = "osxtrayinactive.png";

    private static final Logger LOGGER = Logger.getLogger(OSXAwtGatewayTray.class);

    /**
     * Exit DavMail Gateway.
     *
     * @return true
     */
    @SuppressWarnings({"SameReturnValue", "UnusedDeclaration"})
    public boolean quit() {
        DavGateway.stop();
        // dispose frames
        settingsFrame.dispose();
        aboutFrame.dispose();
        if (logBrokerMonitor != null) {
            logBrokerMonitor.dispose();
        }
        return true;
    }

    @Override
    protected void createAndShowGUI() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        super.createAndShowGUI();
        trayIcon.removeActionListener(settingsListener);
        try {
            OSXAdapter.setAboutHandler(this, AwtGatewayTray.class.getDeclaredMethod("about", (Class[]) null));
            OSXAdapter.setPreferencesHandler(this, AwtGatewayTray.class.getDeclaredMethod("preferences", (Class[]) null));
            OSXAdapter.setQuitHandler(this, OSXAwtGatewayTray.class.getDeclaredMethod("quit", (Class[]) null));
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_OSXADAPTER"), e);
        }
    }

    @Override
    protected String getTrayIconPath() {
        return OSXAwtGatewayTray.OSX_TRAY_PNG;
    }

    @Override
    protected String getTrayIconActivePath() {
        return OSXAwtGatewayTray.OSX_TRAY_ACTIVE_PNG;
    }

    @Override
    protected String getTrayIconInactivePath() {
        return OSXAwtGatewayTray.OSX_TRAY_INACTIVE_PNG;
    }

    @Override
    public void displayMessage(final String message, final Level level) {
        if (!GrowlUtils.isGrowlLoaded()) {
            super.displayMessage(message, level);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (trayIcon != null) {
                        Icon icon = null;
                        if (level.equals(Level.INFO)) {
                            icon = UIManager.getIcon("OptionPane.informationIcon");
                        } else if (level.equals(Level.WARN)) {
                            icon = UIManager.getIcon("OptionPane.warningIcon");
                        } else if (level.equals(Level.ERROR)) {
                            icon = UIManager.getIcon("OptionPane.errorIcon");
                        }

                        if (icon != null && message != null && message.length() > 0) {
                            try {
                                String title = BundleMessage.format("UI_DAVMAIL_GATEWAY");
                                Growl growl = GrowlUtils.getGrowlInstance("DavMail");
                                growl.addNotification(title, true);
                                growl.register();
                                growl.sendNotification(title, title, message, (RenderedImage) getImageForIcon(icon));
                            } catch (GrowlException growlException) {
                                LOGGER.error(growlException);
                            }
                        }
                        trayIcon.setToolTip(BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\n' + message);
                    }
                }
            });
        }
    }

    protected Image getImageForIcon(Icon icon) {
        BufferedImage bufferedimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = bufferedimage.getGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return bufferedimage;
    }
}

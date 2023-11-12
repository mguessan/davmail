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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Tray icon handler based on java 1.6
 */
public class AwtGatewayTray implements DavGatewayTrayInterface {
    protected static final String TRAY_PNG = "tray.png";

    protected static final String TRAY_ACTIVE_PNG = "tray2.png";
    protected static final String TRAY_INACTIVE_PNG = "trayinactive.png";

    protected static final String TRAY128_PNG = "tray128.png";
    protected static final String TRAY128_ACTIVE_PNG = "tray128active.png";
    protected static final String TRAY128_INACTIVE_PNG = "tray128inactive.png";

    protected AwtGatewayTray() {
    }

    static AboutFrame aboutFrame;
    static SettingsFrame settingsFrame;
    ActionListener settingsListener;

    static TrayIcon trayIcon;
    protected static ArrayList<Image> frameIcons;
    protected static BufferedImage image;
    protected static BufferedImage activeImage;
    protected static BufferedImage inactiveImage;

    private boolean isActive = true;

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    @Override
    public java.util.List<Image> getFrameIcons() {
        return frameIcons;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public void switchIcon() {
        isActive = true;
        SwingUtilities.invokeLater(() -> {
            if (trayIcon.getImage().equals(image)) {
                trayIcon.setImage(activeImage);
            } else {
                trayIcon.setImage(image);
            }
        });
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void resetIcon() {
        SwingUtilities.invokeLater(() -> trayIcon.setImage(image));
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void inactiveIcon() {
        isActive = false;
        SwingUtilities.invokeLater(() -> trayIcon.setImage(inactiveImage));
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
     * Display balloon message for log level.
     *
     * @param message text message
     * @param level   log level
     */
    public void displayMessage(final String message, final Level level) {
        SwingUtilities.invokeLater(() -> {
            if (trayIcon != null) {
                TrayIcon.MessageType messageType = null;
                if (level.equals(Level.INFO)) {
                    messageType = TrayIcon.MessageType.INFO;
                } else if (level.equals(Level.WARN)) {
                    messageType = TrayIcon.MessageType.WARNING;
                } else if (level.equals(Level.ERROR)) {
                    messageType = TrayIcon.MessageType.ERROR;
                }
                if (messageType != null) {
                    trayIcon.displayMessage(BundleMessage.format("UI_DAVMAIL_GATEWAY"), message, messageType);
                }
                trayIcon.setToolTip(BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\n' + message);
            }
        });
    }

    /**
     * Open about window
     */
    public void about() {
        SwingUtilities.invokeLater(() -> {
            aboutFrame.update();
            aboutFrame.setVisible(true);
            aboutFrame.toFront();
            aboutFrame.requestFocus();
        });
    }

    /**
     * Open settings window
     */
    public void preferences() {
        SwingUtilities.invokeLater(() -> {
            settingsFrame.reload();
            settingsFrame.setVisible(true);
            settingsFrame.toFront();
            settingsFrame.repaint();
            settingsFrame.requestFocus();
        });
    }

    /**
     * Create tray icon and register frame listeners.
     */
    public void init() {
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    public void dispose() {
        SystemTray.getSystemTray().remove(trayIcon);

        // dispose frames
        settingsFrame.dispose();
        aboutFrame.dispose();
    }

    protected void loadIcons() {
        image = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_PNG));
        activeImage = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_ACTIVE_PNG));
        inactiveImage = DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_INACTIVE_PNG));

        frameIcons = new ArrayList<>();
        frameIcons.add(DavGatewayTray.loadImage(AwtGatewayTray.TRAY128_PNG));
        frameIcons.add(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_PNG));
    }

    protected void createAndShowGUI() {
        System.setProperty("swing.defaultlaf", UIManager.getSystemLookAndFeelClassName());

        // get the SystemTray instance
        SystemTray tray = SystemTray.getSystemTray();
        loadIcons();

        // create a popup menu
        PopupMenu popup = new PopupMenu();

        aboutFrame = new AboutFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        ActionListener aboutListener = e -> about();
        // create menu item for the default action
        MenuItem aboutItem = new MenuItem(BundleMessage.format("UI_ABOUT"));
        aboutItem.addActionListener(aboutListener);
        popup.add(aboutItem);

        settingsFrame = new SettingsFrame();
        // create an action settingsListener to listen for settings action executed on the tray icon
        settingsListener = e -> preferences();
        // create menu item for the default action
        MenuItem defaultItem = new MenuItem(BundleMessage.format("UI_SETTINGS"));
        defaultItem.addActionListener(settingsListener);
        popup.add(defaultItem);

        MenuItem logItem = new MenuItem(BundleMessage.format("UI_SHOW_LOGS"));
        logItem.addActionListener(e -> DavGatewayTray.showLogs());
        popup.add(logItem);

        // create an action exitListener to listen for exit action executed on the tray icon
        ActionListener exitListener = e -> {
            try {
                DavGateway.stop();
            } catch (Exception exc) {
                DavGatewayTray.error(exc);
            }
            // make sure we do exit
            System.exit(0);
        };
        // create menu item for the exit action
        MenuItem exitItem = new MenuItem(BundleMessage.format("UI_EXIT"));
        exitItem.addActionListener(exitListener);
        popup.add(exitItem);

        /// ... add other items
        // construct a TrayIcon
        trayIcon = new TrayIcon(image, BundleMessage.format("UI_DAVMAIL_GATEWAY"), popup);
        // set the TrayIcon properties
        trayIcon.addActionListener(settingsListener);
        // ...
        // add the tray image
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_CREATE_TRAY"), e);
        }

        // display settings frame on first start
        if (Settings.isFirstStart()) {
            settingsFrame.setVisible(true);
            settingsFrame.toFront();
            settingsFrame.repaint();
            settingsFrame.requestFocus();
        }
    }

}

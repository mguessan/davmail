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
import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

/**
 * Tray icon handler based on SWT
 */
public class SwtGatewayTray implements DavGatewayTrayInterface {
    private static final Logger LOGGER = Logger.getLogger(SwtGatewayTray.class);

    private static final Object LOCK = new Object();

    protected SwtGatewayTray() {
    }

    static SettingsFrame settingsFrame;
    static AboutFrame aboutFrame;

    private static TrayItem trayItem;
    private static ArrayList<java.awt.Image> frameIcons;

    private static Image image;
    private static Image image2;
    private static Image inactiveImage;
    private static Display display;
    private static Shell shell;
    private boolean isActive = true;
    private static boolean isReady = false;
    private static Error error;
    private boolean firstMessage = true;

    public static void initDisplay() {
        if (!isReady) {
            // ready
            // start main loop, shell can be null before init
            // dispose AWT frames
            Thread swtThread = new Thread("SWT") {
                @Override
                public void run() {
                    try {
                        display = Display.getDefault();
                        shell = new Shell(display);
                        synchronized (LOCK) {
                            // ready
                            isReady = true;
                            LOCK.notifyAll();
                        }

                        // start main loop, shell can be null before init
                        while (!shell.isDisposed()) {
                            if (!display.readAndDispatch()) {
                                display.sleep();
                            }
                        }
                        // dispose AWT frames
                        if (settingsFrame != null) {
                            settingsFrame.dispose();
                        }
                        if (aboutFrame != null) {
                            aboutFrame.dispose();
                        }
                        System.exit(0);
                    } catch (Throwable e) {
                        LOGGER.error("Error in SWT thread", e);
                        error = new Error(e);
                    }
                }
            };
            swtThread.start();
            while (true) {
                // wait for SWT init
                try {
                    synchronized (LOCK) {
                        if (error != null) {
                            throw error;
                        }
                        if (isReady) {
                            break;
                        }
                        LOCK.wait(1000);
                    }
                } catch (InterruptedException e) {
                    DavGatewayTray.error(new BundleMessage("LOG_ERROR_WAITING_FOR_SWT_INIT"), e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    @Override
    public java.util.List<java.awt.Image> getFrameIcons() {
        return frameIcons;
    }

    /**
     * Switch tray icon between active and standby icon.
     */
    public void switchIcon() {
        isActive = true;
        display.syncExec(() -> {
            Image currentImage = trayItem.getImage();
            if (currentImage != null && currentImage.equals(image)) {
                trayItem.setImage(image2);
            } else {
                trayItem.setImage(image);
            }
        });

    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void resetIcon() {
        display.syncExec(() -> trayItem.setImage(image));
    }

    /**
     * Set tray icon to inactive (network down)
     */
    public void inactiveIcon() {
        isActive = false;
        display.syncExec(() -> trayItem.setImage(inactiveImage));
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
     * Log and display balloon message according to log level.
     *
     * @param message text message
     * @param level   log level
     */
    public void displayMessage(final String message, final Level level) {
        if (trayItem != null) {
            display.asyncExec(() -> {
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
                    // Wait for tray init 1 second on first message
                    if (firstMessage) {
                        firstMessage = false;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    toolTip.setVisible(true);
                }
                trayItem.setToolTipText(BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\n' + message);
            });
        }
    }

    /**
     * Load image with current class loader.
     * No scaling
     *
     * @param fileName image resource file name
     * @return image
     */
    public static Image loadSwtImage(String fileName) {
        Image result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            if (imageUrl == null) {
                throw new IOException("fileName");
            }
            try (InputStream inputStream = imageUrl.openStream()) {
                result = new Image(display, inputStream);

            }

        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }

    /**
     * Load image with current class loader.
     * Scale to size
     *
     * @param fileName image resource file name
     * @param targetSize     target image size
     * @return image
     */
    public static Image loadSwtImage(String fileName, int targetSize) {
        int padding = 0;
        Image result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            if (imageUrl == null) {
                throw new IOException(fileName);
            }
            BufferedImage bufferedImage;
            if (Settings.getBooleanProperty("davmail.trayGrayscale", false)) {
                bufferedImage = DavGatewayTray.convertGrayscale(ImageIO.read(imageUrl));
            } else {
                bufferedImage = ImageIO.read(imageUrl);
            }
            if (bufferedImage.getWidth() != targetSize || bufferedImage.getHeight() != targetSize) {
                bufferedImage = DavGatewayTray.scaleImage(bufferedImage, targetSize);
            }
            byte[] imageBytes;
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(bufferedImage, "png", os);
                imageBytes = os.toByteArray();
            }

            try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
                Image loadedImage = new Image(display, inputStream);

                ImageData resultImageData = new ImageData(targetSize, targetSize, 24, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
                // force init alpha channel
                resultImageData.setAlpha(0, 0, 0);

                result = new Image(display, resultImageData);

                // drow loaded image over transparent image
                GC gc = new GC(result);
                gc.setAntialias(SWT.ON);
                gc.setInterpolation(SWT.HIGH);
                gc.drawImage(loadedImage, 0, 0, loadedImage.getBounds().width, loadedImage.getBounds().height,
                        padding, padding, result.getBounds().width - padding, result.getBounds().height - padding);
                gc.dispose();
                loadedImage.dispose();
            }

        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }

    /**
     * Create tray icon and register frame listeners.
     */
    public void init() {
        try {
            // workaround for bug when SWT and AWT both try to access Gtk
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_SET_LOOK_AND_FEEL"));
        }

        initDisplay();

        display.asyncExec(() -> {
            try {
                final Tray tray = display.getSystemTray();
                if (tray != null) {

                    trayItem = new TrayItem(tray, SWT.NONE);
                    trayItem.setToolTipText(BundleMessage.format("UI_DAVMAIL_GATEWAY"));

                    frameIcons = new ArrayList<>();
                    frameIcons.add(DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(AwtGatewayTray.TRAY128_PNG)));
                    frameIcons.add(DavGatewayTray.adjustTrayIcon(DavGatewayTray.loadImage(AwtGatewayTray.TRAY_PNG)));

                    // assume 24 pixels default icon size
                    int trayIconSize = 24;
                    if (Settings.isWindows()) {
                        trayIconSize = 16;
                    }

                    image = loadSwtImage("tray128.png", trayIconSize);
                    image2 = loadSwtImage("tray128active.png", trayIconSize);
                    inactiveImage = loadSwtImage("tray128inactive.png", trayIconSize);

                    trayItem.setImage(image);
                    trayItem.addDisposeListener(e -> {
                        if (image != null && !image.isDisposed()) {
                            image.dispose();
                        }
                        if (image2 != null && !image2.isDisposed()) {
                            image2.dispose();
                        }
                        if (inactiveImage != null && !inactiveImage.isDisposed()) {
                            inactiveImage.dispose();
                        }
                    });

                    // create a popup menu
                    final Menu popup = new Menu(shell, SWT.POP_UP);
                    trayItem.addListener(SWT.MenuDetect, event -> display.asyncExec(
                            () -> popup.setVisible(true)));

                    MenuItem aboutItem = new MenuItem(popup, SWT.PUSH);
                    aboutItem.setText(BundleMessage.format("UI_ABOUT"));
                    aboutItem.addListener(SWT.Selection, event -> SwingUtilities.invokeLater(
                            () -> {
                                if (aboutFrame == null) {
                                    aboutFrame = new AboutFrame();
                                }
                                aboutFrame.update();
                                aboutFrame.setVisible(true);
                                aboutFrame.toFront();
                                aboutFrame.requestFocus();
                            }));

                    // create menu item for the default action
                    trayItem.addListener(SWT.DefaultSelection, event -> SwingUtilities.invokeLater(
                            this::openSettingsFrame));

                    MenuItem defaultItem = new MenuItem(popup, SWT.PUSH);
                    defaultItem.setText(BundleMessage.format("UI_SETTINGS"));
                    defaultItem.addListener(SWT.Selection, event -> SwingUtilities.invokeLater(
                            this::openSettingsFrame));

                    MenuItem exitItem = new MenuItem(popup, SWT.PUSH);
                    exitItem.setText(BundleMessage.format("UI_EXIT"));
                    exitItem.addListener(SWT.Selection, event -> DavGateway.stop());

                    // display settings frame on first start
                    if (Settings.isFirstStart()) {
                        SwingUtilities.invokeLater(() -> {
                            // create frame on first call
                            if (settingsFrame == null) {
                                settingsFrame = new SettingsFrame();
                            }
                            settingsFrame.setVisible(true);
                            settingsFrame.toFront();
                            settingsFrame.requestFocus();
                        });

                    }

                }
            } catch (Exception exc) {
                DavGatewayTray.error(exc);
            } catch (Error exc) {
                error = exc;
                throw exc;
            }
        });
    }

    private void openSettingsFrame() {
        // create frame on first call
        if (settingsFrame == null) {
            settingsFrame = new SettingsFrame();
        }
        settingsFrame.reload();
        settingsFrame.setVisible(true);
        settingsFrame.toFront();
        settingsFrame.requestFocus();
    }

    public void dispose() {
        shell.dispose();
    }

}

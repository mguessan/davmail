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

import org.apache.log4j.Level;

import java.awt.*;

/**
 * Gateway tray interface common to SWT and pure java implementations
 */
public interface DavGatewayTrayInterface {
    /**
     * Switch tray icon between active and standby icon.
     */
    void switchIcon();

    /**
     * Reset tray icon to standby
     */
    void resetIcon();

    /**
     * Set tray icon to inactive (network down)
     */
    void inactiveIcon();

    /**
     * Check if current tray status is inactive (network down).
     *
     * @return true if inactive
     */
    boolean isActive();

    /**
     * Return AWT Image icon for frame title.
     *
     * @return frame icon
     */
    Image getFrameIcon();

    /**
     * Display balloon message for log level.
     *
     * @param message text message
     * @param level   log level
     */
    void displayMessage(String message, Level level);

    /**
     * Create tray icon and register frame listeners.
     */
    void init();

    /**
     * destroy frames
     */
    void dispose();
}

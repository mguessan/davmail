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

import davmail.ui.OSXAdapter;
import davmail.BundleMessage;
import davmail.DavGateway;

/**
 * Extended Awt tray with OSX extensions.
 */
public class OSXAwtGatewayTray extends AwtGatewayTray {
    @SuppressWarnings({"SameReturnValue"})
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
}

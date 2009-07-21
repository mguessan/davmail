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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * MacOSX specific frame to handle menu
 */
public class OSXFrameGatewayTray extends FrameGatewayTray {

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
    protected void buildMenu() {
        // create a popup menu
        JMenu menu = new JMenu(BundleMessage.format("UI_LOGS"));
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        mainFrame.setJMenuBar(menuBar);

        JMenuItem logItem = new JMenuItem(BundleMessage.format("UI_SHOW_LOGS"));
        logItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showLogs();
            }
        });
        menu.add(logItem);
    }


    @Override
    protected void createAndShowGUI() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        super.createAndShowGUI();
        try {
            OSXAdapter.setAboutHandler(this, FrameGatewayTray.class.getDeclaredMethod("about", (Class[]) null));
            OSXAdapter.setPreferencesHandler(this, FrameGatewayTray.class.getDeclaredMethod("preferences", (Class[]) null));
            OSXAdapter.setQuitHandler(this, OSXFrameGatewayTray.class.getDeclaredMethod("quit", (Class[]) null));
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_OSXADAPTER"), e);
        }
    }
}

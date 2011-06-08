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
package davmail.web;

import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.Settings;
import davmail.ui.tray.DavGatewayTray;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.IOException;
import java.io.InputStream;

/**
 * Context Listener to start/stop DavMail
 */
public class DavGatewayServletContextListener implements ServletContextListener {
    public void contextInitialized(ServletContextEvent event) {
        InputStream settingInputStream = null;
        try {
            settingInputStream = DavGatewayServletContextListener.class.getClassLoader().getResourceAsStream("davmail.properties");
            Settings.load(settingInputStream);
            DavGateway.start();
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_SETTINGS"), e);
        } finally {
            if (settingInputStream != null) {
                try {
                    settingInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug(new BundleMessage("LOG_ERROR_CLOSING_CONFIG_FILE"), e);
                }
            }
        }
        DavGatewayTray.debug(new BundleMessage("LOG_DAVMAIL_STARTED"));
    }

    public void contextDestroyed(ServletContextEvent event) {
        DavGatewayTray.debug(new BundleMessage("LOG_STOPPING_DAVMAIL"));
        DavGateway.stop();
    }
}

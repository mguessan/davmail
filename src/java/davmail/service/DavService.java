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
package davmail.service;

import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.Settings;
import davmail.ui.tray.DavGatewayTray;
import org.boris.winrun4j.AbstractService;

/**
 * WinRun4J DavMail service.
 */
public class DavService extends AbstractService {

    private final Object LOCK = new Object();

    /**
     * Perform a service request.
     *
     * @param control service control.
     * @return return code.
     */
    @Override
    public int serviceRequest(int control) {
        switch (control) {
            case SERVICE_CONTROL_STOP:
            case SERVICE_CONTROL_SHUTDOWN:
                DavGatewayTray.debug(new BundleMessage("LOG_STOPPING_DAVMAIL"));
                DavGateway.stop();
                synchronized (LOCK) {
                    LOCK.notifyAll();
                }
        }
        return 0;
    }

    /**
     * Run the service.
     *
     * @param args command line arguments
     * @return return code
     */
    public int serviceMain(String[] args) {
        if (args.length >= 1) {
            Settings.setConfigFilePath(args[0]);
        }

        Settings.load();
        if (!Settings.getBooleanProperty("davmail.server")) {
            Settings.setProperty("davmail.server", "true");
            Settings.updateLoggingConfig();
        }

        DavGateway.start();
        DavGatewayTray.debug(new BundleMessage("LOG_DAVMAIL_STARTED"));

        synchronized (LOCK) {
            try {
                LOCK.wait();
            } catch (InterruptedException e) {
                DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_INTERRUPTED"));
                Thread.currentThread().interrupt();
            }
        }

        return 0;
    }
}

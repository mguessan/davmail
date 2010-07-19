/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
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
package davmail;

import davmail.exchange.ExchangeSession;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * DavMail generic test case.
 * Loads DavMail settings
 */
public class AbstractDavMailTestCase extends TestCase {
    protected static boolean loaded;
    protected static String url;
    protected static String certificateHash;
    protected static String username;
    protected static String password;
    protected static ExchangeSession session;

    @Override
    public void setUp() throws IOException {
        if (!loaded) {
            loaded = true;

            if (url == null) {
                // try to load settings from current folder davmail.properties
                File file = new File("davmail.properties");
                if (file.exists()) {
                      Settings.setConfigFilePath("davmail.properties");
                }
                // Load current settings
                Settings.load();
            } else {
                Settings.setDefaultSettings();
                Settings.setProperty("davmail.url", url);
                Settings.setProperty("davmail.server.certificate.hash", certificateHash);
                Settings.setProperty("davmail.username", username);
                Settings.setProperty("davmail.password", password);
            }


            DavGatewaySSLProtocolSocketFactory.register();
            // force server mode
            Settings.setProperty("davmail.server", "true");

            // enable WIRE debug log
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
            // enable EWS support
            Settings.setProperty("davmail.enableEws", "false");

        }
    }
}

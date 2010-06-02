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
package davmail.exchange;

import davmail.exchange.dav.DavExchangeSession;
import junit.framework.TestCase;

import java.io.IOException;

import davmail.Settings;
import davmail.http.DavGatewaySSLProtocolSocketFactory;

/**
 * Exchange session test case.
 * Open a session to default DavMail server as found in user davmail.properties,
 * except if url is not null
 */
public class AbstractExchangeSessionTestCase extends TestCase {
    protected static String url;
    protected static String certificateHash;
    protected static String username;
    protected static String password;
    protected static ExchangeSession session;

    @Override
    public void setUp() throws IOException {
        if (session == null) {
            // Load current user settings
            if (url == null) {
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

            // open session, get username and password from davmail.properties
            // Note: those properties should *not* exist in normal production mode,
            // they are not used by DavMail, just by this test case
            session = new DavExchangeSession(Settings.getProperty("davmail.url"), Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
        }
    }


}

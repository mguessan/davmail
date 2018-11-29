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

package davmail.exchange;

import davmail.AbstractDavMailTestCase;
import davmail.Settings;

import java.io.IOException;

public class TestAuthenticators extends AbstractDavMailTestCase {

    public void testEWSAuthenticator() throws IOException {
        Settings.setProperty("davmail.mode", Settings.EWS);
        ExchangeSessionFactory.checkConfig();
        // try application password for MFA enabledusers
        ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"),
                Settings.getProperty("davmail.app.password"));
    }

    public void testO365Authenticator() throws IOException {
        Settings.setProperty("davmail.mode", Settings.O365);
        ExchangeSessionFactory.checkConfig();
        // try application password for MFA enabledusers
        ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"),
                Settings.getProperty("davmail.app.password"));
    }

    public void testO365ModernAuthenticator() throws IOException {
        Settings.setProperty("davmail.mode", Settings.O365_MODERN);
        ExchangeSessionFactory.checkConfig();
        // use normal user password
        ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"),
                Settings.getProperty("davmail.password"));
    }

    public void testO365InteractiveAuthenticator() throws IOException {
        Settings.setProperty("davmail.mode", Settings.O365_INTERACTIVE);
        ExchangeSessionFactory.checkConfig();
        // password entered by end user
        ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"),
                "unused");
    }
}

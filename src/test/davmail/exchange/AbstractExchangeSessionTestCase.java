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

import davmail.AbstractDavMailTestCase;
import davmail.Settings;
import davmail.exchange.dav.DavExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;

import java.io.IOException;

/**
 * Exchange session test case.
 * Open a session to default DavMail server as found in user test.properties
 */
public abstract class AbstractExchangeSessionTestCase extends AbstractDavMailTestCase {

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (session == null) {
            // open session, get username and password from test.properties
            session = ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.url"), Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
        }
    }

}

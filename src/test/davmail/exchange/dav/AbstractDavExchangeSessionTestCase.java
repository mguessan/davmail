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
package davmail.exchange.dav;

import davmail.AbstractExchange2007TestCase;
import davmail.Settings;
import davmail.exchange.ExchangeSessionFactory;

import java.io.IOException;

/**
 * Parent DavExchangeSession Test case.
 */
public abstract class AbstractDavExchangeSessionTestCase extends AbstractExchange2007TestCase {
    HC4DavExchangeSession davSession;

    /**
     * @inheritDoc
     */
    @Override
    public void setUp() throws IOException {
        super.setUp();
        // force mode to HttpClient 4 Dav
        Settings.setProperty("davmail.mode", "HC4WebDav");
        String url = "https://" + server + "/owa";
        if (session == null) {
            session = ExchangeSessionFactory.getInstance(url, username, password);
        }
        davSession = (HC4DavExchangeSession) session;

        assertEquals(username, davSession.getAlias());
        assertEquals(email, davSession.getEmail());
        assertEquals("/exchange/" + email + "/", davSession.getFolderPath(""));
    }

}

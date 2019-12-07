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
import davmail.exchange.auth.HC4ExchangeFormAuthenticator;
import davmail.http.URIUtil;
import org.apache.http.client.utils.URIUtils;

import java.io.IOException;

/**
 * Test cases for new HttpClient 4 DavExchangeSession implementation
 */
public class TestHC4DavExchangeSession extends AbstractExchange2007TestCase {
    HC4ExchangeFormAuthenticator authenticator = null;

    @Override
    public void setUp() throws IOException {
        super.setUp();

        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        url = "https://" + server + "/owa";

        if (authenticator == null) {
            authenticator = new HC4ExchangeFormAuthenticator();
            authenticator.setUrl(url);
            authenticator.setUsername(username);
            authenticator.setPassword(password);
            authenticator.authenticate();
        }
    }

    public void testOpenSession() throws IOException {
        assertEquals("https://" + server + "/owa/", authenticator.getExchangeUri().toString());
        assertNotNull(authenticator.getHttpClientAdapter());
        HC4DavExchangeSession session = new HC4DavExchangeSession(
                authenticator.getHttpClientAdapter(),
                authenticator.getExchangeUri(),
                authenticator.getUsername());
        assertEquals(email, session.getEmail());
        assertEquals(username, session.getAlias());
        assertEquals(URIUtils.resolve(authenticator.getExchangeUri(), "/public/").toString(), session.getCmdBasePath());

        assertNotNull(session.getFolderPath("/users/"+email+"/inbox"));
        assertNotNull(session.getFolderPath("/users/"+email+"/calendar"));
    }
}

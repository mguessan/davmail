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

package davmail.exchange.ews;

import davmail.AbstractExchange2007TestCase;
import davmail.Settings;
import davmail.exchange.auth.ExchangeFormAuthenticator;
import org.apache.commons.httpclient.HttpClient;
import org.apache.log4j.Level;

import java.io.IOException;

public class TestExchange2007EWS extends AbstractExchange2007TestCase {
    public void testSimpleUsernameOWAFormAuthenticator() throws IOException {
        String url = "https://" + server + "/owa";
        ExchangeFormAuthenticator authenticator = new ExchangeFormAuthenticator();
        authenticator.setUrl(url);
        authenticator.setUsername(username);
        authenticator.setPassword(password);
        authenticator.authenticate();
        assertEquals("https://" + server + "/owa/", authenticator.getExchangeUri().toString());
        // create session
        EwsExchangeSession session = new EwsExchangeSession(authenticator.getHttpClient(),
                authenticator.getExchangeUri(), authenticator.getUsername());
        assertEquals(username, session.getAlias());
        assertEquals(email, session.getEmail());
        session.getFolder("");

    }

    public void testSimpleUsernameEWSFormAuthenticator() throws IOException {
        String url = "https://" + server + "/EWS/Exchange.asmx";
        ExchangeFormAuthenticator authenticator = new ExchangeFormAuthenticator();
        authenticator.setUrl(url);
        authenticator.setUsername(username);
        authenticator.setPassword(password);
        authenticator.authenticate();
        assertEquals("/EWS/Services.wsdl", authenticator.getExchangeUri().toString());
        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        // create session
        EwsExchangeSession session = new EwsExchangeSession(authenticator.getHttpClient(),
                authenticator.getExchangeUri(), authenticator.getUsername());
        assertEquals(username, session.getAlias());
        assertEquals(email, session.getEmail());
    }
}

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

package davmail.exchange.graph;

import davmail.Settings;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.AbstractExchangeSessionTestCase;
import davmail.exchange.ExchangeSession;
import davmail.exchange.auth.O365Authenticator;
import davmail.http.HttpClientAdapter;

import java.io.IOException;

public class TestGraphExchangeSessionFolders extends AbstractExchangeSessionTestCase {
    davmail.exchange.auth.O365Authenticator authenticator = null;
    GraphExchangeSession graphExchangeSession = null;

    @Override
    public void setUp() throws IOException {
        if (!loaded) {
            super.setUp();

            // custom app
            Settings.setProperty("davmail.oauth.clientId", "fcd7ee35-332e-48b6-85ba-1b6de796ded7");
            Settings.setProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");

            Settings.setProperty("davmail.enableGraph", "true");

            authenticator = new O365Authenticator();
            authenticator.setUsername(username);
            authenticator.setPassword(password);
            authenticator.authenticate();

            HttpClientAdapter httpClientAdapter = new HttpClientAdapter(Settings.getO365Url(), true);
            graphExchangeSession = new GraphExchangeSession(httpClientAdapter, authenticator.getToken(), username);
        }
    }

    public void testGetInbox() throws IOException {
        ExchangeSession.Folder folder = graphExchangeSession.internalGetFolder("inbox");
        assertNotNull(folder);

        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/inbox"));
    }

    public void testInvalidFolder() throws IOException {
        try {
            graphExchangeSession.internalGetFolder("invalid");
            assertFalse("Should not get there", true);
        } catch (HttpNotFoundException e) {
            // ok
        }
    }

    public void testGetWellknown() throws IOException {


        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/Drafts"));
        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/Sent"));
        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/Trash"));
        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/Junk"));

        assertNotNull(graphExchangeSession.internalGetFolder("/users/" + username + "/Unsent Messages"));

        // TODO calendars and task are not normal folders over graph
        //assertNotNull(graphExchangeSession.internalGetFolder("/users/"+username+"/calendar"));
        //assertNotNull(graphExchangeSession.internalGetFolder("/users/"+username+"/tasks"));

    }

}

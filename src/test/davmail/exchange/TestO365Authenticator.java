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
import davmail.exception.DavMailAuthenticationException;
import davmail.exchange.auth.O365Authenticator;
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.http.HttpClientAdapter;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;

public class TestO365Authenticator extends AbstractDavMailTestCase {
    public void testO365Authenticator() throws IOException, InterruptedException {
        davmail.exchange.auth.O365Authenticator authenticator = new O365Authenticator();
        authenticator.setUsername(username);
        authenticator.setPassword(password);
        authenticator.authenticate();

        // switch to EWS url
        HttpClientAdapter httpClientAdapter = new HttpClientAdapter(authenticator.getExchangeUri(), true);

        int i = 0;
        while (i++ < 12 * 60 * 2) {
            GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY, DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
            checkMethod.setHeader("Authorization", "Bearer " + authenticator.getToken().getAccessToken());
            try (
                    CloseableHttpResponse response = httpClientAdapter.execute(checkMethod);
            ) {
                checkMethod.handleResponse(response);
                //checkMethod.setServerVersion(serverVersion);

                checkMethod.checkSuccess();
            }
            System.out.println("Retrieved folder id " + checkMethod.getResponseItem().get("FolderId"));
            Thread.sleep(5000);
        }

    }

    public void testInvalidPassword() throws IOException {
        try {
            davmail.exchange.auth.O365Authenticator authenticator = new O365Authenticator();
            authenticator.setUsername(username);
            authenticator.setPassword("invalid");
            authenticator.authenticate();
            fail("Should fail");
        } catch (DavMailAuthenticationException e) {
            // OK
        }
    }

}

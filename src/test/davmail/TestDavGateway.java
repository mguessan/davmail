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

import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import davmail.ui.tray.DavGatewayTray;

import java.io.IOException;

public class TestDavGateway extends AbstractDavMailTestCase {
    /**
     * Loop on getReleasedVersion.
     * As the method closes HttpClient instance, this will create and close 100 connections
     */
    public void testGetReleasedVersion() {
        int count = 0;
        while (count++ < 100) {
            DavGateway.getReleasedVersion();
        }
    }

    /**
     * Loop on getReleasedVersion.
     * Use a single HttpClient instance to reuse connections
     */
    public void testLoopGetReleasedVersion() {
        String versionUrl = "http://davmail.sourceforge.net/version.txt";
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(versionUrl)) {
            int count = 0;
            while (count++ < 100) {
                GetRequest getRequest = new GetRequest(versionUrl);
                getRequest = httpClientAdapter.executeFollowRedirect(getRequest);
                String version = getRequest.getResponseBodyAsString();
                System.out.println("DavMail released version: " + version);
            }
        } catch (IOException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_GET_RELEASED_VERSION"));
        }
    }
}

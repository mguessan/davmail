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
import davmail.ui.tray.DavGatewayTray;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;

public class TestDavGateway extends AbstractDavMailTestCase {
    /**
     * Loop on getReleasedVersion.
     * As the method closes HttpClient instance, this will create and close 100 connections
     * @throws InterruptedException on error
     */
    public void testGetReleasedVersion() throws InterruptedException {
        int count = 0;
        while (count++ < 100) {
            DavGateway.getReleasedVersion();
            Thread.sleep(1000);
        }
    }

    /**
     * Loop on getReleasedVersion.
     * Use a single HttpClient instance to reuse connections
     * @throws InterruptedException on error
     */
    public void testLoopGetReleasedVersion() throws InterruptedException {
        HttpClientAdapter httpClientAdapter = new HttpClientAdapter("http://davmail.sourceforge.net/version.txt");
        try {
            int count = 0;
            while (count++ < 100) {
                HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
                CloseableHttpResponse response = httpClientAdapter.execute(httpget);
                try {
                    String version = new BasicResponseHandler().handleResponse(response);
                    System.out.println("DavMail released version: " + version);
                } finally {
                    response.close();
                }
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_GET_RELEASED_VERSION"));
        } finally {
            httpClientAdapter.close();
        }
    }
}

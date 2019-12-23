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

package davmail.http;

import davmail.AbstractDavMailTestCase;
import davmail.Settings;
import davmail.http.request.GetRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

import java.io.IOException;

public class TestGetRequest extends AbstractDavMailTestCase {
    private static final String DAVMAIL_VERSION_URL = "http://davmail.sourceforge.net/version.txt";

    public void testSuccess() throws IOException {
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(DAVMAIL_VERSION_URL)) {
            GetRequest request = new GetRequest(DAVMAIL_VERSION_URL);
            String responseString = httpClientAdapter.executeGetRequest(request);
            assertEquals(HttpStatus.SC_OK, request.getStatusCode());
            assertEquals("OK", request.getReasonPhrase());
            assertNotNull(responseString);
        }
    }

    public void testRedirect() throws IOException {
        // execute get request, do not follow redirect
        final String outlookUrl = "https://outlook.office365.com";
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(outlookUrl)) {
            GetRequest request = new GetRequest(outlookUrl);
            String responseString = httpClientAdapter.executeGetRequest(request);
            assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, request.getStatusCode());
            assertEquals("Moved Temporarily", request.getReasonPhrase());
            assertNull(responseString);
        }
    }

    public void testNotFound() throws IOException {
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(DAVMAIL_VERSION_URL)) {
            GetRequest request = new GetRequest("/notfound.txt");
            try {
                httpClientAdapter.executeGetRequest(request);
                fail("Should fail");
            } catch (HttpResponseException e) {
                assertEquals(HttpStatus.SC_NOT_FOUND, e.getStatusCode());
            }
        }
    }

    public void testAuthorizationRequired() throws IOException {
        // create httpClientAdapter without credentials
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(Settings.O365_URL)) {
            GetRequest request = new GetRequest(Settings.O365_URL);
            httpClientAdapter.executeGetRequest(request);
            fail("Should fail");
        } catch (HttpResponseException e) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getStatusCode());
            assertEquals("Unauthorized", e.getMessage());
        }
    }

    public void testAuthentication() throws IOException {
        // create httpClientAdapter with credentials
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(Settings.O365_URL, username, password)) {
            GetRequest request = new GetRequest(Settings.O365_URL);
            String responseBody = httpClientAdapter.executeGetRequest(request);
            assertEquals(HttpStatus.SC_OK, request.getStatusCode());
            assertEquals("OK", request.getReasonPhrase());
            assertNotNull(responseBody);
        }
    }

}

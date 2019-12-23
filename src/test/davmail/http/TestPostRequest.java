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
import davmail.http.request.PostRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

import java.io.IOException;

public class TestPostRequest extends AbstractDavMailTestCase {
    private static final String DAVMAIL_VERSION_URL = "http://davmail.sourceforge.net/version.txt";

    public void testSuccess() throws IOException {
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(DAVMAIL_VERSION_URL)) {
            PostRequest request = new PostRequest(DAVMAIL_VERSION_URL);
            String responseString = httpClientAdapter.executePostRequest(request);
            assertEquals(HttpStatus.SC_OK, request.getStatusCode());
            assertEquals("OK", request.getReasonPhrase());
            assertNotNull(responseString);
        }
    }

    public void testRedirect() throws IOException {
        // execute get request, do not follow redirect
        final String outlookUrl = "https://outlook.office365.com";
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(outlookUrl)) {
            PostRequest request = new PostRequest(outlookUrl);
            String responseString = httpClientAdapter.executePostRequest(request);
            assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, request.getStatusCode());
            assertEquals("Moved Temporarily", request.getReasonPhrase());
            assertNull(responseString);
        }
    }

    public void testAuthorizationRequired() throws IOException {
        // create httpClientAdapter without credentials
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(Settings.O365_URL)) {
            PostRequest request = new PostRequest(Settings.O365_URL);
            httpClientAdapter.executePostRequest(request);
            fail("Should fail");
        } catch (HttpResponseException e) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getStatusCode());
            assertEquals("Unauthorized", e.getMessage());
        }
    }

    public void testAuthentication() throws IOException {
        // create httpClientAdapter with credentials, empty POST should fail
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(Settings.O365_URL, username, password)) {
            PostRequest request = new PostRequest(Settings.O365_URL);
            httpClientAdapter.executePostRequest(request);
            fail("Should fail");
        } catch (HttpResponseException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatusCode());
            assertEquals("Internal Server Error", e.getMessage());
        }
    }


    public void testNotFound() throws IOException {
        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(DAVMAIL_VERSION_URL)) {
            PostRequest request = new PostRequest("/notfound.txt");
            try {
                httpClientAdapter.executePostRequest(request);
                fail("Should fail");
            } catch (HttpResponseException e) {
                assertEquals(HttpStatus.SC_NOT_FOUND, e.getStatusCode());
            }
        }
    }

}

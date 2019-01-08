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
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.log4j.Level;

import java.io.IOException;

public class TestHttpClientAdapter extends AbstractDavMailTestCase {
    public void testBasicGetRequest() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        HttpClientAdapter httpClientAdapter = new HttpClientAdapter("http://davmail.sourceforge.net/version.txt");
        try {

            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
            CloseableHttpResponse response = httpClientAdapter.execute(httpget);
            try {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClientAdapter.close();
        }
    }

    public void testEWSAuthentication() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        String url = Settings.getProperty("davmail.url");

        HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url, username, password);
        httpClientAdapter.startEvictorThread();
        try {
            HttpGet httpget = new HttpGet(url);
            CloseableHttpResponse response = httpClientAdapter.execute(httpget);
            try {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }

        } finally {
            httpClientAdapter.close();
        }
    }


}

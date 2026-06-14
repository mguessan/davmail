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

package davmail.exchange.auth;

import davmail.Settings;
import davmail.http.HttpClientAdapter;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

/**
 * Implementation of -kerberos command line option, check Kerberos authentication.
 */
public class KerberosCheck {

    public boolean checkKerberosAuthentication() {
        // enable debug output for Kerberos
        System.setProperty("sun.security.krb5.debug", "true");
        // force Kerberos authentication
        Settings.setProperty("davmail.enableKerberos", "true");

        String url = Settings.getProperty("davmail.url");
        if (url == null || !url.toLowerCase().endsWith("/ews/exchange.asmx")) {
            System.out.println("Unable to check Kerberos authentication, invalid Exchange URL, must end with /ews/exchange.asmx");
            return false;
        }
        // try to authenticate on EWS endpoint
        try (HttpClientAdapter httpClient = new HttpClientAdapter(url)) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
            }
        } catch (IOException e) {
            System.out.println("Kerberos authentication failed");
        }
        return false;
    }
}

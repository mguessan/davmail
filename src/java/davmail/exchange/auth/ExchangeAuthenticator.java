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

import davmail.http.HttpClientAdapter;

import java.io.IOException;
import java.net.URI;

/**
 * Common interface for all Exchange and O365 authenticators.
 * Implement this interface to build custom authenticators for unsupported Exchange architecture
 */
public interface ExchangeAuthenticator {
    void setUsername(String username);

    void setPassword(String password);

    /**
     * Authenticate against Exchange or O365
     * @throws IOException on error
     */
    void authenticate() throws IOException;

    O365Token getToken() throws IOException;

    /**
     * Return default or computed Exchange or O365 url
     * @return target url
     */
    URI getExchangeUri();

    /**
     * Return a new HttpClientAdapter instance with pooling enabled for ExchangeSession
     * @return HttpClientAdapter instance
     */
    HttpClientAdapter getHttpClientAdapter();
}

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
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;

/**
 * Experimental: load Oauth2 token from settings
 */
@SuppressWarnings("unused")
public class O365StoredTokenAuthenticator implements ExchangeAuthenticator {
    private static final Logger LOGGER = Logger.getLogger(O365StoredTokenAuthenticator.class);

    URI ewsUrl = URI.create(Settings.getO365Url());

    private String username;
    private String password;
    private O365Token token;

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Return a pool enabled HttpClientAdapter instance to access O365
     * @return HttpClientAdapter instance
     */
    @Override
    public HttpClientAdapter getHttpClientAdapter() {
        return new HttpClientAdapter(getExchangeUri(), username, password, true);
    }

    @Override
    public void authenticate() throws IOException {
        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        final String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", Settings.getO365LoginUrl()+"common/oauth2/nativeclient");
        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        String refreshToken = Settings.getProperty("davmail.oauth."+username.toLowerCase()+".refreshToken");
        if (refreshToken == null) {
            // single user mode
            refreshToken = Settings.getProperty("davmail.oauth.refreshToken");
        }
        String accessToken = Settings.getProperty("davmail.oauth.accessToken");
        if (refreshToken == null && accessToken == null) {
            LOGGER.warn("No stored Oauth refresh token found for "+username);
            throw new IOException("No stored Oauth refresh token found for "+username);
        }

        token = new O365Token(tenantId, clientId, redirectUri, password);
        if (accessToken != null) {
            // for tests only: load access token, will expire in at most one hour
            token.setAccessToken(accessToken);
        } else {
            token.setRefreshToken(refreshToken);
            token.refreshToken();
        }
    }

    @Override
    public O365Token getToken() {
        return token;
    }

    @Override
    public URI getExchangeUri() {
        return ewsUrl;
    }
}

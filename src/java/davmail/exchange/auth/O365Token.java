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

import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.RestMethod;
import davmail.util.IOUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.util.Date;

public class O365Token {
    protected final String URL = "https://login.microsoftonline.com/common/oauth2/token";

    protected static final Logger LOGGER = Logger.getLogger(O365Token.class);

    private String clientId;
    private String redirectUri;
    private String username;
    private String refreshToken;
    private String accessToken;
    private long expireson;

    public O365Token(String clientId, String redirectUri, String code) throws IOException {
        this.clientId = clientId;
        this.redirectUri = redirectUri;

        RestMethod tokenMethod = new RestMethod(URL);
        tokenMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        tokenMethod.addParameter("grant_type", "authorization_code");
        tokenMethod.addParameter("code", code);
        tokenMethod.addParameter("redirect_uri", redirectUri);
        tokenMethod.addParameter("client_id", clientId);

        executeMethod(tokenMethod);
    }


    public String getUsername() {
        return username;
    }

    public void setJsonToken(JSONObject jsonToken) throws IOException {
        try {
            if (jsonToken.opt("error") != null) {
                throw new IOException(jsonToken.optString("error")+" "+jsonToken.optString("error_description"));
            }
            // access token expires after one hour
            accessToken = jsonToken.getString("access_token");
            // precious refresh token
            refreshToken = jsonToken.getString("refresh_token");
            // expires_on is in second, not millisecond
            expireson = jsonToken.getLong("expires_on") * 1000;

            LOGGER.debug("Access token expires at " + new Date(expireson) + ": " + accessToken);

            String decodedBearer = IOUtil.decodeBase64AsString(accessToken.substring(accessToken.indexOf('.') + 1, accessToken.lastIndexOf('.')) + "==");
            JSONObject tokenBody = new JSONObject(decodedBearer);
            LOGGER.debug("Token: " + tokenBody);
            username = tokenBody.getString("unique_name");
        } catch (JSONException e) {
            throw new IOException("Exception parsing token", e);
        }
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAccessToken() throws IOException {
        // detect expiration and refresh token
        if (System.currentTimeMillis() > expireson - 60000) {
            LOGGER.info("Access token expires soon, trying to refresh it");
            refreshToken();
        }
        LOGGER.info("Access token for " + username + " expires in " + (expireson - System.currentTimeMillis()) / 60000 + " minutes");
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    private void refreshToken() throws IOException {
        String url = "https://login.microsoftonline.com/common/oauth2/token";
        RestMethod tokenMethod = new RestMethod(url);
        tokenMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        tokenMethod.addParameter("grant_type", "refresh_token");
        tokenMethod.addParameter("refresh_token", refreshToken);
        tokenMethod.addParameter("redirect_uri", redirectUri);
        tokenMethod.addParameter("client_id", clientId);

        executeMethod(tokenMethod);
    }

    private void executeMethod(RestMethod tokenMethod) throws IOException {
        HttpClient httpClient = null;
        try {
            httpClient = DavGatewayHttpClientFacade.getInstance(URL);
            httpClient.executeMethod(tokenMethod);
            setJsonToken(tokenMethod.getJsonResponse());

        } finally {
            tokenMethod.releaseConnection();
            // do not keep login connections open
            if (httpClient != null) {
                ((SimpleHttpConnectionManager) httpClient.getHttpConnectionManager()).shutdown();
            }
        }

    }
}

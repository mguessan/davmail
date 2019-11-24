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
import davmail.http.request.RestRequest;
import davmail.util.IOUtil;
import davmail.util.StringEncryptor;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class O365Token {
    protected final String RESOURCE_URL = "https://outlook.office365.com/";

    protected static final Logger LOGGER = Logger.getLogger(O365Token.class);

    private String clientId;
    private String tokenUrl;
    private String redirectUri;
    private String username;
    private String refreshToken;
    private String accessToken;
    private long expiresOn;

    public O365Token(String tenantId, String clientId, String redirectUri) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.tokenUrl = "https://login.microsoftonline.com/"+tenantId+"/oauth2/token";
    }

    public O365Token(String tenantId, String clientId, String redirectUri, String code) throws IOException {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.tokenUrl = "https://login.microsoftonline.com/"+tenantId+"/oauth2/token";

        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("grant_type", "authorization_code"));
        parameters.add(new BasicNameValuePair("code", code));
        parameters.add(new BasicNameValuePair("redirect_uri", redirectUri));
        parameters.add(new BasicNameValuePair("client_id", clientId));

        RestRequest tokenRequest = new RestRequest(tokenUrl, new UrlEncodedFormEntity(parameters, Consts.UTF_8));

        executeRequest(tokenRequest);
    }


    public String getUsername() {
        return username;
    }

    public void setJsonToken(JSONObject jsonToken) throws IOException {
        try {
            if (jsonToken.opt("error") != null) {
                throw new IOException(jsonToken.optString("error") + " " + jsonToken.optString("error_description"));
            }
            // access token expires after one hour
            accessToken = jsonToken.getString("access_token");
            // precious refresh token
            refreshToken = jsonToken.getString("refresh_token");
            // expires_on is in second, not millisecond
            expiresOn = jsonToken.getLong("expires_on") * 1000;

            LOGGER.debug("Access token expires " + new Date(expiresOn));

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
        if (System.currentTimeMillis() > expiresOn - 60000) {
            LOGGER.debug("Access token expires soon, trying to refresh it");
            refreshToken();
        }
        //LOGGER.debug("Access token for " + username + " expires in " + (expiresOn - System.currentTimeMillis()) / 60000 + " minutes");
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        // assume unexpired token
        expiresOn = System.currentTimeMillis() + 1000 * 60 * 60;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void refreshToken() throws IOException {
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
        parameters.add(new BasicNameValuePair("refresh_token", refreshToken));
        parameters.add(new BasicNameValuePair("redirect_uri", redirectUri));
        parameters.add(new BasicNameValuePair("client_id", clientId));
        parameters.add(new BasicNameValuePair("resource", "https://outlook.office365.com/"));

        RestRequest tokenRequest = new RestRequest(tokenUrl, new UrlEncodedFormEntity(parameters, Consts.UTF_8));

        executeRequest(tokenRequest);
    }

    private void executeRequest(RestRequest tokenMethod) throws IOException {
        HttpClientAdapter httpClientAdapter = new HttpClientAdapter(RESOURCE_URL);
        try {
            httpClientAdapter.execute(tokenMethod);
            setJsonToken(tokenMethod.getJsonResponse());

        } finally {
            // do not keep login connections open
            httpClientAdapter.close();
        }

    }

    static O365Token build(String tenantId, String clientId, String redirectUri, String code, String password) throws IOException {
        O365Token token = new O365Token(tenantId, clientId, redirectUri, code);
        if (Settings.getBooleanProperty("davmail.oauth.persistToken", true)) {
            try {
                Settings.storeRefreshToken(encryptToken(token.getRefreshToken(), password), token.getUsername());
            } catch (IOException e) {
                LOGGER.warn("Unable to store refreshToken: "+e.getMessage());
            }
        }
        return token;
    }


    static O365Token load(String tenantId, String clientId, String redirectUri, String username, String password) throws IOException {
        O365Token token = null;
        if (Settings.getBooleanProperty("davmail.oauth.persistToken", true)) {
            String encryptedRefreshToken = Settings.loadRefreshToken(username);
            if (encryptedRefreshToken != null) {
                String refreshToken = null;
                try {
                    refreshToken = decryptToken(encryptedRefreshToken, password);
                    LOGGER.debug("Loaded stored token for " + username);
                    O365Token localToken = new O365Token(tenantId, clientId, redirectUri);

                    localToken.setRefreshToken(refreshToken);
                    localToken.refreshToken();
                    LOGGER.debug("Authenticated user " + localToken.getUsername()+" from stored token");
                    token = localToken;
                } catch (IOException e) {
                    LOGGER.warn("refresh token failed " + e.getMessage());
                }
            }
        }
        return token;
    }

    private static String decryptToken(String encryptedRefreshToken,String password) throws IOException {
        return new StringEncryptor(password).decryptString(encryptedRefreshToken);
    }

    private static String encryptToken(String refreshToken, String password) throws IOException {
        return new StringEncryptor(password).encryptString(refreshToken);
    }
}

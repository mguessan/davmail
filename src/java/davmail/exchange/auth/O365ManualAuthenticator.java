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
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.exchange.ews.GetUserConfigurationMethod;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;

public class O365ManualAuthenticator implements ExchangeAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(O365ManualAuthenticator.class);

    String errorCode = null;
    String code = null;

    String resource = "https://outlook.office365.com";
    URI ewsUrl = URI.create(resource + "/EWS/Exchange.asmx");

    private O365ManualAuthenticatorDialog o365ManualAuthenticatorDialog;

    private String username;
    private O365Token token;

    public O365Token getToken() {
        return token;
    }

    @Override
    public URI getExchangeUri() {
        return ewsUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        // unused
    }


    public void authenticate() throws IOException {
        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        final String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");

        URI uri;
        try {
            uri = new URIBuilder()
                    .setScheme("https")
                    .setHost("login.microsoftonline.com")
                    .setPath("/common/oauth2/authorize")
                    .addParameter("client_id", clientId)
                    .addParameter("response_type", "code")
                    .addParameter("redirect_uri", redirectUri)
                    .addParameter("response_mode", "query")
                    .addParameter("resource", resource)
                    .addParameter("login_hint", username)
                    // force consent
                    //.addParameter("prompt", "consent")
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        final String initUrl = uri.toString();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    o365ManualAuthenticatorDialog = new O365ManualAuthenticatorDialog(initUrl);
                }
            });
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (InvocationTargetException e) {
            throw new IOException(e);
        }
        code = o365ManualAuthenticatorDialog.getCode();

        if (code == null) {
            LOGGER.error("Authentication failed, code not available");
            throw new DavMailException("EXCEPTION_AUTHENTICATION_FAILED_REASON", errorCode);
        }

        token = new O365Token(clientId, redirectUri, code);

        LOGGER.debug("Authenticated username: " + token.getUsername());
        if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(token.getUsername())) {
            throw new DavMailAuthenticationException("Authenticated username " + token.getUsername() + " does not match " + username);
        }

    }

    public static void main(String[] argv) {
        try {
            Settings.setDefaultSettings();
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);

            O365ManualAuthenticator authenticator = new O365ManualAuthenticator();
            authenticator.setUsername("");
            authenticator.authenticate();

            // switch to EWS url
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(authenticator.ewsUrl.toString());

            GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY, DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
            checkMethod.setRequestHeader("Authorization", "Bearer " + authenticator.getToken().getAccessToken());
            try {
                //checkMethod.setServerVersion(serverVersion);
                httpClient.executeMethod(checkMethod);

                checkMethod.checkSuccess();
            } finally {
                checkMethod.releaseConnection();
            }
            System.out.println("Retrieved folder id " + checkMethod.getResponseItem().get("FolderId"));

            // loop to check expiration
            int i = 0;
            while (i++ < 12 * 60 * 2) {
                GetUserConfigurationMethod getUserConfigurationMethod = new GetUserConfigurationMethod();
                getUserConfigurationMethod.setRequestHeader("Authorization", "Bearer " + authenticator.getToken().getAccessToken());
                httpClient.executeMethod(getUserConfigurationMethod);
                System.out.println(getUserConfigurationMethod.getResponseItem());

                Thread.sleep(5000);
            }

        } catch (Exception e) {
            LOGGER.error(e + " " + e.getMessage());
            e.printStackTrace();
        }
        System.exit(0);
    }
}

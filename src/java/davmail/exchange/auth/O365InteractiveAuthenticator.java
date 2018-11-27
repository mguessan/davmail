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
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.exchange.ews.GetUserConfigurationMethod;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class O365InteractiveAuthenticator implements ExchangeAuthenticator {

    private static final int MAX_COUNT = 300;
    private static final Logger LOGGER = Logger.getLogger(O365InteractiveAuthenticator.class);

    boolean isAuthenticated = false;
    String errorCode = null;
    String code = null;

    String resource = "https://outlook.office365.com";
    String ewsUrl = resource + "/EWS/Exchange.asmx";
    String authorizeUrl = "https://login.microsoftonline.com/common/oauth2/authorize";

    private String username;
    private String password;
    private O365Token token;

    public O365Token getToken() {
        return token;
    }

    @Override
    public String getEWSUrl() {
        return ewsUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public void authenticate() throws IOException {
        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        final String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");

        final String initUrl = authorizeUrl
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + redirectUri
                + "&response_mode=query"
                + "&resource=" + resource
                + "&login_hint=" + URIUtil.encodeWithinQuery(username);

        // set default authenticator
        Authenticator.setDefault(new Authenticator() {
            @Override
            public PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    String proxyUser = Settings.getProperty("davmail.proxyUser");
                    String proxyPassword = Settings.getProperty("davmail.proxyPassword");
                    if (proxyUser != null && proxyPassword != null) {
                        LOGGER.debug("Proxy authentication with user " + proxyUser);
                        return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                    } else {
                        LOGGER.debug("Missing proxy credentials ");
                        return null;
                    }
                } else {
                    LOGGER.debug("Password authentication with user " + username);
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            }
        });

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                O365InteractiveAuthenticatorFrame o365InteractiveAuthenticatorFrame = new O365InteractiveAuthenticatorFrame();
                o365InteractiveAuthenticatorFrame.setO365InteractiveAuthenticator(O365InteractiveAuthenticator.this);
                o365InteractiveAuthenticatorFrame.authenticate(initUrl, redirectUri);
            }
        });

        int count = 0;

        while (!isAuthenticated && errorCode == null && count++ < MAX_COUNT) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        if (isAuthenticated) {
            token = new O365Token(clientId, redirectUri, code);

            LOGGER.debug("Authenticated username: " + token.getUsername());
            if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(token.getUsername())) {
                throw new DavMailAuthenticationException("Authenticated username " + token.getUsername() + " does not match " + username);
            }

        } else {
            LOGGER.error("Authentication failed " + errorCode);
            throw new IOException("Authentication failed " + errorCode);
        }
    }

    public static void main(String[] argv) {
        try {
            Settings.setDefaultSettings();
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);

            O365InteractiveAuthenticator authenticator = new O365InteractiveAuthenticator();
            authenticator.setUsername("");
            authenticator.authenticate();

            // switch to EWS url
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(authenticator.ewsUrl);

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

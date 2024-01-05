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
import davmail.http.HttpClientAdapter;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.security.Security;

public class O365InteractiveAuthenticator implements ExchangeAuthenticator {

    private static final int MAX_COUNT = 300;
    private static final Logger LOGGER = Logger.getLogger(O365InteractiveAuthenticator.class);

    static {
        // disable HTTP/2 loader on Java 14 and later to enable custom socket factory
        System.setProperty("com.sun.webkit.useHTTP2Loader", "false");
    }

    boolean isAuthenticated = false;
    String errorCode = null;
    String code = null;

    URI ewsUrl = URI.create(Settings.getO365Url());

    private O365InteractiveAuthenticatorFrame o365InteractiveAuthenticatorFrame;
    private O365ManualAuthenticatorDialog o365ManualAuthenticatorDialog;

    private String username;
    private String password;
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
        this.password = password;
    }

    /**
     * Return a pool enabled HttpClientAdapter instance to access O365
     *
     * @return HttpClientAdapter instance
     */
    @Override
    public HttpClientAdapter getHttpClientAdapter() {
        return new HttpClientAdapter(getExchangeUri(), username, password, true);
    }

    public void authenticate() throws IOException {

        // allow cross domain requests for Okta form support
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        // enable NTLM for ADFS support
        System.setProperty("jdk.http.ntlm.transparentAuth", "allHosts");

        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        final String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", Settings.O365_LOGIN_URL+"common/oauth2/nativeclient");
        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load stored token
        token = O365Token.load(tenantId, clientId, redirectUri, username, password);
        if (token != null) {
            isAuthenticated = true;
            return;
        }

        final String initUrl = O365Authenticator.buildAuthorizeUrl(tenantId, clientId, redirectUri, username);

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

        boolean isJFXAvailable = true;
        try {
            Class.forName("javafx.application.Platform");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Unable to load JavaFX (OpenJFX), switch to manual mode");
            isJFXAvailable = false;
        }

        if (isJFXAvailable) {
            SwingUtilities.invokeLater(() -> {
                try {
                    o365InteractiveAuthenticatorFrame = new O365InteractiveAuthenticatorFrame();
                    o365InteractiveAuthenticatorFrame.setO365InteractiveAuthenticator(O365InteractiveAuthenticator.this);
                    o365InteractiveAuthenticatorFrame.authenticate(initUrl, redirectUri);
                } catch (NoClassDefFoundError e) {
                    LOGGER.warn("Unable to load JavaFX (OpenJFX)");
                }

            });
        } else {
            if (o365InteractiveAuthenticatorFrame == null) {
                try {
                    SwingUtilities.invokeAndWait(() -> o365ManualAuthenticatorDialog = new O365ManualAuthenticatorDialog(initUrl));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (InvocationTargetException e) {
                    throw new IOException(e);
                }
                code = o365ManualAuthenticatorDialog.getCode();
                isAuthenticated = code != null;
                if (!isAuthenticated) {
                    errorCode = "User did not provide authentication code";
                }
            }
        }

        int count = 0;

        while (!isAuthenticated && errorCode == null && count++ < MAX_COUNT) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (count > MAX_COUNT) {
            errorCode = "Timed out waiting for interactive authentication";
        }

        if (o365InteractiveAuthenticatorFrame != null && o365InteractiveAuthenticatorFrame.isVisible()) {
            o365InteractiveAuthenticatorFrame.close();
        }

        if (isAuthenticated) {
            token = O365Token.build(tenantId, clientId, redirectUri, code, password);

            LOGGER.debug("Authenticated username: " + token.getUsername());
            if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(token.getUsername())) {
                throw new DavMailAuthenticationException("Authenticated username " + token.getUsername() + " does not match " + username);
            }

        } else {
            LOGGER.error("Authentication failed " + errorCode);
            throw new DavMailException("EXCEPTION_AUTHENTICATION_FAILED_REASON", errorCode);
        }
    }

    public static void main(String[] argv) {

        try {
            // set custom factory before loading OpenJFX
            Security.setProperty("ssl.SocketFactory.provider", "davmail.http.DavGatewaySSLSocketFactory");

            Settings.setDefaultSettings();
            Settings.setConfigFilePath("davmail-interactive.properties");
            Settings.load();

            O365InteractiveAuthenticator authenticator = new O365InteractiveAuthenticator();
            authenticator.setUsername("");
            authenticator.authenticate();

            HttpClientAdapter httpClientAdapter = new HttpClientAdapter(authenticator.getExchangeUri(), true);

            // switch to EWS url
            GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY, DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
            checkMethod.setHeader("Authorization", "Bearer " + authenticator.getToken().getAccessToken());
            try (
                    CloseableHttpResponse response = httpClientAdapter.execute(checkMethod)
            ) {
                checkMethod.handleResponse(response);
                checkMethod.checkSuccess();
            }
            System.out.println("Retrieved folder id " + checkMethod.getResponseItem().get("FolderId"));

            // loop to check expiration
            int i = 0;
            while (i++ < 12 * 60 * 2) {
                GetUserConfigurationMethod getUserConfigurationMethod = new GetUserConfigurationMethod();
                getUserConfigurationMethod.setHeader("Authorization", "Bearer " + authenticator.getToken().getAccessToken());
                try (
                        CloseableHttpResponse response = httpClientAdapter.execute(getUserConfigurationMethod)
                ) {
                    getUserConfigurationMethod.handleResponse(response);
                    getUserConfigurationMethod.checkSuccess();
                }
                System.out.println(getUserConfigurationMethod.getResponseItem());

                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Thread interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error(e + " " + e.getMessage(), e);
        }
        System.exit(0);
    }
}

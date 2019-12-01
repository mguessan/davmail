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

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.exchange.ews.GetUserConfigurationMethod;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

public class O365ManualAuthenticator implements ExchangeAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(O365ManualAuthenticator.class);

    String errorCode = null;
    String code = null;

    String resource = "https://outlook.office365.com";
    URI ewsUrl = URI.create(resource + "/EWS/Exchange.asmx");

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


    public void authenticate() throws IOException {
        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        final String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");
        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load stored token
        token = O365Token.load(tenantId, clientId, redirectUri, username, password);
        if (token != null) {
            return;
        }

        final String initUrl = O365Authenticator.buildAuthorizeUrl(tenantId, clientId, redirectUri, username);

        if (Settings.getBooleanProperty("davmail.server") || GraphicsEnvironment.isHeadless()) {
            // command line mode
            code = getCodeFromConsole(initUrl);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> o365ManualAuthenticatorDialog = new O365ManualAuthenticatorDialog(initUrl));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            }
            code = o365ManualAuthenticatorDialog.getCode();
        }

        if (code == null) {
            LOGGER.error("Authentication failed, code not available");
            throw new DavMailException("EXCEPTION_AUTHENTICATION_FAILED_REASON", errorCode);
        }

        token = O365Token.build(tenantId, clientId, redirectUri, code, password);

        LOGGER.debug("Authenticated username: " + token.getUsername());
        if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(token.getUsername())) {
            throw new DavMailAuthenticationException("Authenticated username " + token.getUsername() + " does not match " + username);
        }

    }

    private String getCodeFromConsole(String initUrl) {
        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder buffer = new StringBuilder();
        buffer.append(BundleMessage.format("UI_0365_AUTHENTICATION_PROMPT_CONSOLE", initUrl)).append("\n")
        .append(BundleMessage.format("UI_0365_AUTHENTICATION_CODE"));
        try {
            System.out.print(buffer.toString());
            code = inReader.readLine();
            if (code != null && code.contains("code=") && code.contains("&session_state=")) {
                code = code.substring(code.indexOf("code=")+5, code.indexOf("&session_state="));
            }
        } catch (IOException e) {
            System.err.println(e + " " + e.getMessage());
        }
        return code;
    }

    public static void main(String[] argv) {
        try {
            Settings.setDefaultSettings();
            Settings.setProperty("davmail.server", "false");
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
            LOGGER.error(e + " " + e.getMessage(), e);
        }
        System.exit(0);
    }
}

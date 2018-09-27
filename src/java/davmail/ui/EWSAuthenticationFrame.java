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
package davmail.ui;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exchange.ExchangeAuthenticator;
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.exchange.ews.GetUserConfigurationMethod;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.IOUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.w3c.dom.Document;

import javax.swing.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Date;

public class EWSAuthenticationFrame extends JFrame implements ExchangeAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(EWSAuthenticationFrame.class);

    String location;
    boolean isAuthenticated = false;
    final JFXPanel fxPanel = new JFXPanel();

    public EWSAuthenticationFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle(BundleMessage.format("UI_DAVMAIL_GATEWAY"));
        try {
            setIconImages(DavGatewayTray.getFrameIcons());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }

        JPanel mainPanel = new JPanel();

        mainPanel.add(fxPanel);
        add(BorderLayout.CENTER, mainPanel);

        pack();
        setResizable(true);
        // center frame
        setSize(600, 600);
        setLocation(getToolkit().getScreenSize().width / 2 -
                        getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setVisible(true);
    }

    private void initFX(final JFXPanel fxPanel, final String url, final String redirectUri) {
        Authenticator.setDefault(new Authenticator() {
            @Override
            public PasswordAuthentication getPasswordAuthentication() {
                LOGGER.debug("Password authentication with user "+username);
                return new PasswordAuthentication(username, password.toCharArray());
            }
        });

        final WebView webView = new WebView();
        fxPanel.setScene(new Scene(webView));
        final WebEngine webViewEngine = webView.getEngine();

        webViewEngine.setUserAgent(DavGatewayHttpClientFacade.IE_USER_AGENT);

        webViewEngine.load(url);
        webViewEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
            @Override
            public void changed(ObservableValue ov, State oldState, State newState) {
                if (newState == State.SUCCEEDED) {
                    location = webViewEngine.getLocation();
                    setTitle("DavMail: " + location);
                    LOGGER.debug("Webview location: " + location);
                    LOGGER.debug(dumpDocument(webViewEngine.getDocument()));
                    if (location.startsWith(redirectUri)) {
                        isAuthenticated = true;
                        setVisible(false);
                    }
                }

            }

        });


    }

    public String dumpDocument(Document document) {
        String result;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            transformer.transform(new DOMSource(document),
                    new StreamResult(new OutputStreamWriter(baos, "UTF-8")));
            result = baos.toString("UTF-8");
        } catch (Exception e) {
            result = e + " " + e.getMessage();
        }
        return result;
    }

    String resource = "https://outlook.office365.com";
    String ewsUrl = resource + "/EWS/Exchange.asmx";
    String clientId = "facd6cff-a294-4415-b59f-c5b01937d7bd"; // common DavMail client id
    String redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient";
    String authorizeUrl = "https://login.microsoftonline.com/common/oauth2/authorize";

    private String username;
    private String password;
    private String bearer;

    public String getBearer() {
        return bearer;
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

        // Run initFX as JavaFX-Thread
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                initFX(fxPanel, authorizeUrl
                                + "?client_id=" + clientId
                                + "&response_type=code"
                                + "&redirect_uri=" + redirectUri
                                + "&response_mode=query"
                                + "&resource="+resource
                                + "&login_hint="+((username == null)?"":username)
                        // force consent
                        //+"&prompt=consent";
                        , redirectUri);
            }
        });

        while (!isAuthenticated && isVisible()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        try {
            if (isAuthenticated) {
                HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(authorizeUrl);

                LOGGER.debug("Authenticated location: " + location);
                String code = location.substring(location.indexOf("code=") + 5, location.indexOf("&session_state="));
                String sessionState = location.substring(location.lastIndexOf('='));

                LOGGER.debug("Authentication Code: " + code);
                LOGGER.debug("Authentication session state: " + sessionState);

                PostMethod tokenMethod = new PostMethod("https://login.microsoftonline.com/common/oauth2/token");
                tokenMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
                tokenMethod.addParameter("grant_type", "authorization_code");
                tokenMethod.addParameter("code", code);
                tokenMethod.addParameter("redirect_uri", redirectUri);
                tokenMethod.addParameter("client_id", clientId);

                httpClient.executeMethod(tokenMethod);
                LOGGER.debug(tokenMethod.getStatusCode() + " " + tokenMethod.getStatusText());


                String responseBodyAsString = tokenMethod.getResponseBodyAsString();
                tokenMethod.releaseConnection();
                LOGGER.debug(responseBodyAsString);

                JSONObject jsonObject = new JSONObject(responseBodyAsString);
                bearer = jsonObject.getString("access_token");
                long expireson = jsonObject.getLong("expires_on");
                String expires_in = jsonObject.getString("expires_in");

                LOGGER.debug("Bearer: " + bearer);
                LOGGER.debug("Expires: " + new Date(expireson));
                LOGGER.debug("Expires in: " + expires_in);

                String decodedBearer = IOUtil.decodeBase64AsString(bearer.substring(bearer.indexOf('.') + 1, bearer.lastIndexOf('.')) + "==");
                LOGGER.debug("Decoded Bearer: " + decodedBearer);
                JSONObject tokenBody = new JSONObject(decodedBearer);
                LOGGER.debug(tokenBody);

                username = tokenBody.getString("unique_name");
                LOGGER.debug("Authenticated username: " + username);

            } else {
                LOGGER.error("Authentication failed");
                throw new IOException("Authentication failed");
            }
        } catch (JSONException e) {
            throw new IOException(e+" "+e.getMessage(), e);
        }
    }

    public static void main(String[] argv) {
        try {
            Settings.setDefaultSettings();
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);

            EWSAuthenticationFrame authenticationFrame = new EWSAuthenticationFrame();
            authenticationFrame.authenticate();

            // switch to EWS url
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(authenticationFrame.ewsUrl);
            DavGatewayHttpClientFacade.createMultiThreadedHttpConnectionManager(httpClient);

            GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY, DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
            checkMethod.setRequestHeader("Authorization", "Bearer " + authenticationFrame.bearer);
            try {
                //checkMethod.setServerVersion(serverVersion);
                httpClient.executeMethod(checkMethod);

                checkMethod.checkSuccess();
            } finally {
                checkMethod.releaseConnection();
            }
            System.out.println("Retrieved folder id " + checkMethod.getResponseItem().get("FolderId"));

            GetUserConfigurationMethod getUserConfigurationMethod = new GetUserConfigurationMethod();
            getUserConfigurationMethod.setRequestHeader("Authorization", "Bearer " + authenticationFrame.bearer);
            httpClient.executeMethod(getUserConfigurationMethod);
            System.out.println(getUserConfigurationMethod.getResponseItem());

        } catch (Exception e) {
            LOGGER.error(e + " " + e.getMessage());
            e.printStackTrace();
        }
        System.exit(0);
    }
}

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
import davmail.exchange.ews.BaseShape;
import davmail.exchange.ews.DistinguishedFolderId;
import davmail.exchange.ews.GetFolderMethod;
import davmail.exchange.ews.GetUserConfigurationMethod;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.ui.tray.DavGatewayTray;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
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
import java.net.*;

public class O365InteractiveAuthenticator extends JFrame implements ExchangeAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(O365InteractiveAuthenticator.class);

    static {
        // register a stream handler for msauth protocol
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if ("msauth".equals(protocol)) {
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) {
                            return new URLConnection(u) {
                                @Override
                                public void connect() {
                                    // ignore
                                }
                            };
                        }
                    };
                }
                return null;
            }
        });
    }

    String location;
    boolean isAuthenticated = false;
    String errorCode = "";
    final JFXPanel fxPanel = new JFXPanel();

    public O365InteractiveAuthenticator() {
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
        WebView webView = new WebView();
        final WebEngine webViewEngine = webView.getEngine();

        final ProgressBar loadProgress = new ProgressBar();
        loadProgress.progressProperty().bind(webViewEngine.getLoadWorker().progressProperty());

        StackPane hBox = new StackPane();
        hBox.getChildren().setAll(webView, loadProgress);
        Scene scene = new Scene(hBox);
        fxPanel.setScene(scene);

        webViewEngine.setUserAgent(DavGatewayHttpClientFacade.getUserAgent());

        webViewEngine.load(url);
        webViewEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
            @Override
            public void changed(ObservableValue ov, State oldState, State newState) {
                LOGGER.debug(webViewEngine.getLocation());
                LOGGER.debug(dumpDocument(webViewEngine.getDocument()));
                if (newState == State.SUCCEEDED) {
                    loadProgress.setVisible(false);
                    // bring window to top
                    setAlwaysOnTop(true);
                    setAlwaysOnTop(false);
                    location = webViewEngine.getLocation();
                    setTitle("DavMail: " + location);
                    LOGGER.debug("Webview location: " + location);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(dumpDocument(webViewEngine.getDocument()));
                    }
                    if (location.startsWith(redirectUri)) {
                        LOGGER.debug("Location starts with redirectUri, check code");

                        isAuthenticated = location.contains("code=") && location.contains("&session_state=");
                        if (!isAuthenticated && location.contains("error=")) {
                            errorCode = location.substring(location.indexOf("error="));
                        }
                        setVisible(false);
                        dispose();
                    }
                } else if (newState == State.FAILED) {
                    Throwable e = webViewEngine.getLoadWorker().getException();
                    if (e != null) {
                        LOGGER.error(e+" "+e.getMessage());
                        errorCode = e.getMessage();
                    }
                    setVisible(false);
                    dispose();
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

        // set system proxy settings
        if (Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE)) {
            System.setProperty("java.net.useSystemProxies", "true");
        } else if (Settings.getProperty("davmail.proxyHost") != null) {
            System.setProperty("https.proxyHost", Settings.getProperty("davmail.proxyHost"));
            System.setProperty("https.proxyPort", Settings.getProperty("davmail.proxyPort"));
        }

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


        // Run initFX as JavaFX-Thread
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                initFX(fxPanel, initUrl, redirectUri);
            }
        });

        while (!isAuthenticated && isVisible()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        if (isAuthenticated) {
            LOGGER.debug("Authenticated location: " + location);
            String code = location.substring(location.indexOf("code=") + 5, location.indexOf("&session_state="));
            String sessionState = location.substring(location.lastIndexOf('='));

            LOGGER.debug("Authentication Code: " + code);
            LOGGER.debug("Authentication session state: " + sessionState);

            token = new O365Token(clientId, redirectUri, code);

            LOGGER.debug("Authenticated username: " + token.getUsername());
            if (username != null && !username.isEmpty() && !username.equalsIgnoreCase(token.getUsername())) {
                throw new IOException("Authenticated username " + token.getUsername() + " does not match " + username);
            }

        } else {
            LOGGER.error("Authentication failed "+errorCode);
            throw new IOException("Authentication failed "+errorCode);
        }
    }

    public static void main(String[] argv) {
        try {
            Settings.setDefaultSettings();
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
            Settings.setProperty("davmail.proxyHost", "proxy.free.fr");
            Settings.setProperty("davmail.proxyPort", "3128");

            O365InteractiveAuthenticator authenticationFrame = new O365InteractiveAuthenticator();
            authenticationFrame.setUsername("");
            authenticationFrame.authenticate();

            // switch to EWS url
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(authenticationFrame.ewsUrl);
            DavGatewayHttpClientFacade.createMultiThreadedHttpConnectionManager(httpClient);

            GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY, DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
            checkMethod.setRequestHeader("Authorization", "Bearer " + authenticationFrame.getToken().getAccessToken());
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
                getUserConfigurationMethod.setRequestHeader("Authorization", "Bearer " + authenticationFrame.getToken().getAccessToken());
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

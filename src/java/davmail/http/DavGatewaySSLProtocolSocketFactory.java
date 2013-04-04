/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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
package davmail.http;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.ui.PasswordPromptDialog;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpsURL;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

import javax.net.ssl.*;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.*;
import java.util.ArrayList;

/**
 * Manual Socket Factory.
 * Let user choose to accept or reject certificate
 */
public class DavGatewaySSLProtocolSocketFactory implements SecureProtocolSocketFactory {
    /**
     * Register custom Socket Factory to let user accept or reject certificate
     */
    public static void register() {
        String urlString = Settings.getProperty("davmail.url");
        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            if ("https".equals(protocol)) {
                int port = url.getPort();
                if (port < 0) {
                    port = HttpsURL.DEFAULT_PORT;
                }
                Protocol.registerProtocol(url.getProtocol(),
                        new Protocol(protocol, (ProtocolSocketFactory) new DavGatewaySSLProtocolSocketFactory(), port));
            }
        } catch (MalformedURLException e) {
            DavGatewayTray.error(new BundleMessage("LOG_INVALID_URL", urlString));
        }
    }

    private KeyStore.ProtectionParameter getProtectionParameter(String password) {
        if (password != null && password.length() > 0) {
            // password provided: create a PasswordProtection
            return new KeyStore.PasswordProtection(password.toCharArray());
        } else {
            // request password at runtime through a callback
            return new KeyStore.CallbackHandlerProtection(new CallbackHandler() {
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    if (callbacks.length > 0 && callbacks[0] instanceof PasswordCallback) {
                        PasswordPromptDialog passwordPromptDialog = new PasswordPromptDialog(((PasswordCallback) callbacks[0]).getPrompt());
                        ((PasswordCallback) callbacks[0]).setPassword(passwordPromptDialog.getPassword());
                    }
                }
            });
        }
    }

    private SSLContext sslcontext;

    private SSLContext createSSLContext() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, KeyManagementException, KeyStoreException {
        // PKCS11 client certificate settings
        String pkcs11Library = Settings.getProperty("davmail.ssl.pkcs11Library");

        String clientKeystoreType = Settings.getProperty("davmail.ssl.clientKeystoreType");
        // set default keystore type
        if (clientKeystoreType == null || clientKeystoreType.length() == 0) {
            clientKeystoreType = "PKCS11";
        }

        if (pkcs11Library != null && pkcs11Library.length() > 0 && "PKCS11".equals(clientKeystoreType)) {
            StringBuilder pkcs11Buffer = new StringBuilder();
            pkcs11Buffer.append("name=DavMail\n");
            pkcs11Buffer.append("library=").append(pkcs11Library).append('\n');
            String pkcs11Config = Settings.getProperty("davmail.ssl.pkcs11Config");
            if (pkcs11Config != null && pkcs11Config.length() > 0) {
                pkcs11Buffer.append(pkcs11Config).append('\n');
            }
            SunPKCS11ProviderHandler.registerProvider(pkcs11Buffer.toString());
        }
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        if ("SunX509".equals(algorithm)) {
            algorithm = "NewSunX509";
        } else if ("IbmX509".equals(algorithm)) {
            algorithm = "NewIbmX509";
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);

        ArrayList<KeyStore.Builder> keyStoreBuilders = new ArrayList<KeyStore.Builder>();
        // PKCS11 (smartcard) keystore with password callback
        KeyStore.Builder scBuilder = KeyStore.Builder.newInstance("PKCS11", null, getProtectionParameter(null));
        keyStoreBuilders.add(scBuilder);

        String clientKeystoreFile = Settings.getProperty("davmail.ssl.clientKeystoreFile");
        String clientKeystorePass = Settings.getProperty("davmail.ssl.clientKeystorePass");
        if (clientKeystoreFile != null && clientKeystoreFile.length() > 0
                && ("PKCS12".equals(clientKeystoreType) || "JKS".equals(clientKeystoreType))) {
            // PKCS12 file based keystore
            KeyStore.Builder fsBuilder = KeyStore.Builder.newInstance(clientKeystoreType, null,
                    new File(clientKeystoreFile), getProtectionParameter(clientKeystorePass));
            keyStoreBuilders.add(fsBuilder);
        }

        ManagerFactoryParameters keyStoreBuilderParameters = new KeyStoreBuilderParameters(keyStoreBuilders);
        keyManagerFactory.init(keyStoreBuilderParameters);

        // Get a list of key managers
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        // Walk through the key managers and replace all X509 Key Managers with
        // a specialized wrapped DavMail X509 Key Manager
        for (int i = 0; i < keyManagers.length; i++) {
            KeyManager keyManager = keyManagers[i];
            if (keyManager instanceof X509KeyManager) {
                keyManagers[i] = new DavMailX509KeyManager((X509KeyManager) keyManager);
            }
        }

        SSLContext context = SSLContext.getInstance("SSL");
        context.init(keyManagers, new TrustManager[]{new DavGatewayX509TrustManager()}, null);
        return context;
    }

    private SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, InvalidAlgorithmParameterException {
        if (this.sslcontext == null) {
            this.sslcontext = createSSLContext();
        }
        return this.sslcontext;
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort, HttpConnectionParams params) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    public Socket createSocket(String host, int port) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    /**
     * All instances of SSLProtocolSocketFactory are the same.
     */
    @Override
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(this.getClass()));
    }

    /**
     * All instances of SSLProtocolSocketFactory have the same hash code.
     */
    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }
}

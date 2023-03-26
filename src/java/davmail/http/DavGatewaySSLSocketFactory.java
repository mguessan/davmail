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

package davmail.http;

import davmail.Settings;
import davmail.ui.PasswordPromptDialog;
import org.apache.log4j.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyStoreBuilderParameters;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.callback.PasswordCallback;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.ArrayList;

/**
 * SSLSocketFactory implementation.
 * Wrapper for DavGatewaySSLProtocolSocketFactory used by HttpClient 4
 */
public class DavGatewaySSLSocketFactory extends SSLSocketFactory {
    static final Logger LOGGER = Logger.getLogger(DavGatewaySSLSocketFactory.class);

    private SSLContext sslcontext;

    private SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, InvalidAlgorithmParameterException {
        if (this.sslcontext == null) {
            this.sslcontext = createSSLContext();
        }
        return this.sslcontext;
    }

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

        ArrayList<KeyStore.Builder> keyStoreBuilders = new ArrayList<>();
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
        // Enable native Windows SmartCard access through MSCAPI (no PKCS11 config required)
        if ("MSCAPI".equals(clientKeystoreType)) {
            try {
                //Provider provider = (Provider) Class.forName("sun.security.mscapi.SunMSCAPI").getDeclaredConstructor().newInstance();
                //KeyStore keyStore = KeyStore.getInstance("Windows-MY", provider);
                KeyStore keyStore = KeyStore.getInstance("Windows-MY");
                keyStore.load(null, null);
                keyStoreBuilders.add(KeyStore.Builder.newInstance(keyStore, new KeyStore.PasswordProtection(null)));
            } catch (Exception e) {
                // ignore
            }
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

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, new TrustManager[]{new DavGatewayX509TrustManager()}, null);
        return context;
    }

    private KeyStore.ProtectionParameter getProtectionParameter(String password) {
        if (password != null && password.length() > 0) {
            // password provided: create a PasswordProtection
            return new KeyStore.PasswordProtection(password.toCharArray());
        } else {
            // request password at runtime through a callback
            return new KeyStore.CallbackHandlerProtection(callbacks -> {
                if (callbacks.length > 0 && callbacks[0] instanceof PasswordCallback) {
                    if (Settings.getBooleanProperty("davmail.server") || GraphicsEnvironment.isHeadless()) {
                        // headless or server mode
                        System.out.print(((PasswordCallback) callbacks[0]).getPrompt() + ": ");
                        String password1 = new BufferedReader(new InputStreamReader(System.in)).readLine();
                        ((PasswordCallback) callbacks[0]).setPassword(password1.toCharArray());
                    } else {
                        PasswordPromptDialog passwordPromptDialog = new PasswordPromptDialog(((PasswordCallback) callbacks[0]).getPrompt());
                        ((PasswordCallback) callbacks[0]).setPassword(passwordPromptDialog.getPassword());
                    }
                }
            });
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        try {
            return getSSLContext().getSocketFactory().getDefaultCipherSuites();
        } catch (Exception e) {
            // ignore
        }
        return new String[]{};
    }

    @Override
    public String[] getSupportedCipherSuites() {
        try {
            return getSSLContext().getSocketFactory().getSupportedCipherSuites();
        } catch (Exception e) {
            // ignore
        }
        return new String[]{};
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        try {
            return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port + " " + clientHost + " " + clientPort);
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress clientHost, int clientPort) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port + " " + clientHost + " " + clientPort);
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | InvalidAlgorithmParameterException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }
}

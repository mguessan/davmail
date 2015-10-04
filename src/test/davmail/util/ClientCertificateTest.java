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

package davmail.util;

import davmail.Settings;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import davmail.http.DavGatewayX509TrustManager;
import davmail.http.DavMailX509KeyManager;
import davmail.http.SunPKCS11ProviderHandler;
import davmail.ui.PasswordPromptDialog;
import junit.framework.TestCase;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import javax.net.ssl.*;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.smartcardio.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

/**
 * Test HTTPS mutual authentication
 */
public class ClientCertificateTest extends TestCase {
    HttpClient httpClient;

    @Override
    public void setUp() throws IOException {
        if (httpClient == null) {
            System.setProperty("javax.net.ssl.trustStore", "cacerts");
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
            System.setProperty("javax.net.debug", "ssl,handshake");

            httpClient = new HttpClient();
            HostConfiguration hostConfig = httpClient.getHostConfiguration();
            URI httpURI = new URI("https://localhost", true);
            hostConfig.setHost(httpURI);

            Protocol.registerProtocol("https",
                    new Protocol("https", (ProtocolSocketFactory) new DavGatewaySSLProtocolSocketFactory(), 443));
        }
    }

    public void testGetRoot() throws IOException {
        GetMethod getMethod = new GetMethod("/");
        httpClient.executeMethod(getMethod);
    }

    public void testConnect() throws IOException {
        GetMethod getMethod = new GetMethod("/testdir");
        httpClient.executeMethod(getMethod);
        assertEquals(HttpStatus.SC_OK, getMethod.getStatusCode());
        System.out.println(getMethod.getResponseBodyAsString());
    }

    public void testCardReaders() throws CardException {
        for (CardTerminal terminal : TerminalFactory.getDefault().terminals().list()) {
            System.out.println("Card terminal: " + terminal.getName() + " " + (terminal.isCardPresent() ? "Card present" : "No card"));
            terminal.waitForCardPresent(10);
            if (terminal.isCardPresent()) {
                Card c = null;
                try {
                    c = terminal.connect("T=0");
                } catch (Exception e) {
                    // failover
                    c = terminal.connect("T=1");
                }

                ATR atr = c.getATR();

                byte[] bytes = atr.getBytes();
                System.out.print("card:");


                for (byte b : bytes) {
                    System.out.print(" " + Integer.toHexString(b & 0xff));
                }
                System.out.println();

            }
        }
    }

    public void testWindowsSmartCard() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-MY");
            ks.load(null, null);
            java.util.Enumeration en = ks.aliases();

            while (en.hasMoreElements()) {
                String aliasKey = (String) en.nextElement();
                X509Certificate c = (X509Certificate) ks.getCertificate(aliasKey);
                System.out.println("---> alias : " + aliasKey + " " + c.getSubjectDN());

                //PrivateKey key = (PrivateKey) ks.getKey(aliasKey, "Passw0rd".toCharArray());
                Certificate[] chain = ks.getCertificateChain(aliasKey);
            }

        } catch (Exception ioe) {
            System.err.println(ioe.getMessage());
        }
    }

    public void testClientSocket() throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, KeyManagementException, UnrecoverableKeyException {



        //System.setProperty("javax.net.ssl.trustStoreProvider", "SunMSCAPI");
        //System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        System.setProperty("javax.net.ssl.trustStore", "cacerts");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");

        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        if ("SunX509".equals(algorithm)) {
            algorithm = "NewSunX509";
        } else if ("IbmX509".equals(algorithm)) {
            algorithm = "NewIbmX509";
        }


        Provider sunMSCAPI = new sun.security.mscapi.SunMSCAPI();
        //Security.insertProviderAt(sunMSCAPI, 1);
        KeyStore keyStore = KeyStore.getInstance("Windows-MY",sunMSCAPI);
        keyStore.load(null, null);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, null);

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



        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, null, null);
        SSLSocketFactory sockFactory = sslContext.getSocketFactory();
        SSLSocket sslSock = (SSLSocket)sockFactory.createSocket("localhost", 443);
        sslSock.startHandshake();

    }


    private SSLContext createSSLContext() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, KeyManagementException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
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
        //keyStoreBuilders.add(scBuilder);

        String clientKeystoreFile = Settings.getProperty("davmail.ssl.clientKeystoreFile");
        String clientKeystorePass = Settings.getProperty("davmail.ssl.clientKeystorePass");
        if (clientKeystoreFile != null && clientKeystoreFile.length() > 0
                && ("PKCS12".equals(clientKeystoreType) || "JKS".equals(clientKeystoreType))) {
            // PKCS12 file based keystore
            KeyStore.Builder fsBuilder = KeyStore.Builder.newInstance(clientKeystoreType, null,
                    new File(clientKeystoreFile), getProtectionParameter(clientKeystorePass));
            keyStoreBuilders.add(fsBuilder);
        }
        System.setProperty("javax.net.debug", "ssl,handshake");
        //try {
            Provider sunMSCAPI = new sun.security.mscapi.SunMSCAPI();
            //Security.insertProviderAt(sunMSCAPI, 1);
            KeyStore keyStore = KeyStore.getInstance("Windows-MY", sunMSCAPI);

            keyStore.load(null, null);



            keyStoreBuilders.add(KeyStore.Builder.newInstance(keyStore, new KeyStore.PasswordProtection(null)));

        /*} catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        }*/

        ManagerFactoryParameters keyStoreBuilderParameters = new KeyStoreBuilderParameters(keyStoreBuilders);
        keyManagerFactory.init(keyStoreBuilderParameters);
        //keyManagerFactory.init(keyStore, null);

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

        //keyManagers = new KeyManager[]{new DavMailX509KeyManager(new X509KeyManagerImpl())}

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, new TrustManager[]{new DavGatewayX509TrustManager()}, null);
        return context;
    }

    protected KeyStore.ProtectionParameter getProtectionParameter(String password) {
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

    public void testClientSocketFactory() throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, KeyManagementException, UnrecoverableKeyException, InvalidAlgorithmParameterException {



        //System.setProperty("javax.net.ssl.trustStoreProvider", "SunMSCAPI");
        //System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        System.setProperty("javax.net.ssl.trustStore", "cacerts");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");


        //SSLSocket sslSock = (SSLSocket)new DavGatewaySSLProtocolSocketFactory().createSocket("localhost", 443);
        SSLSocket sslSock = (SSLSocket) createSSLContext().getSocketFactory().createSocket("localhost", 443);
        sslSock.startHandshake();

    }
}

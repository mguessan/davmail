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
import sun.security.pkcs11.SunPKCS11;

import javax.net.ssl.*;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

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

    private SSLContext sslcontext;

    private SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException, InvalidAlgorithmParameterException {
        // PKCS11 client certificate settings
        String pkcs11Library = Settings.getProperty("davmail.ssl.pkcs11Library");
        if (pkcs11Library != null && pkcs11Library.length() > 0) {
            StringBuilder pkcs11Buffer = new StringBuilder();
            pkcs11Buffer.append("name=DavMail\n");
            pkcs11Buffer.append("library=").append(pkcs11Library).append('\n');
            String pkcs11Config = Settings.getProperty("davmail.ssl.pkcs11Config");
            if (pkcs11Config != null && pkcs11Config.length() > 0) {
                 pkcs11Buffer.append(pkcs11Config).append('\n');
            }
            Provider p = new SunPKCS11(new ByteArrayInputStream(pkcs11Buffer.toString().getBytes()));
            Security.addProvider(p);
        }
        
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(/*KeyManagerFactory.getDefaultAlgorithm()*/"NewSunX509");
        KeyStore.Builder scBuilder = KeyStore.Builder.newInstance("PKCS11", null,
                new KeyStore.CallbackHandlerProtection(new CallbackHandler() {
                    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                        if (callbacks.length > 0 && callbacks[0] instanceof PasswordCallback) {
                            PasswordPromptDialog passwordPromptDialog = new PasswordPromptDialog(((PasswordCallback) callbacks[0]).getPrompt());
                            ((PasswordCallback) callbacks[0]).setPassword(passwordPromptDialog.getPassword());
                        }
                    }
                }));
        ManagerFactoryParameters ksParams = new KeyStoreBuilderParameters(scBuilder);
        keyManagerFactory.init(ksParams);

        SSLContext context = SSLContext.getInstance("SSL");
        context.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{new DavGatewayX509TrustManager()}, null);
        return context;
    }

    private SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException, InvalidAlgorithmParameterException {
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
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (CertificateException e) {
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
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (CertificateException e) {
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
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (CertificateException e) {
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
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (CertificateException e) {
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

package davmail.http;

import davmail.Settings;
import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpsURL;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

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
                        new Protocol(protocol, (ProtocolSocketFactory)new DavGatewaySSLProtocolSocketFactory(), port));
            }
        } catch (MalformedURLException e) {
            DavGatewayTray.error(new BundleMessage("LOG_INVALID_URL", urlString));
        }
    }

    private SSLContext sslcontext ;

    private SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, new TrustManager[]{new DavGatewayX509TrustManager()}, null);
        return context;
    }

    private SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        if (this.sslcontext == null) {
            this.sslcontext = createSSLContext();
        }
        return this.sslcontext;
    }


    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e+" "+e.getMessage());
        }
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort, HttpConnectionParams params) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e+" "+e.getMessage());
        }
    }


    public Socket createSocket(String host, int port) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(host, port);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e+" "+e.getMessage());
        }
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        try {
            return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e+" "+e.getMessage());
        } catch (KeyStoreException e) {
            throw new IOException(e+" "+e.getMessage());
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

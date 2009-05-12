package davmail;

import davmail.exception.DavMailException;
import davmail.ui.tray.DavGatewayTray;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Generic abstract server common to SMTP and POP3 implementations
 */
public abstract class AbstractServer extends Thread {
    private final int port;
    private ServerSocket serverSocket;

    public abstract String getProtocolName();

    /**
     * Server socket TCP port
     *
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     *
     * @param name        thread name
     * @param port        tcp socket chosen port
     * @param defaultPort tcp socket default port
     */
    public AbstractServer(String name, int port, int defaultPort) {
        super(name);
        if (port == 0) {
            this.port = defaultPort;
        } else {
            this.port = port;
        }
    }

    /**
     * Bind server socket on defined port.
     *
     * @throws DavMailException unable to create server socket
     */
    public void bind() throws DavMailException {
        String bindAddress = Settings.getProperty("davmail.bindAddress");
        String keystoreFile = Settings.getProperty("davmail.ssl.keystoreFile");

        ServerSocketFactory serverSocketFactory;
        if (keystoreFile == null || keystoreFile.length() == 0) {
            serverSocketFactory = ServerSocketFactory.getDefault();
        } else {

            try {
                // keystore for keys and certificates
                // keystore and private keys should be password protected...
                KeyStore keystore = KeyStore.getInstance(Settings.getProperty("davmail.ssl.keystoreType"));
                keystore.load(new FileInputStream(keystoreFile),
                        Settings.getProperty("davmail.ssl.keystorePass").toCharArray());

                // KeyManagerFactory to create key managers
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

                // initialize KMF to work with keystore
                kmf.init(keystore, Settings.getProperty("davmail.ssl.keyPass").toCharArray());

                // SSLContext is environment for implementing JSSE...
                // create ServerSocketFactory
                SSLContext sslc = SSLContext.getInstance("SSLv3");

                // initialize sslc to work with key managers
                sslc.init(kmf.getKeyManagers(), null, null);

                // create ServerSocketFactory from sslc
                serverSocketFactory = sslc.getServerSocketFactory();
            } catch (IOException ex) {
                throw new DavMailException("LOG_EXCEPTION_CREATING_SSL_SERVER_SOCKET", getProtocolName(), port, ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } catch (GeneralSecurityException ex) {
                throw new DavMailException("LOG_EXCEPTION_CREATING_SSL_SERVER_SOCKET", getProtocolName(), port, ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
        }
        try {
            // create the server socket
            if (bindAddress == null || bindAddress.length() == 0) {
                serverSocket = serverSocketFactory.createServerSocket(port);
            } else {
                serverSocket = serverSocketFactory.createServerSocket(port, 0, Inet4Address.getByName(bindAddress));
            }
        } catch (IOException e) {
            throw new DavMailException("LOG_SOCKET_BIND_FAILED", getProtocolName(), port);
        }
    }


    /**
     * The body of the server thread.  Loop forever, listening for and
     * accepting connections from clients.  For each connection,
     * create a Connection object to handle communication through the
     * new Socket.
     */
    @Override
    public void run() {
        Socket clientSocket = null;
        AbstractConnection connection = null;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                clientSocket = serverSocket.accept();
                // set default timeout to 5 minutes
                clientSocket.setSoTimeout(300000);
                DavGatewayTray.debug(new BundleMessage("LOG_CONNECTION_FROM", clientSocket.getInetAddress(), port));
                // only accept localhost connections for security reasons
                if (Settings.getBooleanProperty("davmail.allowRemote") ||
                        clientSocket.getInetAddress().isLoopbackAddress()) {
                    connection = createConnectionHandler(clientSocket);
                    connection.start();
                } else {
                    clientSocket.close();
                    DavGatewayTray.warn(new BundleMessage("LOG_EXTERNAL_CONNECTION_REFUSED"));
                }
            }
        } catch (IOException e) {
            // do not warn if exception on socket close (gateway restart)
            if (!serverSocket.isClosed()) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_LISTENING_FOR_CONNECTIONS"), e);
            }
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_CLOSING_CLIENT_SOCKET"), e);
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    public abstract AbstractConnection createConnectionHandler(Socket clientSocket);

    public void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_CLOSING_SERVER_SOCKET"), e);
        }
    }
}

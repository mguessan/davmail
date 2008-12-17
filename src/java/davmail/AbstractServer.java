package davmail;

import davmail.tray.DavGatewayTray;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Generic abstract server common to SMTP and POP3 implementations
 */
public abstract class AbstractServer extends Thread {
    private final int port;
    private ServerSocket serverSocket;

    /**
     * Server socket TCP port
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
     * @throws java.io.IOException unable to create server socket
     */
    public AbstractServer(String name, int port, int defaultPort) throws IOException {
        super(name);
        if (port == 0) {
            this.port = defaultPort;
        } else {
            this.port = port;
        }

        String bindAddress = Settings.getProperty("davmail.bindAddress");
        //noinspection SocketOpenedButNotSafelyClosed
        if (bindAddress == null || bindAddress.length() == 0) {
            serverSocket = new ServerSocket(this.port);
        } else {
            serverSocket = new ServerSocket(this.port, 0, Inet4Address.getByName(bindAddress));
        }
    }


    /**
     * The body of the server thread.  Loop forever, listening for and
     * accepting connections from clients.  For each connection,
     * create a Connection object to handle communication through the
     * new Socket.
     */
    public void run() {
        Socket clientSocket = null;
        AbstractConnection connection = null;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                clientSocket = serverSocket.accept();
                // set default timeout to 5 minutes
                clientSocket.setSoTimeout(300000);
                DavGatewayTray.debug("Connection from " + clientSocket.getInetAddress() + " on port " + port);
                // only accept localhost connections for security reasons
                if (Settings.getBooleanProperty("davmail.allowRemote") ||
                        clientSocket.getInetAddress().toString().indexOf("127.0.0.1") > 0) {
                    connection = createConnectionHandler(clientSocket);
                    connection.start();
                } else {
                    clientSocket.close();
                    DavGatewayTray.warn("Connection from external client refused");
                }
            }
        } catch (IOException e) {
            // do not warn if exception on socket close (gateway restart)
            if (!serverSocket.isClosed()) {
                DavGatewayTray.warn("Exception while listening for connections", e);
            }
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                DavGatewayTray.warn("Exception closing client socket", e);
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
            DavGatewayTray.warn("Exception closing server socket", e);
        }
    }
}


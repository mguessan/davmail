package davmail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Generic abstract server common to SMTP and POP3 implementations
 */
public abstract class AbstractServer extends Thread {
    private int port;
    private ServerSocket serverSocket;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public AbstractServer(int port, int defaultPort) throws IOException {
        if (port == 0) {
            this.port = defaultPort;
        } else {
            this.port = port;
        }
        //noinspection SocketOpenedButNotSafelyClosed
        serverSocket = new ServerSocket(port);
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
                DavGatewayTray.debug("Connection from " + clientSocket.getInetAddress() + " on port " + port);
                // only accept localhost connections for security reasons
                if (clientSocket.getInetAddress().toString().indexOf("127.0.0.1") > 0) {
                    connection = createConnectionHandler(clientSocket);
                    connection.start();
                } else {
                    clientSocket.close();
                    DavGatewayTray.warn("Connection from external client refused");
                }
                System.gc();
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


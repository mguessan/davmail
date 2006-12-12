package davmail;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

/**
 * Generic abstract server common to SMTP and POP3 implementations
 */
public abstract class AbstractServer extends Thread {
    protected int port;
    protected String url;
    protected ServerSocket serverSocket;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public AbstractServer(String url, int port) {
        this.port = port;
        this.url = url;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            fail(e, "Exception creating server socket");
        }
    }

    // Exit with an error message, when an exception occurs.
    public static void fail(Exception e, String msg) {
        System.err.println(msg + ": " + e);
        System.exit(1);
    }

    /**
     * The body of the server thread.  Loop forever, listening for and
     * accepting connections from clients.  For each connection,
     * create a Connection object to handle communication through the
     * new Socket.
     */
    public void run() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = serverSocket.accept();
                DavGatewayTray.debug("Connection from " + clientSocket.getInetAddress() + " on port " + port);
                // only accept localhost connections for security reasons
                if (clientSocket.getInetAddress().toString().indexOf("127.0.0.1") > 0) {
                    createConnectionHandler(url, clientSocket);
                } else {
                    clientSocket.close();
                    DavGatewayTray.warn("Connection from external client refused");
                }
                System.gc();
            }
        } catch (IOException e) {
            fail(e, "Exception while listening for connections");
        }
    }

    public abstract void createConnectionHandler(String url, Socket clientSocket);
}


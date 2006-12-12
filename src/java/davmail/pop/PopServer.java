package davmail.pop;


import davmail.AbstractServer;

import java.net.Socket;

/**
 * Pop3 server
 */
public class PopServer extends AbstractServer {
    public final static int DEFAULT_PORT = 110;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public PopServer(String url, int port) {
        super(url, (port == 0) ? PopServer.DEFAULT_PORT : port);
    }

    public void createConnectionHandler(String url, Socket clientSocket) {
        new PopConnection(url, clientSocket);
    }

}

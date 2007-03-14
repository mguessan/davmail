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
     * @param port pop listen port, 110 if not defined (0)
     */
    public PopServer(int port) {
        super((port == 0) ? PopServer.DEFAULT_PORT : port);
    }

    public void createConnectionHandler(Socket clientSocket) {
        new PopConnection(clientSocket);
    }

}

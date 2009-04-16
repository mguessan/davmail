package davmail.pop;


import davmail.AbstractServer;
import davmail.AbstractConnection;

import java.net.Socket;
import java.io.IOException;

/**
 * Pop3 server
 */
public class PopServer extends AbstractServer {
    public static final int DEFAULT_PORT = 110;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     * @param port pop listen port, 110 if not defined (0)
     * @throws java.io.IOException on error
     */
    public PopServer(int port) throws IOException {
        super("PopServer", port, PopServer.DEFAULT_PORT);
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new PopConnection(clientSocket);
    }

}

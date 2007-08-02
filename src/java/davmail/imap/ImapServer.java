package davmail.imap;


import java.net.Socket;
import java.io.IOException;

import davmail.AbstractServer;
import davmail.AbstractConnection;

/**
 * Pop3 server
 */
public class ImapServer extends AbstractServer {
    public static final int DEFAULT_PORT = 143;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public ImapServer(int port) throws IOException {
        super(port, ImapServer.DEFAULT_PORT);
    }

    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new ImapConnection(clientSocket);
    }

}

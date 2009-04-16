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
     *
     * @param port imap listen port, 143 if not defined (0)
     * @throws java.io.IOException on error
     */
    public ImapServer(int port) throws IOException {
        super("ImapServer", port, ImapServer.DEFAULT_PORT);
    }

    @Override public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new ImapConnection(clientSocket);
    }

}

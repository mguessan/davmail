package davmail.imap;


import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.net.Socket;

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
     */
    public ImapServer(int port) {
        super(ImapServer.class.getName(), port, ImapServer.DEFAULT_PORT);
    }

    @Override
    public String getProtocolName() {
        return "IMAP";
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new ImapConnection(clientSocket);
    }

}

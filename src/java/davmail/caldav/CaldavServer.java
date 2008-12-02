package davmail.caldav;

import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.io.IOException;
import java.net.Socket;

/**
 * Calendar server, handle HTTP Caldav requests.
 */
public class CaldavServer extends AbstractServer {
    public static final int DEFAULT_PORT = 80;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     *
     * @param port pop listen port, 80 if not defined (0)
     * @throws java.io.IOException on error
     */
    public CaldavServer(int port) throws IOException {
        super("CaldavServer", port, CaldavServer.DEFAULT_PORT);
    }

    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new CaldavConnection(clientSocket);
    }
}
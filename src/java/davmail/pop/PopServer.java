package davmail.pop;


import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.net.Socket;

/**
 * Pop3 server
 */
public class PopServer extends AbstractServer {
    public static final int DEFAULT_PORT = 110;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     * @param port pop listen port, 110 if not defined (0)
     */
    public PopServer(int port)  {
        super(PopServer.class.getName(), port, PopServer.DEFAULT_PORT);
    }

    @Override
    public String getProtocolName() {
        return "POP";
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new PopConnection(clientSocket);
    }

}

package davmail.smtp;

import davmail.AbstractServer;
import davmail.AbstractConnection;

import java.net.Socket;
import java.io.IOException;

public class SmtpServer extends AbstractServer {
    public static final int DEFAULT_PORT = 25;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public SmtpServer(int port) throws IOException {
        super(port, SmtpServer.DEFAULT_PORT);
    }

    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new SmtpConnection(clientSocket);
    }

}

package davmail.smtp;

import davmail.AbstractServer;

import java.net.Socket;

public class SmtpServer extends AbstractServer {
    public static final int DEFAULT_PORT = 25;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public SmtpServer(int port) {
        super(port, SmtpServer.DEFAULT_PORT);
    }

    public void createConnectionHandler(Socket clientSocket) {
        new SmtpConnection(clientSocket);
    }

}

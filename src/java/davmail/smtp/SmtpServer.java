package davmail.smtp;

import java.net.Socket;

import davmail.AbstractServer;
import davmail.Settings;

public class SmtpServer extends AbstractServer {
    public final static int DEFAULT_PORT = 25;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public SmtpServer(int port) {
        super((port == 0) ? SmtpServer.DEFAULT_PORT : port);
    }

    public void createConnectionHandler(Socket clientSocket) {
        new SmtpConnection(clientSocket);
    }

}

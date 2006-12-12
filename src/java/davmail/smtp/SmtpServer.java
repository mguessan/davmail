package davmail.smtp;

import java.net.Socket;

import davmail.AbstractServer;

public class SmtpServer extends AbstractServer {
    public final static int DEFAULT_PORT = 25;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     */
    public SmtpServer(String url, int port) {
        super(url, (port == 0) ? SmtpServer.DEFAULT_PORT : port);
    }

    public void createConnectionHandler(String url, Socket clientSocket) {
        new SmtpConnection(url, clientSocket);
    }

}

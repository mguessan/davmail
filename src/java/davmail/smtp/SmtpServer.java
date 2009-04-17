package davmail.smtp;

import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.net.Socket;

public class SmtpServer extends AbstractServer {
    public static final int DEFAULT_PORT = 25;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     * @param port smtp port
     */
    public SmtpServer(int port) {
        super(SmtpServer.class.getName(), port, SmtpServer.DEFAULT_PORT);
    }

    @Override
    public String getProtocolName() {
        return "SMTP";
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new SmtpConnection(clientSocket);
    }

}

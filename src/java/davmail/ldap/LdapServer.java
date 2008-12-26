package davmail.ldap;

import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.io.IOException;
import java.net.Socket;

/**
 * LDAP server, handle LDAP directory requests.
 */
public class LdapServer extends AbstractServer {
    public static final int DEFAULT_PORT = 389;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     *
     * @param port pop listen port, 389 if not defined (0)
     * @throws java.io.IOException on error
     */
    public LdapServer(int port) throws IOException {
        super("LdapServer", port, LdapServer.DEFAULT_PORT);
    }

    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new LdapConnection(clientSocket);
    }
}
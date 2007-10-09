package davmail;

import davmail.exchange.ExchangeSession;
import davmail.tray.DavGatewayTray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Generic connection common to pop3 and smtp implementations
 */
public class AbstractConnection extends Thread {
    protected final Socket client;

    protected BufferedReader in;
    protected OutputStream os;
    // user name and password initialized through connection
    protected String userName = null;
    protected String password = null;
    // connection state
    protected int state = 0;
    // Exchange session proxy
    protected ExchangeSession session;

    // Initialize the streams and start the thread
    public AbstractConnection(Socket clientSocket) {
        this.client = clientSocket;
        try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            os = client.getOutputStream();
        } catch (IOException e) {
            close();
            DavGatewayTray.error("Exception while getting socket streams", e);
        }
    }

    public void sendClient(String message) throws IOException {
        sendClient(null, message);
    }

    public void sendClient(String prefix, String message) throws IOException {
        StringBuffer logBuffer = new StringBuffer("> ");
        if (prefix != null) {
            logBuffer.append(prefix);
            os.write(prefix.getBytes());
        }
        logBuffer.append(message);
        DavGatewayTray.debug(logBuffer.toString());
        os.write(message.getBytes());
        os.write('\r');
        os.write('\n');
        os.flush();
    }

    /**
     * Read a line from the client connection.
     * Log message to stdout
     *
     * @return command line or null
     * @throws IOException when unable to read line
     */
    public String readClient() throws IOException {
        String line = in.readLine();
        if (line != null && !line.startsWith("PASS")) {
            DavGatewayTray.debug("< " + line);
        } else {
            DavGatewayTray.debug("< PASS ********");
        }
        DavGatewayTray.switchIcon();
        return line;
    }

    /**
     * Close client connection, streams and Exchange session .
     */
    public void close() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception closing client input stream", e2);
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception closing client output stream", e2);
            }
        }
        try {
            client.close();
        } catch (IOException e2) {
            DavGatewayTray.warn("Exception closing client socket", e2);
        }
        try {
            if (session != null) {
                session.close();
            }
        } catch (IOException e3) {
            DavGatewayTray.warn("Exception closing gateway", e3);
        }
    }

}

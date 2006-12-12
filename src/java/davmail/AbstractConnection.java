package davmail;

import davmail.exchange.ExchangeSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Generic connection common to pop3 and smtp implementations
 */
public class AbstractConnection extends Thread {
    
    protected Socket client;
    protected BufferedReader in;
    protected OutputStream os;
    // exchange server url
    protected String url;
    // user name and password initialized through connection
    protected String userName = null;
    protected String password = null;
    // connection state
    protected int state = 0;
    // Exchange session proxy
    protected ExchangeSession session;

    // Initialize the streams and start the thread
    public AbstractConnection(String url, Socket clientSocket) {
        this.url = url;
        client = clientSocket;
        try {
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            os = client.getOutputStream();
        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException e2) {
                DavGatewayTray.error("Exception while getting socket streams",e2);
            }
            DavGatewayTray.error("Exception while getting socket streams", e);
            return;
        }
        // start the thread
        this.start();
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
     * @return command line or null
     * @throws IOException
     */
    public String readClient() throws IOException {
        String line = in.readLine();
        DavGatewayTray.debug("< "+line);
        DavGatewayTray.switchIcon();
        return line;
    }

}

package davmail;

import davmail.exchange.ExchangeSession;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.Socket;


/**
 * Generic connection common to pop3 and smtp implementations
 */
public class AbstractConnection extends Thread {

    protected enum State {
        INITIAL, LOGIN, USER, PASSWORD, AUTHENTICATED, STARTMAIL, RECIPIENT, MAILDATA
    }

    protected final Socket client;

    protected BufferedReader in;
    protected OutputStream os;
    // user name and password initialized through connection
    protected String userName;
    protected String password;
    // connection state
    protected State state = State.INITIAL;
    // Exchange session proxy
    protected ExchangeSession session;

    // only set the thread name and socket
    public AbstractConnection(String name, Socket clientSocket) {
        super(name);
        this.client = clientSocket;
        setDaemon(true);
    }

    // Initialize the streams and set thread name
    public AbstractConnection(String name, Socket clientSocket, String encoding) {
        super(name + '-' + clientSocket.getPort());
        this.client = clientSocket;
        try {
            if (encoding == null) {
                //noinspection IOResourceOpenedButNotSafelyClosed
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            }
            os = new BufferedOutputStream(client.getOutputStream());
        } catch (IOException e) {
            close();
            DavGatewayTray.error(new BundleMessage("LOG_EXCEPTION_GETTING_SOCKET_STREAMS"), e);
        }
    }

    /**
     * Send message to client followed by CRLF.
     *
     * @param message message
     * @throws IOException on error
     */
    public void sendClient(String message) throws IOException {
        sendClient(null, message);
    }

    /**
     * Send prefix and message to client followed by CRLF.
     *
     * @param prefix  prefix
     * @param message message
     * @throws IOException on error
     */
    public void sendClient(String prefix, String message) throws IOException {
        if (prefix != null) {
            os.write(prefix.getBytes());
            DavGatewayTray.debug(new BundleMessage("LOG_SEND_CLIENT_PREFIX_MESSAGE", prefix, message));
        } else {
            DavGatewayTray.debug(new BundleMessage("LOG_SEND_CLIENT_MESSAGE", message));
        }
        os.write(message.getBytes());
        os.write((char) 13);
        os.write((char) 10);
        os.flush();
    }

    /**
     * Send only bytes to client.
     *
     * @param messageBytes content
     * @throws IOException on error
     */
    public void sendClient(byte[] messageBytes) throws IOException {
        sendClient(messageBytes, 0, messageBytes.length);
    }

    /**
     * Send only bytes to client.
     *
     * @param messageBytes content
     * @param offset       the start offset in the data.
     * @param length       the number of bytes to write.
     * @throws IOException on error
     */
    public void sendClient(byte[] messageBytes, int offset, int length) throws IOException {
        //StringBuffer logBuffer = new StringBuffer("> ");
        //logBuffer.append(new String(messageBytes, offset, length));
        //DavGatewayTray.debug(logBuffer.toString());
        os.write(messageBytes, offset, length);
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
        if (line != null) {
            if (line.startsWith("PASS")) {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_PASS"));
                // IMAP LOGIN
            } else if (state == State.INITIAL && line.indexOf(' ') >= 0 &&
                    line.substring(line.indexOf(' ') + 1).startsWith("LOGIN")) {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_LOGIN"));
            } else if (state == State.PASSWORD) {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_PASSWORD"));
                // HTTP Basic Authentication
            } else if (line.startsWith("Authorization:")) {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_AUTHORIZATION"));
            } else if (line.startsWith("AUTH PLAIN")) {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_AUTH_PLAIN"));
            } else {
                DavGatewayTray.debug(new BundleMessage("LOG_READ_CLIENT_LINE", line));
            }
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
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_CLOSING_CLIENT_INPUT_STREAM"), e2);
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e2) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_CLOSING_CLIENT_OUTPUT_STREAM"), e2);
            }
        }
        try {
            client.close();
        } catch (IOException e2) {
            DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_CLOSING_CLIENT_SOCKET"), e2);
        }
    }

    protected String base64Encode(String value) {
        return new String(new Base64().encode(value.getBytes()));
    }

    protected String base64Decode(String value) {
        return new String(new Base64().decode(value.getBytes()));
    }
}

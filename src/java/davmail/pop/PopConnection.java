package davmail.pop;

import davmail.AbstractConnection;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Dav Gateway pop connection implementation
 */
public class PopConnection extends AbstractConnection {
    protected static final int INITIAL = 0;
    protected static final int USER = 1;
    protected static final int AUTHENTICATED = 2;
    private List<ExchangeSession.Message> messages;

    // Initialize the streams and start the thread
    public PopConnection(Socket clientSocket) {
        super("PopConnection", clientSocket, null);
    }

    protected long getTotalMessagesLength() {
        int result = 0;
        for (ExchangeSession.Message message : messages) {
            result += message.size;
        }
        return result;
    }

    protected void printCapabilities() throws IOException {
        sendClient("TOP");
        sendClient("USER");
        sendClient("UIDL");
        sendClient(".");
    }

    protected void printList() throws IOException {
        int i = 1;
        for (ExchangeSession.Message message : messages) {
            sendClient(i++ + " " + message.size);
        }
        sendClient(".");
    }

    protected void printUidList() throws IOException {
        int i = 1;
        for (ExchangeSession.Message message : messages) {
            sendClient(i++ + " " + message.uid);
        }
        sendClient(".");
    }


    public void run() {
        String line;
        StringTokenizer tokens;

        try {
            ExchangeSessionFactory.checkConfig();
            sendOK("DavMail POP ready at " + new Date());

            for (; ;) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }

                tokens = new StringTokenizer(line);
                if (tokens.hasMoreTokens()) {
                    String command = tokens.nextToken();

                    if ("QUIT".equalsIgnoreCase(command)) {
                        // delete messages before quit
                        if (session != null) {
                            session.purgeOldestTrashAndSentMessages();
                        }
                        sendOK("Bye");
                        break;
                    } else if ("USER".equalsIgnoreCase(command)) {
                        userName = null;
                        password = null;
                        session = null;
                        if (tokens.hasMoreTokens()) {
                            userName = tokens.nextToken();
                            sendOK("USER : " + userName);
                            state = USER;
                        } else {
                            sendERR("invalid syntax");
                            state = INITIAL;
                        }
                    } else if ("PASS".equalsIgnoreCase(command)) {
                        if (state != USER) {
                            sendERR("invalid state");
                            state = INITIAL;
                        } else if (!tokens.hasMoreTokens()) {
                            sendERR("invalid syntax");
                        } else {
                            // bug 2194492 : allow space in password
                            password = line.substring("PASS".length()+1);
                            try {
                                session = ExchangeSessionFactory.getInstance(userName, password);
                                messages = session.getAllMessages();
                                sendOK("PASS");
                                state = AUTHENTICATED;
                            } catch (SocketException e) {
                                // can not send error to client after a socket exception
                                DavGatewayTray.warn("Client closed connection ", e);
                            } catch (Exception e) {
                                DavGatewayTray.error(e);
                                sendERR(e);
                            }
                        }
                    } else if ("CAPA".equalsIgnoreCase(command)) {
                        sendOK("Capability list follows");
                        printCapabilities();
                    } else if (state != AUTHENTICATED) {
                        sendERR("Invalid state not authenticated");
                    } else {
                        if ("STAT".equalsIgnoreCase(command)) {
                            sendOK(messages.size() + " " +
                                    getTotalMessagesLength());
                        } else if ("LIST".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                String token = tokens.nextToken();
                                try {
                                    int messageNumber = Integer.valueOf(token);
                                    ExchangeSession.Message message = messages.get(messageNumber - 1);
                                    sendOK("" + messageNumber + " " + message.size);
                                } catch (NumberFormatException e) {
                                    sendERR("Invalid message index: " + token);
                                } catch (IndexOutOfBoundsException e) {
                                    sendERR("Invalid message index: " + token);
                                }
                            } else {
                                sendOK(messages.size() +
                                        " messages (" + getTotalMessagesLength() +
                                        " octets)");
                                printList();
                            }
                        } else if ("UIDL".equalsIgnoreCase(command)) {
                            sendOK(messages.size() +
                                    " messages (" + getTotalMessagesLength() +
                                    " octets)");
                            printUidList();
                        } else if ("RETR".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                try {
                                    int messageNumber = Integer.valueOf(tokens.
                                            nextToken()) - 1;
                                    sendOK("");
                                    messages.get(messageNumber).write(os);
                                    sendClient("");
                                    sendClient(".");
                                } catch (SocketException e) {
                                    // can not send error to client after a socket exception
                                    DavGatewayTray.warn("Client closed connection ", e);
                                } catch (Exception e) {
                                    DavGatewayTray.error("Error retreiving message", e);
                                    sendERR("error retreiving message " + e + " " + e.getMessage());
                                }
                            } else {
                                sendERR("invalid message number");
                            }
                        } else if ("DELE".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                try {
                                    int messageNumber = Integer.valueOf(tokens.
                                            nextToken()) - 1;
                                    messages.get(messageNumber).delete();
                                    sendOK("DELETE");
                                } catch (SocketException e) {
                                    // can not send error to client after a socket exception
                                    DavGatewayTray.warn("Client closed connection ", e);
                                } catch (Exception e) {
                                    DavGatewayTray.error("Error deleting message", e);
                                    sendERR("error deleting message");
                                }
                            } else {
                                sendERR("invalid message number");
                            }
                        } else if ("TOP".equalsIgnoreCase(command)) {
                            int message = 0;
                            try {
                                message = Integer.valueOf(tokens.nextToken());
                                int lines = Integer.valueOf(tokens.nextToken());
                                ExchangeSession.Message m = messages.get(message - 1);
                                sendOK("");
                                m.write(new TopOutputStream(os, lines));
                                sendClient("");
                                sendClient(".");
                            } catch (SocketException e) {
                                // can not send error to client after a socket exception
                                DavGatewayTray.warn("Client closed connection ", e);
                            } catch (IndexOutOfBoundsException e) {
                                sendERR("Invalid message index: " + message);
                            } catch (Exception e) {
                                sendERR("error retreiving top of messages");
                                DavGatewayTray.error(e.getMessage(), e);
                            }
                        } else if ("RSET".equalsIgnoreCase(command)) {
                            sendOK("RSET");
                        } else {
                            sendERR("unknown command");
                        }
                    }

                } else {
                    sendERR("unknown command");
                }

                os.flush();
            }
        } catch (IOException e) {
            DavGatewayTray.error(e);
            try {
                sendERR(e.getMessage());
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    protected void sendOK(String message) throws IOException {
        sendClient("+OK ", message);
    }

    protected void sendERR(Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendERR(message);
    }

    protected void sendERR(String message) throws IOException {
        sendClient("-ERR ", message.replaceAll("\\n", " "));
    }

    /**
     * Filter to limit output lines to max body lines after header
     */
    private static class TopOutputStream extends FilterOutputStream {
        protected static final int START = 0;
        protected static final int CR = 1;
        protected static final int CRLF = 2;
        protected static final int CRLFCR = 3;
        protected static final int BODY = 4;

        protected int maxLines;
        protected int STATE = START;

        public TopOutputStream(OutputStream os, int maxLines) {
            super(os);
            this.maxLines = maxLines;
        }

        @Override
        public void write(int b) throws IOException {
            if (STATE != BODY || maxLines > 0) {
                super.write(b);
            }
            if (STATE == BODY) {
                if (b == '\n') {
                    maxLines--;
                }
            } else if (STATE == START) {
                if (b == '\r') {
                    STATE = CR;
                }
            } else if (STATE == CR) {
                if (b == '\n') {
                    STATE = CRLF;
                } else {
                    STATE = START;
                }
            } else if (STATE == CRLF) {
                if (b == '\r') {
                    STATE = CRLFCR;
                } else {
                    STATE = START;
                }
            } else if (STATE == CRLFCR) {
                if (b == '\n') {
                    STATE = BODY;
                } else {
                    STATE = START;
                }
            }
        }
    }

}

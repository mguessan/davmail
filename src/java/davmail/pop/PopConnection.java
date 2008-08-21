package davmail.pop;

import davmail.AbstractConnection;
import davmail.exchange.ExchangeSession;
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
        super(clientSocket);
    }

    public long getTotalMessagesLength() {
        int result = 0;
        for (ExchangeSession.Message message : messages) {
            result += message.size;
        }
        return result;
    }

    public void printCapabilities() throws IOException {
        sendClient("TOP");
        sendClient("USER");
        sendClient("UIDL");
        sendClient(".");
    }

    public void printList() throws IOException {
        int i = 1;
        for (ExchangeSession.Message message : messages) {
            sendClient(i++ + " " + message.size);
        }
        sendClient(".");
    }

    public void printUidList() throws IOException {
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
            ExchangeSession.checkConfig();
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
                            session.purgeOldestTrashMessages();
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
                            password = tokens.nextToken();
                            try {
                                session = new ExchangeSession();
                                session.login(userName, password);
                                messages = session.getAllMessages();
                                sendOK("PASS");
                                state = AUTHENTICATED;
                            } catch (SocketException e) {
                                // can not send error to client after a socket exception
                                DavGatewayTray.warn("Client closed connection ", e);
                            } catch (Exception e) {
                                String message = e.getMessage();
                                if (message == null) {
                                    message = e.toString();
                                }
                                DavGatewayTray.error(message);
                                message = message.replaceAll("\\n", " ");
                                sendERR("authentication failed : " + message);
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
                                    ExchangeSession.Message message = messages.get(messageNumber-1);
                                    sendOK(""+messageNumber+" "+message.size);
                                } catch (NumberFormatException e) {
                                    sendERR("Invalid message index: "+token);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    sendERR("Invalid message index: "+token);
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
                            try {
                                int message = Integer.valueOf(tokens.nextToken()) - 1;
                                int lines = Integer.valueOf(tokens.nextToken());
                                ExchangeSession.Message m = messages.get(message);
                                sendOK("");
                                m.write(new TopOutputStream(os, lines));
                                sendClient("");
                                sendClient(".");
                            } catch (SocketException e) {
                                // can not send error to client after a socket exception
                                DavGatewayTray.warn("Client closed connection ", e);
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
            DavGatewayTray.error(e.getMessage());
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

    public void sendOK(String message) throws IOException {
        sendClient("+OK ", message);
    }

    public void sendERR(String message) throws IOException {
        sendClient("-ERR ", message);
    }

    /**
     * Filter to limit output lines to maxLines
     */
    private class TopOutputStream extends FilterOutputStream {

        private int maxLines;

        public TopOutputStream(OutputStream os, int maxLines) {
            super(os);
            this.maxLines = maxLines;
        }

        @Override
        public void write(int b) throws IOException {
            if (maxLines > 0) {
                super.write(b);
            }

            if (b == '\n') {
                maxLines--;
            }
        }
    }

}

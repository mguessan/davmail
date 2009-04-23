package davmail.pop;

import davmail.AbstractConnection;
import davmail.BundleMessage;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.ui.tray.DavGatewayTray;

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
    private List<ExchangeSession.Message> messages;

    // Initialize the streams and start the thread
    public PopConnection(Socket clientSocket) {
        super(PopConnection.class.getName(), clientSocket, null);
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


    @Override
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
                            state = State.USER;
                        } else {
                            sendERR("invalid syntax");
                            state = State.INITIAL;
                        }
                    } else if ("PASS".equalsIgnoreCase(command)) {
                        if (state != State.USER) {
                            sendERR("invalid state");
                            state = State.INITIAL;
                        } else if (!tokens.hasMoreTokens()) {
                            sendERR("invalid syntax");
                        } else {
                            // bug 2194492 : allow space in password
                            password = line.substring("PASS".length() + 1);
                            try {
                                session = ExchangeSessionFactory.getInstance(userName, password);
                                messages = session.getAllMessages("INBOX");
                                sendOK("PASS");
                                state = State.AUTHENTICATED;
                            } catch (SocketException e) {
                                // can not send error to client after a socket exception
                                DavGatewayTray.warn(new BundleMessage("LOG_CLIENT_CLOSED_CONNECTION"), e);
                            } catch (Exception e) {
                                DavGatewayTray.error(e);
                                sendERR(e);
                            }
                        }
                    } else if ("CAPA".equalsIgnoreCase(command)) {
                        sendOK("Capability list follows");
                        printCapabilities();
                    } else if (state != State.AUTHENTICATED) {
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
                                    sendOK("" + messageNumber + ' ' + message.size);
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
                            if (tokens.hasMoreTokens()) {
                                String token = tokens.nextToken();
                                try {
                                    int messageNumber = Integer.valueOf(token);
                                    sendOK(messageNumber + " " + messages.get(messageNumber - 1).uid);
                                } catch (NumberFormatException e) {
                                    sendERR("Invalid message index: " + token);
                                } catch (IndexOutOfBoundsException e) {
                                    sendERR("Invalid message index: " + token);
                                }
                            } else {
                                sendOK(messages.size() +
                                        " messages (" + getTotalMessagesLength() +
                                        " octets)");
                                printUidList();
                            }
                        } else if ("RETR".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                try {
                                    int messageNumber = Integer.valueOf(tokens.nextToken()) - 1;
                                    sendOK("");
                                    messages.get(messageNumber).write(os);
                                    sendClient("");
                                    sendClient(".");
                                } catch (SocketException e) {
                                    // can not send error to client after a socket exception
                                    DavGatewayTray.warn(new BundleMessage("LOG_CLIENT_CLOSED_CONNECTION"), e);
                                } catch (Exception e) {
                                    DavGatewayTray.error(new BundleMessage("LOG_ERROR_RETRIEVING_MESSAGE"), e);
                                    sendERR("error retreiving message " + e + ' ' + e.getMessage());
                                }
                            } else {
                                sendERR("invalid message index");
                            }
                        } else if ("DELE".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                ExchangeSession.Message message;
                                try {
                                    int messageNumber = Integer.valueOf(tokens.
                                            nextToken()) - 1;
                                    message = messages.get(messageNumber);
                                    message.moveToTrash();
                                    sendOK("DELETE");
                                } catch (NumberFormatException e) {
                                    sendERR("invalid message index");
                                } catch (IndexOutOfBoundsException e) {
                                    sendERR("invalid message index");
                                }
                            } else {
                                sendERR("invalid message index");
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
                            } catch (NumberFormatException e) {
                                sendERR("invalid command");
                            } catch (IndexOutOfBoundsException e) {
                                sendERR("invalid message index: " + message);
                            } catch (Exception e) {
                                sendERR("error retreiving top of messages");
                                DavGatewayTray.error(e);
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
        } catch (SocketException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_CONNECTION_CLOSED"));
        } catch (Exception e) {
            DavGatewayTray.error(e);
            try {
                sendERR(e.getMessage());
            } catch (IOException e2) {
                DavGatewayTray.debug(new BundleMessage("LOG_EXCEPTION_SENDING_ERROR_TO_CLIENT"), e2);
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
        protected int state = START;

        private TopOutputStream(OutputStream os, int maxLines) {
            super(os);
            this.maxLines = maxLines;
        }

        @Override
        public void write(int b) throws IOException {
            if (state != BODY || maxLines > 0) {
                super.write(b);
            }
            if (state == BODY) {
                if (b == '\n') {
                    maxLines--;
                }
            } else if (state == START) {
                if (b == '\r') {
                    state = CR;
                }
            } else if (state == CR) {
                if (b == '\n') {
                    state = CRLF;
                } else {
                    state = START;
                }
            } else if (state == CRLF) {
                if (b == '\r') {
                    state = CRLFCR;
                } else {
                    state = START;
                }
            } else if (state == CRLFCR) {
                if (b == '\n') {
                    state = BODY;
                } else {
                    state = START;
                }
            }
        }
    }

}

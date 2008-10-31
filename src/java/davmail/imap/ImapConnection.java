package davmail.imap;

import java.net.Socket;
import java.util.StringTokenizer;
import java.util.List;
import java.io.IOException;

import davmail.AbstractConnection;
import davmail.tray.DavGatewayTray;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;

/**
 * Dav Gateway smtp connection implementation.
 * Still alpha code : need to find a way to handle message ids
 */
public class ImapConnection extends AbstractConnection {
    protected static final int INITIAL = 0;
    protected static final int AUTHENTICATED = 1;

    // Initialize the streams and start the thread
    public ImapConnection(Socket clientSocket) {
        super(clientSocket);
    }

    public void run() {
        String line;
        StringTokenizer tokens;
        try {
            sendClient("* OK Davmail Imap Server ready");
            for (; ;) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }

                tokens = new StringTokenizer(line);
                if (tokens.hasMoreTokens()) {
                    String commandId = tokens.nextToken();
                    if (tokens.hasMoreTokens()) {
                        String command = tokens.nextToken();

                        if ("LOGOUT".equalsIgnoreCase(command)) {
                            sendClient("* BYE Closing connection");
                            sendClient(commandId + " OK Completed");
                            break;
                        }
                        if ("capability".equalsIgnoreCase(command)) {
                            sendClient("* CAPABILITY IMAP4REV1");
                            sendClient(commandId + " OK CAPABILITY completed");
                        } else if ("login".equalsIgnoreCase(command)) {
                            parseCredentials(tokens);
                            try {
                                session = ExchangeSessionFactory.getInstance(userName, password);
                                sendClient(commandId + " OK Authenticated");
                                state = AUTHENTICATED;
                            } catch (Exception e) {
                                DavGatewayTray.error(e.getMessage());
                                sendClient(commandId + " NO LOGIN failed");
                                state = INITIAL;
                            }
                        } else {
                            if (state != AUTHENTICATED) {
                                sendClient(commandId + " BAD command authentication required");
                            } else {
                                if ("lsub".equalsIgnoreCase(command)) {
                                    /* TODO : implement
                                    2 lsub "" "*"
* LSUB () "/" INBOX/sent-mail
* LSUB () "/" Trash
* LSUB () "/" INBOX/spam
* LSUB () "/" Envoy&AOk-s
* LSUB () "/" Drafts
2 OK LSUB completed
                                    */
                                    sendClient(commandId + " OK LSUB completed");
                                } else if ("list".equalsIgnoreCase(command)) {
                                    /* TODO : implement
                                    */
                                    sendClient(commandId + " OK LIST completed");
                                } else if ("select".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = removeQuotes(tokens.nextToken());
                                        ExchangeSession.Folder folder = session.selectFolder(folderName);
                                        sendClient("* " + folder.childCount + " EXISTS");
                                        sendClient("* " + folder.unreadCount + " RECENT");
                                        // TODO : implement, compute session message ids
                                        //sendClient("* [UNSEEN 1] first unseen message in inbox");
                                        sendClient(commandId + " OK [READ-WRITE] SELECT completed");
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("uid".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens() && "fetch".equalsIgnoreCase(tokens.nextToken())) {
                                        if (tokens.hasMoreTokens()) {
                                            String parameter = tokens.nextToken();
                                            if ("1:*".equals(parameter)) {
                                                List<ExchangeSession.Message> messages = session.getAllMessages();
                                                for (ExchangeSession.Message message : messages) {
                                                    sendClient("* FETCH (UID " + message.uid + " FLAGS ())");
                                                }
                                                sendClient(commandId + " OK UID FETCH completed");
                                            } else {
                                                sendClient(commandId + " BAD command unrecognized");
                                            }
                                        } else {
                                            sendClient(commandId + " BAD command unrecognized");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else {
                                    sendClient(commandId + " BAD command unrecognized");
                                }
                            }
                        }

                    } else {
                        sendClient(commandId + " BAD missing command");
                    }
                } else {
                    sendClient("BAD Null command");
                }
            }


            os.flush();
        } catch (IOException e) {
            DavGatewayTray.error("Exception handling client", e);
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    /**
     * Decode SMTP credentials
     */
    protected void parseCredentials(StringTokenizer tokens) throws IOException {
        if (tokens.hasMoreTokens()) {
            userName = removeQuotes(tokens.nextToken());
        } else {
            throw new IOException("Invalid credentials");
        }

        if (tokens.hasMoreTokens()) {
            password = removeQuotes(tokens.nextToken());
        } else {
            throw new IOException("Invalid credentials");
        }
        int backslashindex = userName.indexOf("\\");
        if (backslashindex > 0) {
            userName = userName.substring(0, backslashindex) + userName.substring(backslashindex + 1);
        }
    }

    protected String removeQuotes(String value) {
        String result = value;
        if (result.startsWith("\"")) {
            result = result.substring(1);
        }
        if (result.endsWith("\"")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public void sendMessage(StringBuffer buffer) {
        // TODO implement
    }
}


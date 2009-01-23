package davmail.imap;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.util.StringTokenizer;
import java.util.List;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

import davmail.AbstractConnection;
import davmail.tray.DavGatewayTray;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import com.sun.mail.imap.protocol.BASE64MailboxEncoder;
import com.sun.mail.imap.protocol.BASE64MailboxDecoder;

/**
 * Dav Gateway smtp connection implementation.
 * Still alpha code : need to find a way to handle message ids
 */
public class ImapConnection extends AbstractConnection {
    protected static final int INITIAL = 0;
    protected static final int AUTHENTICATED = 1;

    ExchangeSession.Folder currentFolder;
    List<ExchangeSession.Message> messages;

    // Initialize the streams and start the thread
    public ImapConnection(Socket clientSocket) {
        super("ImapConnection", clientSocket, null);
    }

    public void run() {
        String line;
        StringTokenizer tokens;
        try {
            sendClient("* OK [CAPABILITY IMAP4REV1 AUTH=LOGIN] IMAP4rev1 DavMail server ready");
            for (; ;) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }

                tokens = new StringTokenizer(line) {
                    public String nextToken() {
                        StringBuilder nextToken = new StringBuilder();
                        nextToken.append(super.nextToken());
                        while (hasMoreTokens() && nextToken.length() > 0 && nextToken.charAt(0) == '"'
                                && nextToken.charAt(nextToken.length() - 1) != '"') {
                            nextToken.append(' ').append(super.nextToken());
                        }
                        while (hasMoreTokens() && nextToken.length() > 0 && nextToken.charAt(0) == '('
                                && nextToken.charAt(nextToken.length() - 1) != ')') {
                            nextToken.append(' ').append(super.nextToken());
                        }
                        return nextToken.toString();
                    }
                };
                if (tokens.hasMoreTokens()) {
                    String commandId = tokens.nextToken();
                    if (tokens.hasMoreTokens()) {
                        String command = tokens.nextToken();

                        if ("LOGOUT".equalsIgnoreCase(command)) {
                            sendClient("* BYE Closing connection");
                            break;
                        }
                        if ("capability".equalsIgnoreCase(command)) {
                            sendClient("* CAPABILITY IMAP4REV1 AUTH=LOGIN");
                            sendClient(commandId + " OK CAPABILITY completed");
                        } else if ("login".equalsIgnoreCase(command)) {
                            parseCredentials(tokens);
                            try {
                                session = ExchangeSessionFactory.getInstance(userName, password);
                                sendClient(commandId + " OK Authenticated");
                                state = AUTHENTICATED;
                            } catch (Exception e) {
                                DavGatewayTray.error(e);
                                sendClient(commandId + " NO LOGIN failed");
                                state = INITIAL;
                            }
                        } else if ("AUTHENTICATE".equalsIgnoreCase(command)) {
                            if (tokens.hasMoreTokens()) {
                                String authenticationMethod = tokens.nextToken();
                                if ("LOGIN".equalsIgnoreCase(authenticationMethod)) {
                                    sendClient("+ " + base64Encode("Username:"));
                                    userName = base64Decode(readClient());
                                    sendClient("+ " + base64Encode("Password:"));
                                    password = base64Decode(readClient());
                                    try {
                                        session = ExchangeSessionFactory.getInstance(userName, password);
                                        sendClient(commandId + " OK Authenticated");
                                        state = AUTHENTICATED;
                                    } catch (Exception e) {
                                        DavGatewayTray.error(e);
                                        sendClient(commandId + " NO LOGIN failed");
                                        state = INITIAL;
                                    }
                                } else {
                                    sendClient(commandId + " NO unsupported authentication method");
                                }
                            } else {
                                sendClient(commandId + " BAD authentication method required");
                            }
                        } else {
                            if (state != AUTHENTICATED) {
                                sendClient(commandId + " BAD command authentication required");
                            } else {
                                if ("lsub".equalsIgnoreCase(command) || "list".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderContext = BASE64MailboxDecoder.decode(removeQuotes(tokens.nextToken()));
                                        if (tokens.hasMoreTokens()) {
                                            String folderQuery = folderContext + BASE64MailboxDecoder.decode(removeQuotes(tokens.nextToken()));
                                            if (folderQuery.endsWith("%/%")) {
                                                folderQuery = folderQuery.substring(0, folderQuery.length() - 2);
                                            }
                                            if (folderQuery.endsWith("%") || folderQuery.endsWith("*")) {
                                                boolean recursive = folderQuery.endsWith("*");
                                                List<ExchangeSession.Folder> folders = session.getSubFolders(folderQuery.substring(0, folderQuery.length() - 1), recursive);
                                                for (ExchangeSession.Folder folder : folders) {
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderUrl) + "\"");
                                                }
                                                sendClient(commandId + " OK " + command + " completed");
                                            } else {
                                                ExchangeSession.Folder folder = session.getFolder(folderQuery);
                                                if (folder != null) {
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderUrl) + "\"");
                                                    sendClient(commandId + " OK " + command + " completed");
                                                } else {
                                                    sendClient(commandId + " NO Folder not found");
                                                }
                                            }
                                        } else {
                                            sendClient(commandId + " BAD missing folder argument");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD missing folder argument");
                                    }
                                } else if ("select".equalsIgnoreCase(command) || "examine".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = BASE64MailboxDecoder.decode(removeQuotes(tokens.nextToken()));
                                        currentFolder = session.getFolder(folderName);
                                        messages = session.getAllMessages(currentFolder.folderUrl);
                                        sendClient("* " + currentFolder.objectCount + " EXISTS");
                                        sendClient("* " + currentFolder.objectCount + " RECENT");
                                        sendClient("* OK [UIDVALIDITY " + currentFolder.lastModified + "]");
                                        sendClient("* OK [UIDNEXT " + (currentFolder.objectCount + 1) + "]");
                                        sendClient("* FLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded $MDNSent Forwarded $Junk $NotJunk Junk JunkRecorded NonJunk NotJunk)");
                                        sendClient("* OK [PERMANENTFLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded $MDNSent Forwarded \\*)] junk-related flags are not permanent");
                                        //sendClient("* [UNSEEN 1] first unseen message in inbox");
                                        sendClient(commandId + " OK [READ-WRITE] " + command + " completed");
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("close".equalsIgnoreCase(command)) {
                                    currentFolder = null;
                                    messages = null;
                                    sendClient(commandId + " OK CLOSE unrecognized");
                                } else if ("create".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = BASE64MailboxDecoder.decode(removeQuotes(tokens.nextToken()));
                                        if (session.getFolder(folderName) != null) {
                                            sendClient(commandId + " OK folder already exists");
                                        } else {
                                            // TODO
                                            sendClient(commandId + " NO unsupported");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD missing create argument");
                                    }
                                } else if ("uid".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens() && "fetch".equalsIgnoreCase(tokens.nextToken())) {
                                        if (tokens.hasMoreTokens()) {
                                            String messageParameter = tokens.nextToken();
                                            if (currentFolder == null) {
                                                sendClient(commandId + " NO no folder selected");
                                            }
                                            int startIndex;
                                            int endIndex;
                                            int colonIndex = messageParameter.indexOf(":");
                                            if (colonIndex < 0) {
                                                startIndex = endIndex = Integer.parseInt(messageParameter);
                                            } else {
                                                startIndex = Integer.parseInt(messageParameter.substring(0, colonIndex));
                                                if (messageParameter.endsWith("*")) {
                                                    endIndex = messages.size();
                                                } else {
                                                    endIndex = Integer.parseInt(messageParameter.substring(colonIndex + 1));
                                                }
                                            }
                                            if ("1:*".equals(messageParameter)) {
                                                int count = 0;
                                                for (ExchangeSession.Message message : messages) {
                                                    count++;
                                                    sendClient("* " + count + " FETCH (UID " + count + " FLAGS (\\Seen))");
                                                }
                                                sendClient(commandId + " OK UID FETCH completed");
                                            } else {
                                                if (tokens.hasMoreTokens()) {
                                                    String parameters = tokens.nextToken();
                                                    for (int messageIndex = startIndex; messageIndex <= endIndex; messageIndex++) {
                                                        ExchangeSession.Message message = messages.get(messageIndex - 1);

                                                        if ("(BODYSTRUCTURE)".equals(parameters)) {
                                                            sendClient("* " + messageIndex + " FETCH (BODYSTRUCTURE (\"TEXT\" \"PLAIN\" (\"CHARSET\" \"windows-1252\") NIL NIL \"QUOTED-PRINTABLE\" " + message.size + " 50 NIL NIL NIL NIL))");
                                                        } else if (parameters.indexOf("BODY[]") >= 0) {
                                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                            message.write(baos);
                                                            baos.close();

                                                            sendClient("* " + messageIndex + " FETCH (UID " + messageIndex + " RFC822.SIZE " + baos.size() + " BODY[]<0>" +
                                                                    " {" + baos.size() + "}");
                                                            message.write(os);
                                                            sendClient(")");
                                                        } else {
                                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                            message.write(baos);
                                                            baos.close();
                                                            sendClient("* " + messageIndex + " FETCH (UID " + messageIndex + " RFC822.SIZE " + baos.size() + " BODY[HEADER.FIELDS (FROM TO CC SUBJECT DATE MESSAGE-ID PRIORITY X-PRIORITY REFERENCES NEWSGROUPS IN-REPLY-TO CONTENT-TYPE)" +
                                                                    "] {" + baos.size() + "}");
                                                            message.write(os);
                                                            sendClient(" FLAGS (\\Seen))");
                                                        }
                                                    }
                                                    sendClient(commandId + " OK FETCH completed");

                                                }
                                            }
                                        } else {
                                            sendClient(commandId + " BAD command unrecognized");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("fetch".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        int messageIndex = Integer.parseInt(tokens.nextToken());
                                        ExchangeSession.Message message = messages.get(messageIndex - 1);
                                        if (tokens.hasMoreTokens()) {
                                            String parameters = tokens.nextToken();
                                            if ("(BODYSTRUCTURE)".equals(parameters)) {
                                                sendClient("* " + messageIndex + " FETCH (BODYSTRUCTURE (\"TEXT\" \"PLAIN\" (\"CHARSET\" \"windows-1252\") NIL NIL \"QUOTED-PRINTABLE\" " + message.size + " 50 NIL NIL NIL NIL))");
                                                sendClient(commandId + " OK FETCH completed");
                                            } else {
                                                sendClient("* " + messageIndex + " 1 FETCH (BODY[TEXT]<0> {" + message.size + "}");
                                                message.write(os);
                                                sendClient(commandId + " OK FETCH completed");
                                            }
                                        }
                                    }
                                } else if ("noop".equalsIgnoreCase(command)) {
                                    sendClient(commandId + " OK NOOP completed");
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
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug("Closing connection on timeout");
            try {
                sendClient("* BYE Closing connection");
            } catch (IOException e1) {
                DavGatewayTray.debug("Exception closing connection on timeout");
            }
        } catch (IOException e) {
            DavGatewayTray.error("Exception handling client", e);
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    /**
     * Decode IMAP credentials
     *
     * @param tokens tokens
     * @throws java.io.IOException on error
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

}


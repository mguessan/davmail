package davmail.imap;

import com.sun.mail.imap.protocol.BASE64MailboxDecoder;
import com.sun.mail.imap.protocol.BASE64MailboxEncoder;
import davmail.AbstractConnection;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;

/**
 * Dav Gateway smtp connection implementation.
 * Still alpha code : need to find a way to handle message ids
 */
public class ImapConnection extends AbstractConnection {
    protected static final int INITIAL = 0;
    protected static final int AUTHENTICATED = 2;

    ExchangeSession.Folder currentFolder;
    ExchangeSession.MessageList messages;

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
                        return removeQuotes(nextToken.toString());
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
                                state = State.AUTHENTICATED;
                            } catch (Exception e) {
                                DavGatewayTray.error(e);
                                sendClient(commandId + " NO LOGIN failed");
                                state = State.INITIAL;
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
                                        state = State.AUTHENTICATED;
                                    } catch (Exception e) {
                                        DavGatewayTray.error(e);
                                        sendClient(commandId + " NO LOGIN failed");
                                        state = State.INITIAL;
                                    }
                                } else {
                                    sendClient(commandId + " NO unsupported authentication method");
                                }
                            } else {
                                sendClient(commandId + " BAD authentication method required");
                            }
                        } else {
                            if (state != State.AUTHENTICATED) {
                                sendClient(commandId + " BAD command authentication required");
                            } else {
                                if ("lsub".equalsIgnoreCase(command) || "list".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderContext = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        if (tokens.hasMoreTokens()) {
                                            String folderQuery = folderContext + BASE64MailboxDecoder.decode(tokens.nextToken());
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
                                    // first purge previous folder
                                    expunge();
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        currentFolder = session.getFolder(folderName);
                                        messages = session.getAllMessages(currentFolder.folderUrl);
                                        sendClient("* " + currentFolder.objectCount + " EXISTS");
                                        sendClient("* " + currentFolder.objectCount + " RECENT");
                                        sendClient("* OK [UIDVALIDITY 1]");
                                        if (messages.size() == 0) {
                                            sendClient("* OK [UIDNEXT " + 1 + "]");
                                        } else {
                                            sendClient("* OK [UIDNEXT " + (messages.get(messages.size() - 1).getUidAsLong() + 1) + "]");
                                        }
                                        sendClient("* FLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Forwarded Junk)");
                                        sendClient("* OK [PERMANENTFLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Forwarded Junk \\*)]");
                                        //sendClient("* [UNSEEN 1] first unseen message in inbox");
                                        sendClient(commandId + " OK [READ-WRITE] " + command + " completed");
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("close".equalsIgnoreCase(command)) {
                                    currentFolder = null;
                                    messages = null;
                                    sendClient(commandId + " OK CLOSE completed");
                                } else if ("create".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        session.createFolder(folderName);
                                        sendClient(commandId + " OK folder created");
                                    } else {
                                        sendClient(commandId + " BAD missing create argument");
                                    }
                                } else if ("rename".equalsIgnoreCase(command)) {
                                    String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    String targetName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    try {
                                        session.moveFolder(folderName, targetName);
                                        sendClient(commandId + " OK rename completed");
                                    } catch (HttpException e) {
                                        sendClient(commandId + " NO " + e.getReason());
                                    }
                                } else if ("delete".equalsIgnoreCase(command)) {
                                    String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    try {
                                        session.deleteFolder(folderName);
                                        sendClient(commandId + " OK delete completed");
                                    } catch (HttpException e) {
                                        sendClient(commandId + " NO " + e.getReason());
                                    }
                                } else if ("uid".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String subcommand = tokens.nextToken();
                                        if ("fetch".equalsIgnoreCase(subcommand)) {
                                            if (tokens.hasMoreTokens()) {
                                                String messageParameter = tokens.nextToken();
                                                if (currentFolder == null) {
                                                    sendClient(commandId + " NO no folder selected");
                                                }
                                                long startIndex;
                                                long endIndex;
                                                int colonIndex = messageParameter.lastIndexOf(":");
                                                if (colonIndex < 0) {
                                                    startIndex = endIndex = Long.parseLong(messageParameter);
                                                } else {
                                                    int commaIndex = messageParameter.indexOf(",");
                                                    if (commaIndex > 0) {
                                                        // workaround for multiple scopes : start at first and end at last
                                                        startIndex = Long.parseLong(messageParameter.substring(0, Math.min(commaIndex, messageParameter.indexOf(":"))));
                                                    } else {
                                                        startIndex = Long.parseLong(messageParameter.substring(0, colonIndex));
                                                    }
                                                    if (messageParameter.endsWith("*")) {
                                                        if (messages.size() > 0) {
                                                            endIndex = messages.get(messages.size() - 1).getUidAsLong();
                                                            // fix according to spec
                                                            //if (startIndex > endIndex) {
                                                            //    startIndex = endIndex;
                                                            //}
                                                        } else {
                                                            endIndex = 1;
                                                        }
                                                    } else {
                                                        endIndex = Long.parseLong(messageParameter.substring(Math.max(colonIndex, messageParameter.lastIndexOf(",") + 1)));
                                                    }
                                                }
                                                if ("1:*".equals(messageParameter)) {
                                                    int count = 0;
                                                    for (ExchangeSession.Message message : messages) {
                                                        count++;
                                                        sendClient("* " + count + " FETCH (UID " + message.getUidAsLong() + " FLAGS (" + (message.getImapFlags()) + "))");
                                                    }
                                                    sendClient(commandId + " OK UID FETCH completed");
                                                } else {
                                                    if (tokens.hasMoreTokens()) {
                                                        String parameters = tokens.nextToken();
                                                        for (int messageIndex = 1; messageIndex <= messages.size(); messageIndex++) {
                                                            ExchangeSession.Message message = messages.get(messageIndex - 1);
                                                            long uid = message.getUidAsLong();
                                                            if (uid >= startIndex && uid <= endIndex) {

                                                                if ("BODYSTRUCTURE".equals(parameters)) {
                                                                    sendClient("* " + messageIndex + " FETCH (BODYSTRUCTURE (\"TEXT\" \"PLAIN\" (\"CHARSET\" \"windows-1252\") NIL NIL \"QUOTED-PRINTABLE\" " + message.size + " 50 NIL NIL NIL NIL))");
                                                                } else if (parameters.indexOf("BODY[]") >= 0) {
                                                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                                    message.write(baos);
                                                                    baos.close();

                                                                    DavGatewayTray.debug("Messagee size: " + message.size + " actual size:" + baos.size() + " message+headers: " + (message.size + baos.size()));
                                                                    sendClient("* " + messageIndex + " FETCH (UID " + message.getUidAsLong() + " RFC822.SIZE " + baos.size() + " BODY[]<0>" +
                                                                            " {" + baos.size() + "}");
                                                                    message.write(os);
                                                                    sendClient(")");
                                                                } else {
                                                                    // write headers to byte array
                                                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                                    HeaderOutputStream headerOutputStream = new HeaderOutputStream(baos);
                                                                    message.write(headerOutputStream);
                                                                    baos.close();
                                                                    sendClient("* " + messageIndex + " FETCH (UID " + message.getUidAsLong() + " RFC822.SIZE " + headerOutputStream.size() + " BODY[HEADER.FIELDS ()" +
                                                                            "] {" + baos.size() + "}");
                                                                    os.write(baos.toByteArray());
                                                                    os.flush();
                                                                    sendClient(" FLAGS (" + (message.getImapFlags()) + "))");
                                                                }
                                                            }
                                                        }
                                                        sendClient(commandId + " OK FETCH completed");

                                                    }
                                                }
                                            } else {
                                                sendClient(commandId + " BAD command unrecognized");
                                            }
                                        } else if ("search".equalsIgnoreCase(subcommand)) {
                                            // only create check search
                                            String messageId = null;
                                            long messageUid = 0;
                                            while (tokens.hasMoreTokens()) {
                                                messageId = tokens.nextToken();
                                            }
                                            // reload messages
                                            messages = session.getAllMessages(currentFolder.folderName);
                                            for (ExchangeSession.Message message : messages) {
                                                if (messageId.equals(message.messageId)) {
                                                    messageUid = message.getUidAsLong();
                                                }
                                            }
                                            if (messageUid > 0) {
                                                sendClient("* SEARCH " + messageUid);
                                            }
                                            sendClient(commandId + " OK SEARCH completed");

                                        } else if ("store".equalsIgnoreCase(subcommand)) {
                                            long uid = Long.parseLong(tokens.nextToken());
                                            ExchangeSession.Message message = messages.getByUid(uid);
                                            String action = tokens.nextToken();
                                            String flags = tokens.nextToken();
                                            HashMap<String, String> properties = new HashMap<String, String>();
                                            if ("-Flags".equalsIgnoreCase(action)) {
                                                StringTokenizer flagtokenizer = new StringTokenizer(flags);
                                                while (flagtokenizer.hasMoreTokens()) {
                                                    String flag = flagtokenizer.nextToken();
                                                    if ("\\Seen".equals(flag)) {
                                                        properties.put("read", "0");
                                                        message.read = false;
                                                    } else if ("\\Flagged".equals(flag)) {
                                                        properties.put("flagged", "0");
                                                        message.flagged = false;
                                                    } else if ("Junk".equals(flag)) {
                                                        properties.put("junk", "0");
                                                        message.junk = false;
                                                    }
                                                }
                                            } else if ("+Flags".equalsIgnoreCase(action)) {
                                                StringTokenizer flagtokenizer = new StringTokenizer(flags);
                                                while (flagtokenizer.hasMoreTokens()) {
                                                    String flag = flagtokenizer.nextToken();
                                                    if ("\\Seen".equals(flag)) {
                                                        properties.put("read", "1");
                                                        message.read = true;
                                                    } else if ("\\Deleted".equals(flag)) {
                                                        message.deleted = true;
                                                    } else if ("\\Flagged".equals(flag)) {
                                                        properties.put("flagged", "2");
                                                        message.flagged = true;
                                                    } else if ("Junk".equals(flag)) {
                                                        properties.put("junk", "1");
                                                        message.junk = true;
                                                    }
                                                }
                                            }
                                            if (properties.size() > 0) {
                                                session.updateMessage(messages.getByUid(uid), properties);
                                            }
                                            int index = 0;
                                            for (ExchangeSession.Message currentMessage : messages) {
                                                index++;
                                                if (currentMessage == message) {
                                                    sendClient("* " + index + " FETCH (UID " + message.getUidAsLong() + " FLAGS (" + (message.getImapFlags()) + "))");
                                                }
                                            }
                                            sendClient(commandId + " OK STORE completed");
                                        } else if ("copy".equalsIgnoreCase(subcommand)) {
                                            try {
                                                long uid = Long.parseLong(tokens.nextToken());
                                                ExchangeSession.Message message = messages.getByUid(uid);
                                                String targetName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                                try {
                                                    session.copyMessage(message.messageUrl, targetName);
                                                    sendClient(commandId + " OK copy completed");
                                                } catch (HttpException e) {
                                                    sendClient(commandId + " NO " + e.getReason());
                                                }
                                            } catch (NumberFormatException nfe) {
                                                sendClient(commandId + " NO unable to copy multiple messages");
                                            }
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("fetch".equalsIgnoreCase(command)) {
                                    // TODO : refactor with uid fetch
                                    if (tokens.hasMoreTokens()) {
                                        int messageIndex = Integer.parseInt(tokens.nextToken());
                                        ExchangeSession.Message message = messages.get(messageIndex - 1);
                                        if (tokens.hasMoreTokens()) {
                                            String parameters = tokens.nextToken();
                                            if ("BODYSTRUCTURE".equals(parameters)) {
                                                sendClient("* " + messageIndex + " FETCH (BODYSTRUCTURE (\"TEXT\" \"PLAIN\" (\"CHARSET\" \"windows-1252\") NIL NIL \"QUOTED-PRINTABLE\" " + message.size + " 50 NIL NIL NIL NIL))");
                                                sendClient(commandId + " OK FETCH completed");
                                            } else {
                                                sendClient("* " + messageIndex + " 1 FETCH (BODY[TEXT]<0> {" + message.size + "}");
                                                message.write(os);
                                                sendClient(commandId + " OK FETCH completed");
                                            }
                                        }
                                    }
                                } else if ("append".equalsIgnoreCase(command)) {
                                    String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    String parameters = tokens.nextToken();
                                    int size = Integer.parseInt(tokens.nextToken());
                                    sendClient("+ send literal data");
                                    char[] buffer = new char[size];
                                    int index = 0;
                                    int count = 0;
                                    while (count >= 0 && index < size) {
                                        count = in.read(buffer, index, size - index);
                                        if (count >= 0) {
                                            index += count;
                                        }
                                    }
                                    // empty line
                                    readClient();

                                    String messageName = UUID.randomUUID().toString();
                                    session.createMessage(session.getFolderPath(folderName), messageName, null, new String(buffer), false);
                                    sendClient(commandId + " OK APPEND completed");
                                } else if ("noop".equalsIgnoreCase(command) || "check".equalsIgnoreCase(command)) {
                                    if (currentFolder != null) {
                                        expunge();
                                        currentFolder = session.getFolder(currentFolder.folderName);
                                        messages = session.getAllMessages(currentFolder.folderUrl);
                                        sendClient("* " + currentFolder.objectCount + " EXISTS");
                                        sendClient("* " + currentFolder.objectCount + " RECENT");
                                    }
                                    sendClient(commandId + " OK " + command + " completed");
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

    protected void expunge() throws IOException {
        if (messages != null) {
            int index = 0;
            for (ExchangeSession.Message message : messages) {
                index++;
                if (message.deleted) {
                    session.deleteMessage(message.messageUrl);
                    sendClient("* " + index + " EXPUNGE");
                }
            }
        }
    }

    /**
     * Decode IMAP credentials
     *
     * @param tokens tokens
     * @throws java.io.IOException on error
     */
    protected void parseCredentials(StringTokenizer tokens) throws IOException {
        if (tokens.hasMoreTokens()) {
            userName = tokens.nextToken();
        } else {
            throw new IOException("Invalid credentials");
        }

        if (tokens.hasMoreTokens()) {
            password = tokens.nextToken();
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
        if (result.startsWith("\"") || result.startsWith("{") || result.startsWith("(")) {
            result = result.substring(1);
        }
        if (result.endsWith("\"") || result.endsWith("}") || result.endsWith(")")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Filter to limit output lines to max body lines after header
     */
    private static class HeaderOutputStream extends FilterOutputStream {
        protected static final int START = 0;
        protected static final int CR = 1;
        protected static final int CRLF = 2;
        protected static final int CRLFCR = 3;
        protected static final int BODY = 4;

        protected int state = START;
        protected int size = 0;

        public HeaderOutputStream(OutputStream os) {
            super(os);
        }

        public int size() {
            return size;
        }

        @Override
        public void write(int b) throws IOException {
            size++;
            if (state != BODY) {
                super.write(b);
            }
            if (state == START) {
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


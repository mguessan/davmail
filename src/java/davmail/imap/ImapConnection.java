package davmail.imap;

import com.sun.mail.imap.protocol.BASE64MailboxDecoder;
import com.sun.mail.imap.protocol.BASE64MailboxEncoder;
import davmail.AbstractConnection;
import davmail.BundleMessage;
import davmail.exception.DavMailException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpException;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimePart;
import javax.mail.MessagingException;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * Dav Gateway smtp connection implementation.
 * Still alpha code : need to find a way to handle message ids
 */
public class ImapConnection extends AbstractConnection {

    ExchangeSession.Folder currentFolder;

    // Initialize the streams and start the thread
    public ImapConnection(Socket clientSocket) {
        super(ImapConnection.class.getSimpleName(), clientSocket, null);
    }

    @Override
    public void run() {
        String line;
        String commandId = null;
        IMAPTokenizer tokens;
        try {
            ExchangeSessionFactory.checkConfig();
            sendClient("* OK [CAPABILITY IMAP4REV1 AUTH=LOGIN] IMAP4rev1 DavMail server ready");
            for (; ;) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }

                tokens = new IMAPTokenizer(line);
                if (tokens.hasMoreTokens()) {
                    commandId = tokens.nextToken();
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
                                    try {
                                        sendClient("+ " + base64Encode("Username:"));
                                        state = State.LOGIN;
                                        userName = base64Decode(readClient());
                                        sendClient("+ " + base64Encode("Password:"));
                                        state = State.PASSWORD;
                                        password = base64Decode(readClient());
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
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderUrl) + '\"');
                                                }
                                                sendClient(commandId + " OK " + command + " completed");
                                            } else {
                                                ExchangeSession.Folder folder = session.getFolder(folderQuery);
                                                if (folder != null) {
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderUrl) + '\"');
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
                                        String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        currentFolder = session.getFolder(folderName);
                                        currentFolder.loadMessages();
                                        sendClient("* " + currentFolder.size() + " EXISTS");
                                        sendClient("* " + currentFolder.size() + " RECENT");
                                        sendClient("* OK [UIDVALIDITY 1]");
                                        if (currentFolder.size() == 0) {
                                            sendClient("* OK [UIDNEXT " + 1 + ']');
                                        } else {
                                            sendClient("* OK [UIDNEXT " + currentFolder.getUidNext() + ']');
                                        }
                                        sendClient("* FLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Junk)");
                                        sendClient("* OK [PERMANENTFLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Junk \\*)]");
                                        if ("select".equalsIgnoreCase(command)) {
                                            sendClient(commandId + " OK [READ-WRITE] " + command + " completed");
                                        } else {
                                            sendClient(commandId + " OK [READ-ONLY] " + command + " completed");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("close".equalsIgnoreCase(command) || "expunge".equalsIgnoreCase(command)) {
                                    expunge("close".equalsIgnoreCase(command));
                                    sendClient(commandId + " OK " + command + " completed");
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
                                        sendClient(commandId + " NO " + e.getMessage());
                                    }
                                } else if ("delete".equalsIgnoreCase(command)) {
                                    String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    try {
                                        session.deleteFolder(folderName);
                                        sendClient(commandId + " OK delete completed");
                                    } catch (HttpException e) {
                                        sendClient(commandId + " NO " + e.getMessage());
                                    }
                                } else if ("uid".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String subcommand = tokens.nextToken();
                                        if ("fetch".equalsIgnoreCase(subcommand)) {
                                            if (currentFolder == null) {
                                                sendClient(commandId + " NO no folder selected");
                                            } else {
                                                currentFolder = session.refreshFolder(currentFolder);
                                                String ranges = tokens.nextToken();
                                                if (ranges == null) {
                                                    sendClient(commandId + " BAD missing range parameter");
                                                } else {
                                                    UIDRangeIterator uidRangeIterator = new UIDRangeIterator(ranges);
                                                    String parameters = null;
                                                    if (tokens.hasMoreTokens()) {
                                                        parameters = tokens.nextToken();
                                                    }
                                                    if (!uidRangeIterator.hasNext() && ranges.indexOf(':') < 0) {
                                                        // message not found in current list, maybe deleted in another thread
                                                        sendClient(commandId + " NO No message found");
                                                    } else {
                                                        while (uidRangeIterator.hasNext()) {
                                                            ExchangeSession.Message message = uidRangeIterator.next();
                                                            handleFetch(message, uidRangeIterator.currentIndex, parameters);
                                                        }
                                                        sendClient(commandId + " OK UID FETCH completed");
                                                    }
                                                }
                                            }

                                        } else if ("search".equalsIgnoreCase(subcommand)) {
                                            SearchConditions conditions = new SearchConditions();
                                            conditions.append("AND (");
                                            boolean undeleted = true;
                                            boolean or = false;

                                            while (tokens.hasMoreTokens()) {
                                                String token = tokens.nextToken();
                                                if ("UNDELETED".equals(token)) {
                                                    undeleted = true;
                                                } else if ("OR".equals(token)) {
                                                    or = true;
                                                } else if (token.startsWith("OR ")) {
                                                    or = true;
                                                    appendOrSearchParams(token, conditions);
                                                } else {
                                                    String operator;
                                                    if (conditions.query.length() == 5) {
                                                        operator = "";
                                                    } else if (or) {
                                                        operator = " OR ";
                                                    } else {
                                                        operator = " AND ";
                                                    }
                                                    appendSearchParam(operator, tokens, token, conditions);
                                                }
                                            }
                                            conditions.append(")");
                                            String query = conditions.query.toString();
                                            DavGatewayTray.debug(new BundleMessage("LOG_SEARCH_QUERY", conditions.query));
                                            if ("AND ()".equals(query)) {
                                                query = null;
                                            }
                                            ExchangeSession.MessageList localMessages = session.searchMessages(currentFolder.folderName, query);
                                            for (ExchangeSession.Message message : localMessages) {
                                                if (((undeleted && !message.deleted) || !undeleted)
                                                        && (conditions.flagged == null || message.flagged == conditions.flagged)
                                                        && (conditions.answered == null || message.answered == conditions.answered)
                                                        && (conditions.startUid == 0 || message.getImapUid() >= conditions.startUid)
                                                        ) {
                                                    sendClient("* SEARCH " + message.getImapUid());
                                                }
                                            }
                                            sendClient(commandId + " OK SEARCH completed");

                                        } else if ("store".equalsIgnoreCase(subcommand)) {
                                            currentFolder = session.refreshFolder(currentFolder);
                                            UIDRangeIterator UIDRangeIterator = new UIDRangeIterator(tokens.nextToken());
                                            String action = tokens.nextToken();
                                            String flags = tokens.nextToken();
                                            while (UIDRangeIterator.hasNext()) {
                                                ExchangeSession.Message message = UIDRangeIterator.next();
                                                updateFlags(message, action, flags);
                                                sendClient("* " + (UIDRangeIterator.currentIndex) + " FETCH (UID " + message.getImapUid() + " FLAGS (" + (message.getImapFlags()) + "))");
                                            }
                                            sendClient(commandId + " OK STORE completed");
                                        } else if ("copy".equalsIgnoreCase(subcommand)) {
                                            try {
                                                currentFolder = session.refreshFolder(currentFolder);
                                                UIDRangeIterator UIDRangeIterator = new UIDRangeIterator(tokens.nextToken());
                                                String targetName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                                while (UIDRangeIterator.hasNext()) {
                                                    ExchangeSession.Message message = UIDRangeIterator.next();
                                                    session.copyMessage(message.messageUrl, targetName);
                                                }
                                                sendClient(commandId + " OK copy completed");
                                            } catch (HttpException e) {
                                                sendClient(commandId + " NO " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("fetch".equalsIgnoreCase(command)) {
                                    if (currentFolder == null) {
                                        sendClient(commandId + " NO no folder selected");
                                    } else {
                                        RangeIterator rangeIterator = new RangeIterator(tokens.nextToken());
                                        String parameters = null;
                                        if (tokens.hasMoreTokens()) {
                                            parameters = tokens.nextToken();
                                        }
                                        while (rangeIterator.hasNext()) {
                                            ExchangeSession.Message message = rangeIterator.next();
                                            handleFetch(message, rangeIterator.currentIndex, parameters);
                                        }
                                        sendClient(commandId + " OK FETCH completed");
                                    }


                                } else if ("append".equalsIgnoreCase(command)) {
                                    String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                    HashMap<String, String> properties = new HashMap<String, String>();
                                    String flags = null;
                                    String date = null;
                                    // handle optional flags
                                    String nextToken = tokens.nextQuotedToken();
                                    if (nextToken.startsWith("(")) {
                                        flags = removeQuotes(nextToken);
                                        if (tokens.hasMoreTokens()) {
                                            nextToken = tokens.nextToken();
                                            if (tokens.hasMoreTokens()) {
                                                date = nextToken;
                                                nextToken = tokens.nextToken();
                                            }
                                        }
                                    } else if (tokens.hasMoreTokens()) {
                                        date = removeQuotes(nextToken);
                                        nextToken = tokens.nextToken();
                                    }

                                    if (flags != null) {
                                        StringTokenizer flagtokenizer = new StringTokenizer(flags);
                                        while (flagtokenizer.hasMoreTokens()) {
                                            String flag = flagtokenizer.nextToken();
                                            if ("\\Seen".equals(flag)) {
                                                properties.put("read", "1");
                                            } else if ("\\Flagged".equals(flag)) {
                                                properties.put("flagged", "2");
                                            } else if ("\\Answered".equals(flag)) {
                                                properties.put("answered", "102");
                                            } else if ("$Forwarded".equals(flag)) {
                                                properties.put("forwarded", "104");
                                            } else if ("\\Draft".equals(flag)) {
                                                properties.put("draft", "9");
                                            } else if ("Junk".equals(flag)) {
                                                properties.put("junk", "1");
                                            }
                                        }
                                    }
                                    // handle optional date
                                    if (date != null) {
                                        SimpleDateFormat dateParser = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", Locale.ENGLISH);
                                        Date dateReceived = dateParser.parse(date);
                                        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                                        dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);

                                        properties.put("datereceived", dateFormatter.format(dateReceived));
                                    }
                                    int size = Integer.parseInt(nextToken);
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
                                    session.createMessage(session.getFolderPath(folderName), messageName, properties, new String(buffer));
                                    sendClient(commandId + " OK APPEND completed");
                                } else if ("noop".equalsIgnoreCase(command) || "check".equalsIgnoreCase(command)) {
                                    if (currentFolder != null) {
                                        DavGatewayTray.debug(new BundleMessage("LOG_IMAP_COMMAND", command, currentFolder.folderName));
                                        currentFolder = session.getFolder(currentFolder.folderName);
                                        currentFolder.loadMessages();
                                        sendClient("* " + currentFolder.size() + " EXISTS");
                                        sendClient("* " + currentFolder.size() + " RECENT");
                                    }
                                    sendClient(commandId + " OK " + command + " completed");
                                } else if ("subscribe".equalsIgnoreCase(command) || "unsubscribe".equalsIgnoreCase(command)) {
                                    sendClient(commandId + " OK " + command + " completed");
                                } else if ("status".equalsIgnoreCase(command)) {
                                    try {
                                        String encodedFolderName = tokens.nextToken();
                                        String folderName = BASE64MailboxDecoder.decode(encodedFolderName);
                                        ExchangeSession.Folder folder = session.getFolder(folderName);
                                        // must retrieve messages
                                        folder.loadMessages();
                                        String parameters = tokens.nextToken();
                                        StringBuilder answer = new StringBuilder();
                                        StringTokenizer parametersTokens = new StringTokenizer(parameters);
                                        while (parametersTokens.hasMoreTokens()) {
                                            String token = parametersTokens.nextToken();
                                            if ("MESSAGES".equalsIgnoreCase(token)) {
                                                answer.append("MESSAGES ").append(folder.size()).append(' ');
                                            }
                                            if ("RECENT".equalsIgnoreCase(token)) {
                                                answer.append("RECENT ").append(folder.size()).append(' ');
                                            }
                                            if ("UIDNEXT".equalsIgnoreCase(token)) {
                                                if (folder.size() == 0) {
                                                    answer.append("UIDNEXT 1 ");
                                                } else {
                                                    if (folder.size() == 0) {
                                                        answer.append("UIDNEXT 1 ");
                                                    } else {
                                                        answer.append("UIDNEXT ").append(folder.getUidNext()).append(' ');
                                                    }
                                                }

                                            }
                                            if ("UIDVALIDITY".equalsIgnoreCase(token)) {
                                                answer.append("UIDVALIDITY 1 ");
                                            }
                                            if ("UNSEEN".equalsIgnoreCase(token)) {
                                                answer.append("UNSEEN ").append(folder.unreadCount).append(' ');
                                            }
                                        }
                                        sendClient("* STATUS " + encodedFolderName + " (" + answer.toString().trim() + ')');
                                        sendClient(commandId + " OK " + command + " completed");
                                    } catch (HttpException e) {
                                        sendClient(commandId + " NO folder not found");
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
                DavGatewayTray.resetIcon();
            }

            os.flush();
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_CLOSE_CONNECTION_ON_TIMEOUT"));
            try {
                sendClient("* BYE Closing connection");
            } catch (IOException e1) {
                DavGatewayTray.debug(new BundleMessage("LOG_EXCEPTION_CLOSING_CONNECTION_ON_TIMEOUT"));
            }
        } catch (SocketException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_CONNECTION_CLOSED"));
        } catch (Exception e) {
            DavGatewayTray.error(e);
            try {
                String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
                if (commandId != null) {
                    sendClient(commandId + " BAD unable to handle request: " + message);
                } else {
                    sendClient("* BYE unable to handle request: " + message);
                }
            } catch (IOException e2) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_SENDING_ERROR_TO_CLIENT"), e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    private void handleFetch(ExchangeSession.Message message, int currentIndex, String parameters) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("* ").append(currentIndex).append(" FETCH (UID ").append(message.getImapUid());
        if (parameters != null) {
            boolean bodystructure = false;
            StringTokenizer paramTokens = new StringTokenizer(parameters);
            while (paramTokens.hasMoreTokens()) {
                String param = paramTokens.nextToken();
                if ("FLAGS".equals(param)) {
                    buffer.append(" FLAGS (").append(message.getImapFlags()).append(')');
                } else if ("BODYSTRUCTURE".equals(param)) {
                    if (parameters.indexOf("BODY.") >= 0) {
                        // Apple Mail: send structure with body, need exact RFC822.SIZE
                        bodystructure = true;
                    } else {
                        // thunderbird : send BODYSTRUCTURE
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        message.write(baos);
                        appendBodyStructure(buffer, baos);
                    }
                } else if ("INTERNALDATE".equals(param) && message.date != null && message.date.length() > 0) {
                    try {
                        SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        dateParser.setTimeZone(ExchangeSession.GMT_TIMEZONE);
                        Date date = dateParser.parse(message.date);
                        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", Locale.ENGLISH);
                        buffer.append(" INTERNALDATE \"").append(dateFormatter.format(date)).append('\"');
                    } catch (ParseException e) {
                        throw new DavMailException("EXCEPTION_INVALID_DATE", message.date);
                    }
                } else if ("BODY.PEEK[HEADER]".equals(param) || param.startsWith("BODY.PEEK[HEADER")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PartOutputStream partOutputStream = new PartOutputStream(baos, true, false, 0);
                    message.write(partOutputStream);
                    baos.close();
                    buffer.append(" RFC822.SIZE ").append(partOutputStream.size);
                    if ("BODY.PEEK[HEADER]".equals(param)) {
                        buffer.append(" BODY[HEADER] {");
                    } else {
                        buffer.append(" BODY[HEADER.FIELDS ()] {");
                    }
                    buffer.append(baos.size()).append('}');
                    sendClient(buffer.toString());
                    os.write(baos.toByteArray());
                    os.flush();
                    buffer.setLength(0);
                } else if (param.startsWith("BODY[]") || param.startsWith("BODY.PEEK[]") || "BODY.PEEK[TEXT]".equals(param)) {
                    // parse buffer size
                    int startIndex = 0;
                    int ltIndex = param.indexOf('<');
                    if (ltIndex >= 0) {
                        int dotIndex = param.indexOf('.', ltIndex);
                        if (dotIndex >= 0) {
                            startIndex = Integer.parseInt(param.substring(ltIndex + 1, dotIndex));
                            // ignore buffer size
                        }
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    boolean writeHeaders = true;
                    int rfc822size;
                    if ("BODY.PEEK[TEXT]".equals(param)) {
                        writeHeaders = false;
                    }
                    PartOutputStream bodyOutputStream = new PartOutputStream(baos, writeHeaders, true, startIndex);
                    message.write(bodyOutputStream);
                    rfc822size = bodyOutputStream.size;
                    baos.close();

                    if (bodystructure) {
                        // Apple Mail: need to build full bodystructure
                        appendBodyStructure(buffer, baos);
                    }
                    buffer.append(" RFC822.SIZE ").append(rfc822size).append(' ').append("BODY[]");
                    // partial
                    if (startIndex > 0) {
                        buffer.append('<').append(startIndex).append('>');
                    }
                    buffer.append(" {").append(baos.size()).append('}');
                    sendClient(buffer.toString());
                    os.write(baos.toByteArray());
                    os.flush();
                    buffer.setLength(0);
                }
            }
        }
        buffer.append(')');
        sendClient(buffer.toString());
    }

    protected void appendBodyStructure(StringBuilder buffer, ByteArrayOutputStream baos) throws IOException {
        buffer.append(" BODYSTRUCTURE ");
        try {
            MimeMessage mimeMessage = new MimeMessage(null, new ByteArrayInputStream(baos.toByteArray()));
            Object mimeBody = mimeMessage.getContent();
            if (mimeBody instanceof MimeMultipart) {
                buffer.append('(');
                MimeMultipart multiPart = (MimeMultipart) mimeBody;

                for (int i = 0; i < multiPart.getCount(); i++) {
                    MimeBodyPart bodyPart = (MimeBodyPart) multiPart.getBodyPart(i);
                    appendBodyStructure(buffer, bodyPart);
                }
                int slashIndex = multiPart.getContentType().indexOf('/');
                if (slashIndex < 0) {
                    throw new DavMailException("EXCEPTION_INVALID_CONTENT_TYPE", multiPart.getContentType());
                }
                int semiColonIndex = multiPart.getContentType().indexOf(';');
                if (semiColonIndex < 0) {
                    buffer.append(" \"").append(multiPart.getContentType().substring(slashIndex + 1).toUpperCase()).append("\")");
                } else {
                    buffer.append(" \"").append(multiPart.getContentType().substring(slashIndex + 1, semiColonIndex).trim().toUpperCase()).append("\")");
                }
            } else {
                // no multipart, single body
                appendBodyStructure(buffer, mimeMessage);
            }
        } catch (MessagingException me) {
            throw new DavMailException("EXCEPTION_INVALID_MESSAGE_CONTENT", me.getMessage());
        }
    }

    protected void appendBodyStructure(StringBuilder buffer, MimePart bodyPart) throws IOException, MessagingException {
        String contentType = bodyPart.getContentType();
        int slashIndex = contentType.indexOf('/');
        if (slashIndex < 0) {
            throw new DavMailException("EXCEPTION_INVALID_CONTENT_TYPE", contentType);
        }
        buffer.append("(\"").append(contentType.substring(0, slashIndex).toUpperCase()).append("\" \"");
        int semiColonIndex = contentType.indexOf(';');
        if (semiColonIndex < 0) {
            buffer.append(contentType.substring(slashIndex + 1).toUpperCase()).append("\" ()");
        } else {
            // extended content type
            buffer.append(contentType.substring(slashIndex + 1, semiColonIndex).trim().toUpperCase()).append('\"');
            int charsetindex = contentType.indexOf("charset=");
            if (charsetindex >= 0) {
                buffer.append(" (\"CHARSET\" ");
                int charsetEndIndex = Math.max(contentType.indexOf(' '), contentType.length());
                String charSet = contentType.substring(charsetindex + "charset=".length(), charsetEndIndex);
                if (!charSet.startsWith("\"")) {
                    buffer.append('"');
                }
                buffer.append(charSet.toUpperCase());
                if (!charSet.endsWith("\"")) {
                    buffer.append('"');
                }
                buffer.append(')');
            } else {
                buffer.append(" ()");
            }
        }
        appendBodyStructureValue(buffer, bodyPart.getContentID());
        appendBodyStructureValue(buffer, bodyPart.getDescription());
        appendBodyStructureValue(buffer, bodyPart.getEncoding());
        appendBodyStructureValue(buffer, bodyPart.getSize());
        appendBodyStructureValue(buffer, bodyPart.getLineCount());
        buffer.append(')');
    }

    protected void appendBodyStructureValue(StringBuilder buffer, String value) {
        if (value == null) {
            buffer.append(" NIL");
        } else {
            buffer.append(" \"").append(value.toUpperCase()).append('\"');
        }
    }

    protected void appendBodyStructureValue(StringBuilder buffer, int value) {
        if (value < 0) {
            buffer.append(" NIL");
        } else {
            buffer.append(' ').append(value);
        }
    }

    static final class SearchConditions {
        Boolean flagged;
        Boolean answered;
        long startUid;
        final StringBuilder query = new StringBuilder();

        public StringBuilder append(String value) {
            return query.append(value);
        }
    }

    protected void appendOrSearchParams(String token, SearchConditions conditions) throws IOException {
        IMAPTokenizer innerTokens = new IMAPTokenizer(token);
        innerTokens.nextToken();
        boolean first = true;
        while (innerTokens.hasMoreTokens()) {
            String innerToken = innerTokens.nextToken();
            String operator = "";
            if (!first) {
                operator = " OR ";
            }
            first = false;
            appendSearchParam(operator, innerTokens, innerToken, conditions);
        }

    }

    protected void appendSearchParam(String operator, StringTokenizer tokens, String token, SearchConditions conditions) throws IOException {
        if ("NOT".equals(token)) {
            conditions.append(operator).append(" NOT ");
            appendSearchParam("", tokens, tokens.nextToken(), conditions);
        } else if (token.startsWith("OR ")) {
            appendOrSearchParams(token, conditions);
        } else if ("SUBJECT".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:httpmail:subject\" LIKE '%").append(tokens.nextToken()).append("%'");
        } else if ("BODY".equals(token)) {
            conditions.append(operator).append("\"http://schemas.microsoft.com/mapi/proptag/x01000001E\" LIKE '%").append(tokens.nextToken()).append("%'");
        } else if ("FROM".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:mailheader:from\" LIKE '%").append(tokens.nextToken()).append("%'");
        } else if ("TO".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:mailheader:to\" LIKE '%").append(tokens.nextToken()).append("%'");
        } else if ("CC".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:mailheader:cc\" LIKE '%").append(tokens.nextToken()).append("%'");
        } else if ("LARGER".equals(token)) {
            conditions.append(operator).append("\"http://schemas.microsoft.com/mapi/proptag/x0e080003\" &gt;= ").append(Long.parseLong(tokens.nextToken())).append("");
        } else if ("SMALLER".equals(token)) {
            conditions.append(operator).append("\"http://schemas.microsoft.com/mapi/proptag/x0e080003\" < ").append(Long.parseLong(tokens.nextToken())).append("");
        } else if (token.startsWith("SENT")) {
            conditions.append(operator);
            appendDateSearchParam(tokens, token, conditions);
        } else if ("SEEN".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:httpmail:read\" = True");
        } else if ("UNSEEN".equals(token) || "NEW".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:httpmail:read\" = False");
        } else if ("FLAGGED".equals(token)) {
            conditions.flagged = Boolean.TRUE;
        } else if ("UNFLAGGED".equals(token) || "NEW".equals(token)) {
            conditions.flagged = Boolean.FALSE;
        } else if ("ANSWERED".equals(token)) {
            conditions.answered = Boolean.TRUE;
        } else if ("UNANSWERED".equals(token)) {
            conditions.answered = Boolean.FALSE;
        } else if ("HEADER".equals(token)) {
            String headerName = tokens.nextToken().toLowerCase();
            String value = tokens.nextToken();
            if ("message-id".equals(headerName) && !value.startsWith("<")) {
                value = '<' + value + '>';
            }
            conditions.append(operator).append("\"urn:schemas:mailheader:").append(headerName).append("\"='").append(value).append('\'');
        } else if ("UID".equals(token)) {
            String range = tokens.nextToken();
            if ("1:*".equals(range)) {
                // ignore: this is a noop filter
            } else if (range.endsWith(":*")) {
                conditions.startUid = Long.parseLong(range.substring(0, range.indexOf(':')));
            } else {
                throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", range);
            }
        } else if ("BEFORE".equals(token)) {
            SimpleDateFormat parser = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
            String dateToken = tokens.nextToken();
            try {
                Date date = parser.parse(dateToken);
                conditions.append(operator).append("\"urn:schemas:httpmail:datereceived\"<'").append(dateFormatter.format(date)).append('\'');
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", dateToken);
            }
        } else if ("OLD".equals(token) || "RECENT".equals(token)) {
            // ignore
        } else {
            throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", token);
        }
    }

    protected void appendDateSearchParam(StringTokenizer tokens, String token, SearchConditions conditions) throws IOException {
        Date startDate;
        Date endDate;
        SimpleDateFormat parser = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
        parser.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        String dateToken = tokens.nextToken();
        try {
            startDate = parser.parse(dateToken);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            endDate = calendar.getTime();
        } catch (ParseException e) {
            throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", dateToken);
        }
        if ("SENTON".equals(token)) {
            conditions.append("(\"urn:schemas:httpmail:date\" > '")
                    .append(dateFormatter.format(startDate))
                    .append("' AND \"urn:schemas:httpmail:date\" < '")
                    .append(dateFormatter.format(endDate))
                    .append("')");
        } else if ("SENTBEFORE".equals(token)) {
            conditions.append("\"urn:schemas:httpmail:date\" < '")
                    .append(dateFormatter.format(startDate))
                    .append('\'');
        } else if ("SENTSINCE".equals(token)) {
            conditions.append("\"urn:schemas:httpmail:date\" >= '")
                    .append(dateFormatter.format(startDate))
                    .append('\'');
        }

    }

    protected void expunge(boolean silent) throws IOException {
        if (currentFolder.messages != null) {
            int index = 0;
            for (ExchangeSession.Message message : currentFolder.messages) {
                index++;
                if (message.deleted) {
                    message.delete();
                    if (!silent) {
                        sendClient("* " + index + " EXPUNGE");
                    }
                }
            }
        }
    }

    protected void updateFlags(ExchangeSession.Message message, String action, String flags) throws IOException {
        HashMap<String, String> properties = new HashMap<String, String>();
        if ("-Flags".equalsIgnoreCase(action) || "-FLAGS.SILENT".equalsIgnoreCase(action)) {
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
        } else if ("+Flags".equalsIgnoreCase(action) || "+FLAGS.SILENT".equalsIgnoreCase(action)) {
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equals(flag)) {
                    properties.put("read", "1");
                    message.read = true;
                } else if ("\\Deleted".equals(flag)) {
                    message.deleted = true;
                    properties.put("deleted", "1");
                } else if ("\\Flagged".equals(flag)) {
                    properties.put("flagged", "2");
                    message.flagged = true;
                } else if ("\\Answered".equals(flag)) {
                    properties.put("answered", "102");
                    message.answered = true;
                } else if ("$Forwarded".equals(flag)) {
                    properties.put("forwarded", "104");
                    message.forwarded = true;
                } else if ("Junk".equals(flag)) {
                    properties.put("junk", "1");
                    message.junk = true;
                }
            }
        } else if ("FLAGS".equalsIgnoreCase(action)) {
            properties.put("read", "0");
            message.read = false;
            properties.put("flagged", "0");
            message.flagged = false;
            properties.put("junk", "0");
            message.junk = false;
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equals(flag)) {
                    properties.put("read", "1");
                    message.read = true;
                } else if ("\\Deleted".equals(flag)) {
                    message.deleted = true;
                    properties.put("deleted", "1");
                } else if ("\\Flagged".equals(flag)) {
                    properties.put("flagged", "2");
                    message.flagged = true;
                } else if ("\\Answered".equals(flag)) {
                    properties.put("answered", "102");
                    message.answered = true;
                } else if ("$Forwarded".equals(flag)) {
                    properties.put("forwarded", "104");
                    message.forwarded = true;
                } else if ("Junk".equals(flag)) {
                    properties.put("junk", "1");
                    message.junk = true;
                }
            }
        }
        if (!properties.isEmpty()) {
            session.updateMessage(message, properties);
        }
    }

    /**
     * Decode IMAP credentials
     *
     * @param tokens tokens
     * @throws IOException on error
     */
    protected void parseCredentials(StringTokenizer tokens) throws IOException {
        if (tokens.hasMoreTokens()) {
            userName = tokens.nextToken();
        } else {
            throw new DavMailException("EXCEPTION_INVALID_CREDENTIALS");
        }

        if (tokens.hasMoreTokens()) {
            password = tokens.nextToken();
        } else {
            throw new DavMailException("EXCEPTION_INVALID_CREDENTIALS");
        }
        int backslashindex = userName.indexOf('\\');
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
     * Filter to output only headers, also count full size
     */
    private static class PartOutputStream extends FilterOutputStream {
        protected static final int START = 0;
        protected static final int CR = 1;
        protected static final int CRLF = 2;
        protected static final int CRLFCR = 3;
        protected static final int BODY = 4;

        protected int state = START;
        protected int size;
        protected final boolean writeHeaders;
        protected final boolean writeBody;
        protected final int startIndex;

        private PartOutputStream(OutputStream os, boolean writeHeaders, boolean writeBody,
                                 int startIndex) {
            super(os);
            this.writeHeaders = writeHeaders;
            this.writeBody = writeBody;
            this.startIndex = startIndex;
        }

        @Override
        public void write(int b) throws IOException {
            size++;
            if (((state != BODY && writeHeaders) || (state == BODY && writeBody)) &&
                    (size > startIndex)
                    ) {
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

    protected class UIDRangeIterator implements Iterator<ExchangeSession.Message> {
        final String[] ranges;
        int currentIndex;
        int currentRangeIndex;
        long startUid;
        long endUid;

        protected UIDRangeIterator(String value) {
            ranges = value.split(",");
        }

        protected long convertToLong(String value) {
            if ("*".equals(value)) {
                return Long.MAX_VALUE;
            } else {
                return Long.parseLong(value);
            }
        }

        protected void skipToStartUid() {
            if (currentRangeIndex < ranges.length) {
                String currentRange = ranges[currentRangeIndex++];
                int colonIndex = currentRange.indexOf(':');
                if (colonIndex > 0) {
                    startUid = convertToLong(currentRange.substring(0, colonIndex));
                    endUid = convertToLong(currentRange.substring(colonIndex + 1));
                } else {
                    startUid = endUid = convertToLong(currentRange);
                }
                while (currentIndex < currentFolder.size() && currentFolder.getImapUid(currentIndex) < startUid) {
                    currentIndex++;
                }
            } else {
                currentIndex = currentFolder.size();
            }
        }

        public boolean hasNext() {
            while (currentIndex < currentFolder.size() && currentFolder.getImapUid(currentIndex) > endUid) {
                skipToStartUid();
            }
            return currentIndex < currentFolder.size();
        }

        public ExchangeSession.Message next() {
            ExchangeSession.Message message = currentFolder.get(currentIndex++);
            long uid = message.getImapUid();
            if (uid < startUid || uid > endUid) {
                throw new RuntimeException("Message uid " + uid + " not in range " + startUid + ':' + endUid);
            }
            return message;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    protected class RangeIterator implements Iterator<ExchangeSession.Message> {
        final String[] ranges;
        int currentIndex;
        int currentRangeIndex;
        long startUid;
        long endUid;

        protected RangeIterator(String value) {
            ranges = value.split(",");
        }

        protected long convertToLong(String value) {
            if ("*".equals(value)) {
                return Long.MAX_VALUE;
            } else {
                return Long.parseLong(value);
            }
        }

        protected void skipToStartUid() {
            if (currentRangeIndex < ranges.length) {
                String currentRange = ranges[currentRangeIndex++];
                int colonIndex = currentRange.indexOf(':');
                if (colonIndex > 0) {
                    startUid = convertToLong(currentRange.substring(0, colonIndex));
                    endUid = convertToLong(currentRange.substring(colonIndex + 1));
                } else {
                    startUid = endUid = convertToLong(currentRange);
                }
                while (currentIndex < currentFolder.size() && (currentIndex + 1) < startUid) {
                    currentIndex++;
                }
            } else {
                currentIndex = currentFolder.size();
            }
        }

        public boolean hasNext() {
            while (currentIndex < currentFolder.size() && (currentIndex + 1) > endUid) {
                skipToStartUid();
            }
            return currentIndex < currentFolder.size();
        }

        public ExchangeSession.Message next() {
            return currentFolder.get(currentIndex++);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    class IMAPTokenizer extends StringTokenizer {
        IMAPTokenizer(String value) {
            super(value);
        }

        @Override
        public String nextToken() {
            return removeQuotes(nextQuotedToken());
        }

        public String nextQuotedToken() {
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
    }

}

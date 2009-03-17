package davmail.imap;

import com.sun.mail.imap.protocol.BASE64MailboxDecoder;
import com.sun.mail.imap.protocol.BASE64MailboxEncoder;
import davmail.AbstractConnection;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;
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
    ExchangeSession.MessageList messages;

    // Initialize the streams and start the thread
    public ImapConnection(Socket clientSocket) {
        super("ImapConnection", clientSocket, null);
    }

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
                                        String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        currentFolder = session.getFolder(folderName);
                                        messages = session.getAllMessages(currentFolder.folderUrl);
                                        sendClient("* " + messages.size() + " EXISTS");
                                        sendClient("* " + messages.size() + " RECENT");
                                        sendClient("* OK [UIDVALIDITY 1]");
                                        if (messages.size() == 0) {
                                            sendClient("* OK [UIDNEXT " + 1 + "]");
                                        } else {
                                            sendClient("* OK [UIDNEXT " + (messages.get(messages.size() - 1).getUidAsLong() + 1) + "]");
                                        }
                                        sendClient("* FLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Junk)");
                                        sendClient("* OK [PERMANENTFLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Junk \\*)]");
                                        sendClient(commandId + " OK [READ-WRITE] " + command + " completed");
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
                                            if (currentFolder == null) {
                                                sendClient(commandId + " NO no folder selected");
                                            } else {
                                                UIDRangeIterator uidRangeIterator = new UIDRangeIterator(tokens.nextToken());
                                                String parameters = null;
                                                if (tokens.hasMoreTokens()) {
                                                    parameters = tokens.nextToken();
                                                }
                                                while (uidRangeIterator.hasNext()) {
                                                    ExchangeSession.Message message = uidRangeIterator.next();
                                                    handleFetch(message, uidRangeIterator.currentIndex, parameters);
                                                }
                                                sendClient(commandId + " OK UID FETCH completed");
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
                                            DavGatewayTray.debug("Search: " + conditions.query);
                                            if ("AND ()".equals(query)) {
                                                query = null;
                                            }
                                            ExchangeSession.MessageList localMessages = session.searchMessages(currentFolder.folderName, query);
                                            for (ExchangeSession.Message message : localMessages) {
                                                if (((undeleted && !message.deleted) || !undeleted)
                                                        && (conditions.flagged == null || message.flagged == conditions.flagged)
                                                        && (conditions.answered == null || message.answered == conditions.answered)
                                                        && (conditions.startUid == 0 || message.getUidAsLong() >= conditions.startUid)
                                                        ) {
                                                    sendClient("* SEARCH " + message.getUidAsLong());
                                                }
                                            }
                                            sendClient(commandId + " OK SEARCH completed");

                                        } else if ("store".equalsIgnoreCase(subcommand)) {
                                            UIDRangeIterator UIDRangeIterator = new UIDRangeIterator(tokens.nextToken());
                                            String action = tokens.nextToken();
                                            String flags = tokens.nextToken();
                                            while (UIDRangeIterator.hasNext()) {
                                                ExchangeSession.Message message = UIDRangeIterator.next();
                                                updateFlags(message, action, flags);
                                                sendClient("* " + (UIDRangeIterator.currentIndex) + " FETCH (UID " + message.getUidAsLong() + " FLAGS (" + (message.getImapFlags()) + "))");
                                            }
                                            sendClient(commandId + " OK STORE completed");
                                        } else if ("copy".equalsIgnoreCase(subcommand)) {
                                            try {
                                                UIDRangeIterator UIDRangeIterator = new UIDRangeIterator(tokens.nextToken());
                                                String targetName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                                while (UIDRangeIterator.hasNext()) {
                                                    ExchangeSession.Message message = UIDRangeIterator.next();
                                                    session.copyMessage(message.messageUrl, targetName);
                                                }
                                                sendClient(commandId + " OK copy completed");
                                            } catch (HttpException e) {
                                                sendClient(commandId + " NO " + e.getReason());
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
                                        currentFolder = session.getFolder(currentFolder.folderName);
                                        messages = session.getAllMessages(currentFolder.folderUrl);
                                        sendClient("* " + messages.size() + " EXISTS");
                                        sendClient("* " + messages.size() + " RECENT");
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
                                        ExchangeSession.MessageList localMessages = session.getAllMessages(folder.folderUrl);
                                        String parameters = tokens.nextToken();
                                        StringBuilder answer = new StringBuilder();
                                        StringTokenizer parametersTokens = new StringTokenizer(parameters);
                                        while (parametersTokens.hasMoreTokens()) {
                                            String token = parametersTokens.nextToken();
                                            if ("MESSAGES".equalsIgnoreCase(token)) {
                                                answer.append("MESSAGES ").append(localMessages.size()).append(" ");
                                            }
                                            if ("RECENT".equalsIgnoreCase(token)) {
                                                answer.append("RECENT ").append(localMessages.size()).append(" ");
                                            }
                                            if ("UIDNEXT".equalsIgnoreCase(token)) {
                                                if (localMessages.size() == 0) {
                                                    answer.append("UIDNEXT 1 ");
                                                } else {
                                                    if (localMessages.size() == 0) {
                                                        answer.append("UIDNEXT 1 ");
                                                    } else {
                                                        answer.append("UIDNEXT ").append((localMessages.get(localMessages.size() - 1).getUidAsLong() + 1)).append(" ");
                                                    }
                                                }

                                            }
                                            if ("UIDVALIDITY".equalsIgnoreCase(token)) {
                                                answer.append("UIDVALIDITY 1 ");
                                            }
                                            if ("UNSEEN".equalsIgnoreCase(token)) {
                                                answer.append("UNSEEN ").append(folder.unreadCount).append(" ");
                                            }
                                        }
                                        sendClient("* STATUS " + encodedFolderName + " (" + answer.toString().trim() + ")");
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
            }


            os.flush();
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug("Closing connection on timeout");
            try {
                sendClient("* BYE Closing connection");
            } catch (IOException e1) {
                DavGatewayTray.debug("Exception closing connection on timeout");
            }
        } catch (SocketException e) {
            DavGatewayTray.debug("Connection closed");
        } catch (Exception e) {
            StringBuilder buffer = new StringBuilder();
            if (e instanceof HttpException) {
                buffer.append(((HttpException) e).getReasonCode()).append(" ").append(((HttpException) e).getReason());
            } else {
                buffer.append(e);
            }
            String message = buffer.toString();
            try {
                if (commandId != null) {
                    sendClient(commandId + " BAD unable to handle request: "+message);
                } else {
                    sendClient("* BYE unable to handle request: "+message);
                }
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception sending error to client", e2);
            }
            DavGatewayTray.error("Exception handling client: "+message, e);
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    private void handleFetch(ExchangeSession.Message message, int currentIndex, String parameters) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("* ").append(currentIndex).append(" FETCH (UID ").append(message.getUidAsLong());
        boolean bodystructure = false;
        StringTokenizer paramTokens = new StringTokenizer(parameters);
        while (paramTokens.hasMoreTokens()) {
            String param = paramTokens.nextToken();
            if ("FLAGS".equals(param)) {
                buffer.append(" FLAGS (").append(message.getImapFlags()).append(")");
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
                    buffer.append(" INTERNALDATE \"").append(dateFormatter.format(date)).append("\"");
                } catch (ParseException e) {
                    throw new IOException("Invalid date: " + message.date);
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
                buffer.append(baos.size()).append("}");
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
                DavGatewayTray.debug("Message RFC822 size: " + rfc822size + " buffer size:" + baos.size());
                if (bodystructure) {
                    // Apple Mail: need to build full bodystructure
                    appendBodyStructure(buffer, baos);
                }
                buffer.append(" RFC822.SIZE ").append(rfc822size).append(" ").append("BODY[]");
                // partial
                if (startIndex > 0) {
                    buffer.append('<').append(startIndex).append('>');
                }
                buffer.append(" {").append(baos.size()).append("}");
                sendClient(buffer.toString());
                os.write(baos.toByteArray());
                os.flush();
                buffer.setLength(0);
            }
        }
        buffer.append(")");
        sendClient(buffer.toString());
    }

    protected void appendBodyStructure(StringBuilder buffer, ByteArrayOutputStream baos) throws IOException {
        buffer.append(" BODYSTRUCTURE ");
        try {
            MimeMessage mimeMessage = new MimeMessage(null, new ByteArrayInputStream(baos.toByteArray()));
            Object mimeBody = mimeMessage.getContent();
            if (mimeBody instanceof MimeMultipart) {
                buffer.append("(");
                MimeMultipart multiPart = (MimeMultipart) mimeBody;

                for (int i = 0; i < multiPart.getCount(); i++) {
                    MimeBodyPart bodyPart = (MimeBodyPart) multiPart.getBodyPart(i);
                    appendBodyStructure(buffer, bodyPart);
                }
                int slashIndex = multiPart.getContentType().indexOf('/');
                if (slashIndex < 0) {
                    throw new IOException("Invalid content type: " + multiPart.getContentType());
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
            throw new IOException(me);
        }
    }

    protected void appendBodyStructure(StringBuilder buffer, MimePart bodyPart) throws IOException, MessagingException {
        String contentType = bodyPart.getContentType();
        int slashIndex = contentType.indexOf('/');
        if (slashIndex < 0) {
            throw new IOException("Invalid content type: " + contentType);
        }
        buffer.append("(\"").append(contentType.substring(0, slashIndex).toUpperCase()).append("\" \"");
        int semiColonIndex = contentType.indexOf(';');
        if (semiColonIndex < 0) {
            buffer.append(contentType.substring(slashIndex + 1).toUpperCase()).append("\" ()");
        } else {
            // extended content type
            buffer.append(contentType.substring(slashIndex + 1, semiColonIndex).trim().toUpperCase()).append("\"");
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
                buffer.append(")");
            } else {
                buffer.append(" ()");
            }
        }
        // body id
        if (bodyPart.getContentID() == null) {
            buffer.append(" NIL");
        } else {
            buffer.append(" \"").append(bodyPart.getContentID()).append("\"");
        }
        if (bodyPart.getDescription() == null) {
            buffer.append(" NIL");
        } else {
            buffer.append(" \"").append(bodyPart.getDescription()).append("\"");
        }
        if (bodyPart.getHeader("Content-Transfer-Encoding") == null) {
            buffer.append(" NIL");
        } else {
            buffer.append(" \"").append(bodyPart.getEncoding().toUpperCase()).append("\"");
        }
        buffer.append(' ').append(bodyPart.getSize());
        if (bodyPart.getLineCount() < 0) {
            buffer.append(" NIL");
        } else {
            buffer.append(' ').append(bodyPart.getLineCount()).append('"');
        }
        buffer.append(')');
    }

    static final class SearchConditions {
        Boolean flagged = null;
        Boolean answered = null;
        long startUid = 0;
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
            conditions.append(operator).append("\"http://schemas.microsoft.com/mapi/proptag/x0e080003\" &lt; ").append(Long.parseLong(tokens.nextToken())).append("");
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
            conditions.append(operator).append("\"urn:schemas:mailheader:").append(headerName).append("\"='").append(tokens.nextToken()).append("'");
        } else if ("UID".equals(token)) {
            String range = tokens.nextToken();
            if ("1:*".equals(range)) {
                // ignore: this is a noop filter
            } else if (range.endsWith(":*")) {
                conditions.startUid = Long.parseLong(range.substring(0, range.indexOf(':')));
            } else {
                throw new IOException("Invalid search parameters");
            }
        } else if ("BEFORE".equals(token)) {
            SimpleDateFormat parser = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
            try {
                Date date = parser.parse(tokens.nextToken());
                conditions.append(operator).append("\"urn:schemas:httpmail:datereceived\"&lt;'").append(dateFormatter.format(date)).append("'");
            } catch (ParseException e) {
                throw new IOException("Invalid search parameters");
            }
        } else if ("OLD".equals(token)) {
            // ignore
        } else {
            throw new IOException("Invalid search parameters");
        }
    }

    protected void appendDateSearchParam(StringTokenizer tokens, String token, SearchConditions conditions) throws IOException {
        Date startDate;
        Date endDate;
        SimpleDateFormat parser = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
        parser.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        try {
            startDate = parser.parse(tokens.nextToken());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            endDate = calendar.getTime();
        } catch (ParseException e) {
            throw new IOException("Invalid search parameters");
        }
        if ("SENTON".equals(token)) {
            conditions.append("(\"urn:schemas:httpmail:date\" &gt; '")
                    .append(dateFormatter.format(startDate))
                    .append("' AND \"urn:schemas:httpmail:date\" &lt; '")
                    .append(dateFormatter.format(endDate))
                    .append("')");
        } else if ("SENTBEFORE".equals(token)) {
            conditions.append("\"urn:schemas:httpmail:date\" &lt; '")
                    .append(dateFormatter.format(startDate))
                    .append("'");
        } else if ("SENTSINCE".equals(token)) {
            conditions.append("\"urn:schemas:httpmail:date\" &gt;= '")
                    .append(dateFormatter.format(startDate))
                    .append("'");
        }

    }

    protected void expunge(boolean silent) throws IOException {
        if (messages != null) {
            int index = 0;
            for (ExchangeSession.Message message : messages) {
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
        if (properties.size() > 0) {
            session.updateMessage(message, properties);
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
     * Filter to output only headers, also count full size
     */
    private static class PartOutputStream extends FilterOutputStream {
        protected static final int START = 0;
        protected static final int CR = 1;
        protected static final int CRLF = 2;
        protected static final int CRLFCR = 3;
        protected static final int BODY = 4;

        protected int state = START;
        protected int size = 0;
        protected final boolean writeHeaders;
        protected final boolean writeBody;
        protected final int startIndex;

        public PartOutputStream(OutputStream os, boolean writeHeaders, boolean writeBody,
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
        int currentIndex = 0;
        int currentRangeIndex = 0;
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
                while (currentIndex < messages.size() && messages.get(currentIndex).getUidAsLong() < startUid) {
                    currentIndex++;
                }
            } else {
                currentIndex = messages.size();
            }
        }

        public boolean hasNext() {
            while (currentIndex < messages.size() && messages.get(currentIndex).getUidAsLong() > endUid) {
                skipToStartUid();
            }
            return currentIndex < messages.size();
        }

        public ExchangeSession.Message next() {
            ExchangeSession.Message message = messages.get(currentIndex++);
            long uid = message.getUidAsLong();
            if (uid < startUid || uid > endUid) {
                throw new RuntimeException("Message uid " + uid + " not in range " + startUid + ":" + endUid);
            }
            return message;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    protected class RangeIterator implements Iterator<ExchangeSession.Message> {
        final String[] ranges;
        int currentIndex = 0;
        int currentRangeIndex = 0;
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
                while (currentIndex < messages.size() && (currentIndex + 1) < startUid) {
                    currentIndex++;
                }
            } else {
                currentIndex = messages.size();
            }
        }

        public boolean hasNext() {
            while (currentIndex < messages.size() && (currentIndex + 1) > endUid) {
                skipToStartUid();
            }
            return currentIndex < messages.size();
        }

        public ExchangeSession.Message next() {
            return messages.get(currentIndex++);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    class IMAPTokenizer extends StringTokenizer {
        public IMAPTokenizer(String value) {
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

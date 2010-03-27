/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.imap;

import com.sun.mail.imap.protocol.BASE64MailboxDecoder;
import com.sun.mail.imap.protocol.BASE64MailboxEncoder;
import davmail.AbstractConnection;
import davmail.BundleMessage;
import davmail.exception.DavMailException;
import davmail.exception.HttpForbiddenException;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.StringUtil;
import org.apache.commons.httpclient.HttpException;

import javax.mail.MessagingException;
import javax.mail.internet.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Dav Gateway smtp connection implementation.
 * Still alpha code : need to find a way to handle message ids
 */
public class ImapConnection extends AbstractConnection {

    ExchangeSession.Folder currentFolder;

    /**
     * Initialize the streams and start the thread.
     *
     * @param clientSocket IMAP client socket
     */
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
                                // check for expired session
                                session = ExchangeSessionFactory.getInstance(session, userName, password);
                                if ("lsub".equalsIgnoreCase(command) || "list".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderContext = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        if (tokens.hasMoreTokens()) {
                                            String folderQuery = folderContext + BASE64MailboxDecoder.decode(tokens.nextToken());
                                            if (folderQuery.endsWith("%/%") && !"/%/%".equals(folderQuery)) {
                                                List<ExchangeSession.Folder> folders = session.getSubFolders(folderQuery.substring(0, folderQuery.length() - 3), false);
                                                for (ExchangeSession.Folder folder : folders) {
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderPath) + '\"');
                                                    sendSubFolders(command, folder.folderPath, false);
                                                }
                                                sendClient(commandId + " OK " + command + " completed");
                                            } else if (folderQuery.endsWith("%") || folderQuery.endsWith("*")) {
                                                if ("/*".equals(folderQuery) || "/%".equals(folderQuery) || "/%/%".equals(folderQuery)) {
                                                    folderQuery = folderQuery.substring(1);
                                                    if ("%/%".equals(folderQuery)) {
                                                        folderQuery = folderQuery.substring(0, folderQuery.length() - 2);
                                                    }
                                                    sendClient("* " + command + " (\\HasChildren) \"/\" \"/public\"");
                                                }
                                                if ("*%".equals(folderQuery)) {
                                                    folderQuery = "*";
                                                }
                                                boolean recursive = folderQuery.endsWith("*") && !folderQuery.startsWith("/public");
                                                sendSubFolders(command, folderQuery.substring(0, folderQuery.length() - 1), recursive);
                                                sendClient(commandId + " OK " + command + " completed");
                                            } else {
                                                ExchangeSession.Folder folder = null;
                                                try {
                                                    folder = session.getFolder(folderQuery);
                                                } catch (HttpForbiddenException e) {
                                                    // access forbidden, ignore
                                                    DavGatewayTray.debug(new BundleMessage("LOG_FOLDER_ACCESS_FORBIDDEN", folderQuery));
                                                } catch (HttpNotFoundException e) {
                                                    // not found, ignore
                                                    DavGatewayTray.debug(new BundleMessage("LOG_FOLDER_NOT_FOUND", folderQuery));
                                                } catch (HttpException e) {
                                                    // other errors, ignore
                                                    DavGatewayTray.debug(new BundleMessage("LOG_FOLDER_ACCESS_ERROR", folderQuery, e.getMessage()));
                                                }
                                                if (folder != null) {
                                                    sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderPath) + '\"');
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
                                        try {
                                            currentFolder = session.getFolder(folderName);
                                            currentFolder.loadMessages();
                                            sendClient("* " + currentFolder.count() + " EXISTS");
                                            sendClient("* " + currentFolder.count() + " RECENT");
                                            sendClient("* OK [UIDVALIDITY 1]");
                                            if (currentFolder.count() == 0) {
                                                sendClient("* OK [UIDNEXT 1]");
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
                                        } catch (HttpNotFoundException e) {
                                            sendClient(commandId + " NO Not found");
                                        } catch (HttpForbiddenException e) {
                                            sendClient(commandId + " NO Forbidden");
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("expunge".equalsIgnoreCase(command)) {
                                    expunge(false);
                                    // need to refresh folder to avoid 404 errors
                                    session.refreshFolder(currentFolder);
                                    sendClient(commandId + " OK " + command + " completed");
                                } else if ("close".equalsIgnoreCase(command)) {
                                    expunge(true);
                                    // deselect folder
                                    currentFolder = null;
                                    sendClient(commandId + " OK " + command + " completed");
                                } else if ("create".equalsIgnoreCase(command)) {
                                    if (tokens.hasMoreTokens()) {
                                        String folderName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        session.createMessageFolder(folderName);
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
                                                String ranges = tokens.nextToken();
                                                if (ranges == null) {
                                                    sendClient(commandId + " BAD missing range parameter");
                                                } else {
                                                    String parameters = null;
                                                    if (tokens.hasMoreTokens()) {
                                                        parameters = tokens.nextToken();
                                                    }
                                                    UIDRangeIterator uidRangeIterator = new UIDRangeIterator(currentFolder.messages, ranges);
                                                    while (uidRangeIterator.hasNext()) {
                                                        DavGatewayTray.switchIcon();
                                                        ExchangeSession.Message message = uidRangeIterator.next();
                                                        try {
                                                            handleFetch(message, uidRangeIterator.currentIndex, parameters);
                                                        } catch (IOException e) {
                                                            DavGatewayTray.log(e);
                                                            sendClient(commandId + " NO Unable to retrieve message: " + e.getMessage());
                                                        }
                                                    }
                                                    sendClient(commandId + " OK UID FETCH completed");
                                                }
                                            }

                                        } else if ("search".equalsIgnoreCase(subcommand)) {
                                            List<Long> uidList = handleSearch(tokens);
                                            for (long uid : uidList) {
                                                sendClient("* SEARCH " + uid);
                                            }
                                            sendClient(commandId + " OK SEARCH completed");

                                        } else if ("store".equalsIgnoreCase(subcommand)) {
                                            UIDRangeIterator uidRangeIterator = new UIDRangeIterator(currentFolder.messages, tokens.nextToken());
                                            String action = tokens.nextToken();
                                            String flags = tokens.nextToken();
                                            handleStore(commandId, uidRangeIterator, action, flags);
                                        } else if ("copy".equalsIgnoreCase(subcommand)) {
                                            try {
                                                UIDRangeIterator uidRangeIterator = new UIDRangeIterator(currentFolder.messages, tokens.nextToken());
                                                String targetName = BASE64MailboxDecoder.decode(tokens.nextToken());
                                                while (uidRangeIterator.hasNext()) {
                                                    DavGatewayTray.switchIcon();
                                                    ExchangeSession.Message message = uidRangeIterator.next();
                                                    session.copyMessage(message, targetName);
                                                }
                                                sendClient(commandId + " OK copy completed");
                                            } catch (HttpException e) {
                                                sendClient(commandId + " NO " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        sendClient(commandId + " BAD command unrecognized");
                                    }
                                } else if ("search".equalsIgnoreCase(command)) {
                                    if (currentFolder == null) {
                                        sendClient(commandId + " NO no folder selected");
                                    } else {
                                        List<Long> uidList = handleSearch(tokens);
                                        int currentIndex = 0;
                                        for (ExchangeSession.Message message : currentFolder.messages) {
                                            currentIndex++;
                                            if (uidList.contains(message.getImapUid())) {
                                                sendClient("* SEARCH " + currentIndex);
                                            }
                                        }
                                        sendClient(commandId + " OK SEARCH completed");
                                    }
                                } else if ("fetch".equalsIgnoreCase(command)) {
                                    if (currentFolder == null) {
                                        sendClient(commandId + " NO no folder selected");
                                    } else {
                                        RangeIterator rangeIterator = new RangeIterator(currentFolder.messages, tokens.nextToken());
                                        String parameters = null;
                                        if (tokens.hasMoreTokens()) {
                                            parameters = tokens.nextToken();
                                        }
                                        while (rangeIterator.hasNext()) {
                                            DavGatewayTray.switchIcon();
                                            ExchangeSession.Message message = rangeIterator.next();
                                            try {
                                                handleFetch(message, rangeIterator.currentIndex, parameters);
                                            } catch (SocketException e) {
                                                // client closed connection, rethrow exception
                                                throw e;
                                            } catch (IOException e) {
                                                DavGatewayTray.log(e);
                                                sendClient(commandId + " NO Unable to retrieve message: " + e.getMessage());
                                            }

                                        }
                                        sendClient(commandId + " OK FETCH completed");
                                    }

                                } else if ("store".equalsIgnoreCase(command)) {
                                    RangeIterator rangeIterator = new RangeIterator(currentFolder.messages, tokens.nextToken());
                                    String action = tokens.nextToken();
                                    String flags = tokens.nextToken();
                                    handleStore(commandId, rangeIterator, action, flags);

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
                                    session.createMessage(folderName, messageName, properties, new String(buffer));
                                    sendClient(commandId + " OK APPEND completed");
                                } else if ("noop".equalsIgnoreCase(command) || "check".equalsIgnoreCase(command)) {
                                    if (currentFolder != null) {
                                        DavGatewayTray.debug(new BundleMessage("LOG_IMAP_COMMAND", command, currentFolder.folderName));
                                        ExchangeSession.MessageList currentMessages = currentFolder.messages;
                                        if (session.refreshFolder(currentFolder)) {
                                            // build new uid set
                                            HashSet<Long> uidSet = new HashSet<Long>();
                                            for (ExchangeSession.Message message : currentFolder.messages) {
                                                uidSet.add(message.getImapUid());
                                            }
                                            // send expunge untagged response for removed IMAP message uids
                                            // note: some STORE commands trigger a uid change in Exchange,
                                            // thus those messages are expunged and reappear with a new uid
                                            int index = 1;
                                            for (ExchangeSession.Message message : currentMessages) {
                                                if (!uidSet.contains(message.getImapUid())) {
                                                    sendClient("* " + index + " EXPUNGE");
                                                } else {
                                                    index++;
                                                }
                                            }
                                            sendClient("* " + currentFolder.count() + " EXISTS");
                                            sendClient("* " + currentFolder.count() + " RECENT");
                                        }
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
                                                answer.append("MESSAGES ").append(folder.count()).append(' ');
                                            }
                                            if ("RECENT".equalsIgnoreCase(token)) {
                                                answer.append("RECENT ").append(folder.count()).append(' ');
                                            }
                                            if ("UIDNEXT".equalsIgnoreCase(token)) {
                                                if (folder.count() == 0) {
                                                    answer.append("UIDNEXT 1 ");
                                                } else {
                                                    if (folder.count() == 0) {
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
                                        sendClient("* STATUS \"" + encodedFolderName + "\" (" + answer.toString().trim() + ')');
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
            DavGatewayTray.debug(new BundleMessage("LOG_CLIENT_CLOSED_CONNECTION"));
        } catch (Exception e) {
            DavGatewayTray.log(e);
            try {
                String message = ((e.getMessage() == null) ? e.toString() : e.getMessage()).replaceAll("\\n", " ");
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

    private void handleFetch(ExchangeSession.Message message, int currentIndex, String parameters) throws IOException, MessagingException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("* ").append(currentIndex).append(" FETCH (UID ").append(message.getImapUid());
        if (parameters != null) {
            StringTokenizer paramTokens = new StringTokenizer(parameters);
            while (paramTokens.hasMoreTokens()) {
                String param = paramTokens.nextToken();
                if ("FLAGS".equals(param)) {
                    buffer.append(" FLAGS (").append(message.getImapFlags()).append(')');
                } else if ("ENVELOPE".equals(param)) {
                    appendEnvelope(buffer, message);
                } else if ("BODYSTRUCTURE".equals(param)) {
                    appendBodyStructure(buffer, message);
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
                } else if ("BODY.PEEK[HEADER]".equals(param) || param.startsWith("BODY.PEEK[HEADER") || "RFC822.HEADER".equals(param)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PartOutputStream partOutputStream = new PartOutputStream(baos, true, false, 0, Integer.MAX_VALUE);
                    message.write(partOutputStream);
                    baos.close();

                    buffer.append(" RFC822.SIZE ").append(partOutputStream.size);
                    if ("BODY.PEEK[HEADER]".equals(param)) {
                        buffer.append(" BODY[HEADER] {");
                    } else if ("RFC822.HEADER".equals(param)) {
                        buffer.append(" RFC822.HEADER {");
                    } else {
                        buffer.append(" BODY[HEADER.FIELDS ()] {");
                    }
                    buffer.append(baos.size()).append('}');
                    sendClient(buffer.toString());
                    os.write(baos.toByteArray());
                    os.flush();
                    buffer.setLength(0);
                } else if (param.startsWith("BODY[]") || param.startsWith("BODY.PEEK[]") || param.startsWith("BODY[TEXT]") || "BODY.PEEK[TEXT]".equals(param)) {
                    // parse buffer size
                    int startIndex = 0;
                    int maxSize = Integer.MAX_VALUE;
                    int ltIndex = param.indexOf('<');
                    if (ltIndex >= 0) {
                        int dotIndex = param.indexOf('.', ltIndex);
                        if (dotIndex >= 0) {
                            startIndex = Integer.parseInt(param.substring(ltIndex + 1, dotIndex));
                            maxSize = Integer.parseInt(param.substring(dotIndex+1, param.indexOf('>')));
                        }
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    boolean writeHeaders = true;
                    int rfc822size;
                    if ("BODY.PEEK[TEXT]".equals(param)) {
                        writeHeaders = false;
                    }
                    PartOutputStream bodyOutputStream = new PartOutputStream(baos, writeHeaders, true, startIndex, maxSize);
                    message.write(bodyOutputStream);
                    rfc822size = bodyOutputStream.size;
                    baos.close();

                    buffer.append(" RFC822.SIZE ").append(rfc822size).append(' ');
                    if ("BODY.PEEK[TEXT]".equals(param)) {
                        buffer.append("BODY[TEXT]");
                    } else {
                        buffer.append("BODY[]");
                    }
                    // partial
                    if (startIndex > 0 || maxSize != Integer.MAX_VALUE) {
                        buffer.append('<').append(startIndex).append('>');
                    }
                    buffer.append(" {").append(baos.size()).append('}');
                    sendClient(buffer.toString());
                    os.write(baos.toByteArray());
                    os.flush();
                    buffer.setLength(0);
                } else if (param.startsWith("BODY[")) {
                    int partIndex = 0;
                    // try to parse message part index
                    String partIndexString = StringUtil.getToken(param, "[", "]");
                    if (partIndexString != null) {
                        try {
                            partIndex = Integer.parseInt(partIndexString);
                        } catch (NumberFormatException e) {
                            throw new DavMailException("EXCEPTION_UNSUPPORTED_PARAMETER", param);
                        }
                    }
                    if (partIndex == 0) {
                        throw new DavMailException("EXCEPTION_INVALID_PARAMETER", param);
                    }

                    // parse buffer size
                    int startIndex = 0;
                    int maxSize = Integer.MAX_VALUE;
                    int ltIndex = param.indexOf('<');
                    if (ltIndex >= 0) {
                        int dotIndex = param.indexOf('.', ltIndex);
                        if (dotIndex >= 0) {
                            startIndex = Integer.parseInt(param.substring(ltIndex + 1, dotIndex));
                            maxSize = Integer.parseInt(param.substring(dotIndex+1, param.indexOf('>')));
                        }
                    }

                    MimeMessage mimeMessage = message.getMimeMessage();
                    Object mimeBody = mimeMessage.getContent();
                    MimePart bodyPart;
                    if (mimeBody instanceof MimeMultipart) {
                        MimeMultipart multiPart = (MimeMultipart) mimeBody;
                        bodyPart = (MimePart) multiPart.getBodyPart(partIndex - 1);
                    } else if (partIndex == 1) {
                        // no multipart, single body
                        bodyPart = mimeMessage;
                    } else {
                        throw new DavMailException("EXCEPTION_INVALID_PARAMETER", param);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bodyPart.getDataHandler().writeTo(new PartialOutputStream(baos, startIndex, maxSize));
                    baos.close();

                    buffer.append(" BODY[").append(partIndexString).append(']');
                    // partial
                    if (startIndex > 0 || maxSize != Integer.MAX_VALUE) {
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
        // do not keep message content in memory
        message.dropMimeMessage();
    }

    protected void handleStore(String commandId, AbstractRangeIterator rangeIterator, String action, String flags) throws IOException {
        while (rangeIterator.hasNext()) {
            DavGatewayTray.switchIcon();
            ExchangeSession.Message message = rangeIterator.next();
            updateFlags(message, action, flags);
            sendClient("* " + (rangeIterator.getCurrentIndex()) + " FETCH (UID " + message.getImapUid() + " FLAGS (" + (message.getImapFlags()) + "))");
        }
        sendClient(commandId + " OK STORE completed");
    }


    protected List<Long> handleSearch(IMAPTokenizer tokens) throws IOException {
        List<Long> uidList = new ArrayList<Long>();
        SearchConditions conditions = new SearchConditions();
        conditions.append("AND (");
        boolean or = false;

        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().toUpperCase();
            if ("OR".equals(token)) {
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
        ExchangeSession.MessageList localMessages = currentFolder.searchMessages(query);
        int index = 1;
        for (ExchangeSession.Message message : localMessages) {
            if ((conditions.deleted == null || message.deleted == conditions.deleted)
                    && (conditions.flagged == null || message.flagged == conditions.flagged)
                    && (conditions.answered == null || message.answered == conditions.answered)
                    && (conditions.startUid == 0 || message.getImapUid() >= conditions.startUid)
                    && (conditions.startIndex == 0 || (index++ >= conditions.startIndex))
                    ) {
                uidList.add(message.getImapUid());
            }
        }
        return uidList;
    }

    protected void appendEnvelope(StringBuilder buffer, ExchangeSession.Message message) throws IOException {
        buffer.append(" ENVELOPE (");

        try {
            MimeMessage mimeMessage = message.getMimeMessage();
            // Envelope for date, subject, from, sender, reply-to, to, cc, bcc,in-reply-to, message-id
            appendEnvelopeHeader(buffer, mimeMessage.getHeader("Date"));
            appendEnvelopeHeader(buffer, mimeMessage.getHeader("Subject"));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("From", ","));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("Sender", ","));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("Reply-To", ","));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("CC", ","));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("BCC", ","));
            appendMailEnvelopeHeader(buffer, mimeMessage.getHeader("In-Reply-To", ","));
            appendEnvelopeHeader(buffer, mimeMessage.getHeader("Messagee-Id"));

        } catch (MessagingException me) {
            DavGatewayTray.warn(me);
            // send fake envelope
            buffer.append(" NIL NIL NIL NIL NIL NIL NIL NIL NIL NIL");
        }
        buffer.append(')');
    }

    protected void appendEnvelopeHeader(StringBuilder buffer, String[] value) {
        buffer.append(' ');
        if (value != null && value.length > 0) {
            String unfoldedValue = MimeUtility.unfold(value[0]);
            if (unfoldedValue.indexOf('"') >= 0) {
                buffer.append('{');
                buffer.append(unfoldedValue.length());
                buffer.append("}\r\n");
                buffer.append(unfoldedValue);
            } else {
                buffer.append('"');
                buffer.append(unfoldedValue);
                buffer.append('"');
            }
        } else {
            buffer.append("NIL");
        }
    }

    protected void appendMailEnvelopeHeader(StringBuilder buffer, String value) {
        buffer.append(' ');
        if (value != null) {
            try {
                InternetAddress[] addresses = InternetAddress.parseHeader(value, false);
                buffer.append('(');
                for (InternetAddress address : addresses) {
                    buffer.append('(');
                    String personal = address.getPersonal();
                    if (personal != null) {
                        buffer.append('"').append(MimeUtility.encodeText(personal)).append('"');
                    } else {
                        buffer.append("NIL");
                    }
                    buffer.append(" NIL ");
                    String mail = address.getAddress();
                    int atIndex = mail.indexOf('@');
                    if (atIndex >= 0) {
                        buffer.append('"').append(mail.substring(0, atIndex)).append('"');
                        buffer.append(' ');
                        buffer.append('"').append(mail.substring(atIndex + 1)).append('"');
                    } else {
                        buffer.append("NIL NIL");
                    }
                    buffer.append(')');
                }
                buffer.append(')');
            } catch (AddressException e) {
                DavGatewayTray.warn(e);
                buffer.append("NIL");
            } catch (UnsupportedEncodingException e) {
                DavGatewayTray.warn(e);
                buffer.append("NIL");
            }
        } else {
            buffer.append("NIL");
        }
    }

    protected void appendBodyStructure(StringBuilder buffer, ExchangeSession.Message message) throws IOException {

        buffer.append(" BODYSTRUCTURE ");
        try {
            MimeMessage mimeMessage = message.getMimeMessage();
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
        } catch (UnsupportedEncodingException e) {
            DavGatewayTray.warn(e);
            // failover: send default bodystructure
            buffer.append("(\"TEXT\" \"PLAIN\" (\"CHARSET\" \"US-ASCII\") NIL NIL NIL NIL NIL)");
        } catch (MessagingException me) {
            DavGatewayTray.warn(me);
            // failover: send default bodystructure
            buffer.append("(\"TEXT\" \"PLAIN\" (\"CHARSET\" \"US-ASCII\") NIL NIL NIL NIL NIL)");
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
                int charsetSemiColonIndex = contentType.indexOf(';', charsetindex);
                int charsetEndIndex;
                if (charsetSemiColonIndex > 0) {
                    charsetEndIndex = charsetSemiColonIndex;
                } else {
                    charsetEndIndex = contentType.length();
                }
                String charSet = contentType.substring(charsetindex + "charset=".length(), charsetEndIndex);
                if (!charSet.startsWith("\"")) {
                    buffer.append('"');
                }
                buffer.append(charSet.trim().toUpperCase());
                if (!charSet.endsWith("\"")) {
                    buffer.append('"');
                }
                buffer.append(')');
            } else {
                buffer.append(" NIL");
            }
        }
        appendBodyStructureValue(buffer, bodyPart.getContentID());
        appendBodyStructureValue(buffer, bodyPart.getDescription());
        appendBodyStructureValue(buffer, bodyPart.getEncoding());
        appendBodyStructureValue(buffer, bodyPart.getSize());
        // line count not implemented in JavaMail, return 0
        appendBodyStructureValue(buffer, 0);
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

    protected void sendSubFolders(String command, String folderPath, boolean recursive) throws IOException {
        try {
            List<ExchangeSession.Folder> folders = session.getSubFolders(folderPath, recursive);
            for (ExchangeSession.Folder folder : folders) {
                sendClient("* " + command + " (" + folder.getFlags() + ") \"/\" \"" + BASE64MailboxEncoder.encode(folder.folderPath) + '\"');
            }
        } catch (HttpForbiddenException e) {
            // access forbidden, ignore
            DavGatewayTray.debug(new BundleMessage("LOG_SUBFOLDER_ACCESS_FORBIDDEN", folderPath));
        } catch (HttpNotFoundException e) {
            // not found, ignore
            DavGatewayTray.debug(new BundleMessage("LOG_FOLDER_NOT_FOUND", folderPath));
        } catch (HttpException e) {
            // other errors, ignore
            DavGatewayTray.debug(new BundleMessage("LOG_FOLDER_ACCESS_ERROR", folderPath, e.getMessage()));
        }
    }

    static final class SearchConditions {
        Boolean flagged;
        Boolean answered;
        Boolean deleted;
        long startUid;
        int startIndex;
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
            appendSearchParam(operator + " NOT ", tokens, tokens.nextToken(), conditions);
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
        } else if (token.startsWith("SENT") || "SINCE".equals(token) || "BEFORE".equals(token)) {
            conditions.append(operator);
            appendDateSearchParam(tokens, token, conditions);
        } else if ("SEEN".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:httpmail:read\" = True");
        } else if ("UNSEEN".equals(token) || "NEW".equals(token)) {
            conditions.append(operator).append("\"urn:schemas:httpmail:read\" = False");
        } else if ("DELETED".equals(token)) {
            conditions.deleted = !operator.endsWith(" NOT ");
        } else if ("UNDELETED".equals(token) || "NOT DELETED".equals(token)) {
            conditions.deleted = Boolean.FALSE;
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
            String namespace;
            if (headerName.startsWith("x-")) {
                // look for extended headers in MAPI namespace
                namespace = "http://schemas.microsoft.com/mapi/string/{00020386-0000-0000-C000-000000000046}/";
            } else {
                // look for standard properties in mailheader namespace
                namespace = "urn:schemas:mailheader:";
            }
            conditions.append(operator).append('"').append(namespace).append(headerName).append("\"='").append(value).append('\'');
        } else if ("UID".equals(token)) {
            String range = tokens.nextToken();
            if ("1:*".equals(range)) {
                // ignore: this is a noop filter
            } else if (range.endsWith(":*")) {
                conditions.startUid = Long.parseLong(range.substring(0, range.indexOf(':')));
            } else {
                throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", range);
            }
        } else if ("OLD".equals(token) || "RECENT".equals(token) || "ALL".equals(token)) {
            // ignore
        } else if (token.indexOf(':') >= 0) {
            // range search
            try {
                conditions.startIndex = Integer.parseInt(token.substring(0, token.indexOf(':')));
            } catch (NumberFormatException e) {
                throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", token);
            }
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
        String searchAttribute;
        if (token.startsWith("SENT")) {
            searchAttribute = "urn:schemas:httpmail:date";
        } else {
            searchAttribute = "DAV:getlastmodified";
        }

        if (token.endsWith("ON")) {
            conditions.append("(\"").append(searchAttribute).append("\" > '")
                    .append(dateFormatter.format(startDate))
                    .append("' AND \"").append(searchAttribute).append("\" < '")
                    .append(dateFormatter.format(endDate))
                    .append("')");
        } else if (token.endsWith("BEFORE")) {
            conditions.append("\"").append(searchAttribute).append("\" < '")
                    .append(dateFormatter.format(startDate))
                    .append('\'');
        } else if (token.endsWith("SINCE")) {
            conditions.append("\"").append(searchAttribute).append("\" >= '")
                    .append(dateFormatter.format(startDate))
                    .append('\'');
        }

    }

    protected void expunge(boolean silent) throws IOException {
        if (currentFolder.messages != null) {
            int index = 1;
            for (ExchangeSession.Message message : currentFolder.messages) {
                if (message.deleted) {
                    message.delete();
                    if (!silent) {
                        sendClient("* " + index + " EXPUNGE");
                    }
                } else {
                    index++;
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
                if ("\\Seen".equals(flag) && message.read) {
                    properties.put("read", "0");
                    message.read = false;
                } else if ("\\Flagged".equals(flag) && message.flagged) {
                    properties.put("flagged", "0");
                    message.flagged = false;
                } else if ("\\Deleted".equals(flag) && message.deleted) {
                    properties.put("deleted", null);
                    message.deleted = false;
                } else if ("Junk".equals(flag) && message.junk) {
                    properties.put("junk", "0");
                    message.junk = false;
                }
            }
        } else if ("+Flags".equalsIgnoreCase(action) || "+FLAGS.SILENT".equalsIgnoreCase(action)) {
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equals(flag) && !message.read) {
                    properties.put("read", "1");
                    message.read = true;
                } else if ("\\Deleted".equals(flag) && !message.deleted) {
                    message.deleted = true;
                    properties.put("deleted", "1");
                } else if ("\\Flagged".equals(flag) && !message.flagged) {
                    properties.put("flagged", "2");
                    message.flagged = true;
                } else if ("\\Answered".equals(flag) && !message.answered) {
                    properties.put("answered", "102");
                    message.answered = true;
                } else if ("$Forwarded".equals(flag) && !message.forwarded) {
                    properties.put("forwarded", "104");
                    message.forwarded = true;
                } else if ("Junk".equals(flag) && !message.junk) {
                    properties.put("junk", "1");
                    message.junk = true;
                }
            }
        } else if ("FLAGS".equalsIgnoreCase(action) || "FLAGS.SILENT".equalsIgnoreCase(action)) {
            // flag list with default values
            boolean read = false;
            boolean deleted = false;
            boolean junk = false;
            boolean flagged = false;
            boolean answered = false;
            boolean forwarded = false;
            // set flags from new flag list
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equals(flag)) {
                    read = true;
                } else if ("\\Deleted".equals(flag)) {
                    deleted = true;
                } else if ("\\Flagged".equals(flag)) {
                    flagged = true;
                } else if ("\\Answered".equals(flag)) {
                    answered = true;
                } else if ("$Forwarded".equals(flag)) {
                    forwarded = true;
                } else if ("Junk".equals(flag)) {
                    junk = true;
                }
            }
            if (read != message.read) {
                message.read = read;
                if (message.read) {
                    properties.put("read", "1");
                } else {
                    properties.put("read", "0");
                }
            }
            if (deleted != message.deleted) {
                message.deleted = deleted;
                if (message.deleted) {
                    properties.put("deleted", "1");
                } else {
                    properties.put("deleted", null);
                }
            }
            if (flagged != message.flagged) {
                message.flagged = flagged;
                if (message.flagged) {
                    properties.put("flagged", "2");
                } else {
                    properties.put("flagged", "0");
                }
            }
            if (answered != message.answered) {
                message.answered = answered;
                if (message.answered) {
                    properties.put("answered", "102");
                } else if (!forwarded) {
                    // remove property only if not forwarded
                    properties.put("answered", null);
                }
            }
            if (forwarded != message.forwarded) {
                message.forwarded = forwarded;
                if (message.forwarded) {
                    properties.put("forwarded", "104");
                } else if (!answered) {
                    // remove property only if not answered
                    properties.put("forwarded", null);
                }
            }
            if (junk != message.junk) {
                message.junk = junk;
                if (message.junk) {
                    properties.put("junk", "1");
                } else {
                    properties.put("junk", "0");
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
    private static final class PartOutputStream extends FilterOutputStream {
        private static final int START = 0;
        private static final int CR = 1;
        private static final int CRLF = 2;
        private static final int CRLFCR = 3;
        private static final int BODY = 4;

        private int state = START;
        private int size;
        private int bufferSize;
        private final boolean writeHeaders;
        private final boolean writeBody;
        private final int startIndex;
        private final int maxSize;

        private PartOutputStream(OutputStream os, boolean writeHeaders, boolean writeBody,
                                 int startIndex, int maxSize) {
            super(os);
            this.writeHeaders = writeHeaders;
            this.writeBody = writeBody;
            this.startIndex = startIndex;
            this.maxSize = maxSize;
        }

        @Override
        public void write(int b) throws IOException {
            size++;
            if (((state != BODY && writeHeaders) || (state == BODY && writeBody)) &&
                    (size > startIndex) && (bufferSize < maxSize)
                    ) {
                super.write(b);
                bufferSize++;
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

    /**
     * Partial output stream, start at startIndex and write maxSize bytes.
     */
    private static final class PartialOutputStream extends FilterOutputStream {
        private int size;
        private int bufferSize;
        private final int startIndex;
        private final int maxSize;

        private PartialOutputStream(OutputStream os, int startIndex, int maxSize) {
            super(os);
            this.startIndex = startIndex;
            this.maxSize = maxSize;
        }

        @Override
        public void write(int b) throws IOException {
            size++;
            if ((size > startIndex) && (bufferSize < maxSize)) {
                super.write(b);
                bufferSize++;
            }
        }
    }

    protected abstract static class AbstractRangeIterator implements Iterator<ExchangeSession.Message> {
        ExchangeSession.MessageList messages;
        int currentIndex;

        protected int getCurrentIndex() {
            return currentIndex;
        }
    }

    protected static class UIDRangeIterator extends AbstractRangeIterator {
        final String[] ranges;
        int currentRangeIndex;
        long startUid;
        long endUid;

        protected UIDRangeIterator(ExchangeSession.MessageList messages, String value) {
            this.messages = messages;
            ranges = value.split(",");
        }

        protected long convertToLong(String value) {
            if ("*".equals(value)) {
                return Long.MAX_VALUE;
            } else {
                return Long.parseLong(value);
            }
        }

        protected void skipToNextRangeStartUid() {
            if (currentRangeIndex < ranges.length) {
                String currentRange = ranges[currentRangeIndex++];
                int colonIndex = currentRange.indexOf(':');
                if (colonIndex > 0) {
                    startUid = convertToLong(currentRange.substring(0, colonIndex));
                    endUid = convertToLong(currentRange.substring(colonIndex + 1));
                    if (endUid < startUid) {
                        long swap = endUid;
                        endUid = startUid;
                        startUid = swap;
                    }
                } else {
                    startUid = endUid = convertToLong(currentRange);
                }
                while (currentIndex < messages.size() && messages.get(currentIndex).getImapUid() < startUid) {
                    currentIndex++;
                }
            } else {
                currentIndex = messages.size();
            }
        }

        protected boolean hasNextInRange() {
            return hasNextIndex() && messages.get(currentIndex).getImapUid() <= endUid;
        }

        protected boolean hasNextIndex() {
            return currentIndex < messages.size();
        }

        protected boolean hasNextRange() {
            return currentRangeIndex < ranges.length;
        }

        public boolean hasNext() {
            boolean hasNextInRange = hasNextInRange();
            // if has next range and current index after current range end, reset index
            if (hasNextRange() && !hasNextInRange) {
                currentIndex = 0;
            }
            while (hasNextIndex() && !hasNextInRange) {
                skipToNextRangeStartUid();
                hasNextInRange = hasNextInRange();
            }
            return hasNextIndex();
        }

        public ExchangeSession.Message next() {
            ExchangeSession.Message message = messages.get(currentIndex++);
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

    protected static class RangeIterator extends AbstractRangeIterator {
        final String[] ranges;
        int currentRangeIndex;
        long startUid;
        long endUid;

        protected RangeIterator(ExchangeSession.MessageList messages, String value) {
            this.messages = messages;
            ranges = value.split(",");
        }

        protected long convertToLong(String value) {
            if ("*".equals(value)) {
                return Long.MAX_VALUE;
            } else {
                return Long.parseLong(value);
            }
        }

        protected void skipToNextRangeStart() {
            if (currentRangeIndex < ranges.length) {
                String currentRange = ranges[currentRangeIndex++];
                int colonIndex = currentRange.indexOf(':');
                if (colonIndex > 0) {
                    startUid = convertToLong(currentRange.substring(0, colonIndex));
                    endUid = convertToLong(currentRange.substring(colonIndex + 1));
                    if (endUid < startUid) {
                        long swap = endUid;
                        endUid = startUid;
                        startUid = swap;
                    }
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

        protected boolean hasNextInRange() {
            return hasNextIndex() && currentIndex < endUid;
        }

        protected boolean hasNextIndex() {
            return currentIndex < messages.size();
        }

        protected boolean hasNextRange() {
            return currentRangeIndex < ranges.length;
        }

        public boolean hasNext() {
            boolean hasNextInRange = hasNextInRange();
            // if has next range and current index after current range end, reset index
            if (hasNextRange() && !hasNextInRange) {
                currentIndex = 0;
            }
            while (hasNextIndex() && !hasNextInRange) {
                skipToNextRangeStart();
                hasNextInRange = hasNextInRange();
            }
            return hasNextIndex();
        }

        public ExchangeSession.Message next() {
            return messages.get(currentIndex++);
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
                    && (nextToken.charAt(nextToken.length() - 1) != '"' || nextToken.length() == 1)) {
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

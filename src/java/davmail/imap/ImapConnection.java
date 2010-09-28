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
import davmail.Settings;
import davmail.exception.DavMailException;
import davmail.exception.HttpForbiddenException;
import davmail.exception.HttpNotFoundException;
import davmail.exception.InsufficientStorageException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.commons.httpclient.HttpException;
import org.apache.log4j.Logger;

import javax.mail.MessagingException;
import javax.mail.internet.*;
import javax.mail.util.SharedByteArrayInputStream;
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
    private static final Logger LOGGER = Logger.getLogger(ImapConnection.class);

    protected String baseMailboxPath;
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
        final String capabilities;
        int imapIdleDelay = Settings.getIntProperty("davmail.imapIdleDelay") * 60;
        if (imapIdleDelay > 0) {
            capabilities = "CAPABILITY IMAP4REV1 AUTH=LOGIN IDLE";
        } else {
            capabilities = "CAPABILITY IMAP4REV1 AUTH=LOGIN";
        }

        String line;
        String commandId = null;
        IMAPTokenizer tokens;
        try {
            ExchangeSessionFactory.checkConfig();
            sendClient("* OK [" + capabilities + "] IMAP4rev1 DavMail server ready");
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
                            sendClient("* " + capabilities);
                            sendClient(commandId + " OK CAPABILITY completed");
                        } else if ("login".equalsIgnoreCase(command)) {
                            parseCredentials(tokens);
                            // detect shared mailbox access 
                            splitUserName();
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
                                        // detect shared mailbox access
                                        splitUserName();
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
                                        String folderContext;
                                        if (baseMailboxPath == null) {
                                            folderContext = BASE64MailboxDecoder.decode(tokens.nextToken());
                                        } else {
                                            folderContext = baseMailboxPath + BASE64MailboxDecoder.decode(tokens.nextToken());
                                        }
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
                                        if (baseMailboxPath != null && !folderName.startsWith("/")) {
                                            folderName = baseMailboxPath + folderName;
                                        }
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
                                            sendClient("* OK [PERMANENTFLAGS (\\Answered \\Deleted \\Draft \\Flagged \\Seen $Forwarded Junk)]");
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
                                    if (expunge(false)) {
                                        // need to refresh folder to avoid 404 errors
                                        session.refreshFolder(currentFolder);
                                    }
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
                                        sendClient(commandId + " OK folder deleted");
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
                                                        } catch (SocketException e) {
                                                            // client closed connection
                                                            throw e;
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
                                            if (uidList.isEmpty()) {
                                                sendClient("* SEARCH");
                                            } else {
                                                for (long uid : uidList) {
                                                    sendClient("* SEARCH " + uid);
                                                }
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
                                        if (uidList.isEmpty()) {
                                            sendClient("* SEARCH");
                                        } else {
                                            int currentIndex = 0;
                                            for (ExchangeSession.Message message : currentFolder.messages) {
                                                currentIndex++;
                                                if (uidList.contains(message.getImapUid())) {
                                                    sendClient("* SEARCH " + currentIndex);
                                                }
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
                                        // parse flags, on create read and draft flags are on the
                                        // same messageFlags property, 8 means draft and 1 means read
                                        StringTokenizer flagtokenizer = new StringTokenizer(flags);
                                        while (flagtokenizer.hasMoreTokens()) {
                                            String flag = flagtokenizer.nextToken();
                                            if ("\\Seen".equals(flag)) {
                                                if (properties.containsKey("draft")) {
                                                    // draft message, add read flag
                                                    properties.put("draft", "9");
                                                } else {
                                                    // not (yet) draft, set read flag
                                                    properties.put("draft", "1");
                                                }
                                            } else if ("\\Flagged".equals(flag)) {
                                                properties.put("flagged", "2");
                                            } else if ("\\Answered".equals(flag)) {
                                                properties.put("answered", "102");
                                            } else if ("$Forwarded".equals(flag)) {
                                                properties.put("forwarded", "104");
                                            } else if ("\\Draft".equals(flag)) {
                                                if (properties.containsKey("draft")) {
                                                    // read message, add draft flag
                                                    properties.put("draft", "9");
                                                } else {
                                                    // not (yet) read, set draft flag
                                                    properties.put("draft", "8");
                                                }
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
                                    byte[] buffer = in.readContent(size);
                                    // empty line
                                    readClient();
                                    MimeMessage mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(buffer));

                                    String messageName = UUID.randomUUID().toString() + ".EML";
                                    try {
                                        session.createMessage(folderName, messageName, properties, mimeMessage);
                                        sendClient(commandId + " OK APPEND completed");
                                    } catch (InsufficientStorageException e) {
                                        sendClient(commandId + " NO " + e.getMessage());
                                    }
                                } else if ("idle".equalsIgnoreCase(command) && imapIdleDelay > 0) {
                                    if (currentFolder != null) {
                                        sendClient("+ idling ");
                                        // clear cache before going to idle mode
                                        currentFolder.clearCache();
                                        DavGatewayTray.resetIcon();
                                        try {
                                            int count = 0;
                                            while (in.available() == 0) {
                                                if (++count >= imapIdleDelay) {
                                                    count = 0;
                                                    List<Long> previousImapUidList = currentFolder.getImapUidList();
                                                    if (session.refreshFolder(currentFolder)) {
                                                        handleRefresh(previousImapUidList, currentFolder.getImapUidList());
                                                    }
                                                }
                                                // sleep 1 second
                                                Thread.sleep(1000);
                                            }
                                            // read DONE line
                                            line = readClient();
                                            if ("DONE".equals(line)) {
                                                sendClient(commandId + " OK " + command + " terminated");
                                            } else {
                                                sendClient(commandId + " BAD command unrecognized");
                                            }
                                        } catch (IOException e) {
                                            // client connection closed
                                            throw new SocketException(e.getMessage());
                                        }
                                    } else {
                                        sendClient(commandId + " NO no folder selected");
                                    }
                                } else if ("noop".equalsIgnoreCase(command) || "check".equalsIgnoreCase(command)) {
                                    if (currentFolder != null) {
                                        DavGatewayTray.debug(new BundleMessage("LOG_IMAP_COMMAND", command, currentFolder.folderPath));
                                        List<Long> previousImapUidList = currentFolder.getImapUidList();
                                        if (session.refreshFolder(currentFolder)) {
                                            handleRefresh(previousImapUidList, currentFolder.getImapUidList());
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
            LOGGER.warn(BundleMessage.formatLog("LOG_CLIENT_CLOSED_CONNECTION"));
        } catch (Exception e) {
            DavGatewayTray.log(e);
            try {
                String message = ((e.getMessage() == null) ? e.toString() : e.getMessage()).replaceAll("\\n", " ");
                if (commandId != null) {
                    sendClient(commandId + " BAD unable to handle request: " + message);
                } else {
                    sendClient("* BAD unable to handle request: " + message);
                }
            } catch (IOException e2) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_SENDING_ERROR_TO_CLIENT"), e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    /**
     * Detect shared mailbox access.
     * see http://msexchangeteam.com/archive/2004/03/31/105275.aspx
     */
    protected void splitUserName() {
        String[] tokens = null;
        if (userName.indexOf('/') >= 0) {
            tokens = userName.split("/");
        } else if (userName.indexOf('\\') >= 0) {
            tokens = userName.split("\\\\");
        }

        if (tokens != null && tokens.length == 3) {
            userName = tokens[0] + '\\' + tokens[1];
            baseMailboxPath = "/users/" + tokens[2] + '/';
        }
    }

    /**
     * Send expunge untagged response for removed IMAP message uids.
     *
     * @param previousImapUidList uid list before refresh
     * @param imapUidList         uid list after refresh
     * @throws IOException on error
     */
    private void handleRefresh(List<Long> previousImapUidList, List<Long> imapUidList) throws IOException {
        //
        int index = 1;
        for (long previousImapUid : previousImapUidList) {
            if (!imapUidList.contains(previousImapUid)) {
                sendClient("* " + index + " EXPUNGE");
            } else {
                index++;
            }
        }
        sendClient("* " + currentFolder.count() + " EXISTS");
        sendClient("* " + currentFolder.count() + " RECENT");
    }

    private void handleFetch(ExchangeSession.Message message, int currentIndex, String parameters) throws IOException, MessagingException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("* ").append(currentIndex).append(" FETCH (UID ").append(message.getImapUid());
        if (parameters != null) {
            StringTokenizer paramTokens = new StringTokenizer(parameters);
            while (paramTokens.hasMoreTokens()) {
                @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
                String param = paramTokens.nextToken();
                if ("FLAGS".equals(param)) {
                    buffer.append(" FLAGS (").append(message.getImapFlags()).append(')');
                } else if ("RFC822.SIZE".equals(param)) {
                    buffer.append(" RFC822.SIZE ").append(message.getMimeMessageSize());
                } else if ("ENVELOPE".equals(param)) {
                    appendEnvelope(buffer, message);
                } else if ("BODYSTRUCTURE".equals(param)) {
                    appendBodyStructure(buffer, message);
                } else if ("INTERNALDATE".equals(param) && message.date != null && message.date.length() > 0) {
                    try {
                        SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        dateParser.setTimeZone(ExchangeSession.GMT_TIMEZONE);
                        Date date = ExchangeSession.getZuluDateFormat().parse(message.date);
                        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", Locale.ENGLISH);
                        buffer.append(" INTERNALDATE \"").append(dateFormatter.format(date)).append('\"');
                    } catch (ParseException e) {
                        throw new DavMailException("EXCEPTION_INVALID_DATE", message.date);
                    }
                } else if (param.startsWith("BODY[") || param.startsWith("BODY.PEEK[") || "RFC822.HEADER".equals(param)) {
                    // get full param
                    if (param.indexOf('[') >= 0) {
                        StringBuilder paramBuffer = new StringBuilder(param);
                        while (paramTokens.hasMoreTokens() && param.indexOf(']') < 0) {
                            paramBuffer.append(' ').append(paramTokens.nextToken());
                        }
                        param = paramBuffer.toString();
                    }
                    // parse buffer size
                    int startIndex = 0;
                    int maxSize = Integer.MAX_VALUE;
                    int ltIndex = param.indexOf('<');
                    if (ltIndex >= 0) {
                        int dotIndex = param.indexOf('.', ltIndex);
                        if (dotIndex >= 0) {
                            startIndex = Integer.parseInt(param.substring(ltIndex + 1, dotIndex));
                            maxSize = Integer.parseInt(param.substring(dotIndex + 1, param.indexOf('>')));
                        }
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    InputStream partInputStream = null;
                    OutputStream partOutputStream = null;

                    // load message
                    MimeMessage mimeMessage = message.getMimeMessage();
                    // try to parse message part index
                    String partIndexString = StringUtil.getToken(param, "[", "]");
                    if ("".equals(partIndexString)) {
                        // write message with headers
                        partOutputStream = new PartialOutputStream(baos, startIndex, maxSize);
                        partInputStream = message.getRawInputStream();
                    } else if ("TEXT".equals(partIndexString)) {
                        // write message without headers
                        partOutputStream = new PartialOutputStream(baos, startIndex, maxSize);
                        partInputStream = mimeMessage.getRawInputStream();
                    } else if ("RFC822.HEADER".equals(param) || partIndexString.startsWith("HEADER")) {
                        // Header requested fetch  headers
                        String[] requestedHeaders = getRequestedHeaders(partIndexString);
                        if (requestedHeaders != null) {
                            Enumeration headerEnumeration = message.getMimeMessage().getMatchingHeaderLines(requestedHeaders);
                            while (headerEnumeration.hasMoreElements()) {
                                baos.write(((String) headerEnumeration.nextElement()).getBytes("UTF-8"));
                                baos.write(13);
                                baos.write(10);
                            }
                        } else {
                            // write headers only
                            partOutputStream = new PartOutputStream(baos, true, false, startIndex, maxSize);
                            partInputStream = message.getRawInputStream();
                        }
                    } else {
                        MimePart bodyPart = mimeMessage;
                        String[] partIndexStrings = partIndexString.split("\\.");
                        for (String subPartIndexString : partIndexStrings) {
                            // ignore MIME subpart index, will return full part
                            if ("MIME".equals(subPartIndexString)) {
                                break;
                            }
                            int subPartIndex;
                            // try to parse part index
                            try {
                                subPartIndex = Integer.parseInt(subPartIndexString);
                            } catch (NumberFormatException e) {
                                throw new DavMailException("EXCEPTION_INVALID_PARAMETER", param);
                            }

                            Object mimeBody = bodyPart.getContent();
                            if (mimeBody instanceof MimeMultipart) {
                                MimeMultipart multiPart = (MimeMultipart) mimeBody;
                                if (subPartIndex - 1 < multiPart.getCount()) {
                                    bodyPart = (MimePart) multiPart.getBodyPart(subPartIndex - 1);
                                } else {
                                    throw new DavMailException("EXCEPTION_INVALID_PARAMETER", param);
                                }
                            } else if (subPartIndex != 1) {
                                throw new DavMailException("EXCEPTION_INVALID_PARAMETER", param);
                            }
                        }

                        // write selected part, without headers
                        partOutputStream = new PartialOutputStream(baos, startIndex, maxSize);
                        if (bodyPart instanceof MimeMessage) {
                            partInputStream = ((MimeMessage) bodyPart).getRawInputStream();
                        } else {
                            partInputStream = ((MimeBodyPart) bodyPart).getRawInputStream();
                        }
                    }

                    // copy selected content to baos
                    if (partInputStream != null && partOutputStream != null) {
                        IOUtil.write(partInputStream, partOutputStream);
                        partInputStream.close();
                        partOutputStream.close();
                    }
                    baos.close();

                    if ("RFC822.HEADER".equals(param)) {
                        buffer.append(" RFC822.HEADER ");
                    } else {
                        buffer.append(" BODY[").append(partIndexString).append(']');
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
                }
            }
        }
        buffer.append(')');
        sendClient(buffer.toString());
        // do not keep message content in memory
        message.dropMimeMessage();
    }

    protected String[] getRequestedHeaders(String partIndexString) {
        int startIndex = partIndexString.indexOf('(');
        int endIndex = partIndexString.indexOf(')');
        if (startIndex >= 0 && endIndex >= 0) {
            return partIndexString.substring(startIndex + 1, endIndex - 1).split(" ");
        } else {
            return null;
        }
    }

    protected void handleStore(String commandId, AbstractRangeIterator rangeIterator, String action, String flags) throws IOException {
        while (rangeIterator.hasNext()) {
            DavGatewayTray.switchIcon();
            ExchangeSession.Message message = rangeIterator.next();
            updateFlags(message, action, flags);
            sendClient("* " + (rangeIterator.getCurrentIndex()) + " FETCH (UID " + message.getImapUid() + " FLAGS (" + (message.getImapFlags()) + "))");
        }
        // auto expunge
        if (Settings.getBooleanProperty("davmail.imapAutoExpunge")) {
            if (expunge(false)) {
                session.refreshFolder(currentFolder);
            }
        }
        sendClient(commandId + " OK STORE completed");
    }

    protected ExchangeSession.Condition buildConditions(SearchConditions conditions, IMAPTokenizer tokens) throws IOException {
        ExchangeSession.MultiCondition condition = null;
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextQuotedToken().toUpperCase();
            if (token.startsWith("(") && token.endsWith(")")) {
                // quoted search param
                if (condition == null) {
                    condition = session.and();
                }
                condition.add(buildConditions(conditions, new IMAPTokenizer(token.substring(1, token.length() - 1))));
            } else if ("OR".equals(token)) {
                condition = session.or();
            } else if (token.startsWith("OR ")) {
                condition = appendOrSearchParams(token, conditions);
            } else {
                if (condition == null) {
                    condition = session.and();
                }
                condition.add(appendSearchParam(tokens, token, conditions));
            }
        }
        return condition;
    }


    protected List<Long> handleSearch(IMAPTokenizer tokens) throws IOException {
        List<Long> uidList = new ArrayList<Long>();
        SearchConditions conditions = new SearchConditions();
        ExchangeSession.Condition condition = buildConditions(conditions, tokens);
        ExchangeSession.MessageList localMessages = currentFolder.searchMessages(condition);
        Iterator<ExchangeSession.Message> iterator;
        if (conditions.uidRange != null) {
            iterator = new UIDRangeIterator(localMessages, conditions.uidRange);
        } else if (conditions.indexRange != null) {
            iterator = new RangeIterator(localMessages, conditions.indexRange);
        } else {
            iterator = localMessages.iterator();
        }
        while (iterator.hasNext()) {
            ExchangeSession.Message message = iterator.next();
            if ((conditions.flagged == null || message.flagged == conditions.flagged)
                    && (conditions.answered == null || message.answered == conditions.answered)) {
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
                appendBodyStructure(buffer, (MimeMultipart) mimeBody);
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

    protected void appendBodyStructure(StringBuilder buffer, MimeMultipart multiPart) throws IOException, MessagingException {
        buffer.append('(');

        for (int i = 0; i < multiPart.getCount(); i++) {
            MimeBodyPart bodyPart = (MimeBodyPart) multiPart.getBodyPart(i);
            Object mimeBody = bodyPart.getContent();
            if (mimeBody instanceof MimeMultipart) {
                appendBodyStructure(buffer, (MimeMultipart) mimeBody);
            } else {
                // no multipart, single body
                appendBodyStructure(buffer, bodyPart);
            }
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

    /**
     * client side search conditions
     */
    static final class SearchConditions {
        Boolean flagged;
        Boolean answered;
        String indexRange;
        String uidRange;
    }

    protected ExchangeSession.MultiCondition appendOrSearchParams(String token, SearchConditions conditions) throws IOException {
        ExchangeSession.MultiCondition orCondition = session.or();
        IMAPTokenizer innerTokens = new IMAPTokenizer(token);
        innerTokens.nextToken();
        while (innerTokens.hasMoreTokens()) {
            String innerToken = innerTokens.nextToken();
            orCondition.add(appendSearchParam(innerTokens, innerToken, conditions));
        }
        return orCondition;
    }

    protected ExchangeSession.Condition appendSearchParam(StringTokenizer tokens, String token, SearchConditions conditions) throws IOException {
        if ("NOT".equals(token)) {
            String nextToken = tokens.nextToken();
            if ("DELETED".equals(nextToken)) {
                // conditions.deleted = Boolean.FALSE;
                return session.isNull("deleted");
            } else {
                return session.not(appendSearchParam(tokens, nextToken, conditions));
            }
        } else if (token.startsWith("OR ")) {
            return appendOrSearchParams(token, conditions);
        } else if ("SUBJECT".equals(token)) {
            return session.contains("subject", tokens.nextToken());
        } else if ("BODY".equals(token)) {
            return session.contains("body", tokens.nextToken());
        } else if ("FROM".equals(token)) {
            return session.contains("from", tokens.nextToken());
        } else if ("TO".equals(token)) {
            return session.contains("to", tokens.nextToken());
        } else if ("CC".equals(token)) {
            return session.contains("cc", tokens.nextToken());
        } else if ("LARGER".equals(token)) {
            return session.gte("messageSize", tokens.nextToken());
        } else if ("SMALLER".equals(token)) {
            return session.lt("messageSize", tokens.nextToken());
        } else if (token.startsWith("SENT") || "SINCE".equals(token) || "BEFORE".equals(token)) {
            return appendDateSearchParam(tokens, token);
        } else if ("SEEN".equals(token)) {
            return session.isTrue("read");
        } else if ("UNSEEN".equals(token) || "NEW".equals(token)) {
            return session.isFalse("read");
        } else if ("DELETED".equals(token)) {
            // conditions.deleted = Boolean.TRUE;
            return session.isEqualTo("deleted", "1");
        } else if ("UNDELETED".equals(token) || "NOT DELETED".equals(token)) {
            // conditions.deleted = Boolean.FALSE;
            return session.isNull("deleted");
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
            return session.headerIsEqualTo(headerName, value);
        } else if ("UID".equals(token)) {
            String range = tokens.nextToken();
            if ("1:*".equals(range)) {
                // ignore: this is a noop filter
            } else {
                conditions.uidRange = range;
            }
        } else if ("OLD".equals(token) || "RECENT".equals(token) || "ALL".equals(token)) {
            // ignore
        } else if (token.indexOf(':') >= 0) {
            // range search
            conditions.indexRange = token;
        } else {
            throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", token);
        }
        // client side search token
        return null;
    }

    protected ExchangeSession.Condition appendDateSearchParam(StringTokenizer tokens, String token) throws IOException {
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
            searchAttribute = "date";
        } else {
            searchAttribute = "lastmodified";
        }

        if (token.endsWith("ON")) {
            return session.and(session.gt(searchAttribute, dateFormatter.format(startDate)),
                    session.lt(searchAttribute, dateFormatter.format(endDate)));
        } else if (token.endsWith("BEFORE")) {
            return session.lt(searchAttribute, dateFormatter.format(startDate));
        } else if (token.endsWith("SINCE")) {
            return session.gte(searchAttribute, dateFormatter.format(startDate));
        } else {
            throw new DavMailException("EXCEPTION_INVALID_SEARCH_PARAMETERS", dateToken);
        }
    }

    protected boolean expunge(boolean silent) throws IOException {
        boolean hasDeleted = false;
        if (currentFolder.messages != null) {
            int index = 1;
            for (ExchangeSession.Message message : currentFolder.messages) {
                if (message.deleted) {
                    message.delete();
                    hasDeleted = true;
                    if (!silent) {
                        sendClient("* " + index + " EXPUNGE");
                    }
                } else {
                    index++;
                }
            }
        }
        return hasDeleted;
    }

    protected void updateFlags(ExchangeSession.Message message, String action, String flags) throws IOException {
        HashMap<String, String> properties = new HashMap<String, String>();
        if ("-Flags".equalsIgnoreCase(action) || "-FLAGS.SILENT".equalsIgnoreCase(action)) {
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equalsIgnoreCase(flag) && message.read) {
                    properties.put("read", "0");
                    message.read = false;
                } else if ("\\Flagged".equalsIgnoreCase(flag) && message.flagged) {
                    properties.put("flagged", "0");
                    message.flagged = false;
                } else if ("\\Deleted".equalsIgnoreCase(flag) && message.deleted) {
                    properties.put("deleted", null);
                    message.deleted = false;
                } else if ("Junk".equalsIgnoreCase(flag) && message.junk) {
                    properties.put("junk", "0");
                    message.junk = false;
                } else if ("$Forwarded".equalsIgnoreCase(flag) && message.forwarded) {
                    properties.put("forwarded", null);
                    message.forwarded = false;
                } else if ("\\Answered".equalsIgnoreCase(flag) && message.answered) {
                    properties.put("answered", null);
                    message.answered = false;
                }
            }
        } else if ("+Flags".equalsIgnoreCase(action) || "+FLAGS.SILENT".equalsIgnoreCase(action)) {
            StringTokenizer flagtokenizer = new StringTokenizer(flags);
            while (flagtokenizer.hasMoreTokens()) {
                String flag = flagtokenizer.nextToken();
                if ("\\Seen".equalsIgnoreCase(flag) && !message.read) {
                    properties.put("read", "1");
                    message.read = true;
                } else if ("\\Deleted".equalsIgnoreCase(flag) && !message.deleted) {
                    message.deleted = true;
                    properties.put("deleted", "1");
                } else if ("\\Flagged".equalsIgnoreCase(flag) && !message.flagged) {
                    properties.put("flagged", "2");
                    message.flagged = true;
                } else if ("\\Answered".equalsIgnoreCase(flag) && !message.answered) {
                    properties.put("answered", "102");
                    message.answered = true;
                } else if ("$Forwarded".equalsIgnoreCase(flag) && !message.forwarded) {
                    properties.put("forwarded", "104");
                    message.forwarded = true;
                } else if ("Junk".equalsIgnoreCase(flag) && !message.junk) {
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
                if ("\\Seen".equalsIgnoreCase(flag)) {
                    read = true;
                } else if ("\\Deleted".equalsIgnoreCase(flag)) {
                    deleted = true;
                } else if ("\\Flagged".equalsIgnoreCase(flag)) {
                    flagged = true;
                } else if ("\\Answered".equalsIgnoreCase(flag)) {
                    answered = true;
                } else if ("$Forwarded".equalsIgnoreCase(flag)) {
                    forwarded = true;
                } else if ("Junk".equalsIgnoreCase(flag)) {
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
                } else if ("*".equals(currentRange)) {
                    startUid = endUid = messages.get(messages.size() - 1).getImapUid();
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
                } else if ("*".equals(currentRange)) {
                    startUid = endUid = messages.size();
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

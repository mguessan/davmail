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
package davmail.exchange;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.DavMailException;
import davmail.exception.HttpNotFoundException;
import davmail.http.URIUtil;
import davmail.ui.NotificationDialog;
import davmail.util.StringUtil;
import org.apache.log4j.Logger;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exchange session through Outlook Web Access (DAV)
 */
public abstract class ExchangeSession {

    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

    /**
     * Reference GMT timezone to format dates
     */
    public static final SimpleTimeZone GMT_TIMEZONE = new SimpleTimeZone(0, "GMT");

    protected static final int FREE_BUSY_INTERVAL = 15;

    protected static final String PUBLIC_ROOT = "/public/";
    protected static final String CALENDAR = "calendar";
    protected static final String TASKS = "tasks";
    /**
     * Contacts folder logical name
     */
    public static final String CONTACTS = "contacts";
    protected static final String ADDRESSBOOK = "addressbook";
    protected static final String INBOX = "INBOX";
    protected static final String LOWER_CASE_INBOX = "inbox";
    protected static final String MIXED_CASE_INBOX = "Inbox";
    protected static final String SENT = "Sent";
    protected static final String SENDMSG = "##DavMailSubmissionURI##";
    protected static final String DRAFTS = "Drafts";
    protected static final String TRASH = "Trash";
    protected static final String JUNK = "Junk";
    protected static final String UNSENT = "Unsent Messages";

    protected static final List<String> SPECIAL = Arrays.asList(SENT, DRAFTS, TRASH, JUNK);

    static {
        // Adjust Mime decoder settings
        System.setProperty("mail.mime.ignoreunknownencoding", "true");
        System.setProperty("mail.mime.decodetext.strict", "false");
    }

    protected String publicFolderUrl;

    /**
     * Base user mailboxes path (used to select folder)
     */
    protected String mailPath;
    protected String rootPath;
    protected String email;
    protected String alias;
    /**
     * Lower case Caldav path to current user mailbox.
     * /users/<i>email</i>
     */
    protected String currentMailboxPath;

    protected String userName;

    protected String serverVersion;

    protected static final String YYYY_MM_DD_HH_MM_SS = "yyyy/MM/dd HH:mm:ss";
    private static final String YYYYMMDD_T_HHMMSS_Z = "yyyyMMdd'T'HHmmss'Z'";
    protected static final String YYYY_MM_DD_T_HHMMSS_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String YYYY_MM_DD = "yyyy-MM-dd";
    private static final String YYYY_MM_DD_T_HHMMSS_SSS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public ExchangeSession() {
        // empty constructor
    }

    /**
     * Close session.
     * Shutdown http client connection manager
     */
    public abstract void close();

    /**
     * Format date to exchange search format.
     *
     * @param date date object
     * @return formatted search date
     */
    public abstract String formatSearchDate(Date date);

    /**
     * Return standard zulu date formatter.
     *
     * @return zulu date formatter
     */
    public static SimpleDateFormat getZuluDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYYMMDD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected static SimpleDateFormat getVcardBdayFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected static SimpleDateFormat getExchangeDateFormat(String value) {
        SimpleDateFormat dateFormat;
        if (value.length() == 8) {
            dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
            dateFormat.setTimeZone(GMT_TIMEZONE);
        } else if (value.length() == 15) {
            dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH);
            dateFormat.setTimeZone(GMT_TIMEZONE);
        } else if (value.length() == 16) {
            dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
            dateFormat.setTimeZone(GMT_TIMEZONE);
        } else {
            dateFormat = ExchangeSession.getExchangeZuluDateFormat();
        }
        return dateFormat;
    }

    protected static SimpleDateFormat getExchangeZuluDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected static SimpleDateFormat getExchangeZuluDateFormatMillisecond() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_SSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected static Date parseDate(String dateString) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat.parse(dateString);
    }


    /**
     * Test if the session expired.
     *
     * @return true this session expired
     * @throws NoRouteToHostException on error
     * @throws UnknownHostException   on error
     */
    public boolean isExpired() throws NoRouteToHostException, UnknownHostException {
        boolean isExpired = false;
        try {
            getFolder("");
        } catch (UnknownHostException | NoRouteToHostException exc) {
            throw exc;
        } catch (IOException e) {
            isExpired = true;
        }

        return isExpired;
    }

    protected abstract void buildSessionInfo(java.net.URI uri) throws IOException;

    /**
     * Create message in specified folder.
     * Will overwrite an existing message with same subject in the same folder
     *
     * @param folderPath  Exchange folder path
     * @param messageName message name
     * @param properties  message properties (flags)
     * @param mimeMessage MIME message
     * @throws IOException when unable to create message
     */
    public abstract Message createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException;

    /**
     * Update given properties on message.
     *
     * @param message    Exchange message
     * @param properties Webdav properties map
     * @throws IOException on error
     */
    public abstract void updateMessage(Message message, Map<String, String> properties) throws IOException;


    /**
     * Delete Exchange message.
     *
     * @param message Exchange message
     * @throws IOException on error
     */
    public abstract void deleteMessage(Message message) throws IOException;

    /**
     * Get raw MIME message content
     *
     * @param message Exchange message
     * @return message body
     * @throws IOException on error
     */
    protected abstract byte[] getContent(Message message) throws IOException;

    protected static final Set<String> POP_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        POP_MESSAGE_ATTRIBUTES.add("uid");
        POP_MESSAGE_ATTRIBUTES.add("imapUid");
        POP_MESSAGE_ATTRIBUTES.add("messageSize");
    }

    /**
     * Return folder message list with id and size only (for POP3 listener).
     *
     * @param folderName Exchange folder name
     * @return folder message list
     * @throws IOException on error
     */
    public MessageList getAllMessageUidAndSize(String folderName) throws IOException {
        return searchMessages(folderName, POP_MESSAGE_ATTRIBUTES, null);
    }

    protected static final Set<String> IMAP_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        IMAP_MESSAGE_ATTRIBUTES.add("permanenturl");
        IMAP_MESSAGE_ATTRIBUTES.add("urlcompname");
        IMAP_MESSAGE_ATTRIBUTES.add("uid");
        IMAP_MESSAGE_ATTRIBUTES.add("messageSize");
        IMAP_MESSAGE_ATTRIBUTES.add("imapUid");
        IMAP_MESSAGE_ATTRIBUTES.add("junk");
        IMAP_MESSAGE_ATTRIBUTES.add("flagStatus");
        IMAP_MESSAGE_ATTRIBUTES.add("messageFlags");
        IMAP_MESSAGE_ATTRIBUTES.add("lastVerbExecuted");
        IMAP_MESSAGE_ATTRIBUTES.add("read");
        IMAP_MESSAGE_ATTRIBUTES.add("deleted");
        IMAP_MESSAGE_ATTRIBUTES.add("date");
        IMAP_MESSAGE_ATTRIBUTES.add("lastmodified");
        // OSX IMAP requests content-class
        IMAP_MESSAGE_ATTRIBUTES.add("contentclass");
        IMAP_MESSAGE_ATTRIBUTES.add("keywords");
    }

    protected static final Set<String> UID_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        UID_MESSAGE_ATTRIBUTES.add("uid");
    }

    /**
     * Get all folder messages.
     *
     * @param folderPath Exchange folder name
     * @return message list
     * @throws IOException on error
     */
    public MessageList searchMessages(String folderPath) throws IOException {
        return searchMessages(folderPath, IMAP_MESSAGE_ATTRIBUTES, null);
    }

    /**
     * Search folder for messages matching conditions, with attributes needed by IMAP listener.
     *
     * @param folderName Exchange folder name
     * @param condition  search filter
     * @return message list
     * @throws IOException on error
     */
    public MessageList searchMessages(String folderName, Condition condition) throws IOException {
        return searchMessages(folderName, IMAP_MESSAGE_ATTRIBUTES, condition);
    }

    /**
     * Search folder for messages matching conditions, with given attributes.
     *
     * @param folderName Exchange folder name
     * @param attributes requested Webdav attributes
     * @param condition  search filter
     * @return message list
     * @throws IOException on error
     */
    public abstract MessageList searchMessages(String folderName, Set<String> attributes, Condition condition) throws IOException;

    /**
     * Get server version (Exchange2003, Exchange2007 or Exchange2010)
     *
     * @return server version
     */
    public String getServerVersion() {
        return serverVersion;
    }

    public enum Operator {
        Or, And, Not, IsEqualTo,
        IsGreaterThan, IsGreaterThanOrEqualTo,
        IsLessThan, IsLessThanOrEqualTo,
        IsNull, IsTrue, IsFalse,
        Like, StartsWith, Contains
    }

    /**
     * Exchange search filter.
     */
    public interface Condition {
        /**
         * Append condition to buffer.
         *
         * @param buffer search filter buffer
         */
        void appendTo(StringBuilder buffer);

        /**
         * True if condition is empty.
         *
         * @return true if condition is empty
         */
        boolean isEmpty();

        /**
         * Test if the contact matches current condition.
         *
         * @param contact Exchange Contact
         * @return true if contact matches condition
         */
        boolean isMatch(ExchangeSession.Contact contact);
    }

    /**
     * Attribute condition.
     */
    public abstract static class AttributeCondition implements Condition {
        protected final String attributeName;
        protected final Operator operator;
        protected final String value;

        protected AttributeCondition(String attributeName, Operator operator, String value) {
            this.attributeName = attributeName;
            this.operator = operator;
            this.value = value;
        }

        public boolean isEmpty() {
            return false;
        }

        /**
         * Get attribute name.
         *
         * @return attribute name
         */
        public String getAttributeName() {
            return attributeName;
        }

        /**
         * Condition value.
         *
         * @return value
         */
        public String getValue() {
            return value;
        }

    }

    /**
     * Multiple condition.
     */
    public abstract static class MultiCondition implements Condition {
        protected final Operator operator;
        protected final List<Condition> conditions;

        protected MultiCondition(Operator operator, Condition... conditions) {
            this.operator = operator;
            this.conditions = new ArrayList<>();
            for (Condition condition : conditions) {
                if (condition != null) {
                    this.conditions.add(condition);
                }
            }
        }

        /**
         * Conditions list.
         *
         * @return conditions
         */
        public List<Condition> getConditions() {
            return conditions;
        }

        /**
         * Condition operator.
         *
         * @return operator
         */
        public Operator getOperator() {
            return operator;
        }

        /**
         * Add a new condition.
         *
         * @param condition single condition
         */
        public void add(Condition condition) {
            if (condition != null) {
                conditions.add(condition);
            }
        }

        public boolean isEmpty() {
            boolean isEmpty = true;
            for (Condition condition : conditions) {
                if (!condition.isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            return isEmpty;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            if (operator == Operator.And) {
                for (Condition condition : conditions) {
                    if (!condition.isMatch(contact)) {
                        return false;
                    }
                }
                return true;
            } else if (operator == Operator.Or) {
                for (Condition condition : conditions) {
                    if (condition.isMatch(contact)) {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }

    }

    /**
     * Not condition.
     */
    public abstract static class NotCondition implements Condition {
        protected final Condition condition;

        protected NotCondition(Condition condition) {
            this.condition = condition;
        }

        public boolean isEmpty() {
            return condition.isEmpty();
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            return !condition.isMatch(contact);
        }
    }

    /**
     * Single search filter condition.
     */
    public abstract static class MonoCondition implements Condition {
        protected final String attributeName;
        protected final Operator operator;

        protected MonoCondition(String attributeName, Operator operator) {
            this.attributeName = attributeName;
            this.operator = operator;
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return (operator == Operator.IsNull && actualValue == null) ||
                    (operator == Operator.IsFalse && "false".equals(actualValue)) ||
                    (operator == Operator.IsTrue && "true".equals(actualValue));
        }
    }

    /**
     * And search filter.
     *
     * @param condition search conditions
     * @return condition
     */
    public abstract MultiCondition and(Condition... condition);

    /**
     * Or search filter.
     *
     * @param condition search conditions
     * @return condition
     */
    public abstract MultiCondition or(Condition... condition);

    /**
     * Not search filter.
     *
     * @param condition search condition
     * @return condition
     */
    public abstract Condition not(Condition condition);

    /**
     * Equals condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition isEqualTo(String attributeName, String value);

    /**
     * Equals condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition isEqualTo(String attributeName, int value);

    /**
     * MIME header equals condition.
     *
     * @param headerName MIME header name
     * @param value      attribute value
     * @return condition
     */
    public abstract Condition headerIsEqualTo(String headerName, String value);

    /**
     * Greater than or equals condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition gte(String attributeName, String value);

    /**
     * Greater than condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition gt(String attributeName, String value);

    /**
     * Lower than condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition lt(String attributeName, String value);

    /**
     * Lower than or equals condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public abstract Condition lte(String attributeName, String value);

    /**
     * Contains condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition contains(String attributeName, String value);

    /**
     * Starts with condition.
     *
     * @param attributeName logical Exchange attribute name
     * @param value         attribute value
     * @return condition
     */
    public abstract Condition startsWith(String attributeName, String value);

    /**
     * Is null condition.
     *
     * @param attributeName logical Exchange attribute name
     * @return condition
     */
    public abstract Condition isNull(String attributeName);

    /**
     * Exists condition.
     *
     * @param attributeName logical Exchange attribute name
     * @return condition
     */
    public abstract Condition exists(String attributeName);

    /**
     * Is true condition.
     *
     * @param attributeName logical Exchange attribute name
     * @return condition
     */
    public abstract Condition isTrue(String attributeName);

    /**
     * Is false condition.
     *
     * @param attributeName logical Exchange attribute name
     * @return condition
     */
    public abstract Condition isFalse(String attributeName);

    /**
     * Search mail and generic folders under given folder.
     * Exclude calendar and contacts folders
     *
     * @param folderName Exchange folder name
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public List<Folder> getSubFolders(String folderName, boolean recursive, boolean wildcard) throws IOException {
        MultiCondition folderCondition = and();
        if (!Settings.getBooleanProperty("davmail.imapIncludeSpecialFolders", false)) {
            folderCondition.add(or(isEqualTo("folderclass", "IPF.Note"),
                    isEqualTo("folderclass", "IPF.Note.Microsoft.Conversation"),
                    isNull("folderclass")));
        }
        if (wildcard) {
            folderCondition.add(startsWith("displayname", folderName));
            folderName = "";
        }
        List<Folder> results = getSubFolders(folderName, folderCondition,
                recursive);
        // need to include base folder in recursive search, except on root
        if (recursive && folderName.length() > 0) {
            results.add(getFolder(folderName));
        }

        return results;
    }

    /**
     * Search calendar folders under given folder.
     *
     * @param folderName Exchange folder name
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public List<Folder> getSubCalendarFolders(String folderName, boolean recursive) throws IOException {
        return getSubFolders(folderName, isEqualTo("folderclass", "IPF.Appointment"), recursive);
    }

    /**
     * Search folders under given folder matching filter.
     *
     * @param folderName Exchange folder name
     * @param condition  search filter
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public abstract List<Folder> getSubFolders(String folderName, Condition condition, boolean recursive) throws IOException;

    /**
     * Delete oldest messages in trash.
     * keepDelay is the number of days to keep messages in trash before delete
     *
     * @throws IOException when unable to purge messages
     */
    public void purgeOldestTrashAndSentMessages() throws IOException {
        int keepDelay = Settings.getIntProperty("davmail.keepDelay");
        if (keepDelay != 0) {
            purgeOldestFolderMessages(TRASH, keepDelay);
        }
        // this is a new feature, default is : do nothing
        int sentKeepDelay = Settings.getIntProperty("davmail.sentKeepDelay");
        if (sentKeepDelay != 0) {
            purgeOldestFolderMessages(SENT, sentKeepDelay);
        }
    }

    protected void purgeOldestFolderMessages(String folderPath, int keepDelay) throws IOException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -keepDelay);
        LOGGER.debug("Delete messages in " + folderPath + " not modified since " + cal.getTime());

        MessageList messages = searchMessages(folderPath, UID_MESSAGE_ATTRIBUTES,
                lt("lastmodified", formatSearchDate(cal.getTime())));

        for (Message message : messages) {
            message.delete();
        }
    }

    protected void convertResentHeader(MimeMessage mimeMessage, String headerName) throws MessagingException {
        String[] resentHeader = mimeMessage.getHeader("Resent-" + headerName);
        if (resentHeader != null) {
            mimeMessage.removeHeader("Resent-" + headerName);
            mimeMessage.removeHeader(headerName);
            for (String value : resentHeader) {
                mimeMessage.addHeader(headerName, value);
            }
        }
    }

    protected String lastSentMessageId;

    /**
     * Send message in reader to recipients.
     * Detect visible recipients in message body to determine bcc recipients
     *
     * @param rcptToRecipients recipients list
     * @param mimeMessage      mime message
     * @throws IOException        on error
     * @throws MessagingException on error
     */
    public void sendMessage(List<String> rcptToRecipients, MimeMessage mimeMessage) throws IOException, MessagingException {
        // detect duplicate send command
        String messageId = mimeMessage.getMessageID();
        if (lastSentMessageId != null && lastSentMessageId.equals(messageId)) {
            LOGGER.debug("Dropping message id " + messageId + ": already sent");
            return;
        }
        lastSentMessageId = messageId;

        convertResentHeader(mimeMessage, "From");
        convertResentHeader(mimeMessage, "To");
        convertResentHeader(mimeMessage, "Cc");
        convertResentHeader(mimeMessage, "Bcc");
        convertResentHeader(mimeMessage, "Message-Id");

        // do not allow send as another user on Exchange 2003
        if ("Exchange2003".equals(serverVersion) || Settings.getBooleanProperty("davmail.smtpStripFrom", false)) {
            mimeMessage.removeHeader("From");
        }

        // remove visible recipients from list
        Set<String> visibleRecipients = new HashSet<>();
        List<InternetAddress> recipients = getAllRecipients(mimeMessage);
        for (InternetAddress address : recipients) {
            visibleRecipients.add((address.getAddress().toLowerCase()));
        }
        for (String recipient : rcptToRecipients) {
            if (!visibleRecipients.contains(recipient.toLowerCase())) {
                mimeMessage.addRecipient(javax.mail.Message.RecipientType.BCC, new InternetAddress(recipient));
            }
        }
        sendMessage(mimeMessage);

    }

    static final String[] RECIPIENT_HEADERS = {"to", "cc", "bcc"};

    protected List<InternetAddress> getAllRecipients(MimeMessage mimeMessage) throws MessagingException {
        List<InternetAddress> recipientList = new ArrayList<>();
        for (String recipientHeader : RECIPIENT_HEADERS) {
            final String recipientHeaderValue = mimeMessage.getHeader(recipientHeader, ",");
            if (recipientHeaderValue != null) {
                // parse headers in non strict mode
                recipientList.addAll(Arrays.asList(InternetAddress.parseHeader(recipientHeaderValue, false)));
            }

        }
        return recipientList;
    }

    /**
     * Send Mime message.
     *
     * @param mimeMessage MIME message
     * @throws IOException        on error
     * @throws MessagingException on error
     */
    public abstract void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException;

    /**
     * Get folder object.
     * Folder name can be logical names INBOX, Drafts, Trash or calendar,
     * or a path relative to user base folder or absolute path.
     *
     * @param folderPath folder path
     * @return Folder object
     * @throws IOException on error
     */
    public ExchangeSession.Folder getFolder(String folderPath) throws IOException {
        Folder folder = internalGetFolder(folderPath);
        if (isMainCalendar(folderPath)) {
            Folder taskFolder = internalGetFolder(TASKS);
            folder.ctag += taskFolder.ctag;
        }
        return folder;
    }

    protected abstract Folder internalGetFolder(String folderName) throws IOException;

    /**
     * Check folder ctag and reload messages as needed.
     *
     * @param currentFolder current folder
     * @return true if folder changed
     * @throws IOException on error
     */
    public boolean refreshFolder(Folder currentFolder) throws IOException {
        Folder newFolder = getFolder(currentFolder.folderPath);
        if (currentFolder.ctag == null || !currentFolder.ctag.equals(newFolder.ctag)
                // ctag stamp is limited to second, check message count
                || !(currentFolder.count == newFolder.count)
        ) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Contenttag or count changed on " + currentFolder.folderPath +
                        " ctag: " + currentFolder.ctag + " => " + newFolder.ctag +
                        " count: " + currentFolder.count + " => " + newFolder.count
                        + ", reloading messages");
            }
            currentFolder.hasChildren = newFolder.hasChildren;
            currentFolder.noInferiors = newFolder.noInferiors;
            currentFolder.unreadCount = newFolder.unreadCount;
            currentFolder.ctag = newFolder.ctag;
            currentFolder.etag = newFolder.etag;
            if (newFolder.uidNext > currentFolder.uidNext) {
                currentFolder.uidNext = newFolder.uidNext;
            }
            currentFolder.loadMessages();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create Exchange message folder.
     *
     * @param folderName logical folder name
     * @throws IOException on error
     */
    public void createMessageFolder(String folderName) throws IOException {
        createFolder(folderName, "IPF.Note", null);
    }

    /**
     * Create Exchange calendar folder.
     *
     * @param folderName logical folder name
     * @param properties folder properties
     * @return status
     * @throws IOException on error
     */
    public int createCalendarFolder(String folderName, Map<String, String> properties) throws IOException {
        return createFolder(folderName, "IPF.Appointment", properties);
    }

    /**
     * Create Exchange contact folder.
     *
     * @param folderName logical folder name
     * @param properties folder properties
     * @throws IOException on error
     */
    public void createContactFolder(String folderName, Map<String, String> properties) throws IOException {
        createFolder(folderName, "IPF.Contact", properties);
    }

    /**
     * Create Exchange folder with given folder class.
     *
     * @param folderName  logical folder name
     * @param folderClass folder class
     * @param properties  folder properties
     * @return status
     * @throws IOException on error
     */
    public abstract int createFolder(String folderName, String folderClass, Map<String, String> properties) throws IOException;

    /**
     * Update Exchange folder properties.
     *
     * @param folderName logical folder name
     * @param properties folder properties
     * @return status
     * @throws IOException on error
     */
    public abstract int updateFolder(String folderName, Map<String, String> properties) throws IOException;

    /**
     * Delete Exchange folder.
     *
     * @param folderName logical folder name
     * @throws IOException on error
     */
    public abstract void deleteFolder(String folderName) throws IOException;

    /**
     * Copy message to target folder
     *
     * @param message      Exchange message
     * @param targetFolder target folder
     * @throws IOException on error
     */
    public abstract void copyMessage(Message message, String targetFolder) throws IOException;

    public void copyMessages(List<Message> messages, String targetFolder) throws IOException {
        for (Message message : messages) {
            copyMessage(message, targetFolder);
        }
    }


    /**
     * Move message to target folder
     *
     * @param message      Exchange message
     * @param targetFolder target folder
     * @throws IOException on error
     */
    public abstract void moveMessage(Message message, String targetFolder) throws IOException;

    public void moveMessages(List<Message> messages, String targetFolder) throws IOException {
        for (Message message : messages) {
            moveMessage(message, targetFolder);
        }
    }

    /**
     * Move folder to target name.
     *
     * @param folderName current folder name/path
     * @param targetName target folder name/path
     * @throws IOException on error
     */
    public abstract void moveFolder(String folderName, String targetName) throws IOException;

    /**
     * Move item from source path to target path.
     *
     * @param sourcePath item source path
     * @param targetPath item target path
     * @throws IOException on error
     */
    public abstract void moveItem(String sourcePath, String targetPath) throws IOException;

    protected abstract void moveToTrash(Message message) throws IOException;

    /**
     * Convert keyword value to IMAP flag.
     *
     * @param value keyword value
     * @return IMAP flag
     */
    public String convertKeywordToFlag(String value) {
        // first test for keyword in settings
        Properties flagSettings = Settings.getSubProperties("davmail.imapFlags");
        Enumeration<?> flagSettingsEnum = flagSettings.propertyNames();
        while (flagSettingsEnum.hasMoreElements()) {
            String key = (String) flagSettingsEnum.nextElement();
            if (value.equalsIgnoreCase(flagSettings.getProperty(key))) {
                return key;
            }
        }

        ResourceBundle flagBundle = ResourceBundle.getBundle("imapflags");
        Enumeration<String> flagBundleEnum = flagBundle.getKeys();
        while (flagBundleEnum.hasMoreElements()) {
            String key = flagBundleEnum.nextElement();
            if (value.equalsIgnoreCase(flagBundle.getString(key))) {
                return key;
            }
        }

        // fall back to raw value
        return value;
    }

    /**
     * Convert IMAP flag to keyword value.
     *
     * @param value IMAP flag
     * @return keyword value
     */
    public String convertFlagToKeyword(String value) {
        // first test for flag in settings
        Properties flagSettings = Settings.getSubProperties("davmail.imapFlags");
        // case insensitive lookup
        for (String key : flagSettings.stringPropertyNames()) {
            if (key.equalsIgnoreCase(value)) {
                return flagSettings.getProperty(key);
            }
        }

        // fall back to predefined flags
        ResourceBundle flagBundle = ResourceBundle.getBundle("imapflags");
        for (String key : flagBundle.keySet()) {
            if (key.equalsIgnoreCase(value)) {
                return flagBundle.getString(key);
            }
        }

        // fall back to raw value
        return value;
    }

    /**
     * Convert IMAP flags to keyword value.
     *
     * @param flags IMAP flags
     * @return keyword value
     */
    public String convertFlagsToKeywords(HashSet<String> flags) {
        HashSet<String> keywordSet = new HashSet<>();
        for (String flag : flags) {
            keywordSet.add(decodeKeyword(convertFlagToKeyword(flag)));
        }
        return StringUtil.join(keywordSet, ",");
    }

    protected String decodeKeyword(String keyword) {
        String result = keyword;
        if (keyword.contains("_x0028_") || keyword.contains("_x0029_")) {
            result = result.replaceAll("_x0028_", "(")
                    .replaceAll("_x0029_", ")");
        }
        return result;
    }

    protected String encodeKeyword(String keyword) {
        String result = keyword;
        if (keyword.indexOf('(') >= 0|| keyword.indexOf(')') >= 0) {
            result = result.replaceAll("\\(", "_x0028_")
                    .replaceAll("\\)", "_x0029_" );
        }
        return result;
    }

    /**
     * Exchange folder with IMAP properties
     */
    public class Folder {
        /**
         * Logical (IMAP) folder path.
         */
        public String folderPath;

        /**
         * Display Name.
         */
        public String displayName;
        /**
         * Folder class (PR_CONTAINER_CLASS).
         */
        public String folderClass;
        /**
         * Folder message count.
         */
        public int count;
        /**
         * Folder unread message count.
         */
        public int unreadCount;
        /**
         * true if folder has subfolders (DAV:hassubs).
         */
        public boolean hasChildren;
        /**
         * true if folder has no subfolders (DAV:nosubs).
         */
        public boolean noInferiors;
        /**
         * Folder content tag (to detect folder content changes).
         */
        public String ctag;
        /**
         * Folder etag (to detect folder object changes).
         */
        public String etag;
        /**
         * Next IMAP uid
         */
        public long uidNext;
        /**
         * recent count
         */
        public int recent;

        /**
         * Folder message list, empty before loadMessages call.
         */
        public ExchangeSession.MessageList messages;
        /**
         * Permanent uid (PR_SEARCH_KEY) to IMAP UID map.
         */
        private final HashMap<String, Long> permanentUrlToImapUidMap = new HashMap<>();

        /**
         * Get IMAP folder flags.
         *
         * @return folder flags in IMAP format
         */
        public String getFlags() {
            String specialFlag = "";
            if (isSpecial()) {
                specialFlag = "\\" + folderPath + " ";
            }
            if (noInferiors) {
                return specialFlag + "\\NoInferiors";
            } else if (hasChildren) {
                return specialFlag + "\\HasChildren";
            } else {
                return specialFlag + "\\HasNoChildren";
            }
        }

        /**
         * Special folder flag (Sent, Drafts, Trash, Junk).
         * @return true if folder is special
         */
        public boolean isSpecial() {
            return SPECIAL.contains(folderPath);
        }

        /**
         * Load folder messages.
         *
         * @throws IOException on error
         */
        public void loadMessages() throws IOException {
            messages = ExchangeSession.this.searchMessages(folderPath, null);
            fixUids(messages);
            recent = 0;
            for (Message message : messages) {
                if (message.recent) {
                    recent++;
                }
            }
            long computedUidNext = 1;
            if (!messages.isEmpty()) {
                computedUidNext = messages.get(messages.size() - 1).getImapUid() + 1;
            }
            if (computedUidNext > uidNext) {
                uidNext = computedUidNext;
            }
        }

        /**
         * Search messages in folder matching query.
         *
         * @param condition search query
         * @return message list
         * @throws IOException on error
         */
        public MessageList searchMessages(Condition condition) throws IOException {
            MessageList localMessages = ExchangeSession.this.searchMessages(folderPath, condition);
            fixUids(localMessages);
            return localMessages;
        }

        /**
         * Restore previous uids changed by a PROPPATCH (flag change).
         *
         * @param messages message list
         */
        protected void fixUids(MessageList messages) {
            boolean sortNeeded = false;
            for (Message message : messages) {
                if (permanentUrlToImapUidMap.containsKey(message.getPermanentId())) {
                    long previousUid = permanentUrlToImapUidMap.get(message.getPermanentId());
                    if (message.getImapUid() != previousUid) {
                        LOGGER.debug("Restoring IMAP uid " + message.getImapUid() + " -> " + previousUid + " for message " + message.getPermanentId());
                        message.setImapUid(previousUid);
                        sortNeeded = true;
                    }
                } else {
                    // add message to uid map
                    permanentUrlToImapUidMap.put(message.getPermanentId(), message.getImapUid());
                }
            }
            if (sortNeeded) {
                Collections.sort(messages);
            }
        }

        /**
         * Folder message count.
         *
         * @return message count
         */
        public int count() {
            if (messages == null) {
                return count;
            } else {
                return messages.size();
            }
        }

        /**
         * Compute IMAP uidnext.
         *
         * @return max(messageuids)+1
         */
        public long getUidNext() {
            return uidNext;
        }

        /**
         * Get message at index.
         *
         * @param index message index
         * @return message
         */
        public Message get(int index) {
            return messages.get(index);
        }

        /**
         * Get current folder messages imap uids and flags
         *
         * @return imap uid list
         */
        public TreeMap<Long, String> getImapFlagMap() {
            TreeMap<Long, String> imapFlagMap = new TreeMap<>();
            for (ExchangeSession.Message message : messages) {
                imapFlagMap.put(message.getImapUid(), message.getImapFlags());
            }
            return imapFlagMap;
        }

        /**
         * Calendar folder flag.
         *
         * @return true if this is a calendar folder
         */
        public boolean isCalendar() {
            return "IPF.Appointment".equals(folderClass);
        }

        /**
         * Contact folder flag.
         *
         * @return true if this is a calendar folder
         */
        public boolean isContact() {
            return "IPF.Contact".equals(folderClass);
        }

        /**
         * Task folder flag.
         *
         * @return true if this is a task folder
         */
        public boolean isTask() {
            return "IPF.Task".equals(folderClass);
        }

        /**
         * drop cached message
         */
        public void clearCache() {
            messages.cachedMimeContent = null;
            messages.cachedMimeMessage = null;
            messages.cachedMessageImapUid = 0;
        }
    }

    /**
     * Exchange message.
     */
    public abstract class Message implements Comparable<Message> {
        /**
         * enclosing message list
         */
        public MessageList messageList;
        /**
         * Message url.
         */
        public String messageUrl;
        /**
         * Message permanent url (does not change on message move).
         */
        public String permanentUrl;
        /**
         * Message uid.
         */
        public String uid;
        /**
         * Message content class.
         */
        public String contentClass;
        /**
         * Message keywords (categories).
         */
        public String keywords;
        /**
         * Message IMAP uid, unique in folder (x0e230003).
         */
        public long imapUid;
        /**
         * MAPI message size.
         */
        public int size;
        /**
         * Message date (urn:schemas:mailheader:date).
         */
        public String date;

        /**
         * Message flag: read.
         */
        public boolean read;
        /**
         * Message flag: deleted.
         */
        public boolean deleted;
        /**
         * Message flag: junk.
         */
        public boolean junk;
        /**
         * Message flag: flagged.
         */
        public boolean flagged;
        /**
         * Message flag: recent.
         */
        public boolean recent;
        /**
         * Message flag: draft.
         */
        public boolean draft;
        /**
         * Message flag: answered.
         */
        public boolean answered;
        /**
         * Message flag: fowarded.
         */
        public boolean forwarded;

        /**
         * Unparsed message content.
         */
        protected byte[] mimeContent;

        /**
         * Message content parsed in a MIME message.
         */
        protected MimeMessage mimeMessage;

        /**
         * Get permanent message id.
         * permanentUrl over WebDav or IitemId over EWS
         *
         * @return permanent id
         */
        public abstract String getPermanentId();

        /**
         * IMAP uid , unique in folder (x0e230003)
         *
         * @return IMAP uid
         */
        public long getImapUid() {
            return imapUid;
        }

        /**
         * Set IMAP uid.
         *
         * @param imapUid new uid
         */
        public void setImapUid(long imapUid) {
            this.imapUid = imapUid;
        }

        /**
         * Exchange uid.
         *
         * @return uid
         */
        public String getUid() {
            return uid;
        }

        /**
         * Return message flags in IMAP format.
         *
         * @return IMAP flags
         */
        public String getImapFlags() {
            StringBuilder buffer = new StringBuilder();
            if (read) {
                buffer.append("\\Seen ");
            }
            if (deleted) {
                buffer.append("\\Deleted ");
            }
            if (recent) {
                buffer.append("\\Recent ");
            }
            if (flagged) {
                buffer.append("\\Flagged ");
            }
            if (junk) {
                buffer.append("Junk ");
            }
            if (draft) {
                buffer.append("\\Draft ");
            }
            if (answered) {
                buffer.append("\\Answered ");
            }
            if (forwarded) {
                buffer.append("$Forwarded ");
            }
            if (keywords != null) {
                for (String keyword : keywords.split(",")) {
                    buffer.append(encodeKeyword(convertKeywordToFlag(keyword))).append(" ");
                }
            }
            return buffer.toString().trim();
        }

        /**
         * Load message content in a Mime message
         *
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public void loadMimeMessage() throws IOException, MessagingException {
            if (mimeMessage == null) {
                // try to get message content from cache
                if (this.imapUid == messageList.cachedMessageImapUid
                        // make sure we never return null even with broken 0 uid message
                        && messageList.cachedMimeContent != null
                        && messageList.cachedMimeMessage != null) {
                    mimeContent = messageList.cachedMimeContent;
                    mimeMessage = messageList.cachedMimeMessage;
                    LOGGER.debug("Got message content for " + imapUid + " from cache");
                } else {
                    // load and parse message
                    mimeContent = getContent(this);
                    mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(mimeContent));
                    // workaround for Exchange 2003 ActiveSync bug
                    if (mimeMessage.getHeader("MAIL FROM") != null) {
                        // find start of actual message
                        byte[] mimeContentCopy = new byte[((SharedByteArrayInputStream) mimeMessage.getRawInputStream()).available()];
                        int offset = mimeContent.length - mimeContentCopy.length;
                        // remove unwanted header
                        System.arraycopy(mimeContent, offset, mimeContentCopy, 0, mimeContentCopy.length);
                        mimeContent = mimeContentCopy;
                        mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(mimeContent));
                    }
                    LOGGER.debug("Downloaded full message content for IMAP UID " + imapUid + " (" + mimeContent.length + " bytes)");
                }
            }
        }

        /**
         * Get message content as a Mime message.
         *
         * @return mime message
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public MimeMessage getMimeMessage() throws IOException, MessagingException {
            loadMimeMessage();
            return mimeMessage;
        }

        public Enumeration<?> getMatchingHeaderLinesFromHeaders(String[] headerNames) throws MessagingException {
            Enumeration<?> result = null;
            if (mimeMessage == null) {
                // message not loaded, try to get headers only
                InputStream headers = getMimeHeaders();
                if (headers != null) {
                    InternetHeaders internetHeaders = new InternetHeaders(headers);
                    if (internetHeaders.getHeader("Subject") == null) {
                        // invalid header content
                        return null;
                    }
                    if (headerNames == null) {
                        result = internetHeaders.getAllHeaderLines();
                    } else {
                        result = internetHeaders.getMatchingHeaderLines(headerNames);
                    }
                }
            }
            return result;
        }

        public Enumeration<?> getMatchingHeaderLines(String[] headerNames) throws MessagingException, IOException {
            Enumeration<?> result = getMatchingHeaderLinesFromHeaders(headerNames);
            if (result == null) {
                if (headerNames == null) {
                    result = getMimeMessage().getAllHeaderLines();
                } else {
                    result = getMimeMessage().getMatchingHeaderLines(headerNames);
                }

            }
            return result;
        }

        protected abstract InputStream getMimeHeaders();

        /**
         * Get message body size.
         *
         * @return mime message size
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public int getMimeMessageSize() throws IOException, MessagingException {
            loadMimeMessage();
            return mimeContent.length;
        }

        /**
         * Get message body input stream.
         *
         * @return mime message InputStream
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public InputStream getRawInputStream() throws IOException, MessagingException {
            loadMimeMessage();
            return new SharedByteArrayInputStream(mimeContent);
        }


        /**
         * Drop mime message to avoid keeping message content in memory,
         * keep a single message in MessageList cache to handle chunked fetch.
         */
        public void dropMimeMessage() {
            // update single message cache
            if (mimeMessage != null) {
                messageList.cachedMessageImapUid = imapUid;
                messageList.cachedMimeContent = mimeContent;
                messageList.cachedMimeMessage = mimeMessage;
            }
            // drop curent message body to save memory
            mimeMessage = null;
            mimeContent = null;
        }

        public boolean isLoaded() {
            // check and retrieve cached content
            if (imapUid == messageList.cachedMessageImapUid) {
                mimeContent = messageList.cachedMimeContent;
                mimeMessage = messageList.cachedMimeMessage;
            }
            return mimeMessage != null;
        }

        /**
         * Delete message.
         *
         * @throws IOException on error
         */
        public void delete() throws IOException {
            deleteMessage(this);
        }

        /**
         * Move message to trash, mark message read.
         *
         * @throws IOException on error
         */
        public void moveToTrash() throws IOException {
            markRead();

            ExchangeSession.this.moveToTrash(this);
        }

        /**
         * Mark message as read.
         *
         * @throws IOException on error
         */
        public void markRead() throws IOException {
            HashMap<String, String> properties = new HashMap<>();
            properties.put("read", "1");
            updateMessage(this, properties);
        }

        /**
         * Comparator to sort messages by IMAP uid
         *
         * @param message other message
         * @return imapUid comparison result
         */
        public int compareTo(Message message) {
            long compareValue = (imapUid - message.imapUid);
            if (compareValue > 0) {
                return 1;
            } else if (compareValue < 0) {
                return -1;
            } else {
                return 0;
            }
        }

        /**
         * Override equals, compare IMAP uids
         *
         * @param message other message
         * @return true if IMAP uids are equal
         */
        @Override
        public boolean equals(Object message) {
            return message instanceof Message && imapUid == ((Message) message).imapUid;
        }

        /**
         * Override hashCode, return imapUid hashcode.
         *
         * @return imapUid hashcode
         */
        @Override
        public int hashCode() {
            return (int) (imapUid ^ (imapUid >>> 32));
        }

        public String removeFlag(String flag) {
            if (keywords != null) {
                final String exchangeFlag = convertFlagToKeyword(flag);
                Set<String> keywordSet = new HashSet<>();
                String[] keywordArray = keywords.split(",");
                for (String value : keywordArray) {
                    if (!value.equalsIgnoreCase(exchangeFlag)) {
                        keywordSet.add(value);
                    }
                }
                keywords = StringUtil.join(keywordSet, ",");
            }
            return keywords;
        }

        public String addFlag(String flag) {
            final String exchangeFlag = convertFlagToKeyword(flag);
            HashSet<String> keywordSet = new HashSet<>();
            boolean hasFlag = false;
            if (keywords != null) {
                String[] keywordArray = keywords.split(",");
                for (String value : keywordArray) {
                    keywordSet.add(value);
                    if (value.equalsIgnoreCase(exchangeFlag)) {
                        hasFlag = true;
                    }
                }
            }
            if (!hasFlag) {
                keywordSet.add(exchangeFlag);
            }
            keywords = StringUtil.join(keywordSet, ",");
            return keywords;
        }

        public String setFlags(HashSet<String> flags) {
            keywords = convertFlagsToKeywords(flags);
            return keywords;
        }

    }

    /**
     * Message list, includes a single messsage cache
     */
    public static class MessageList extends ArrayList<Message> {
        /**
         * Cached message content parsed in a MIME message.
         */
        protected transient MimeMessage cachedMimeMessage;
        /**
         * Cached message uid.
         */
        protected transient long cachedMessageImapUid;
        /**
         * Cached unparsed message
         */
        protected transient byte[] cachedMimeContent;

    }

    /**
     * Generic folder item.
     */
    public abstract static class Item extends HashMap<String, String> {
        protected String folderPath;
        protected String itemName;
        protected String permanentUrl;
        /**
         * Display name.
         */
        public String displayName;
        /**
         * item etag
         */
        public String etag;
        protected String noneMatch;

        /**
         * Build item instance.
         *
         * @param folderPath folder path
         * @param itemName   item name class
         * @param etag       item etag
         * @param noneMatch  none match flag
         */
        public Item(String folderPath, String itemName, String etag, String noneMatch) {
            this.folderPath = folderPath;
            this.itemName = itemName;
            this.etag = etag;
            this.noneMatch = noneMatch;
        }

        /**
         * Default constructor.
         */
        protected Item() {
        }

        /**
         * Return item content type
         *
         * @return content type
         */
        public abstract String getContentType();

        /**
         * Retrieve item body from Exchange
         *
         * @return item body
         * @throws IOException on error
         */
        public abstract String getBody() throws IOException;

        /**
         * Get event name (file name part in URL).
         *
         * @return event name
         */
        public String getName() {
            return itemName;
        }

        /**
         * Get event etag (last change tag).
         *
         * @return event etag
         */
        public String getEtag() {
            return etag;
        }

        /**
         * Set item href.
         *
         * @param href item href
         */
        public void setHref(String href) {
            int index = href.lastIndexOf('/');
            if (index >= 0) {
                folderPath = href.substring(0, index);
                itemName = href.substring(index + 1);
            } else {
                throw new IllegalArgumentException(href);
            }
        }

        /**
         * Return item href.
         *
         * @return item href
         */
        public String getHref() {
            return folderPath + '/' + itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }
    }

    /**
     * Contact object
     */
    public abstract class Contact extends Item {

        protected ArrayList<String> distributionListMembers = null;
        protected String vCardVersion;

        public Contact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) {
            super(folderPath, itemName.endsWith(".vcf") ? itemName.substring(0, itemName.length() - 3) + "EML" : itemName, etag, noneMatch);
            this.putAll(properties);
        }

        protected Contact() {
        }

        public void setVCardVersion(String vCardVersion) {
            this.vCardVersion = vCardVersion;
        }

        public abstract ItemResult createOrUpdate() throws IOException;

        /**
         * Convert EML extension to vcf.
         *
         * @return item name
         */
        @Override
        public String getName() {
            String name = super.getName();
            if (name.endsWith(".EML")) {
                name = name.substring(0, name.length() - 3) + "vcf";
            }
            return name;
        }

        /**
         * Set contact name
         *
         * @param name contact name
         */
        public void setName(String name) {
            this.itemName = name;
        }

        /**
         * Compute vcard uid from name.
         *
         * @return uid
         */
        public String getUid() {
            String uid = getName();
            int dotIndex = uid.lastIndexOf('.');
            if (dotIndex > 0) {
                uid = uid.substring(0, dotIndex);
            }
            return URIUtil.encodePath(uid);
        }

        @Override
        public String getContentType() {
            return "text/vcard";
        }

        public void addMember(String member) {
            if (distributionListMembers == null) {
                distributionListMembers = new ArrayList<>();
            }
            distributionListMembers.add(member);
        }


        @Override
        public String getBody() {
            // build RFC 2426 VCard from contact information
            VCardWriter writer = new VCardWriter();
            writer.startCard(vCardVersion);
            writer.appendProperty("UID", getUid());
            // common name
            String cn = get("cn");
            if (cn == null) {
                cn = get("displayname");
            }
            String sn = get("sn");
            if (sn == null) {
                sn = cn;
            }
            writer.appendProperty("FN", cn);
            // RFC 2426: Family Name, Given Name, Additional Names, Honorific Prefixes, and Honorific Suffixes
            writer.appendProperty("N", sn, get("givenName"), get("middlename"), get("personaltitle"), get("namesuffix"));

            if (distributionListMembers != null) {
                writer.appendProperty("KIND", "group");
                for (String member : distributionListMembers) {
                    writer.appendProperty("MEMBER", member);
                }
            }

            writer.appendProperty("TEL;TYPE=cell", get("mobile"));
            writer.appendProperty("TEL;TYPE=work", get("telephoneNumber"));
            writer.appendProperty("TEL;TYPE=home", get("homePhone"));
            writer.appendProperty("TEL;TYPE=fax", get("facsimiletelephonenumber"));
            writer.appendProperty("TEL;TYPE=pager", get("pager"));
            writer.appendProperty("TEL;TYPE=car", get("othermobile"));
            writer.appendProperty("TEL;TYPE=home,fax", get("homefax"));
            writer.appendProperty("TEL;TYPE=isdn", get("internationalisdnnumber"));
            writer.appendProperty("TEL;TYPE=msg", get("otherTelephone"));

            // The structured type value corresponds, in sequence, to the post office box; the extended address;
            // the street address; the locality (e.g., city); the region (e.g., state or province);
            // the postal code; the country name
            writer.appendProperty("ADR;TYPE=home",
                    get("homepostofficebox"), null, get("homeStreet"), get("homeCity"), get("homeState"), get("homePostalCode"), get("homeCountry"));
            writer.appendProperty("ADR;TYPE=work",
                    get("postofficebox"), get("roomnumber"), get("street"), get("l"), get("st"), get("postalcode"), get("co"));
            writer.appendProperty("ADR;TYPE=other",
                    get("otherpostofficebox"), null, get("otherstreet"), get("othercity"), get("otherstate"), get("otherpostalcode"), get("othercountry"));

            writer.appendProperty("EMAIL;TYPE=work", get("smtpemail1"));
            writer.appendProperty("EMAIL;TYPE=home", get("smtpemail2"));
            writer.appendProperty("EMAIL;TYPE=other", get("smtpemail3"));

            writer.appendProperty("ORG", get("o"), get("department"));
            writer.appendProperty("URL;TYPE=work", get("businesshomepage"));
            writer.appendProperty("URL;TYPE=home", get("personalHomePage"));
            writer.appendProperty("TITLE", get("title"));
            writer.appendProperty("NOTE", get("description"));

            writer.appendProperty("CUSTOM1", get("extensionattribute1"));
            writer.appendProperty("CUSTOM2", get("extensionattribute2"));
            writer.appendProperty("CUSTOM3", get("extensionattribute3"));
            writer.appendProperty("CUSTOM4", get("extensionattribute4"));

            writer.appendProperty("ROLE", get("profession"));
            writer.appendProperty("NICKNAME", get("nickname"));
            writer.appendProperty("X-AIM", get("im"));

            writer.appendProperty("BDAY", convertZuluDateToBday(get("bday")));
            writer.appendProperty("ANNIVERSARY", convertZuluDateToBday(get("anniversary")));

            String gender = get("gender");
            if ("1".equals(gender)) {
                writer.appendProperty("SEX", "2");
            } else if ("2".equals(gender)) {
                writer.appendProperty("SEX", "1");
            }

            writer.appendProperty("CATEGORIES", get("keywords"));

            writer.appendProperty("FBURL", get("fburl"));

            if ("1".equals(get("private"))) {
                writer.appendProperty("CLASS", "PRIVATE");
            }

            writer.appendProperty("X-ASSISTANT", get("secretarycn"));
            writer.appendProperty("X-MANAGER", get("manager"));
            writer.appendProperty("X-SPOUSE", get("spousecn"));

            writer.appendProperty("REV", get("lastmodified"));

            ContactPhoto contactPhoto = null;

            if (Settings.getBooleanProperty("davmail.carddavReadPhoto", true)) {
                if (("true".equals(get("haspicture")))) {
                    try {
                        contactPhoto = getContactPhoto(this);
                    } catch (IOException e) {
                        LOGGER.warn("Unable to get photo from contact " + this.get("cn"));
                    }
                }

                if (contactPhoto == null) {
                    contactPhoto = getADPhoto(get("smtpemail1"));
                }
            }

            if (contactPhoto != null) {
                writer.writeLine("PHOTO;TYPE=" + contactPhoto.contentType + ";ENCODING=BASE64:");
                writer.writeLine(contactPhoto.content, true);
            }

            writer.appendProperty("KEY1;X509;ENCODING=BASE64", get("msexchangecertificate"));
            writer.appendProperty("KEY2;X509;ENCODING=BASE64", get("usersmimecertificate"));

            writer.endCard();
            return writer.toString();
        }
    }

    /**
     * Calendar event object.
     */
    public abstract class Event extends Item {
        protected String contentClass;
        protected String subject;
        protected VCalendar vCalendar;

        public Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) throws IOException {
            super(folderPath, itemName, etag, noneMatch);
            this.contentClass = contentClass;
            fixICS(itemBody.getBytes(StandardCharsets.UTF_8), false);
            // fix task item name
            if (vCalendar.isTodo() && this.itemName.endsWith(".ics")) {
                this.itemName = itemName.substring(0, itemName.length() - 3) + "EML";
            }
        }

        protected Event() {
        }

        @Override
        public String getContentType() {
            return "text/calendar;charset=UTF-8";
        }

        @Override
        public String getBody() throws IOException {
            if (vCalendar == null) {
                fixICS(getEventContent(), true);
            }
            return vCalendar.toString();
        }

        protected HttpNotFoundException buildHttpNotFoundException(Exception e) {
            String message = "Unable to get event " + getName() + " subject: " + subject + " at " + permanentUrl + ": " + e.getMessage();
            LOGGER.warn(message);
            return new HttpNotFoundException(message);
        }

        /**
         * Retrieve item body from Exchange
         *
         * @return item content
         * @throws IOException on error
         */
        public abstract byte[] getEventContent() throws IOException;

        protected static final String TEXT_CALENDAR = "text/calendar";
        protected static final String APPLICATION_ICS = "application/ics";

        protected boolean isCalendarContentType(String contentType) {
            return TEXT_CALENDAR.regionMatches(true, 0, contentType, 0, TEXT_CALENDAR.length()) ||
                    APPLICATION_ICS.regionMatches(true, 0, contentType, 0, APPLICATION_ICS.length());
        }

        protected MimePart getCalendarMimePart(MimeMultipart multiPart) throws IOException, MessagingException {
            MimePart bodyPart = null;
            for (int i = 0; i < multiPart.getCount(); i++) {
                String contentType = multiPart.getBodyPart(i).getContentType();
                if (isCalendarContentType(contentType)) {
                    bodyPart = (MimePart) multiPart.getBodyPart(i);
                    break;
                } else if (contentType.startsWith("multipart")) {
                    Object content = multiPart.getBodyPart(i).getContent();
                    if (content instanceof MimeMultipart) {
                        bodyPart = getCalendarMimePart((MimeMultipart) content);
                    }
                }
            }

            return bodyPart;
        }

        /**
         * Load ICS content from MIME message input stream
         *
         * @param mimeInputStream mime message input stream
         * @return mime message ics attachment body
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        protected byte[] getICS(InputStream mimeInputStream) throws IOException, MessagingException {
            byte[] result;
            MimeMessage mimeMessage = new MimeMessage(null, mimeInputStream);
            String[] contentClassHeader = mimeMessage.getHeader("Content-class");
            // task item, return null
            if (contentClassHeader != null && contentClassHeader.length > 0 && "urn:content-classes:task".equals(contentClassHeader[0])) {
                return null;
            }
            Object mimeBody = mimeMessage.getContent();
            MimePart bodyPart = null;
            if (mimeBody instanceof MimeMultipart) {
                bodyPart = getCalendarMimePart((MimeMultipart) mimeBody);
            } else if (isCalendarContentType(mimeMessage.getContentType())) {
                // no multipart, single body
                bodyPart = mimeMessage;
            }

            if (bodyPart != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bodyPart.getDataHandler().writeTo(baos);
                baos.close();
                result = baos.toByteArray();
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                mimeMessage.writeTo(baos);
                baos.close();
                throw new DavMailException("EXCEPTION_INVALID_MESSAGE_CONTENT", new String(baos.toByteArray(), StandardCharsets.UTF_8));
            }
            return result;
        }

        protected void fixICS(byte[] icsContent, boolean fromServer) throws IOException {
            if (LOGGER.isDebugEnabled() && fromServer) {
                dumpIndex++;
                String icsBody = new String(icsContent, StandardCharsets.UTF_8);
                ICSCalendarValidator.ValidationResult vr = ICSCalendarValidator.validateWithDetails(icsBody);
                dumpICS(icsBody, true, false);
                LOGGER.debug("Vcalendar body ValidationResult: "+ vr.isValid() +" "+ vr.showReason());
                LOGGER.debug("Vcalendar body received from server:\n" + icsBody);
            }
            vCalendar = new VCalendar(icsContent, getEmail(), getVTimezone());
            vCalendar.fixVCalendar(fromServer);
            if (LOGGER.isDebugEnabled() && !fromServer) {
                String resultString = vCalendar.toString();
                ICSCalendarValidator.ValidationResult vr = ICSCalendarValidator.validateWithDetails(resultString);
                LOGGER.debug("Fixed Vcalendar body ValidationResult: "+ vr.isValid() +" "+ vr.showReason());
                LOGGER.debug("Fixed Vcalendar body to server:\n" + resultString);
                dumpICS(resultString, false, true);
            }
        }

        protected void dumpICS(String icsBody, boolean fromServer, boolean after) {
            String logFileDirectory = Settings.getLogFileDirectory();

            // additional setting to activate ICS dump (not available in GUI)
            int dumpMax = Settings.getIntProperty("davmail.dumpICS");
            if (dumpMax > 0) {
                if (dumpIndex > dumpMax) {
                    // Delete the oldest dump file
                    final int oldest = dumpIndex - dumpMax;
                    try {
                        File[] oldestFiles = (new File(logFileDirectory)).listFiles((dir, name) -> {
                            if (name.endsWith(".ics")) {
                                int dashIndex = name.indexOf('-');
                                if (dashIndex > 0) {
                                    try {
                                        int fileIndex = Integer.parseInt(name.substring(0, dashIndex));
                                        return fileIndex < oldest;
                                    } catch (NumberFormatException nfe) {
                                        // ignore
                                    }
                                }
                            }
                            return false;
                        });
                        if (oldestFiles != null) {
                            for (File file : oldestFiles) {
                                if (!file.delete()) {
                                    LOGGER.warn("Unable to delete " + file.getAbsolutePath());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("Error deleting ics dump: " + ex.getMessage());
                    }
                }

                StringBuilder filePath = new StringBuilder();
                filePath.append(logFileDirectory).append('/')
                        .append(dumpIndex)
                        .append(after ? "-to" : "-from")
                        .append((after ^ fromServer) ? "-server" : "-client")
                        .append(".ics");
                if ((icsBody != null) && (icsBody.length() > 0)) {
                    OutputStreamWriter writer = null;
                    try {
                        writer = new OutputStreamWriter(new FileOutputStream(filePath.toString()), StandardCharsets.UTF_8);
                        writer.write(icsBody);
                    } catch (IOException e) {
                        LOGGER.error(e);
                    } finally {
                        if (writer != null) {
                            try {
                                writer.close();
                            } catch (IOException e) {
                                LOGGER.error(e);
                            }
                        }
                    }


                }
            }

        }

        /**
         * Build Mime body for event or event message.
         *
         * @return mimeContent as byte array or null
         * @throws IOException on error
         */
        public byte[] createMimeContent() throws IOException {
            String boundary = UUID.randomUUID().toString();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MimeOutputStreamWriter writer = new MimeOutputStreamWriter(baos);

            writer.writeHeader("Content-Transfer-Encoding", "7bit");
            writer.writeHeader("Content-class", contentClass);
            // append date
            writer.writeHeader("Date", new Date());

            // Make sure invites have a proper subject line
            String vEventSubject = vCalendar.getFirstVeventPropertyValue("SUMMARY");
            if (vEventSubject == null) {
                vEventSubject = BundleMessage.format("MEETING_REQUEST");
            }

            // Write a part of the message that contains the
            // ICS description so that invites contain the description text
            String description = vCalendar.getFirstVeventPropertyValue("DESCRIPTION");

            // handle notifications
            if ("urn:content-classes:calendarmessage".equals(contentClass)) {
                // need to parse attendees and organizer to build recipients
                VCalendar.Recipients recipients = vCalendar.getRecipients(true);
                String to;
                String cc;
                String notificationSubject;
                if (email.equalsIgnoreCase(recipients.organizer)) {
                    // current user is organizer => notify all
                    to = recipients.attendees;
                    cc = recipients.optionalAttendees;
                    notificationSubject = subject;
                } else {
                    String status = vCalendar.getAttendeeStatus();
                    // notify only organizer
                    to = recipients.organizer;
                    cc = null;
                    notificationSubject = (status != null) ? (BundleMessage.format(status) + vEventSubject) : subject;
                    description = "";
                }

                // Allow end user notification edit
                if (Settings.getBooleanProperty("davmail.caldavEditNotifications")) {
                    // create notification edit dialog
                    NotificationDialog notificationDialog = new NotificationDialog(to,
                            cc, notificationSubject, description);
                    if (!notificationDialog.getSendNotification()) {
                        LOGGER.debug("Notification canceled by user");
                        return null;
                    }
                    // get description from dialog
                    to = notificationDialog.getTo();
                    cc = notificationDialog.getCc();
                    notificationSubject = notificationDialog.getSubject();
                    description = notificationDialog.getBody();
                }

                // do not send notification if no recipients found
                if ((to == null || to.length() == 0) && (cc == null || cc.length() == 0)) {
                    return null;
                }

                writer.writeHeader("To", to);
                writer.writeHeader("Cc", cc);
                writer.writeHeader("Subject", notificationSubject);


                if (LOGGER.isDebugEnabled()) {
                    StringBuilder logBuffer = new StringBuilder("Sending notification ");
                    if (to != null) {
                        logBuffer.append("to: ").append(to);
                    }
                    if (cc != null) {
                        logBuffer.append("cc: ").append(cc);
                    }
                    LOGGER.debug(logBuffer.toString());
                }
            } else {
                // need to parse attendees and organizer to build recipients
                VCalendar.Recipients recipients = vCalendar.getRecipients(false);
                // storing appointment, full recipients header
                if (recipients.attendees != null) {
                    writer.writeHeader("To", recipients.attendees);
                } else {
                    // use current user as attendee
                    writer.writeHeader("To", email);
                }
                writer.writeHeader("Cc", recipients.optionalAttendees);

                if (recipients.organizer != null) {
                    writer.writeHeader("From", recipients.organizer);
                } else {
                    writer.writeHeader("From", email);
                }
            }
            if (vCalendar.getMethod() == null) {
                vCalendar.setPropertyValue("METHOD", "REQUEST");
            }
            writer.writeHeader("MIME-Version", "1.0");
            writer.writeHeader("Content-Type", "multipart/alternative;\r\n" +
                    "\tboundary=\"----=_NextPart_" + boundary + '\"');
            writer.writeLn();
            writer.writeLn("This is a multi-part message in MIME format.");
            writer.writeLn();
            writer.writeLn("------=_NextPart_" + boundary);

            if (description != null && description.length() > 0) {
                writer.writeHeader("Content-Type", "text/plain;\r\n" +
                        "\tcharset=\"utf-8\"");
                writer.writeHeader("content-transfer-encoding", "8bit");
                writer.writeLn();
                writer.flush();
                baos.write(description.getBytes(StandardCharsets.UTF_8));
                writer.writeLn();
                writer.writeLn("------=_NextPart_" + boundary);
            }
            writer.writeHeader("Content-class", contentClass);
            writer.writeHeader("Content-Type", "text/calendar;\r\n" +
                    "\tmethod=" + vCalendar.getMethod() + ";\r\n" +
                    "\tcharset=\"utf-8\""
            );
            writer.writeHeader("Content-Transfer-Encoding", "8bit");
            writer.writeLn();
            writer.flush();
            baos.write(vCalendar.toString().getBytes(StandardCharsets.UTF_8));
            writer.writeLn();
            writer.writeLn("------=_NextPart_" + boundary + "--");
            writer.close();
            return baos.toByteArray();
        }

        /**
         * Create or update item
         *
         * @return action result
         * @throws IOException on error
         */
        public abstract ItemResult createOrUpdate() throws IOException;

    }

    protected abstract Set<String> getItemProperties();

    /**
     * Search contacts in provided folder.
     *
     * @param folderPath Exchange folder path
     * @param includeDistList include distribution lists
     * @return list of contacts
     * @throws IOException on error
     */
    public List<ExchangeSession.Contact> getAllContacts(String folderPath, boolean includeDistList) throws IOException {
        return searchContacts(folderPath, ExchangeSession.CONTACT_ATTRIBUTES, isEqualTo("outlookmessageclass", "IPM.Contact"), 0);
    }


    /**
     * Search contacts in provided folder matching the search query.
     *
     * @param folderPath Exchange folder path
     * @param attributes requested attributes
     * @param condition  Exchange search query
     * @param maxCount   maximum item count
     * @return list of contacts
     * @throws IOException on error
     */
    public abstract List<Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException;

    /**
     * Search calendar messages in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of calendar messages as Event objects
     * @throws IOException on error
     */
    public abstract List<Event> getEventMessages(String folderPath) throws IOException;

    /**
     * Search calendar events in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of calendar events
     * @throws IOException on error
     */
    public List<Event> getAllEvents(String folderPath) throws IOException {
        List<Event> results = searchEvents(folderPath, getCalendarItemCondition(getPastDelayCondition("dtstart")));

        if (!Settings.getBooleanProperty("davmail.caldavDisableTasks", false) && isMainCalendar(folderPath)) {
            // retrieve tasks from main tasks folder
            results.addAll(searchTasksOnly(TASKS));
        }

        return results;
    }

    protected abstract Condition getCalendarItemCondition(Condition dateCondition);

    protected Condition getPastDelayCondition(String attribute) {
        int caldavPastDelay = Settings.getIntProperty("davmail.caldavPastDelay");
        Condition dateCondition = null;
        if (caldavPastDelay != 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -caldavPastDelay);
            dateCondition = gt(attribute, formatSearchDate(cal.getTime()));
        }
        return dateCondition;
    }

    protected Condition getRangeCondition(String timeRangeStart, String timeRangeEnd) throws IOException {
        try {
            SimpleDateFormat parser = getZuluDateFormat();
            ExchangeSession.MultiCondition andCondition = and();
            if (timeRangeStart != null) {
                andCondition.add(gt("dtend", formatSearchDate(parser.parse(timeRangeStart))));
            }
            if (timeRangeEnd != null) {
                andCondition.add(lt("dtstart", formatSearchDate(parser.parse(timeRangeEnd))));
            }
            return andCondition;
        } catch (ParseException e) {
            throw new IOException(e + " " + e.getMessage());
        }
    }

    /**
     * Search events between start and end.
     *
     * @param folderPath     Exchange folder path
     * @param timeRangeStart date range start in zulu format
     * @param timeRangeEnd   date range start in zulu format
     * @return list of calendar events
     * @throws IOException on error
     */
    public List<Event> searchEvents(String folderPath, String timeRangeStart, String timeRangeEnd) throws IOException {
        Condition dateCondition = getRangeCondition(timeRangeStart, timeRangeEnd);
        Condition condition = getCalendarItemCondition(dateCondition);

        return searchEvents(folderPath, condition);
    }

    /**
     * Search events between start and end, exclude tasks.
     *
     * @param folderPath     Exchange folder path
     * @param timeRangeStart date range start in zulu format
     * @param timeRangeEnd   date range start in zulu format
     * @return list of calendar events
     * @throws IOException on error
     */
    public List<Event> searchEventsOnly(String folderPath, String timeRangeStart, String timeRangeEnd) throws IOException {
        Condition dateCondition = getRangeCondition(timeRangeStart, timeRangeEnd);
        return searchEvents(folderPath, getCalendarItemCondition(dateCondition));
    }

    /**
     * Search tasks only (VTODO).
     *
     * @param folderPath Exchange folder path
     * @return list of tasks
     * @throws IOException on error
     */
    public List<Event> searchTasksOnly(String folderPath) throws IOException {
        return searchEvents(folderPath, and(isEqualTo("outlookmessageclass", "IPM.Task"),
                or(isNull("datecompleted"), getPastDelayCondition("datecompleted"))));
    }

    /**
     * Search calendar events in provided folder.
     *
     * @param folderPath Exchange folder path
     * @param filter     search filter
     * @return list of calendar events
     * @throws IOException on error
     */
    public List<Event> searchEvents(String folderPath, Condition filter) throws IOException {

        Condition privateCondition = null;
        if (isSharedFolder(folderPath) && Settings.getBooleanProperty("davmail.excludePrivateEvents", true)) {
            LOGGER.debug("Shared or public calendar: exclude private events");
            privateCondition = isEqualTo("sensitivity", 0);
        }

        return searchEvents(folderPath, getItemProperties(),
                and(filter, privateCondition));
    }

    /**
     * Search calendar events or messages in provided folder matching the search query.
     *
     * @param folderPath Exchange folder path
     * @param attributes requested attributes
     * @param condition  Exchange search query
     * @return list of calendar messages as Event objects
     * @throws IOException on error
     */
    public abstract List<Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException;

    /**
     * convert vcf extension to EML.
     *
     * @param itemName item name
     * @return EML item name
     */
    protected String convertItemNameToEML(String itemName) {
        if (itemName.endsWith(".vcf")) {
            return itemName.substring(0, itemName.length() - 3) + "EML";
        } else {
            return itemName;
        }
    }

    /**
     * Get item named eventName in folder
     *
     * @param folderPath Exchange folder path
     * @param itemName   event name
     * @return event object
     * @throws IOException on error
     */
    public abstract Item getItem(String folderPath, String itemName) throws IOException;

    /**
     * Contact picture
     */
    public static class ContactPhoto {
        /**
         * Contact picture content type (always image/jpeg on read)
         */
        public String contentType;
        /**
         * Base64 encoded picture content
         */
        public String content;
    }

    /**
     * Retrieve contact photo attached to contact
     *
     * @param contact address book contact
     * @return contact photo
     * @throws IOException on error
     */
    public abstract ContactPhoto getContactPhoto(Contact contact) throws IOException;

    /**
     * Retrieve contact photo from AD
     *
     * @param email address book contact
     * @return contact photo
     */
    public ContactPhoto getADPhoto(String email) {
        return null;
    }

    /**
     * Delete event named itemName in folder
     *
     * @param folderPath Exchange folder path
     * @param itemName   item name
     * @throws IOException on error
     */
    public abstract void deleteItem(String folderPath, String itemName) throws IOException;

    /**
     * Mark event processed named eventName in folder
     *
     * @param folderPath Exchange folder path
     * @param itemName   item name
     * @throws IOException on error
     */
    public abstract void processItem(String folderPath, String itemName) throws IOException;


    private static int dumpIndex;

    /**
     * Replace iCal4 (Snow Leopard) principal paths with mailto expression
     *
     * @param value attendee value or ics line
     * @return fixed value
     */
    protected String replaceIcal4Principal(String value) {
        if (value != null && value.contains("/principals/__uuids__/")) {
            return value.replaceAll("/principals/__uuids__/([^/]*)__AT__([^/]*)/", "mailto:$1@$2");
        } else {
            return value;
        }
    }

    /**
     * Event result object to hold HTTP status and event etag from an event creation/update.
     */
    public static class ItemResult {
        /**
         * HTTP status
         */
        public int status;
        /**
         * Event etag from response HTTP header
         */
        public String etag;
        /**
         * Created item name
         */
        public String itemName;
    }

    /**
     * Build and send the MIME message for the provided ICS event.
     *
     * @param icsBody event in iCalendar format
     * @return HTTP status
     * @throws IOException on error
     */
    public abstract int sendEvent(String icsBody) throws IOException;

    /**
     * Create or update item (event or contact) on the Exchange server
     *
     * @param folderPath Exchange folder path
     * @param itemName   event name
     * @param itemBody   event body in iCalendar format
     * @param etag       previous event etag to detect concurrent updates
     * @param noneMatch  if-none-match header value
     * @return HTTP response event result (status and etag)
     * @throws IOException on error
     */
    public ItemResult createOrUpdateItem(String folderPath, String itemName, String itemBody, String etag, String noneMatch) throws IOException {
        if (itemBody.startsWith("BEGIN:VCALENDAR")) {
            return internalCreateOrUpdateEvent(folderPath, itemName, "urn:content-classes:appointment", itemBody, etag, noneMatch);
        } else if (itemBody.startsWith("BEGIN:VCARD")) {
            return createOrUpdateContact(folderPath, itemName, itemBody, etag, noneMatch);
        } else {
            throw new IOException(BundleMessage.format("EXCEPTION_INVALID_MESSAGE_CONTENT", itemBody));
        }
    }

    static final String[] VCARD_N_PROPERTIES = {"sn", "givenName", "middlename", "personaltitle", "namesuffix"};
    static final String[] VCARD_ADR_HOME_PROPERTIES = {"homepostofficebox", null, "homeStreet", "homeCity", "homeState", "homePostalCode", "homeCountry"};
    static final String[] VCARD_ADR_WORK_PROPERTIES = {"postofficebox", "roomnumber", "street", "l", "st", "postalcode", "co"};
    static final String[] VCARD_ADR_OTHER_PROPERTIES = {"otherpostofficebox", null, "otherstreet", "othercity", "otherstate", "otherpostalcode", "othercountry"};
    static final String[] VCARD_ORG_PROPERTIES = {"o", "department"};

    protected void convertContactProperties(Map<String, String> properties, String[] contactProperties, List<String> values) {
        for (int i = 0; i < values.size() && i < contactProperties.length; i++) {
            if (contactProperties[i] != null) {
                properties.put(contactProperties[i], values.get(i));
            }
        }
    }

    protected ItemResult createOrUpdateContact(String folderPath, String itemName, String itemBody, String etag, String noneMatch) throws IOException {
        // parse VCARD body to build contact property map
        Map<String, String> properties = new HashMap<>();

        VObject vcard = new VObject(new ICSBufferedReader(new StringReader(itemBody)));
        if ("group".equalsIgnoreCase(vcard.getPropertyValue("KIND"))) {
            properties.put("outlookmessageclass", "IPM.DistList");
            properties.put("displayname", vcard.getPropertyValue("FN"));
        } else {
            properties.put("outlookmessageclass", "IPM.Contact");

            for (VProperty property : vcard.getProperties()) {
                if ("FN".equals(property.getKey())) {
                    properties.put("cn", property.getValue());
                    properties.put("subject", property.getValue());
                    properties.put("fileas", property.getValue());

                } else if ("N".equals(property.getKey())) {
                    convertContactProperties(properties, VCARD_N_PROPERTIES, property.getValues());
                } else if ("NICKNAME".equals(property.getKey())) {
                    properties.put("nickname", property.getValue());
                } else if ("TEL".equals(property.getKey())) {
                    if (property.hasParam("TYPE", "cell") || property.hasParam("X-GROUP", "cell")) {
                        properties.put("mobile", property.getValue());
                    } else if (property.hasParam("TYPE", "work") || property.hasParam("X-GROUP", "work")) {
                        properties.put("telephoneNumber", property.getValue());
                    } else if (property.hasParam("TYPE", "home") || property.hasParam("X-GROUP", "home")) {
                        properties.put("homePhone", property.getValue());
                    } else if (property.hasParam("TYPE", "fax")) {
                        if (property.hasParam("TYPE", "home")) {
                            properties.put("homefax", property.getValue());
                        } else {
                            properties.put("facsimiletelephonenumber", property.getValue());
                        }
                    } else if (property.hasParam("TYPE", "pager")) {
                        properties.put("pager", property.getValue());
                    } else if (property.hasParam("TYPE", "car")) {
                        properties.put("othermobile", property.getValue());
                    } else {
                        properties.put("otherTelephone", property.getValue());
                    }
                } else if ("ADR".equals(property.getKey())) {
                    // address
                    if (property.hasParam("TYPE", "home")) {
                        convertContactProperties(properties, VCARD_ADR_HOME_PROPERTIES, property.getValues());
                    } else if (property.hasParam("TYPE", "work")) {
                        convertContactProperties(properties, VCARD_ADR_WORK_PROPERTIES, property.getValues());
                        // any other type goes to other address
                    } else {
                        convertContactProperties(properties, VCARD_ADR_OTHER_PROPERTIES, property.getValues());
                    }
                } else if ("EMAIL".equals(property.getKey())) {
                    if (property.hasParam("TYPE", "home")) {
                        properties.put("email2", property.getValue());
                        properties.put("smtpemail2", property.getValue());
                    } else if (property.hasParam("TYPE", "other")) {
                        properties.put("email3", property.getValue());
                        properties.put("smtpemail3", property.getValue());
                    } else {
                        properties.put("email1", property.getValue());
                        properties.put("smtpemail1", property.getValue());
                    }
                } else if ("ORG".equals(property.getKey())) {
                    convertContactProperties(properties, VCARD_ORG_PROPERTIES, property.getValues());
                } else if ("URL".equals(property.getKey())) {
                    if (property.hasParam("TYPE", "work")) {
                        properties.put("businesshomepage", property.getValue());
                    } else if (property.hasParam("TYPE", "home")) {
                        properties.put("personalHomePage", property.getValue());
                    } else {
                        // default: set personal home page
                        properties.put("personalHomePage", property.getValue());
                    }
                } else if ("TITLE".equals(property.getKey())) {
                    properties.put("title", property.getValue());
                } else if ("NOTE".equals(property.getKey())) {
                    properties.put("description", property.getValue());
                } else if ("CUSTOM1".equals(property.getKey())) {
                    properties.put("extensionattribute1", property.getValue());
                } else if ("CUSTOM2".equals(property.getKey())) {
                    properties.put("extensionattribute2", property.getValue());
                } else if ("CUSTOM3".equals(property.getKey())) {
                    properties.put("extensionattribute3", property.getValue());
                } else if ("CUSTOM4".equals(property.getKey())) {
                    properties.put("extensionattribute4", property.getValue());
                } else if ("ROLE".equals(property.getKey())) {
                    properties.put("profession", property.getValue());
                } else if ("X-AIM".equals(property.getKey())) {
                    properties.put("im", property.getValue());
                } else if ("BDAY".equals(property.getKey())) {
                    properties.put("bday", convertBDayToZulu(property.getValue()));
                } else if ("ANNIVERSARY".equals(property.getKey()) || "X-ANNIVERSARY".equals(property.getKey())) {
                    properties.put("anniversary", convertBDayToZulu(property.getValue()));
                } else if ("CATEGORIES".equals(property.getKey())) {
                    properties.put("keywords", property.getValue());
                } else if ("CLASS".equals(property.getKey())) {
                    if ("PUBLIC".equals(property.getValue())) {
                        properties.put("sensitivity", "0");
                        properties.put("private", "false");
                    } else {
                        properties.put("sensitivity", "2");
                        properties.put("private", "true");
                    }
                } else if ("SEX".equals(property.getKey())) {
                    String propertyValue = property.getValue();
                    if ("1".equals(propertyValue)) {
                        properties.put("gender", "2");
                    } else if ("2".equals(propertyValue)) {
                        properties.put("gender", "1");
                    }
                } else if ("FBURL".equals(property.getKey())) {
                    properties.put("fburl", property.getValue());
                } else if ("X-ASSISTANT".equals(property.getKey())) {
                    properties.put("secretarycn", property.getValue());
                } else if ("X-MANAGER".equals(property.getKey())) {
                    properties.put("manager", property.getValue());
                } else if ("X-SPOUSE".equals(property.getKey())) {
                    properties.put("spousecn", property.getValue());
                } else if ("PHOTO".equals(property.getKey())) {
                    properties.put("photo", property.getValue());
                    properties.put("haspicture", "true");
                } else if ("KEY1".equals(property.getKey())) {
                    properties.put("msexchangecertificate", property.getValue());
                } else if ("KEY2".equals(property.getKey())) {
                    properties.put("usersmimecertificate", property.getValue());
                }
            }
            LOGGER.debug("Create or update contact " + itemName + ": " + properties);
            // reset missing properties to null
            for (String key : CONTACT_ATTRIBUTES) {
                if (!"imapUid".equals(key) && !"etag".equals(key) && !"urlcompname".equals(key)
                        && !"lastmodified".equals(key) && !"sensitivity".equals(key) &&
                        !properties.containsKey(key)) {
                    properties.put(key, null);
                }
            }
        }

        Contact contact = buildContact(folderPath, itemName, properties, etag, noneMatch);
        for (VProperty property : vcard.getProperties()) {
            if ("MEMBER".equals(property.getKey())) {
                String member = property.getValue();
                if (member.startsWith("urn:uuid:")) {
                    Item item = getItem(folderPath, member.substring(9) + ".EML");
                    if (item != null) {
                        if (item.get("smtpemail1") != null) {
                            member = "mailto:" + item.get("smtpemail1");
                        } else if (item.get("smtpemail2") != null) {
                            member = "mailto:" + item.get("smtpemail2");
                        } else if (item.get("smtpemail3") != null) {
                            member = "mailto:" + item.get("smtpemail3");
                        }
                    }
                }
                contact.addMember(member);
            }
        }
        return contact.createOrUpdate();
    }

    protected String convertZuluDateToBday(String value) {
        String result = null;
        if (value != null && value.length() > 0) {
            try {
                SimpleDateFormat parser = ExchangeSession.getZuluDateFormat();
                Calendar cal = Calendar.getInstance();
                cal.setTime(parser.parse(value));
                cal.add(Calendar.HOUR_OF_DAY, 12);
                result = ExchangeSession.getVcardBdayFormat().format(cal.getTime());
            } catch (ParseException e) {
                LOGGER.warn("Invalid date: " + value);
            }
        }
        return result;
    }

    protected String convertBDayToZulu(String value) {
        String result = null;
        if (value != null && value.length() > 0) {
            try {
                SimpleDateFormat parser;
                if (value.length() == 10) {
                    parser = ExchangeSession.getVcardBdayFormat();
                } else if (value.length() == 15) {
                    parser = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH);
                    parser.setTimeZone(GMT_TIMEZONE);
                } else {
                    parser = ExchangeSession.getExchangeZuluDateFormat();
                }
                result = ExchangeSession.getExchangeZuluDateFormatMillisecond().format(parser.parse(value));
            } catch (ParseException e) {
                LOGGER.warn("Invalid date: " + value);
            }
        }

        return result;
    }


    protected abstract Contact buildContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) throws IOException;

    protected abstract ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException;

    /**
     * Get current Exchange alias name from login name
     *
     * @return user name
     */
    public String getAliasFromLogin() {
        // login is email, not alias
        if (this.userName.indexOf('@') >= 0) {
            return null;
        }
        String result = this.userName;
        // remove domain name
        int index = Math.max(result.indexOf('\\'), result.indexOf('/'));
        if (index >= 0) {
            result = result.substring(index + 1);
        }
        return result;
    }

    /**
     * Test if folderPath is inside user mailbox.
     *
     * @param folderPath absolute folder path
     * @return true if folderPath is a public or shared folder
     */
    public abstract boolean isSharedFolder(String folderPath);

    /**
     * Test if folderPath is main calendar.
     *
     * @param folderPath absolute folder path
     * @return true if folderPath is a public or shared folder
     */
    public abstract boolean isMainCalendar(String folderPath) throws IOException;

    protected static final String MAILBOX_BASE = "/cn=";

    /**
     * Get current user email
     *
     * @return user email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Get current user alias
     *
     * @return user email
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Search global address list
     *
     * @param condition           search filter
     * @param returningAttributes returning attributes
     * @param sizeLimit           size limit
     * @return matching contacts from gal
     * @throws IOException on error
     */
    public abstract Map<String, Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException;

    /**
     * Full Contact attribute list
     */
    public static final Set<String> CONTACT_ATTRIBUTES = new HashSet<>();

    static {
        CONTACT_ATTRIBUTES.add("imapUid");
        CONTACT_ATTRIBUTES.add("etag");
        CONTACT_ATTRIBUTES.add("urlcompname");

        CONTACT_ATTRIBUTES.add("extensionattribute1");
        CONTACT_ATTRIBUTES.add("extensionattribute2");
        CONTACT_ATTRIBUTES.add("extensionattribute3");
        CONTACT_ATTRIBUTES.add("extensionattribute4");
        CONTACT_ATTRIBUTES.add("bday");
        CONTACT_ATTRIBUTES.add("anniversary");
        CONTACT_ATTRIBUTES.add("businesshomepage");
        CONTACT_ATTRIBUTES.add("personalHomePage");
        CONTACT_ATTRIBUTES.add("cn");
        CONTACT_ATTRIBUTES.add("co");
        CONTACT_ATTRIBUTES.add("department");
        CONTACT_ATTRIBUTES.add("smtpemail1");
        CONTACT_ATTRIBUTES.add("smtpemail2");
        CONTACT_ATTRIBUTES.add("smtpemail3");
        CONTACT_ATTRIBUTES.add("facsimiletelephonenumber");
        CONTACT_ATTRIBUTES.add("givenName");
        CONTACT_ATTRIBUTES.add("homeCity");
        CONTACT_ATTRIBUTES.add("homeCountry");
        CONTACT_ATTRIBUTES.add("homePhone");
        CONTACT_ATTRIBUTES.add("homePostalCode");
        CONTACT_ATTRIBUTES.add("homeState");
        CONTACT_ATTRIBUTES.add("homeStreet");
        CONTACT_ATTRIBUTES.add("homepostofficebox");
        CONTACT_ATTRIBUTES.add("l");
        CONTACT_ATTRIBUTES.add("manager");
        CONTACT_ATTRIBUTES.add("mobile");
        CONTACT_ATTRIBUTES.add("namesuffix");
        CONTACT_ATTRIBUTES.add("nickname");
        CONTACT_ATTRIBUTES.add("o");
        CONTACT_ATTRIBUTES.add("pager");
        CONTACT_ATTRIBUTES.add("personaltitle");
        CONTACT_ATTRIBUTES.add("postalcode");
        CONTACT_ATTRIBUTES.add("postofficebox");
        CONTACT_ATTRIBUTES.add("profession");
        CONTACT_ATTRIBUTES.add("roomnumber");
        CONTACT_ATTRIBUTES.add("secretarycn");
        CONTACT_ATTRIBUTES.add("sn");
        CONTACT_ATTRIBUTES.add("spousecn");
        CONTACT_ATTRIBUTES.add("st");
        CONTACT_ATTRIBUTES.add("street");
        CONTACT_ATTRIBUTES.add("telephoneNumber");
        CONTACT_ATTRIBUTES.add("title");
        CONTACT_ATTRIBUTES.add("description");
        CONTACT_ATTRIBUTES.add("im");
        CONTACT_ATTRIBUTES.add("middlename");
        CONTACT_ATTRIBUTES.add("lastmodified");
        CONTACT_ATTRIBUTES.add("otherstreet");
        CONTACT_ATTRIBUTES.add("otherstate");
        CONTACT_ATTRIBUTES.add("otherpostofficebox");
        CONTACT_ATTRIBUTES.add("otherpostalcode");
        CONTACT_ATTRIBUTES.add("othercountry");
        CONTACT_ATTRIBUTES.add("othercity");
        CONTACT_ATTRIBUTES.add("haspicture");
        CONTACT_ATTRIBUTES.add("keywords");
        CONTACT_ATTRIBUTES.add("othermobile");
        CONTACT_ATTRIBUTES.add("otherTelephone");
        CONTACT_ATTRIBUTES.add("gender");
        CONTACT_ATTRIBUTES.add("private");
        CONTACT_ATTRIBUTES.add("sensitivity");
        CONTACT_ATTRIBUTES.add("fburl");
        CONTACT_ATTRIBUTES.add("msexchangecertificate");
        CONTACT_ATTRIBUTES.add("usersmimecertificate");
    }

    protected static final Set<String> DISTRIBUTION_LIST_ATTRIBUTES = new HashSet<>();

    static {
        DISTRIBUTION_LIST_ATTRIBUTES.add("imapUid");
        DISTRIBUTION_LIST_ATTRIBUTES.add("etag");
        DISTRIBUTION_LIST_ATTRIBUTES.add("urlcompname");

        DISTRIBUTION_LIST_ATTRIBUTES.add("cn");
        DISTRIBUTION_LIST_ATTRIBUTES.add("members");
    }

    /**
     * Get freebusy data string from Exchange.
     *
     * @param attendee attendee email address
     * @param start    start date in Exchange zulu format
     * @param end      end date in Exchange zulu format
     * @param interval freebusy interval in minutes
     * @return freebusy data or null
     * @throws IOException on error
     */
    protected abstract String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException;

    /**
     * Get freebusy info for attendee between start and end date.
     *
     * @param attendee       attendee email
     * @param startDateValue start date
     * @param endDateValue   end date
     * @return FreeBusy info
     * @throws IOException on error
     */
    public FreeBusy getFreebusy(String attendee, String startDateValue, String endDateValue) throws IOException {
        // replace ical encoded attendee name
        attendee = replaceIcal4Principal(attendee);

        // then check that email address is valid to avoid InvalidSmtpAddress error
        if (attendee == null || attendee.indexOf('@') < 0 || attendee.charAt(attendee.length() - 1) == '@') {
            return null;
        }

        if (attendee.startsWith("mailto:") || attendee.startsWith("MAILTO:")) {
            attendee = attendee.substring("mailto:".length());
        }

        SimpleDateFormat exchangeZuluDateFormat = getExchangeZuluDateFormat();
        SimpleDateFormat icalDateFormat = getZuluDateFormat();

        Date startDate;
        Date endDate;
        try {
            if (startDateValue.length() == 8) {
                startDate = parseDate(startDateValue);
            } else {
                startDate = icalDateFormat.parse(startDateValue);
            }
            if (endDateValue.length() == 8) {
                endDate = parseDate(endDateValue);
            } else {
                endDate = icalDateFormat.parse(endDateValue);
            }
        } catch (ParseException e) {
            throw new DavMailException("EXCEPTION_INVALID_DATES", e.getMessage());
        }

        FreeBusy freeBusy = null;
        String fbdata = getFreeBusyData(attendee, exchangeZuluDateFormat.format(startDate), exchangeZuluDateFormat.format(endDate), FREE_BUSY_INTERVAL);
        if (fbdata != null) {
            freeBusy = new FreeBusy(icalDateFormat, startDate, fbdata);
        }

        if (freeBusy != null && freeBusy.knownAttendee) {
            return freeBusy;
        } else {
            return null;
        }
    }

    /**
     * Exchange to iCalendar Free/Busy parser.
     * Free time returns 0, Tentative returns 1, Busy returns 2, and Out of Office (OOF) returns 3
     */
    public static final class FreeBusy {
        final SimpleDateFormat icalParser;
        boolean knownAttendee = true;
        static final HashMap<Character, String> FBTYPES = new HashMap<>();

        static {
            FBTYPES.put('1', "BUSY-TENTATIVE");
            FBTYPES.put('2', "BUSY");
            FBTYPES.put('3', "BUSY-UNAVAILABLE");
        }

        final HashMap<String, StringBuilder> busyMap = new HashMap<>();

        StringBuilder getBusyBuffer(char type) {
            String fbType = FBTYPES.get(type);
            StringBuilder buffer = busyMap.get(fbType);
            if (buffer == null) {
                buffer = new StringBuilder();
                busyMap.put(fbType, buffer);
            }
            return buffer;
        }

        void startBusy(char type, Calendar currentCal) {
            if (type == '4') {
                knownAttendee = false;
            } else if (type != '0') {
                StringBuilder busyBuffer = getBusyBuffer(type);
                if (busyBuffer.length() > 0) {
                    busyBuffer.append(',');
                }
                busyBuffer.append(icalParser.format(currentCal.getTime()));
            }
        }

        void endBusy(char type, Calendar currentCal) {
            if (type != '0' && type != '4') {
                getBusyBuffer(type).append('/').append(icalParser.format(currentCal.getTime()));
            }
        }

        FreeBusy(SimpleDateFormat icalParser, Date startDate, String fbdata) {
            this.icalParser = icalParser;
            if (fbdata.length() > 0) {
                Calendar currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                currentCal.setTime(startDate);

                startBusy(fbdata.charAt(0), currentCal);
                for (int i = 1; i < fbdata.length() && knownAttendee; i++) {
                    currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                    char previousState = fbdata.charAt(i - 1);
                    char currentState = fbdata.charAt(i);
                    if (previousState != currentState) {
                        endBusy(previousState, currentCal);
                        startBusy(currentState, currentCal);
                    }
                }
                currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                endBusy(fbdata.charAt(fbdata.length() - 1), currentCal);
            }
        }

        /**
         * Append freebusy information to buffer.
         *
         * @param buffer String buffer
         */
        public void appendTo(StringBuilder buffer) {
            for (Map.Entry<String, StringBuilder> entry : busyMap.entrySet()) {
                buffer.append("FREEBUSY;FBTYPE=").append(entry.getKey())
                        .append(':').append(entry.getValue()).append((char) 13).append((char) 10);
            }
        }
    }

    protected VObject vTimezone;

    /**
     * Load and return current user OWA timezone.
     *
     * @return current timezone
     */
    public VObject getVTimezone() {
        if (vTimezone == null) {
            // need to load Timezone info from OWA
            loadVtimezone();
        }
        return vTimezone;
    }

    public void clearVTimezone() {
        vTimezone = null;
    }

    protected abstract void loadVtimezone();

}

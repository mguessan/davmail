/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
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
package davmail.exchange.ews;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.VCalendar;
import davmail.exchange.VObject;
import davmail.exchange.VProperty;
import davmail.exchange.auth.O365Token;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import davmail.ui.NotificationDialog;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * EWS Exchange adapter.
 * Compatible with Exchange 2007, 2010 and 2013.
 */
public class EwsExchangeSession extends ExchangeSession {

    protected static final int PAGE_SIZE = 500;

    protected static final String ARCHIVE_ROOT = "/archive/";

    /**
     * Message types.
     *
     * @see <a href="http://msdn.microsoft.com/en-us/library/aa565652%28v=EXCHG.140%29.aspx">
     * http://msdn.microsoft.com/en-us/library/aa565652%28v=EXCHG.140%29.aspx</a>
     */
    protected static final Set<String> MESSAGE_TYPES = new HashSet<>();

    static {
        MESSAGE_TYPES.add("Message");
        MESSAGE_TYPES.add("CalendarItem");

        MESSAGE_TYPES.add("MeetingMessage");
        MESSAGE_TYPES.add("MeetingRequest");
        MESSAGE_TYPES.add("MeetingResponse");
        MESSAGE_TYPES.add("MeetingCancellation");

        MESSAGE_TYPES.add("Item");
        MESSAGE_TYPES.add("PostItem");

        // exclude types from IMAP
        //MESSAGE_TYPES.add("Contact");
        //MESSAGE_TYPES.add("DistributionList");
        //MESSAGE_TYPES.add("Task");

        //ReplyToItem
        //ForwardItem
        //ReplyAllToItem
        //AcceptItem
        //TentativelyAcceptItem
        //DeclineItem
        //CancelCalendarItem
        //RemoveItem
        //PostReplyItem
        //SuppressReadReceipt
        //AcceptSharingInvitation
    }

    static final Map<String, String> partstatToResponseMap = new HashMap<>();
    static final Map<String, String> responseTypeToPartstatMap = new HashMap<>();
    static final Map<String, String> statusToBusyStatusMap = new HashMap<>();

    static {
        partstatToResponseMap.put("ACCEPTED", "AcceptItem");
        partstatToResponseMap.put("TENTATIVE", "TentativelyAcceptItem");
        partstatToResponseMap.put("DECLINED", "DeclineItem");
        partstatToResponseMap.put("NEEDS-ACTION", "ReplyToItem");

        responseTypeToPartstatMap.put("Accept", "ACCEPTED");
        responseTypeToPartstatMap.put("Tentative", "TENTATIVE");
        responseTypeToPartstatMap.put("Decline", "DECLINED");
        responseTypeToPartstatMap.put("NoResponseReceived", "NEEDS-ACTION");
        responseTypeToPartstatMap.put("Unknown", "NEEDS-ACTION");

        statusToBusyStatusMap.put("TENTATIVE", "Tentative");
        statusToBusyStatusMap.put("CONFIRMED", "Busy");
        // Unable to map CANCELLED: cancelled events are directly deleted on Exchange
    }

    protected HttpClientAdapter httpClient;

    protected Map<String, String> folderIdMap;
    protected boolean directEws;

    /**
     * Oauth2 token
     */
    private O365Token token;

    protected class Folder extends ExchangeSession.Folder {
        public FolderId folderId;
    }

    protected static class FolderPath {
        protected final String parentPath;
        protected final String folderName;

        protected FolderPath(String folderPath) {
            int slashIndex = folderPath.lastIndexOf('/');
            if (slashIndex < 0) {
                parentPath = "";
                folderName = folderPath;
            } else {
                parentPath = folderPath.substring(0, slashIndex);
                folderName = folderPath.substring(slashIndex + 1);
            }
        }
    }

    public EwsExchangeSession(HttpClientAdapter httpClient, String userName) throws IOException {
        this.httpClient = httpClient;
        this.userName = userName;
        if (userName.contains("@")) {
            this.email = userName;
        }
        buildSessionInfo(null);
    }

    public EwsExchangeSession(HttpClientAdapter httpClient, URI uri, String userName) throws IOException {
        this.httpClient = httpClient;
        this.userName = userName;
        if (userName.contains("@")) {
            this.email = userName;
            this.alias = userName.substring(0, userName.indexOf('@'));
        }
        buildSessionInfo(uri);
    }

    public EwsExchangeSession(HttpClientAdapter httpClient, O365Token token, String userName) throws IOException {
        this.httpClient = httpClient;
        this.userName = userName;
        if (userName.contains("@")) {
            this.email = userName;
            this.alias = userName.substring(0, userName.indexOf('@'));
        }
        this.token = token;
        buildSessionInfo(null);
    }

    public EwsExchangeSession(URI uri, O365Token token, String userName) throws IOException {
        this(new HttpClientAdapter(uri, true), token, userName);
    }

    public EwsExchangeSession(String url, String userName, String password) throws IOException {
        this(new HttpClientAdapter(url, userName, password, true), userName);
    }

    /**
     * EWS fetch page size.
     *
     * @return page size
     */
    private static int getPageSize() {
        return Settings.getIntProperty("davmail.folderFetchPageSize", PAGE_SIZE);
    }

    /**
     * Check endpoint url.
     *
     * @throws IOException on error
     */
    protected void checkEndPointUrl() throws IOException {
        GetFolderMethod checkMethod = new GetFolderMethod(BaseShape.ID_ONLY,
                DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root), null);
        int status = executeMethod(checkMethod);

        if (status == HttpStatus.SC_UNAUTHORIZED) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        } else if (status != HttpStatus.SC_OK) {
            throw new IOException("Ews endpoint not available at " + checkMethod.getURI().toString() + " status " + status);
        }
    }

    @Override
    public void buildSessionInfo(java.net.URI uri) throws IOException {
        // send a first request to get server version
        checkEndPointUrl();

        // new approach based on ConvertId to find primary email address
        if (email == null || alias == null) {
            try {
                GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ID_ONLY,
                        DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root),
                        null);
                executeMethod(getFolderMethod);
                EWSMethod.Item item = getFolderMethod.getResponseItem();
                String folderId = item.get("FolderId");

                ConvertIdMethod convertIdMethod = new ConvertIdMethod(folderId);
                executeMethod(convertIdMethod);
                EWSMethod.Item convertIdItem = convertIdMethod.getResponseItem();
                if (convertIdItem != null && !convertIdItem.isEmpty()) {
                    email = convertIdItem.get("Mailbox");
                    alias = email.substring(0, email.indexOf('@'));
                } else {
                    LOGGER.error("Unable to resolve email from root folder");
                    throw new IOException();
                }

            } catch (IOException e) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }
        }

        directEws = uri == null
                || "/ews/services.wsdl".equalsIgnoreCase(uri.getPath())
                || "/ews/exchange.asmx".equalsIgnoreCase(uri.getPath());

        currentMailboxPath = "/users/" + email.toLowerCase();

        try {
            folderIdMap = new HashMap<>();
            // load actual well known folder ids
            folderIdMap.put(internalGetFolder(INBOX).folderId.value, INBOX);
            folderIdMap.put(internalGetFolder(CALENDAR).folderId.value, CALENDAR);
            folderIdMap.put(internalGetFolder(CONTACTS).folderId.value, CONTACTS);
            folderIdMap.put(internalGetFolder(SENT).folderId.value, SENT);
            folderIdMap.put(internalGetFolder(DRAFTS).folderId.value, DRAFTS);
            folderIdMap.put(internalGetFolder(TRASH).folderId.value, TRASH);
            folderIdMap.put(internalGetFolder(JUNK).folderId.value, JUNK);
            folderIdMap.put(internalGetFolder(UNSENT).folderId.value, UNSENT);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        }
        LOGGER.debug("Current user email is " + email + ", alias is " + alias + " on " + serverVersion);
    }

    protected String getEmailSuffixFromHostname() {
        String domain = httpClient.getHost();
        int start = domain.lastIndexOf('.', domain.lastIndexOf('.') - 1);
        if (start >= 0) {
            return '@' + domain.substring(start + 1);
        } else {
            return '@' + domain;
        }
    }

    protected void resolveEmailAddress(String userName) {
        String searchValue = userName;
        int index = searchValue.indexOf('\\');
        if (index >= 0) {
            searchValue = searchValue.substring(index + 1);
        }
        ResolveNamesMethod resolveNamesMethod = new ResolveNamesMethod(searchValue);
        try {
            // send a fake request to get server version
            internalGetFolder("");
            executeMethod(resolveNamesMethod);
            List<EWSMethod.Item> responses = resolveNamesMethod.getResponseItems();
            if (responses.size() == 1) {
                email = responses.get(0).get("EmailAddress");
            }

        } catch (IOException e) {
            // ignore
        }
    }

    class Message extends ExchangeSession.Message {
        // message item id
        ItemId itemId;

        @Override
        public String getPermanentId() {
            return itemId.id;
        }

        @Override
        protected InputStream getMimeHeaders() {
            InputStream result = null;
            try {
                GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
                getItemMethod.addAdditionalProperty(Field.get("messageheaders"));
                getItemMethod.addAdditionalProperty(Field.get("from"));
                executeMethod(getItemMethod);
                EWSMethod.Item item = getItemMethod.getResponseItem();

                String messageHeaders = item.get(Field.get("messageheaders").getResponseName());
                if (messageHeaders != null
                        // workaround for broken message headers on Exchange 2010
                        && messageHeaders.toLowerCase().contains("message-id:")) {
                    // workaround for messages in Sent folder
                    if (!messageHeaders.contains("From:")) {
                        String from = item.get(Field.get("from").getResponseName());
                        messageHeaders = "From: " + from + '\n' + messageHeaders;
                    }

                    result = new ByteArrayInputStream(messageHeaders.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }

            return result;
        }
    }

    /**
     * Message create/update properties
     *
     * @param properties flag values map
     * @return field values
     */
    protected List<FieldUpdate> buildProperties(Map<String, String> properties) {
        ArrayList<FieldUpdate> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if ("read".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("read", Boolean.toString("1".equals(entry.getValue()))));
            } else if ("junk".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("junk", entry.getValue()));
            } else if ("flagged".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("flagStatus", entry.getValue()));
            } else if ("answered".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("lastVerbExecuted", entry.getValue()));
                if ("102".equals(entry.getValue())) {
                    list.add(Field.createFieldUpdate("iconIndex", "261"));
                }
            } else if ("forwarded".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("lastVerbExecuted", entry.getValue()));
                if ("104".equals(entry.getValue())) {
                    list.add(Field.createFieldUpdate("iconIndex", "262"));
                }
            } else if ("draft".equals(entry.getKey())) {
                // note: draft is readonly after create
                list.add(Field.createFieldUpdate("messageFlags", entry.getValue()));
            } else if ("deleted".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("deleted", entry.getValue()));
            } else if ("datereceived".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("datereceived", entry.getValue()));
            } else if ("keywords".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("keywords", entry.getValue()));
            }
        }
        return list;
    }

    @Override
    public ExchangeSession.Message createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(baos);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
        baos.close();
        item.mimeContent = IOUtil.encodeBase64(baos.toByteArray());

        List<FieldUpdate> fieldUpdates = buildProperties(properties);
        if (!properties.containsKey("draft")) {
            // need to force draft flag to false
            if (properties.containsKey("read")) {
                fieldUpdates.add(Field.createFieldUpdate("messageFlags", "1"));
            } else {
                fieldUpdates.add(Field.createFieldUpdate("messageFlags", "0"));
            }
        }
        fieldUpdates.add(Field.createFieldUpdate("urlcompname", messageName));
        item.setFieldUpdates(fieldUpdates);
        CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, getFolderId(folderPath), item);
        executeMethod(createItemMethod);

        ItemId newItemId = new ItemId(createItemMethod.getResponseItem());
        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, newItemId, false);
        for (String attribute : IMAP_MESSAGE_ATTRIBUTES) {
            getItemMethod.addAdditionalProperty(Field.get(attribute));
        }
        executeMethod(getItemMethod);

        return buildMessage(getItemMethod.getResponseItem());

    }

    @Override
    public void updateMessage(ExchangeSession.Message message, Map<String, String> properties) throws IOException {
        if (properties.containsKey("read") && "urn:content-classes:appointment".equals(message.contentClass)) {
            properties.remove("read");
        }
        if (!properties.isEmpty()) {
            UpdateItemMethod updateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                    ConflictResolution.AlwaysOverwrite,
                    SendMeetingInvitationsOrCancellations.SendToNone,
                    ((EwsExchangeSession.Message) message).itemId, buildProperties(properties));
            executeMethod(updateItemMethod);
        }
    }

    @Override
    public void deleteMessage(ExchangeSession.Message message) throws IOException {
        LOGGER.debug("Delete " + message.imapUid);
        DeleteItemMethod deleteItemMethod = new DeleteItemMethod(((EwsExchangeSession.Message) message).itemId, DeleteType.HardDelete, SendMeetingCancellations.SendToNone);
        executeMethod(deleteItemMethod);
    }


    protected void sendMessage(String itemClass, byte[] messageBody) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
        item.mimeContent = IOUtil.encodeBase64(messageBody);
        if (itemClass != null) {
            item.put("ItemClass", itemClass);
        }

        MessageDisposition messageDisposition;
        if (Settings.getBooleanProperty("davmail.smtpSaveInSent", true)) {
            messageDisposition = MessageDisposition.SendAndSaveCopy;
        } else {
            messageDisposition = MessageDisposition.SendOnly;
        }

        CreateItemMethod createItemMethod = new CreateItemMethod(messageDisposition, getFolderId(SENT), item);
        executeMethod(createItemMethod);
    }

    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {
        String itemClass = null;
        if (mimeMessage.getContentType().startsWith("multipart/report")) {
            itemClass = "REPORT.IPM.Note.IPNRN";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(baos);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
        sendMessage(itemClass, baos.toByteArray());
    }

    /**
     * @inheritDoc
     */
    @Override
    protected byte[] getContent(ExchangeSession.Message message) throws IOException {
        return getContent(((EwsExchangeSession.Message) message).itemId);
    }

    /**
     * Get item content.
     *
     * @param itemId EWS item id
     * @return item content as byte array
     * @throws IOException on error
     */
    protected byte[] getContent(ItemId itemId) throws IOException {
        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, true);
        byte[] mimeContent = null;
        try {
            executeMethod(getItemMethod);
            mimeContent = getItemMethod.getMimeContent();
        } catch (EWSException e) {
            LOGGER.warn("GetItem with MimeContent failed: " + e.getMessage());
        }
        if (getItemMethod.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
            throw new HttpNotFoundException("Item " + itemId + " not found");
        }
        if (mimeContent == null) {
            LOGGER.warn("MimeContent not available, trying to rebuild from properties");
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
                getItemMethod.addAdditionalProperty(Field.get("contentclass"));
                getItemMethod.addAdditionalProperty(Field.get("message-id"));
                getItemMethod.addAdditionalProperty(Field.get("from"));
                getItemMethod.addAdditionalProperty(Field.get("to"));
                getItemMethod.addAdditionalProperty(Field.get("cc"));
                getItemMethod.addAdditionalProperty(Field.get("subject"));
                getItemMethod.addAdditionalProperty(Field.get("date"));
                getItemMethod.addAdditionalProperty(Field.get("body"));
                executeMethod(getItemMethod);
                EWSMethod.Item item = getItemMethod.getResponseItem();

                if (item == null) {
                    throw new HttpNotFoundException("Item " + itemId + " not found");
                }

                MimeMessage mimeMessage = new MimeMessage((Session) null);
                mimeMessage.addHeader("Content-class", item.get(Field.get("contentclass").getResponseName()));
                mimeMessage.setSentDate(parseDateFromExchange(item.get(Field.get("date").getResponseName())));
                mimeMessage.addHeader("From", item.get(Field.get("from").getResponseName()));
                mimeMessage.addHeader("To", item.get(Field.get("to").getResponseName()));
                mimeMessage.addHeader("Cc", item.get(Field.get("cc").getResponseName()));
                mimeMessage.setSubject(item.get(Field.get("subject").getResponseName()));
                String propertyValue = item.get(Field.get("body").getResponseName());
                if (propertyValue == null) {
                    propertyValue = "";
                }
                mimeMessage.setContent(propertyValue, "text/html; charset=UTF-8");

                mimeMessage.writeTo(baos);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Rebuilt message content: " + new String(baos.toByteArray(), StandardCharsets.UTF_8));
                }
                mimeContent = baos.toByteArray();

            } catch (IOException | MessagingException e2) {
                LOGGER.warn(e2);
            }
            if (mimeContent == null) {
                throw new IOException("GetItem returned null MimeContent");
            }
        }
        return mimeContent;
    }

    protected ExchangeSession.Message buildMessage(EWSMethod.Item response) throws DavMailException {
        Message message = new Message();

        // get item id
        message.itemId = new ItemId(response);

        message.permanentUrl = response.get(Field.get("permanenturl").getResponseName());

        message.size = response.getInt(Field.get("messageSize").getResponseName());
        message.uid = response.get(Field.get("uid").getResponseName());
        message.contentClass = response.get(Field.get("contentclass").getResponseName());
        message.imapUid = response.getLong(Field.get("imapUid").getResponseName());
        message.read = response.getBoolean(Field.get("read").getResponseName());
        message.junk = response.getBoolean(Field.get("junk").getResponseName());
        message.flagged = "2".equals(response.get(Field.get("flagStatus").getResponseName()));
        message.draft = (response.getInt(Field.get("messageFlags").getResponseName()) & 8) != 0;
        String lastVerbExecuted = response.get(Field.get("lastVerbExecuted").getResponseName());
        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
        message.forwarded = "104".equals(lastVerbExecuted);
        message.date = convertDateFromExchange(response.get(Field.get("date").getResponseName()));
        message.deleted = "1".equals(response.get(Field.get("deleted").getResponseName()));

        String lastmodified = convertDateFromExchange(response.get(Field.get("lastmodified").getResponseName()));
        message.recent = !message.read && lastmodified != null && lastmodified.equals(message.date);

        message.keywords = response.get(Field.get("keywords").getResponseName());

        if (LOGGER.isDebugEnabled()) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("Message");
            if (message.imapUid != 0) {
                buffer.append(" IMAP uid: ").append(message.imapUid);
            }
            if (message.uid != null) {
                buffer.append(" uid: ").append(message.uid);
            }
            buffer.append(" ItemId: ").append(message.itemId.id);
            buffer.append(" ChangeKey: ").append(message.itemId.changeKey);
            LOGGER.debug(buffer.toString());
        }
        return message;
    }

    @Override
    public MessageList searchMessages(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        MessageList messages = new MessageList();
        int maxCount = Settings.getIntProperty("davmail.folderSizeLimit", 0);
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition, FolderQueryTraversal.SHALLOW, maxCount);

        for (EWSMethod.Item response : responses) {
            if (MESSAGE_TYPES.contains(response.type)) {
                ExchangeSession.Message message = buildMessage(response);
                message.messageList = messages;
                messages.add(message);
            }
        }
        Collections.sort(messages);
        return messages;
    }

    protected List<EWSMethod.Item> searchItems(String folderPath, Set<String> attributes, Condition condition, FolderQueryTraversal folderQueryTraversal, int maxCount) throws IOException {
        if (maxCount == 0) {
            // unlimited search
            return searchItems(folderPath, attributes, condition, folderQueryTraversal);
        }
        // limited search, do not use paged search, limit with maxCount, sort by imapUid descending to get latest items
        int resultCount;
        FindItemMethod findItemMethod;

        // search items in folder, do not retrieve all properties
        findItemMethod = new FindItemMethod(folderQueryTraversal, BaseShape.ID_ONLY, getFolderId(folderPath), 0, maxCount);
        for (String attribute : attributes) {
            findItemMethod.addAdditionalProperty(Field.get(attribute));
        }
        // make sure imapUid is available
        if (!attributes.contains("imapUid")) {
            findItemMethod.addAdditionalProperty(Field.get("imapUid"));
        }

        // always sort items by imapUid descending to retrieve recent messages first
        findItemMethod.setFieldOrder(new FieldOrder(Field.get("imapUid"), FieldOrder.Order.Descending));

        if (condition != null && !condition.isEmpty()) {
            findItemMethod.setSearchExpression((SearchExpression) condition);
        }
        executeMethod(findItemMethod);
        List<EWSMethod.Item> results = new ArrayList<>(findItemMethod.getResponseItems());
        resultCount = results.size();
        if (resultCount > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Folder " + folderPath + " - Search items count: " + resultCount + " maxCount: " + maxCount
                    + " highest uid: " + results.get(0).getLong(Field.get("imapUid").getResponseName())
                    + " lowest uid: " + results.get(resultCount - 1).getLong(Field.get("imapUid").getResponseName()));
        }


        return results;
    }

    /**
     * Paged search, retrieve all items.
     *
     * @param folderPath           folder path
     * @param attributes           attributes
     * @param condition            search condition
     * @param folderQueryTraversal search mode
     * @return items
     * @throws IOException on error
     */
    protected List<EWSMethod.Item> searchItems(String folderPath, Set<String> attributes, Condition condition, FolderQueryTraversal folderQueryTraversal) throws IOException {
        int resultCount = 0;
        List<EWSMethod.Item> results = new ArrayList<>();
        FolderId folderId = getFolderId(folderPath);
        FindItemMethod findItemMethod;
        do {
            // search items in folder, do not retrieve all properties
            findItemMethod = new FindItemMethod(folderQueryTraversal, BaseShape.ID_ONLY, folderId, resultCount, getPageSize());
            for (String attribute : attributes) {
                findItemMethod.addAdditionalProperty(Field.get(attribute));
            }
            // make sure imapUid is available
            if (!attributes.contains("imapUid")) {
                findItemMethod.addAdditionalProperty(Field.get("imapUid"));
            }

            // always sort items by imapUid ascending to retrieve pages in creation order
            findItemMethod.setFieldOrder(new FieldOrder(Field.get("imapUid"), FieldOrder.Order.Ascending));

            if (condition != null && !condition.isEmpty()) {
                findItemMethod.setSearchExpression((SearchExpression) condition);
            }
            executeMethod(findItemMethod);
            if (findItemMethod.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                throw new EWSException(findItemMethod.errorDetail);
            }

            long highestUid = 0;
            if (resultCount > 0) {
                highestUid = results.get(resultCount - 1).getLong(Field.get("imapUid").getResponseName());
            }
            // Only add new result if not already available (concurrent folder changes issue)
            for (EWSMethod.Item item : findItemMethod.getResponseItems()) {
                long imapUid = item.getLong(Field.get("imapUid").getResponseName());
                if (imapUid > highestUid) {
                    results.add(item);
                }
            }
            resultCount = results.size();
            if (resultCount > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Folder " + folderPath + " - Search items current count: " + resultCount + " fetchCount: " + getPageSize()
                        + " highest uid: " + results.get(resultCount - 1).getLong(Field.get("imapUid").getResponseName())
                        + " lowest uid: " + results.get(0).getLong(Field.get("imapUid").getResponseName()));
            }
            if (Thread.interrupted()) {
                LOGGER.debug("Folder " + folderPath + " - Search items failed: Interrupted by client");
                throw new IOException("Search items failed: Interrupted by client");
            }
        } while (!(findItemMethod.includesLastItemInRange));
        return results;
    }

    protected static class MultiCondition extends ExchangeSession.MultiCondition implements SearchExpression {
        protected MultiCondition(Operator operator, Condition... condition) {
            super(operator, condition);
        }

        public void appendTo(StringBuilder buffer) {
            int actualConditionCount = 0;
            for (Condition condition : conditions) {
                if (!condition.isEmpty()) {
                    actualConditionCount++;
                }
            }
            if (actualConditionCount > 0) {
                if (actualConditionCount > 1) {
                    buffer.append("<t:").append(operator.toString()).append('>');
                }

                for (Condition condition : conditions) {
                    condition.appendTo(buffer);
                }

                if (actualConditionCount > 1) {
                    buffer.append("</t:").append(operator).append('>');
                }
            }
        }
    }

    protected static class NotCondition extends ExchangeSession.NotCondition implements SearchExpression {
        protected NotCondition(Condition condition) {
            super(condition);
        }

        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:Not>");
            condition.appendTo(buffer);
            buffer.append("</t:Not>");
        }
    }


    protected static class AttributeCondition extends ExchangeSession.AttributeCondition implements SearchExpression {
        protected ContainmentMode containmentMode;
        protected ContainmentComparison containmentComparison;

        protected AttributeCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
        }

        protected AttributeCondition(String attributeName, Operator operator, String value,
                                     ContainmentMode containmentMode, ContainmentComparison containmentComparison) {
            super(attributeName, operator, value);
            this.containmentMode = containmentMode;
            this.containmentComparison = containmentComparison;
        }

        protected FieldURI getFieldURI() {
            FieldURI fieldURI = Field.get(attributeName);
            // check to detect broken field mapping
            //noinspection ConstantConditions
            if (fieldURI == null) {
                throw new IllegalArgumentException("Unknown field: " + attributeName);
            }
            return fieldURI;
        }

        protected Operator getOperator() {
            return operator;
        }

        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:").append(operator.toString());
            if (containmentMode != null) {
                containmentMode.appendTo(buffer);
            }
            if (containmentComparison != null) {
                containmentComparison.appendTo(buffer);
            }
            buffer.append('>');
            FieldURI fieldURI = getFieldURI();
            fieldURI.appendTo(buffer);

            if (operator != Operator.Contains) {
                buffer.append("<t:FieldURIOrConstant>");
            }
            buffer.append("<t:Constant Value=\"");
            // encode urlcompname
            if (fieldURI instanceof ExtendedFieldURI && "0x10f3".equals(((ExtendedFieldURI) fieldURI).propertyTag)) {
                buffer.append(StringUtil.xmlEncodeAttribute(StringUtil.encodeUrlcompname(value)));
            } else if (fieldURI instanceof ExtendedFieldURI
                    && ((ExtendedFieldURI) fieldURI).propertyType == ExtendedFieldURI.PropertyType.Integer) {
                // check value
                try {
                    Integer.parseInt(value);
                    buffer.append(value);
                } catch (NumberFormatException e) {
                    // invalid value, replace with 0
                    buffer.append('0');
                }
            } else {
                buffer.append(StringUtil.xmlEncodeAttribute(value));
            }
            buffer.append("\"/>");
            if (operator != Operator.Contains) {
                buffer.append("</t:FieldURIOrConstant>");
            }

            buffer.append("</t:").append(operator).append('>');
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String lowerCaseValue = value.toLowerCase();

            String actualValue = contact.get(attributeName);
            if (actualValue == null) {
                return false;
            }
            actualValue = actualValue.toLowerCase();
            if (operator == Operator.IsEqualTo) {
                return lowerCaseValue.equals(actualValue);
            } else {
                return operator == Operator.Contains && ((containmentMode.equals(ContainmentMode.Substring) && actualValue.contains(lowerCaseValue)) ||
                        (containmentMode.equals(ContainmentMode.Prefixed) && actualValue.startsWith(lowerCaseValue)));
            }
        }

    }

    protected static class HeaderCondition extends AttributeCondition {

        protected HeaderCondition(String attributeName, String value) {
            super(attributeName, Operator.Contains, value);
            containmentMode = ContainmentMode.Substring;
            containmentComparison = ContainmentComparison.IgnoreCase;
        }

        @Override
        protected FieldURI getFieldURI() {
            return new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, attributeName);
        }

    }

    protected static class IsNullCondition implements ExchangeSession.Condition, SearchExpression {
        protected final String attributeName;

        protected IsNullCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:Not><t:Exists>");
            Field.get(attributeName).appendTo(buffer);
            buffer.append("</t:Exists></t:Not>");
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return actualValue == null;
        }

    }

    protected static class ExistsCondition implements ExchangeSession.Condition, SearchExpression {
        protected final String attributeName;

        protected ExistsCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:Exists>");
            Field.get(attributeName).appendTo(buffer);
            buffer.append("</t:Exists>");
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return actualValue == null;
        }

    }

    @Override
    public ExchangeSession.MultiCondition and(Condition... condition) {
        return new MultiCondition(Operator.And, condition);
    }

    @Override
    public ExchangeSession.MultiCondition or(Condition... condition) {
        return new MultiCondition(Operator.Or, condition);
    }

    @Override
    public Condition not(Condition condition) {
        return new NotCondition(condition);
    }

    @Override
    public Condition isEqualTo(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, value);
    }

    @Override
    public Condition isEqualTo(String attributeName, int value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, String.valueOf(value));
    }

    @Override
    public Condition headerIsEqualTo(String headerName, String value) {
        if (serverVersion.startsWith("Exchange201")) {
            if ("from".equals(headerName)
                    || "to".equals(headerName)
                    || "cc".equals(headerName)) {
                return new AttributeCondition("msg" + headerName, Operator.Contains, value, ContainmentMode.Substring, ContainmentComparison.IgnoreCase);
            } else if ("message-id".equals(headerName)
                    || "bcc".equals(headerName)) {
                return new AttributeCondition(headerName, Operator.Contains, value, ContainmentMode.Substring, ContainmentComparison.IgnoreCase);
            } else {
                // Exchange 2010 does not support header search, use PR_TRANSPORT_MESSAGE_HEADERS instead
                return new AttributeCondition("messageheaders", Operator.Contains, headerName + ": " + value, ContainmentMode.Substring, ContainmentComparison.IgnoreCase);
            }
        } else {
            return new HeaderCondition(headerName, value);
        }
    }

    @Override
    public Condition gte(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsGreaterThanOrEqualTo, value);
    }

    @Override
    public Condition lte(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsLessThanOrEqualTo, value);
    }

    @Override
    public Condition lt(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsLessThan, value);
    }

    @Override
    public Condition gt(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsGreaterThan, value);
    }

    @Override
    public Condition contains(String attributeName, String value) {
        // workaround for from: and to: headers not searchable over EWS
        if ("from".equals(attributeName)) {
            attributeName = "msgfrom";
        } else if ("to".equals(attributeName)) {
            attributeName = "displayto";
        } else if ("cc".equals(attributeName)) {
            attributeName = "displaycc";
        }
        return new AttributeCondition(attributeName, Operator.Contains, value, ContainmentMode.Substring, ContainmentComparison.IgnoreCase);
    }

    @Override
    public Condition startsWith(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.Contains, value, ContainmentMode.Prefixed, ContainmentComparison.IgnoreCase);
    }

    @Override
    public Condition isNull(String attributeName) {
        return new IsNullCondition(attributeName);
    }

    @Override
    public Condition exists(String attributeName) {
        return new ExistsCondition(attributeName);
    }

    @Override
    public Condition isTrue(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "true");
    }

    @Override
    public Condition isFalse(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "false");
    }

    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<>();

    static {
        FOLDER_PROPERTIES.add(Field.get("urlcompname"));
        FOLDER_PROPERTIES.add(Field.get("folderDisplayName"));
        FOLDER_PROPERTIES.add(Field.get("lastmodified"));
        FOLDER_PROPERTIES.add(Field.get("folderclass"));
        FOLDER_PROPERTIES.add(Field.get("ctag"));
        FOLDER_PROPERTIES.add(Field.get("count"));
        FOLDER_PROPERTIES.add(Field.get("unread"));
        FOLDER_PROPERTIES.add(Field.get("hassubs"));
        FOLDER_PROPERTIES.add(Field.get("uidNext"));
        FOLDER_PROPERTIES.add(Field.get("highestUid"));
    }

    protected Folder buildFolder(EWSMethod.Item item) {
        Folder folder = new Folder();
        folder.folderId = new FolderId(item);
        folder.displayName = encodeFolderName(item.get(Field.get("folderDisplayName").getResponseName()));
        folder.folderClass = item.get(Field.get("folderclass").getResponseName());
        folder.etag = item.get(Field.get("lastmodified").getResponseName());
        folder.ctag = item.get(Field.get("ctag").getResponseName());
        folder.messageCount = item.getInt(Field.get("count").getResponseName());
        folder.unreadCount = item.getInt(Field.get("unread").getResponseName());
        // fake recent value
        folder.recent = folder.unreadCount;
        folder.hasChildren = item.getBoolean(Field.get("hassubs").getResponseName());
        // noInferiors not implemented
        folder.uidNext = item.getInt(Field.get("uidNext").getResponseName());
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<ExchangeSession.Folder> getSubFolders(String folderPath, Condition condition, boolean recursive) throws IOException {
        String baseFolderPath = folderPath;
        if (baseFolderPath.startsWith("/users/")) {
            int index = baseFolderPath.indexOf('/', "/users/".length());
            if (index >= 0) {
                baseFolderPath = baseFolderPath.substring(index + 1);
            }
        }
        List<ExchangeSession.Folder> folders = new ArrayList<>();
        appendSubFolders(folders, baseFolderPath, getFolderId(folderPath), condition, recursive);
        return folders;
    }

    protected void appendSubFolders(List<ExchangeSession.Folder> folders,
                                    String parentFolderPath, FolderId parentFolderId,
                                    Condition condition, boolean recursive) throws IOException {
        int resultCount = 0;
        FindFolderMethod findFolderMethod;
        do {
            findFolderMethod = new FindFolderMethod(FolderQueryTraversal.SHALLOW,
                    BaseShape.ID_ONLY, parentFolderId, FOLDER_PROPERTIES, (SearchExpression) condition, resultCount, getPageSize());
            executeMethod(findFolderMethod);
            for (EWSMethod.Item item : findFolderMethod.getResponseItems()) {
                resultCount++;
                Folder folder = buildFolder(item);
                if (!parentFolderPath.isEmpty()) {
                    if (parentFolderPath.endsWith("/")) {
                        folder.folderPath = parentFolderPath + folder.displayName;
                    } else {
                        folder.folderPath = parentFolderPath + '/' + folder.displayName;
                    }
                } else if (folderIdMap.get(folder.folderId.value) != null) {
                    folder.folderPath = folderIdMap.get(folder.folderId.value);
                } else {
                    folder.folderPath = folder.displayName;
                }
                folders.add(folder);
                if (recursive && folder.hasChildren) {
                    appendSubFolders(folders, folder.folderPath, folder.folderId, condition, true);
                }
            }
        } while (!(findFolderMethod.includesLastItemInRange));
    }

    /**
     * Get folder by path.
     *
     * @param folderPath folder path
     * @return folder object
     * @throws IOException on error
     */
    @Override
    protected EwsExchangeSession.Folder internalGetFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ID_ONLY, folderId, FOLDER_PROPERTIES);
        executeMethod(getFolderMethod);
        EWSMethod.Item item = getFolderMethod.getResponseItem();
        Folder folder;
        if (item != null) {
            folder = buildFolder(item);
            folder.folderPath = folderPath;
        } else {
            throw new HttpNotFoundException("Folder " + folderPath + " not found");
        }
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int createFolder(String folderPath, String folderClass, Map<String, String> properties) throws IOException {
        FolderPath path = new FolderPath(folderPath);
        EWSMethod.Item folder = new EWSMethod.Item();
        folder.type = "Folder";
        folder.put("FolderClass", folderClass);
        folder.put("DisplayName", decodeFolderName(path.folderName));
        // TODO: handle properties
        CreateFolderMethod createFolderMethod = new CreateFolderMethod(getFolderId(path.parentPath), folder);
        executeMethod(createFolderMethod);
        return HttpStatus.SC_CREATED;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int updateFolder(String folderPath, Map<String, String> properties) throws IOException {
        ArrayList<FieldUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            updates.add(new FieldUpdate(Field.get(entry.getKey()), entry.getValue()));
        }
        UpdateFolderMethod updateFolderMethod = new UpdateFolderMethod(internalGetFolder(folderPath).folderId, updates);

        executeMethod(updateFolderMethod);
        return HttpStatus.SC_CREATED;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deleteFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId != null) {
            DeleteFolderMethod deleteFolderMethod = new DeleteFolderMethod(folderId);
            executeMethod(deleteFolderMethod);
        } else {
            LOGGER.debug("Folder " + folderPath + " not found");
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void moveMessage(ExchangeSession.Message message, String targetFolder) throws IOException {
        MoveItemMethod moveItemMethod = new MoveItemMethod(((EwsExchangeSession.Message) message).itemId, getFolderId(targetFolder));
        executeMethod(moveItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void moveMessages(List<ExchangeSession.Message> messages, String targetFolder) throws IOException {
        ArrayList<ItemId> itemIds = new ArrayList<>();
        for (ExchangeSession.Message message : messages) {
            itemIds.add(((EwsExchangeSession.Message) message).itemId);
        }

        MoveItemMethod moveItemMethod = new MoveItemMethod(itemIds, getFolderId(targetFolder));
        executeMethod(moveItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void copyMessage(ExchangeSession.Message message, String targetFolder) throws IOException {
        CopyItemMethod copyItemMethod = new CopyItemMethod(((EwsExchangeSession.Message) message).itemId, getFolderId(targetFolder));
        executeMethod(copyItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void copyMessages(List<ExchangeSession.Message> messages, String targetFolder) throws IOException {
        ArrayList<ItemId> itemIds = new ArrayList<>();
        for (ExchangeSession.Message message : messages) {
            itemIds.add(((EwsExchangeSession.Message) message).itemId);
        }

        CopyItemMethod copyItemMethod = new CopyItemMethod(itemIds, getFolderId(targetFolder));
        executeMethod(copyItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void moveFolder(String folderPath, String targetFolderPath) throws IOException {
        FolderPath path = new FolderPath(folderPath);
        FolderPath targetPath = new FolderPath(targetFolderPath);
        FolderId folderId = getFolderId(folderPath);
        FolderId toFolderId = getFolderId(targetPath.parentPath);
        toFolderId.changeKey = null;
        // move folder
        if (!path.parentPath.equals(targetPath.parentPath)) {
            MoveFolderMethod moveFolderMethod = new MoveFolderMethod(folderId, toFolderId);
            executeMethod(moveFolderMethod);
        }
        // rename folder
        if (!path.folderName.equals(targetPath.folderName)) {
            ArrayList<FieldUpdate> updates = new ArrayList<>();
            updates.add(new FieldUpdate(Field.get("folderDisplayName"), targetPath.folderName));
            UpdateFolderMethod updateFolderMethod = new UpdateFolderMethod(folderId, updates);
            executeMethod(updateFolderMethod);
        }
    }

    @Override
    public void moveItem(String sourcePath, String targetPath) throws IOException {
        FolderPath sourceFolderPath = new FolderPath(sourcePath);
        Item item = getItem(sourceFolderPath.parentPath, sourceFolderPath.folderName);
        FolderPath targetFolderPath = new FolderPath(targetPath);
        FolderId toFolderId = getFolderId(targetFolderPath.parentPath);
        MoveItemMethod moveItemMethod = new MoveItemMethod(((Event) item).itemId, toFolderId);
        executeMethod(moveItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void moveToTrash(ExchangeSession.Message message) throws IOException {
        MoveItemMethod moveItemMethod = new MoveItemMethod(((EwsExchangeSession.Message) message).itemId, getFolderId(TRASH));
        executeMethod(moveItemMethod);
    }

    protected class Contact extends ExchangeSession.Contact {
        // item id
        ItemId itemId;

        protected Contact(EWSMethod.Item response) throws DavMailException {
            itemId = new ItemId(response);

            permanentUrl = response.get(Field.get("permanenturl").getResponseName());
            etag = response.get(Field.get("etag").getResponseName());
            displayName = response.get(Field.get("displayname").getResponseName());
            // prefer urlcompname (client provided item name) for contacts
            itemName = StringUtil.decodeUrlcompname(response.get(Field.get("urlcompname").getResponseName()));
            // if urlcompname is empty, this is a server created item
            // if urlcompname is an itemId, something went wrong, ignore
            if (itemName == null || isItemId(itemName)) {
                itemName = StringUtil.base64ToUrl(itemId.id) + ".EML";
            }
            for (String attributeName : CONTACT_ATTRIBUTES) {
                String value = response.get(Field.get(attributeName).getResponseName());
                if (value != null && !value.isEmpty()) {
                    if ("bday".equals(attributeName) || "anniversary".equals(attributeName) || "lastmodified".equals(attributeName) || "datereceived".equals(attributeName)) {
                        value = convertDateFromExchange(value);
                    }
                    put(attributeName, value);
                }
            }

            if (response.getMembers() != null) {
                for (String member : response.getMembers()) {
                    addMember(member);
                }
            }
        }

        protected Contact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) {
            super(folderPath, itemName, properties, etag, noneMatch);
        }

        /**
         * Empty constructor for GalFind
         */
        protected Contact() {
        }

        protected void buildFieldUpdates(List<FieldUpdate> updates, boolean create) {
            for (Map.Entry<String, String> entry : entrySet()) {
                if ("photo".equals(entry.getKey())) {
                    updates.add(Field.createFieldUpdate("haspicture", "true"));
                } else if (!entry.getKey().startsWith("email") && !entry.getKey().startsWith("smtpemail")
                        && !"fileas".equals(entry.getKey())) {
                    updates.add(Field.createFieldUpdate(entry.getKey(), entry.getValue()));
                }
            }
            if (create && get("fileas") != null) {
                updates.add(Field.createFieldUpdate("fileas", get("fileas")));
            }
            // handle email addresses
            IndexedFieldUpdate emailFieldUpdate = null;
            for (Map.Entry<String, String> entry : entrySet()) {
                if (entry.getKey().startsWith("smtpemail")) {
                    if (emailFieldUpdate == null) {
                        emailFieldUpdate = new IndexedFieldUpdate("EmailAddresses");
                    }
                    emailFieldUpdate.addFieldValue(Field.createFieldUpdate(entry.getKey(), entry.getValue()));
                }
            }
            if (emailFieldUpdate != null) {
                updates.add(emailFieldUpdate);
            }
            // handle list members
            MultiValuedFieldUpdate memberFieldUpdate = null;
            if (distributionListMembers != null) {
                for (String member : distributionListMembers) {
                    if (memberFieldUpdate == null) {
                        memberFieldUpdate = new MultiValuedFieldUpdate(Field.get("members"));
                    }
                    memberFieldUpdate.addValue(member);
                }
            }
            if (memberFieldUpdate != null) {
                updates.add(memberFieldUpdate);
            }
        }


        /**
         * Create or update contact
         *
         * @return action result
         * @throws IOException on error
         */
        @Override
        public ItemResult createOrUpdate() throws IOException {
            String photo = get("photo");

            ItemResult itemResult = new ItemResult();
            EWSMethod createOrUpdateItemMethod;

            // first try to load existing event
            String currentEtag = null;
            ItemId currentItemId = null;
            FileAttachment currentFileAttachment = null;
            EWSMethod.Item currentItem = getEwsItem(folderPath, itemName, ITEM_PROPERTIES);
            if (currentItem != null) {
                currentItemId = new ItemId(currentItem);
                currentEtag = currentItem.get(Field.get("etag").getResponseName());

                // load current picture
                GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, currentItemId, false);
                getItemMethod.addAdditionalProperty(Field.get("attachments"));
                executeMethod(getItemMethod);
                EWSMethod.Item item = getItemMethod.getResponseItem();
                if (item != null) {
                    currentFileAttachment = item.getAttachmentByName("ContactPicture.jpg");
                }
            }
            if ("*".equals(noneMatch)) {
                // create requested
                //noinspection VariableNotUsedInsideIf
                if (currentItemId != null) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            } else if (etag != null) {
                // update requested
                if (currentItemId == null || !etag.equals(currentEtag)) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            }

            List<FieldUpdate> fieldUpdates = new ArrayList<>();
            if (currentItemId != null) {
                buildFieldUpdates(fieldUpdates, false);
                // update
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AlwaysOverwrite,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        currentItemId, fieldUpdates);
            } else {
                // create
                EWSMethod.Item newItem = new EWSMethod.Item();
                if ("IPM.DistList".equals(get("outlookmessageclass"))) {
                    newItem.type = "DistributionList";
                } else {
                    newItem.type = "Contact";
                }
                // force urlcompname on create
                fieldUpdates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                buildFieldUpdates(fieldUpdates, true);
                newItem.setFieldUpdates(fieldUpdates);
                createOrUpdateItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, getFolderId(folderPath), newItem);
            }
            executeMethod(createOrUpdateItemMethod);

            itemResult.status = createOrUpdateItemMethod.getStatusCode();
            if (itemResult.status == HttpURLConnection.HTTP_OK) {
                //noinspection VariableNotUsedInsideIf
                if (etag == null) {
                    itemResult.status = HttpStatus.SC_CREATED;
                    LOGGER.debug("Created contact " + getHref());
                } else {
                    LOGGER.debug("Updated contact " + getHref());
                }
            } else {
                return itemResult;
            }

            ItemId newItemId = new ItemId(createOrUpdateItemMethod.getResponseItem());

            // disable contact picture handling on Exchange 2007
            if (!"Exchange2007_SP1".equals(serverVersion)
                    // prefer user provided photo
                    && getADPhoto(get("smtpemail1")) == null) {
                // first delete current picture
                if (currentFileAttachment != null) {
                    DeleteAttachmentMethod deleteAttachmentMethod = new DeleteAttachmentMethod(currentFileAttachment.attachmentId);
                    executeMethod(deleteAttachmentMethod);
                }

                if (photo != null) {
                    // convert image to jpeg
                    byte[] resizedImageBytes = IOUtil.resizeImage(IOUtil.decodeBase64(photo), 90);

                    FileAttachment attachment = new FileAttachment("ContactPicture.jpg", "image/jpeg", IOUtil.encodeBase64AsString(resizedImageBytes));
                    attachment.setIsContactPhoto(true);

                    // update photo attachment
                    CreateAttachmentMethod createAttachmentMethod = new CreateAttachmentMethod(newItemId, attachment);
                    executeMethod(createAttachmentMethod);
                }
            }

            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, newItemId, false);
            getItemMethod.addAdditionalProperty(Field.get("etag"));
            executeMethod(getItemMethod);
            itemResult.etag = getItemMethod.getResponseItem().get(Field.get("etag").getResponseName());

            return itemResult;
        }
    }

    protected class Event extends ExchangeSession.Event {
        // item id
        ItemId itemId;
        String type;
        boolean isException;

        protected Event(String folderPath, EWSMethod.Item response) {
            this.folderPath = folderPath;
            itemId = new ItemId(response);

            type = response.type;

            permanentUrl = response.get(Field.get("permanenturl").getResponseName());
            etag = response.get(Field.get("etag").getResponseName());
            displayName = response.get(Field.get("displayname").getResponseName());
            subject = response.get(Field.get("subject").getResponseName());
            // ignore urlcompname and use item id
            itemName = StringUtil.base64ToUrl(itemId.id) + ".EML";
            String instancetype = response.get(Field.get("instancetype").getResponseName());
            isException = "3".equals(instancetype);
        }

        protected Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) throws IOException {
            super(folderPath, itemName, contentClass, itemBody, etag, noneMatch);
        }

        /**
         * Handle excluded dates (deleted occurrences).
         *
         * @param currentItemId current item id to iterate over occurrences
         * @param vCalendar     vCalendar object
         * @throws DavMailException on error
         */
        protected void handleExcludedDates(ItemId currentItemId, VCalendar vCalendar) throws DavMailException {
            List<VProperty> excludedDates = vCalendar.getFirstVeventProperties("EXDATE");
            if (excludedDates != null) {
                for (VProperty property : excludedDates) {
                    List<String> values = property.getValues();
                    for (String value : values) {
                        String convertedValue;
                        try {
                            convertedValue = vCalendar.convertCalendarDateToExchangeZulu(value, property.getParamValue("TZID"));
                        } catch (IOException e) {
                            throw new DavMailException("EXCEPTION_INVALID_DATE", value);
                        }
                        LOGGER.debug("Looking for occurrence " + convertedValue);

                        int instanceIndex = 0;

                        // let's try to find occurrence
                        while (true) {
                            instanceIndex++;
                            try {
                                GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY,
                                        new OccurrenceItemId(currentItemId.id, instanceIndex)
                                        , false);
                                getItemMethod.addAdditionalProperty(Field.get("originalstart"));
                                executeMethod(getItemMethod);
                                if (getItemMethod.getResponseItem() != null) {
                                    String itemOriginalStart = getItemMethod.getResponseItem().get(Field.get("originalstart").getResponseName());
                                        LOGGER.debug("Occurrence " + instanceIndex + " itemOriginalStart " + itemOriginalStart + " looking for " + convertedValue);
                                    if (convertedValue.equals(itemOriginalStart)) {
                                        // found item, delete it
                                        DeleteItemMethod deleteItemMethod = new DeleteItemMethod(new ItemId(getItemMethod.getResponseItem()),
                                                DeleteType.HardDelete, SendMeetingCancellations.SendToAllAndSaveCopy);
                                        executeMethod(deleteItemMethod);
                                        break;
                                    } else if (convertedValue.compareTo(itemOriginalStart) < 0) {
                                        // current item is after searched item => probably already deleted
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                LOGGER.warn("Error looking for occurrence " + convertedValue + ": " + e.getMessage());
                                // after end of recurrence
                                break;
                            }
                        }
                    }
                }
            }


        }

        /**
         * Handle modified occurrences.
         *
         * @param currentItemId current item id to iterate over occurrences
         * @param vCalendar     vCalendar object
         * @throws DavMailException on error
         */
        protected void handleModifiedOccurrences(ItemId currentItemId, VCalendar vCalendar, SendMeetingInvitationsOrCancellations sendMeetingInvitationsOrCancellations) throws DavMailException {
            for (VObject modifiedOccurrence : vCalendar.getModifiedOccurrences()) {
                VProperty originalDateProperty = modifiedOccurrence.getProperty("RECURRENCE-ID");
                String convertedValue;
                try {
                    convertedValue = vCalendar.convertCalendarDateToExchangeZulu(originalDateProperty.getValue(), originalDateProperty.getParamValue("TZID"));
                } catch (IOException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", originalDateProperty.getValue());
                }
                LOGGER.debug("Looking for occurrence " + convertedValue);
                int instanceIndex = 0;

                // let's try to find occurrence
                while (true) {
                    instanceIndex++;
                    try {
                        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY,
                                new OccurrenceItemId(currentItemId.id, instanceIndex)
                                , false);
                        getItemMethod.addAdditionalProperty(Field.get("originalstart"));
                        executeMethod(getItemMethod);
                        if (getItemMethod.getResponseItem() != null) {
                            String itemOriginalStart = getItemMethod.getResponseItem().get(Field.get("originalstart").getResponseName());
                            if (convertedValue.equals(itemOriginalStart)) {
                                // found item, update it
                                UpdateItemMethod updateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                                        ConflictResolution.AutoResolve,
                                        sendMeetingInvitationsOrCancellations,
                                        new ItemId(getItemMethod.getResponseItem()), buildFieldUpdates(vCalendar, modifiedOccurrence, false));
                                // force context Timezone on Exchange 2010 and 2013
                                if (serverVersion != null && serverVersion.startsWith("Exchange201")) {
                                    updateItemMethod.setTimezoneContext(EwsExchangeSession.this.getVTimezone().getPropertyValue("TZID"));
                                }
                                executeMethod(updateItemMethod);

                                break;
                            } else if (convertedValue.compareTo(itemOriginalStart) < 0) {
                                // current item is after searched item => probably already deleted
                                break;
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.warn("Error looking for occurrence " + convertedValue + ": " + e.getMessage());
                        // after end of recurrence
                        break;
                    }
                }
            }
        }

        protected List<FieldUpdate> buildFieldUpdates(VCalendar vCalendar, VObject vEvent, boolean isMozDismiss) throws DavMailException {
            boolean isShared = !email.equalsIgnoreCase(vCalendar.getCalendarEmail());

            List<FieldUpdate> updates = new ArrayList<>();

            if (isMozDismiss || "1".equals(vEvent.getPropertyValue("X-MOZ-FAKED-MASTER"))) {
                String xMozLastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
                if (xMozLastack != null) {
                    updates.add(Field.createFieldUpdate("xmozlastack", xMozLastack));
                }
                String xMozSnoozeTime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
                if (xMozSnoozeTime != null) {
                    updates.add(Field.createFieldUpdate("xmozsnoozetime", xMozSnoozeTime));
                }
                return updates;
            }

            // if we are not organizer, update only reminder info
            if (!vCalendar.isMeeting() || vCalendar.isMeetingOrganizer()) {
                // TODO: update all event fields and handle other occurrences
                updates.add(Field.createFieldUpdate("dtstart", convertCalendarDateToExchange(vEvent.getPropertyValue("DTSTART"))));
                updates.add(Field.createFieldUpdate("dtend", convertCalendarDateToExchange(vEvent.getPropertyValue("DTEND"))));
                if ("Exchange2007_SP1".equals(serverVersion)) {
                    updates.add(Field.createFieldUpdate("meetingtimezone", vEvent.getProperty("DTSTART").getParamValue("TZID")));
                } else {
                    String starttimezone = vEvent.getProperty("DTSTART").getParamValue("TZID");
                    String endtimezone = starttimezone;
                    if (vEvent.getProperty("DTEND") != null) {
                        endtimezone = vEvent.getProperty("DTEND").getParamValue("TZID");
                    }
                    // for some reason we are unable to update timezone on a shared calendar
                    if (!isShared || Settings.getBooleanProperty("davmail.caldavImpersonate", false)) {
                        updates.add(Field.createFieldUpdate("starttimezone", starttimezone));
                        updates.add(Field.createFieldUpdate("endtimezone", endtimezone));
                    }
                }

                String status = statusToBusyStatusMap.get(vEvent.getPropertyValue("STATUS"));
                if (status != null) {
                    updates.add(Field.createFieldUpdate("busystatus", status));
                }

                updates.add(Field.createFieldUpdate("isalldayevent", Boolean.toString(vCalendar.isCdoAllDay())));

                String eventClass = vEvent.getPropertyValue("CLASS");
                if ("PRIVATE".equals(eventClass)) {
                    eventClass = "Private";
                } else if ("CONFIDENTIAL".equals(eventClass)) {
                    eventClass = "Confidential";
                } else {
                    // PUBLIC
                    eventClass = "Normal";
                }
                updates.add(Field.createFieldUpdate("itemsensitivity", eventClass));

                updates.add(Field.createFieldUpdate("description", vEvent.getPropertyValue("DESCRIPTION")));
                updates.add(Field.createFieldUpdate("subject", vEvent.getPropertyValue("SUMMARY")));
                updates.add(Field.createFieldUpdate("location", vEvent.getPropertyValue("LOCATION")));
                // Collect categories on multiple lines
                List<VProperty> categories = vEvent.getProperties("CATEGORIES");
                if (categories != null) {
                    HashSet<String> categoryValues = new HashSet<>();
                    for (VProperty category : categories) {
                        categoryValues.add(category.getValue());
                    }
                    updates.add(Field.createFieldUpdate("keywords", StringUtil.join(categoryValues, ",")));
                }

                VProperty rrule = vEvent.getProperty("RRULE");
                if (rrule != null) {
                    RecurrenceFieldUpdate recurrenceFieldUpdate = new RecurrenceFieldUpdate();
                    List<String> rruleValues = rrule.getValues();
                    for (String rruleValue : rruleValues) {
                        int index = rruleValue.indexOf("=");
                        if (index >= 0) {
                            String key = rruleValue.substring(0, index);
                            String value = rruleValue.substring(index + 1);
                            switch (key) {
                                case "FREQ":
                                    recurrenceFieldUpdate.setRecurrencePattern(value);
                                    break;
                                case "UNTIL":
                                    recurrenceFieldUpdate.setEndDate(parseDateFromExchange(convertCalendarDateToExchange(value) + "Z"));
                                    break;
                                case "COUNT":
                                    recurrenceFieldUpdate.setCount(value);
                                    break;
                                case "BYDAY":
                                    recurrenceFieldUpdate.setByDay(value.split(","));
                                    break;
                                case "INTERVAL":
                                    recurrenceFieldUpdate.setRecurrenceInterval(value);
                                    break;
                            }
                        }
                    }
                    recurrenceFieldUpdate.setStartDate(parseDateFromExchange(convertCalendarDateToExchange(vEvent.getPropertyValue("DTSTART")) + "Z"));
                    updates.add(recurrenceFieldUpdate);
                }


                MultiValuedFieldUpdate requiredAttendees = new MultiValuedFieldUpdate(Field.get("requiredattendees"));
                MultiValuedFieldUpdate optionalAttendees = new MultiValuedFieldUpdate(Field.get("optionalattendees"));

                // for some reason we are unable to update timezone on a shared calendar
                if (!isShared || Settings.getBooleanProperty("davmail.caldavImpersonate", false)) {
                    updates.add(requiredAttendees);
                    updates.add(optionalAttendees);
                }

                List<VProperty> attendees = vEvent.getProperties("ATTENDEE");
                if (attendees != null) {
                    for (VProperty property : attendees) {
                        String attendeeEmail = vCalendar.getEmailValue(property);
                        if (attendeeEmail != null && attendeeEmail.indexOf('@') >= 0) {
                            if (!vCalendar.getCalendarEmail().equals(attendeeEmail)) {
                                String attendeeRole = property.getParamValue("ROLE");
                                if ("REQ-PARTICIPANT".equals(attendeeRole)) {
                                    requiredAttendees.addValue(attendeeEmail);
                                } else {
                                    optionalAttendees.addValue(attendeeEmail);
                                }
                            }
                        }
                    }
                }

                // store mozilla invitations option
                String xMozSendInvitations = vCalendar.getFirstVeventPropertyValue("X-MOZ-SEND-INVITATIONS");
                if (xMozSendInvitations != null) {
                    updates.add(Field.createFieldUpdate("xmozsendinvitations", xMozSendInvitations));
                }
            }

            // TODO: check with recurrence
            updates.add(Field.createFieldUpdate("reminderset", String.valueOf(vCalendar.hasVAlarm())));
            if (vCalendar.hasVAlarm()) {
                updates.add(Field.createFieldUpdate("reminderminutesbeforestart", vCalendar.getReminderMinutesBeforeStart()));
            }

            // handle mozilla alarm
            String xMozLastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
            if (xMozLastack != null) {
                updates.add(Field.createFieldUpdate("xmozlastack", xMozLastack));
            }
            String xMozSnoozeTime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
            if (xMozSnoozeTime != null) {
                updates.add(Field.createFieldUpdate("xmozsnoozetime", xMozSnoozeTime));
            }

            return updates;
        }

        @Override
        public ItemResult createOrUpdate() throws IOException {
            if (vCalendar.isTodo() && isMainCalendar(folderPath)) {
                // task item, move to tasks folder
                folderPath = TASKS;
            }

            ItemResult itemResult = new ItemResult();
            EWSMethod createOrUpdateItemMethod = null;

            // first try to load existing event
            String currentEtag = null;
            ItemId currentItemId = null;
            String ownerResponseReply = null;
            boolean isMeetingResponse = false;
            boolean isMozSendInvitations = true;
            boolean isMozDismiss = false;

            HashSet<String> itemRequestProperties = CALENDAR_ITEM_REQUEST_PROPERTIES;
            if (vCalendar.isTodo()) {
                itemRequestProperties = EVENT_REQUEST_PROPERTIES;
            }

            EWSMethod.Item currentItem = getEwsItem(folderPath, itemName, itemRequestProperties);
            if (currentItem != null) {
                currentItemId = new ItemId(currentItem);
                currentEtag = currentItem.get(Field.get("etag").getResponseName());
                String currentAttendeeStatus = responseTypeToPartstatMap.get(currentItem.get(Field.get("myresponsetype").getResponseName()));
                String newAttendeeStatus = vCalendar.getAttendeeStatus();

                isMeetingResponse = vCalendar.isMeeting() && !vCalendar.isMeetingOrganizer()
                        && newAttendeeStatus != null
                        && !newAttendeeStatus.equals(currentAttendeeStatus)
                        // avoid nullpointerexception on unknown status
                        && partstatToResponseMap.get(newAttendeeStatus) != null;

                // Check mozilla last ack and snooze
                String newmozlastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
                String currentmozlastack = currentItem.get(Field.get("xmozlastack").getResponseName());
                boolean ismozack = newmozlastack != null && !newmozlastack.equals(currentmozlastack);

                String newmozsnoozetime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
                String currentmozsnoozetime = currentItem.get(Field.get("xmozsnoozetime").getResponseName());
                boolean ismozsnooze = newmozsnoozetime != null && !newmozsnoozetime.equals(currentmozsnoozetime);

                isMozSendInvitations = (newmozlastack == null && newmozsnoozetime == null) // not thunderbird
                        || !(ismozack || ismozsnooze);
                isMozDismiss = ismozack || ismozsnooze;

                LOGGER.debug("Existing item found with etag: " + currentEtag + " client etag: " + etag + " id: " + currentItemId.id);
            }
            if (isMeetingResponse) {
                LOGGER.debug("Ignore etag check, meeting response");
            } else if ("*".equals(noneMatch) && !Settings.getBooleanProperty("davmail.ignoreNoneMatchStar", true)) {
                // create requested
                //noinspection VariableNotUsedInsideIf
                if (currentItemId != null) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            } else if (etag != null) {
                // update requested
                if (currentItemId == null || !etag.equals(currentEtag)) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            }

            // by default no notifications
            SendMeetingInvitationsOrCancellations sendMeetingInvitationsOrCancellations = SendMeetingInvitationsOrCancellations.SendToNone;

            if (vCalendar.isTodo()) {
                // create or update task method
                EWSMethod.Item newItem = new EWSMethod.Item();
                newItem.type = "Task";
                List<FieldUpdate> updates = new ArrayList<>();
                updates.add(Field.createFieldUpdate("importance", convertPriorityToExchange(vCalendar.getFirstVeventPropertyValue("PRIORITY"))));
                updates.add(Field.createFieldUpdate("calendaruid", vCalendar.getFirstVeventPropertyValue("UID")));
                // force urlcompname
                updates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                updates.add(Field.createFieldUpdate("subject", vCalendar.getFirstVeventPropertyValue("SUMMARY")));
                updates.add(Field.createFieldUpdate("description", vCalendar.getFirstVeventPropertyValue("DESCRIPTION")));

                // handle multiple keywords/categories
                List<VProperty> categories = vCalendar.getFirstVeventProperties("CATEGORIES");
                if (categories != null) {
                    HashSet<String> categoryValues = new HashSet<>();
                    for (VProperty category : categories) {
                        categoryValues.add(category.getValue());
                    }
                    updates.add(Field.createFieldUpdate("keywords", StringUtil.join(categoryValues, ",")));
                }

                updates.add(Field.createFieldUpdate("startdate", convertTaskDateToZulu(vCalendar.getFirstVeventPropertyValue("DTSTART"))));
                updates.add(Field.createFieldUpdate("duedate", convertTaskDateToZulu(vCalendar.getFirstVeventPropertyValue("DUE"))));
                updates.add(Field.createFieldUpdate("datecompleted", convertTaskDateToZulu(vCalendar.getFirstVeventPropertyValue("COMPLETED"))));

                updates.add(Field.createFieldUpdate("commonstart", convertTaskDateToZulu(vCalendar.getFirstVeventPropertyValue("DTSTART"))));
                updates.add(Field.createFieldUpdate("commonend", convertTaskDateToZulu(vCalendar.getFirstVeventPropertyValue("DUE"))));

                String percentComplete = vCalendar.getFirstVeventPropertyValue("PERCENT-COMPLETE");
                if (percentComplete == null) {
                    percentComplete = "0";
                }
                updates.add(Field.createFieldUpdate("percentcomplete", percentComplete));
                String vTodoStatus = vCalendar.getFirstVeventPropertyValue("STATUS");
                if (vTodoStatus == null) {
                    updates.add(Field.createFieldUpdate("taskstatus", "NotStarted"));
                } else {
                    updates.add(Field.createFieldUpdate("taskstatus", vTodoToTaskStatusMap.get(vTodoStatus)));
                }

                //updates.add(Field.createFieldUpdate("iscomplete", "COMPLETED".equals(vTodoStatus)?"True":"False"));

                if (currentItemId != null) {
                    // update
                    createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                            ConflictResolution.AutoResolve,
                            SendMeetingInvitationsOrCancellations.SendToNone,
                            currentItemId, updates);
                } else {
                    newItem.setFieldUpdates(updates);
                    // create
                    createOrUpdateItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId(folderPath), newItem);
                }

            } else {

                // update existing item
                if (currentItemId != null) {
                    if (isMeetingResponse && Settings.getBooleanProperty("davmail.caldavAutoSchedule", true)) {
                        // meeting response with server managed notifications
                        SendMeetingInvitations sendMeetingInvitations = SendMeetingInvitations.SendToAllAndSaveCopy;
                        MessageDisposition messageDisposition = MessageDisposition.SendAndSaveCopy;
                        String body = null;
                        // This is a meeting response, let user edit notification message
                        if (Settings.getBooleanProperty("davmail.caldavEditNotifications")) {
                            String vEventSubject = vCalendar.getFirstVeventPropertyValue("SUMMARY");
                            if (vEventSubject == null) {
                                vEventSubject = BundleMessage.format("MEETING_REQUEST");
                            }

                            String status = vCalendar.getAttendeeStatus();
                            String notificationSubject = (status != null) ? (BundleMessage.format(status) + vEventSubject) : subject;

                            NotificationDialog notificationDialog = new NotificationDialog(notificationSubject, "");
                            if (!notificationDialog.getSendNotification()) {
                                LOGGER.debug("Notification canceled by user");
                                sendMeetingInvitations = SendMeetingInvitations.SendToNone;
                                messageDisposition = MessageDisposition.SaveOnly;
                            }
                            // get description from dialog
                            body = notificationDialog.getBody();
                        }
                        EWSMethod.Item item = new EWSMethod.Item();

                        item.type = partstatToResponseMap.get(vCalendar.getAttendeeStatus());
                        item.referenceItemId = new ItemId("ReferenceItemId", currentItemId.id, currentItemId.changeKey);
                        if (body != null && !body.isEmpty()) {
                            item.put("Body", body);
                        }
                        createOrUpdateItemMethod = new CreateItemMethod(messageDisposition,
                                sendMeetingInvitations,
                                getFolderId(SENT),
                                item
                        );
                    } else if (Settings.getBooleanProperty("davmail.caldavAutoSchedule", true)) {
                        // other changes with server side managed notifications
                        MessageDisposition messageDisposition = MessageDisposition.SaveOnly;

                        if (vCalendar.isMeeting() && vCalendar.isMeetingOrganizer() && isMozSendInvitations) {
                            messageDisposition = MessageDisposition.SendAndSaveCopy;
                            sendMeetingInvitationsOrCancellations = SendMeetingInvitationsOrCancellations.SendToAllAndSaveCopy;
                        }
                        createOrUpdateItemMethod = new UpdateItemMethod(messageDisposition,
                                ConflictResolution.AutoResolve,
                                sendMeetingInvitationsOrCancellations,
                                currentItemId, buildFieldUpdates(vCalendar, vCalendar.getFirstVevent(), isMozDismiss));

                        boolean isShared = !email.equalsIgnoreCase(vCalendar.getCalendarEmail());
                        if (isShared && Settings.getBooleanProperty("davmail.caldavImpersonate", false)) {
                            createOrUpdateItemMethod.mailbox = vCalendar.getCalendarEmail();
                        }
                        // force context Timezone on Exchange 2010 and 2013
                        if (serverVersion != null && serverVersion.startsWith("Exchange201")) {
                            createOrUpdateItemMethod.setTimezoneContext(EwsExchangeSession.this.getVTimezone().getPropertyValue("TZID"));
                        }
                    } else {
                        // old hard/delete approach on update, used with client side notifications
                        DeleteItemMethod deleteItemMethod = new DeleteItemMethod(currentItemId, DeleteType.HardDelete, SendMeetingCancellations.SendToNone);
                        executeMethod(deleteItemMethod);
                    }
                }

                if (createOrUpdateItemMethod == null) {
                    // create
                    EWSMethod.Item newItem = new EWSMethod.Item();
                    newItem.type = "CalendarItem";
                    newItem.mimeContent = IOUtil.encodeBase64(vCalendar.toString());
                    ArrayList<FieldUpdate> updates = new ArrayList<>();
                    if (!vCalendar.hasVAlarm()) {
                        updates.add(Field.createFieldUpdate("reminderset", "false"));
                    }
                    //updates.add(Field.createFieldUpdate("outlookmessageclass", "IPM.Appointment"));
                    // force urlcompname
                    updates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                    if (vCalendar.isMeeting()) {
                        if (vCalendar.isMeetingOrganizer()) {
                            updates.add(Field.createFieldUpdate("apptstateflags", "1"));
                        } else {
                            updates.add(Field.createFieldUpdate("apptstateflags", "3"));
                        }
                    } else {
                        updates.add(Field.createFieldUpdate("apptstateflags", "0"));
                    }
                    // store mozilla invitations option
                    String xMozSendInvitations = vCalendar.getFirstVeventPropertyValue("X-MOZ-SEND-INVITATIONS");
                    if (xMozSendInvitations != null) {
                        updates.add(Field.createFieldUpdate("xmozsendinvitations", xMozSendInvitations));
                    }
                    // handle mozilla alarm
                    String xMozLastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
                    if (xMozLastack != null) {
                        updates.add(Field.createFieldUpdate("xmozlastack", xMozLastack));
                    }
                    String xMozSnoozeTime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
                    if (xMozSnoozeTime != null) {
                        updates.add(Field.createFieldUpdate("xmozsnoozetime", xMozSnoozeTime));
                    }

                    if (vCalendar.isMeeting() && "Exchange2007_SP1".equals(serverVersion)) {
                        Set<String> requiredAttendees = new HashSet<>();
                        Set<String> optionalAttendees = new HashSet<>();
                        List<VProperty> attendeeProperties = vCalendar.getFirstVeventProperties("ATTENDEE");
                        if (attendeeProperties != null) {
                            for (VProperty property : attendeeProperties) {
                                String attendeeEmail = vCalendar.getEmailValue(property);
                                if (attendeeEmail != null && attendeeEmail.indexOf('@') >= 0) {
                                    if (email.equals(attendeeEmail)) {
                                        String ownerPartStat = property.getParamValue("PARTSTAT");
                                        if ("ACCEPTED".equals(ownerPartStat)) {
                                            ownerResponseReply = "AcceptItem";
                                            // do not send DeclineItem to avoid deleting target event
                                        } else if ("DECLINED".equals(ownerPartStat) ||
                                                "TENTATIVE".equals(ownerPartStat)) {
                                            ownerResponseReply = "TentativelyAcceptItem";
                                        }
                                    }
                                    InternetAddress internetAddress = new InternetAddress(attendeeEmail, property.getParamValue("CN"));
                                    String attendeeRole = property.getParamValue("ROLE");
                                    if ("REQ-PARTICIPANT".equals(attendeeRole)) {
                                        requiredAttendees.add(internetAddress.toString());
                                    } else {
                                        optionalAttendees.add(internetAddress.toString());
                                    }
                                }
                            }
                        }
                        List<VProperty> organizerProperties = vCalendar.getFirstVeventProperties("ORGANIZER");
                        if (organizerProperties != null) {
                            VProperty property = organizerProperties.get(0);
                            String organizerEmail = vCalendar.getEmailValue(property);
                            if (organizerEmail != null && organizerEmail.indexOf('@') >= 0) {
                                updates.add(Field.createFieldUpdate("from", organizerEmail));
                            }
                        }

                        if (!requiredAttendees.isEmpty()) {
                            updates.add(Field.createFieldUpdate("to", StringUtil.join(requiredAttendees, ", ")));
                        }
                        if (!optionalAttendees.isEmpty()) {
                            updates.add(Field.createFieldUpdate("cc", StringUtil.join(optionalAttendees, ", ")));
                        }
                    }

                    // patch allday date values, only on 2007
                    if ("Exchange2007_SP1".equals(serverVersion) && vCalendar.isCdoAllDay()) {
                        updates.add(Field.createFieldUpdate("dtstart", convertCalendarDateToExchange(vCalendar.getFirstVeventPropertyValue("DTSTART"))));
                        updates.add(Field.createFieldUpdate("dtend", convertCalendarDateToExchange(vCalendar.getFirstVeventPropertyValue("DTEND"))));
                    }

                    String status = vCalendar.getFirstVeventPropertyValue("STATUS");
                    if ("TENTATIVE".equals(status)) {
                        // this is a tentative event
                        updates.add(Field.createFieldUpdate("busystatus", "Tentative"));
                    } else {
                        // otherwise, we use the same value as before, as received from the server
                        // however, the case matters, so we still have to transform it "BUSY" -> "Busy"
                        updates.add(Field.createFieldUpdate("busystatus", "BUSY".equals(vCalendar.getFirstVeventPropertyValue("X-MICROSOFT-CDO-BUSYSTATUS")) ? "Busy" : "Free"));
                    }

                    if ("Exchange2007_SP1".equals(serverVersion) && vCalendar.isCdoAllDay()) {
                        updates.add(Field.createFieldUpdate("meetingtimezone", vCalendar.getVTimezone().getPropertyValue("TZID")));
                    }

                    newItem.setFieldUpdates(updates);
                    MessageDisposition messageDisposition = MessageDisposition.SaveOnly;
                    SendMeetingInvitations sendMeetingInvitations = SendMeetingInvitations.SendToNone;
                    if (vCalendar.isMeeting() && vCalendar.isMeetingOrganizer() && isMozSendInvitations
                            && Settings.getBooleanProperty("davmail.caldavAutoSchedule", true)) {
                        // meeting request creation with server managed notifications
                        messageDisposition = MessageDisposition.SendAndSaveCopy;
                        sendMeetingInvitations = SendMeetingInvitations.SendToAllAndSaveCopy;
                    }
                    createOrUpdateItemMethod = new CreateItemMethod(messageDisposition, sendMeetingInvitations, getFolderId(folderPath), newItem);
                    // force context Timezone on Exchange 2010 and 2013
                    if (serverVersion != null && serverVersion.startsWith("Exchange201")) {
                        createOrUpdateItemMethod.setTimezoneContext(EwsExchangeSession.this.getVTimezone().getPropertyValue("TZID"));
                    }
                }
            }

            executeMethod(createOrUpdateItemMethod);

            itemResult.status = createOrUpdateItemMethod.getStatusCode();
            if (itemResult.status == HttpURLConnection.HTTP_OK) {
                //noinspection VariableNotUsedInsideIf
                if (currentItemId == null) {
                    itemResult.status = HttpStatus.SC_CREATED;
                    LOGGER.debug("Created event " + getHref());
                } else {
                    LOGGER.warn("Overwritten event " + getHref());
                }
            }

            // force responsetype on Exchange 2007
            if (ownerResponseReply != null) {
                EWSMethod.Item responseTypeItem = new EWSMethod.Item();
                responseTypeItem.referenceItemId = new ItemId("ReferenceItemId", createOrUpdateItemMethod.getResponseItem());
                responseTypeItem.type = ownerResponseReply;
                createOrUpdateItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, null, responseTypeItem);
                executeMethod(createOrUpdateItemMethod);

                // force urlcompname again
                ArrayList<FieldUpdate> updates = new ArrayList<>();
                updates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AlwaysOverwrite,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        new ItemId(createOrUpdateItemMethod.getResponseItem()),
                        updates);
                executeMethod(createOrUpdateItemMethod);
            }

            // handle deleted occurrences
            if (!vCalendar.isTodo() && currentItemId != null && !isMeetingResponse && !isMozDismiss) {
                handleExcludedDates(currentItemId, vCalendar);
                handleModifiedOccurrences(currentItemId, vCalendar, sendMeetingInvitationsOrCancellations);
            }


            // update etag
            if (createOrUpdateItemMethod.getResponseItem() != null) {
                ItemId newItemId = new ItemId(createOrUpdateItemMethod.getResponseItem());
                GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, newItemId, false);
                getItemMethod.addAdditionalProperty(Field.get("etag"));
                executeMethod(getItemMethod);
                itemResult.etag = getItemMethod.getResponseItem().get(Field.get("etag").getResponseName());
                itemResult.itemName = StringUtil.base64ToUrl(newItemId.id) + ".EML";
            }

            return itemResult;

        }

        @Override
        public byte[] getEventContent() throws IOException {
            byte[] content;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Get event: " + itemName);
            }
            try {
                GetItemMethod getItemMethod;
                if ("Task".equals(type)) {
                    getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
                    getItemMethod.addAdditionalProperty(Field.get("importance"));
                    getItemMethod.addAdditionalProperty(Field.get("subject"));
                    getItemMethod.addAdditionalProperty(Field.get("created"));
                    getItemMethod.addAdditionalProperty(Field.get("lastmodified"));
                    getItemMethod.addAdditionalProperty(Field.get("calendaruid"));
                    getItemMethod.addAdditionalProperty(Field.get("description"));
                    if (isExchange2013OrLater()) {
                        getItemMethod.addAdditionalProperty(Field.get("textbody"));
                    }
                    getItemMethod.addAdditionalProperty(Field.get("percentcomplete"));
                    getItemMethod.addAdditionalProperty(Field.get("taskstatus"));
                    getItemMethod.addAdditionalProperty(Field.get("startdate"));
                    getItemMethod.addAdditionalProperty(Field.get("duedate"));
                    getItemMethod.addAdditionalProperty(Field.get("datecompleted"));
                    getItemMethod.addAdditionalProperty(Field.get("keywords"));

                } else if (!"Message".equals(type)
                        && !"MeetingCancellation".equals(type)
                        && !"MeetingResponse".equals(type)) {
                    getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, true);
                    getItemMethod.addAdditionalProperty(Field.get("lastmodified"));
                    getItemMethod.addAdditionalProperty(Field.get("reminderset"));
                    getItemMethod.addAdditionalProperty(Field.get("calendaruid"));
                    getItemMethod.addAdditionalProperty(Field.get("myresponsetype"));
                    getItemMethod.addAdditionalProperty(Field.get("requiredattendees"));
                    getItemMethod.addAdditionalProperty(Field.get("optionalattendees"));
                    getItemMethod.addAdditionalProperty(Field.get("modifiedoccurrences"));
                    getItemMethod.addAdditionalProperty(Field.get("xmozlastack"));
                    getItemMethod.addAdditionalProperty(Field.get("xmozsnoozetime"));
                    getItemMethod.addAdditionalProperty(Field.get("xmozsendinvitations"));
                } else {
                    getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, true);
                }

                executeMethod(getItemMethod);
                if ("Task".equals(type)) {
                    VCalendar localVCalendar = new VCalendar();
                    VObject vTodo = new VObject();
                    vTodo.type = "VTODO";
                    localVCalendar.setTimezone(getVTimezone());
                    vTodo.setPropertyValue("LAST-MODIFIED", convertDateFromExchange(getItemMethod.getResponseItem().get(Field.get("lastmodified").getResponseName())));
                    vTodo.setPropertyValue("CREATED", convertDateFromExchange(getItemMethod.getResponseItem().get(Field.get("created").getResponseName())));
                    String calendarUid = getItemMethod.getResponseItem().get(Field.get("calendaruid").getResponseName());
                    if (calendarUid == null) {
                        // use item id as uid for Exchange created tasks
                        calendarUid = itemId.id;
                    }
                    vTodo.setPropertyValue("UID", calendarUid);
                    vTodo.setPropertyValue("SUMMARY", getItemMethod.getResponseItem().get(Field.get("subject").getResponseName()));
                    String description = getItemMethod.getResponseItem().get(Field.get("description").getResponseName());
                    if (description == null) {
                        // Exchange 2013: try to get description from body
                        description = getItemMethod.getResponseItem().get(Field.get("textbody").getResponseName());
                    }
                    vTodo.setPropertyValue("DESCRIPTION", description);
                    vTodo.setPropertyValue("PRIORITY", convertPriorityFromExchange(getItemMethod.getResponseItem().get(Field.get("importance").getResponseName())));
                    vTodo.setPropertyValue("PERCENT-COMPLETE", getItemMethod.getResponseItem().get(Field.get("percentcomplete").getResponseName()));
                    vTodo.setPropertyValue("STATUS", taskTovTodoStatusMap.get(getItemMethod.getResponseItem().get(Field.get("taskstatus").getResponseName())));

                    vTodo.setPropertyValue("DUE;VALUE=DATE", convertDateFromExchangeToTaskDate(getItemMethod.getResponseItem().get(Field.get("duedate").getResponseName())));
                    vTodo.setPropertyValue("DTSTART;VALUE=DATE", convertDateFromExchangeToTaskDate(getItemMethod.getResponseItem().get(Field.get("startdate").getResponseName())));
                    vTodo.setPropertyValue("COMPLETED;VALUE=DATE", convertDateFromExchangeToTaskDate(getItemMethod.getResponseItem().get(Field.get("datecompleted").getResponseName())));

                    vTodo.setPropertyValue("CATEGORIES", getItemMethod.getResponseItem().get(Field.get("keywords").getResponseName()));

                    localVCalendar.addVObject(vTodo);
                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    content = getItemMethod.getMimeContent();
                    if (content == null) {
                        throw new IOException("empty event body");
                    }
                    if (!"CalendarItem".equals(type)) {
                        content = getICS(new SharedByteArrayInputStream(content));
                    }
                    VCalendar localVCalendar = new VCalendar(content, email, getVTimezone());

                    String calendaruid = getItemMethod.getResponseItem().get(Field.get("calendaruid").getResponseName());

                    if ("Exchange2007_SP1".equals(serverVersion)) {
                        // remove additional reminder
                        if (!"true".equals(getItemMethod.getResponseItem().get(Field.get("reminderset").getResponseName()))) {
                            localVCalendar.removeVAlarm();
                        }
                        if (calendaruid != null) {
                            localVCalendar.setFirstVeventPropertyValue("UID", calendaruid);
                        }
                    }
                    fixAttendees(getItemMethod, localVCalendar.getFirstVevent());
                    // fix UID and RECURRENCE-ID, broken at least on Exchange 2007
                    List<EWSMethod.Occurrence> occurrences = getItemMethod.getResponseItem().getOccurrences();
                    if (occurrences != null) {
                        Iterator<VObject> modifiedOccurrencesIterator = localVCalendar.getModifiedOccurrences().iterator();
                        for (EWSMethod.Occurrence occurrence : occurrences) {
                            if (modifiedOccurrencesIterator.hasNext()) {
                                VObject modifiedOccurrence = modifiedOccurrencesIterator.next();
                                // fix modified occurrences attendees
                                GetItemMethod getOccurrenceMethod = new GetItemMethod(BaseShape.ID_ONLY, occurrence.itemId, false);
                                getOccurrenceMethod.addAdditionalProperty(Field.get("requiredattendees"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("optionalattendees"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("modifiedoccurrences"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("lastmodified"));
                                executeMethod(getOccurrenceMethod);
                                fixAttendees(getOccurrenceMethod, modifiedOccurrence);
                                // LAST-MODIFIED is missing in event content
                                modifiedOccurrence.setPropertyValue("LAST-MODIFIED", convertDateFromExchange(getOccurrenceMethod.getResponseItem().get(Field.get("lastmodified").getResponseName())));

                                // fix uid, should be the same as main VEVENT
                                if (calendaruid != null) {
                                    modifiedOccurrence.setPropertyValue("UID", calendaruid);
                                }

                                VProperty recurrenceId = modifiedOccurrence.getProperty("RECURRENCE-ID");
                                if (recurrenceId != null) {
                                    recurrenceId.removeParam("TZID");
                                    recurrenceId.getValues().set(0, convertDateFromExchange(occurrence.originalStart));
                                }
                            }
                        }
                    }
                    // LAST-MODIFIED is missing in event content
                    localVCalendar.setFirstVeventPropertyValue("LAST-MODIFIED", convertDateFromExchange(getItemMethod.getResponseItem().get(Field.get("lastmodified").getResponseName())));

                    // restore mozilla invitations option
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-SEND-INVITATIONS",
                            getItemMethod.getResponseItem().get(Field.get("xmozsendinvitations").getResponseName()));
                    // restore mozilla alarm status
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-LASTACK",
                            getItemMethod.getResponseItem().get(Field.get("xmozlastack").getResponseName()));
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME",
                            getItemMethod.getResponseItem().get(Field.get("xmozsnoozetime").getResponseName()));
                    // overwrite method
                    // localVCalendar.setPropertyValue("METHOD", "REQUEST");
                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);
                }
            } catch (IOException | MessagingException e) {
                throw buildHttpNotFoundException(e);
            }
            return content;
        }

        protected void fixAttendees(GetItemMethod getItemMethod, VObject vEvent) throws EWSException {
            if (getItemMethod.getResponseItem() != null) {
                List<EWSMethod.Attendee> attendees = getItemMethod.getResponseItem().getAttendees();
                if (attendees != null) {
                    for (EWSMethod.Attendee attendee : attendees) {
                        VProperty attendeeProperty = new VProperty("ATTENDEE", "mailto:" + attendee.email);
                        attendeeProperty.addParam("CN", attendee.name);
                        String myResponseType = getItemMethod.getResponseItem().get(Field.get("myresponsetype").getResponseName());
                        if (email.equalsIgnoreCase(attendee.email) && myResponseType != null) {
                            attendeeProperty.addParam("PARTSTAT", EWSMethod.responseTypeToPartstat(myResponseType));
                        } else {
                            attendeeProperty.addParam("PARTSTAT", attendee.partstat);
                        }
                        //attendeeProperty.addParam("RSVP", "TRUE");
                        attendeeProperty.addParam("ROLE", attendee.role);
                        vEvent.addProperty(attendeeProperty);
                    }
                }
            }
        }
    }

    private boolean isExchange2013OrLater() {
        return "Exchange2013".compareTo(serverVersion) <= 0;
    }

    /**
     * Get all contacts and distribution lists in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of contacts
     * @throws IOException on error
     */
    @Override
    public List<ExchangeSession.Contact> getAllContacts(String folderPath, boolean includeDistList) throws IOException {
        Condition condition;
        if (includeDistList) {
            condition = or(isEqualTo("outlookmessageclass", "IPM.Contact"), isEqualTo("outlookmessageclass", "IPM.DistList"));
        } else {
            condition = isEqualTo("outlookmessageclass", "IPM.Contact");
        }
        return searchContacts(folderPath, ExchangeSession.CONTACT_ATTRIBUTES, condition, 0);
    }

    @Override
    public List<ExchangeSession.Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException {
        List<ExchangeSession.Contact> contacts = new ArrayList<>();
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition,
                FolderQueryTraversal.SHALLOW, maxCount);

        for (EWSMethod.Item response : responses) {
            contacts.add(new Contact(response));
        }
        return contacts;
    }

    @Override
    protected Condition getCalendarItemCondition(Condition dateCondition) {
        // tasks in calendar not supported over EWS => do not look for instancetype null
        return or(
                // Exchange 2010
                or(isTrue("isrecurring"),
                        and(isFalse("isrecurring"), dateCondition)),
                // Exchange 2007
                or(isEqualTo("instancetype", 1),
                        and(isEqualTo("instancetype", 0), dateCondition))
        );
    }

    @Override
    public List<ExchangeSession.Event> getEventMessages(String folderPath) throws IOException {
        return searchEvents(folderPath, ITEM_PROPERTIES,
                and(startsWith("outlookmessageclass", "IPM.Schedule.Meeting."),
                        or(isNull("processed"), isFalse("processed"))));
    }

    @Override
    public List<ExchangeSession.Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        List<ExchangeSession.Event> events = new ArrayList<>();
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes,
                condition,
                FolderQueryTraversal.SHALLOW, 0);
        for (EWSMethod.Item response : responses) {
            Event event = new Event(folderPath, response);
            if ("Message".equals(event.type)) {
                // TODO: just exclude
                // need to check body
                try {
                    event.getEventContent();
                    events.add(event);
                } catch (HttpNotFoundException e) {
                    LOGGER.warn("Ignore invalid event " + event.getHref());
                }
                // exclude exceptions
            } else if (event.isException) {
                LOGGER.debug("Exclude recurrence exception " + event.getHref());
            } else {
                events.add(event);
            }

        }

        return events;
    }

    /**
     * Common item properties
     */
    protected static final Set<String> ITEM_PROPERTIES = new HashSet<>();

    static {
        ITEM_PROPERTIES.add("etag");
        ITEM_PROPERTIES.add("displayname");
        // calendar CdoInstanceType
        ITEM_PROPERTIES.add("instancetype");
        ITEM_PROPERTIES.add("urlcompname");
        ITEM_PROPERTIES.add("subject");
    }

    protected static final HashSet<String> EVENT_REQUEST_PROPERTIES = new HashSet<>();

    static {
        EVENT_REQUEST_PROPERTIES.add("permanenturl");
        EVENT_REQUEST_PROPERTIES.add("etag");
        EVENT_REQUEST_PROPERTIES.add("displayname");
        EVENT_REQUEST_PROPERTIES.add("subject");
        EVENT_REQUEST_PROPERTIES.add("urlcompname");
        EVENT_REQUEST_PROPERTIES.add("displayto");
        EVENT_REQUEST_PROPERTIES.add("displaycc");

        EVENT_REQUEST_PROPERTIES.add("xmozlastack");
        EVENT_REQUEST_PROPERTIES.add("xmozsnoozetime");
    }

    protected static final HashSet<String> CALENDAR_ITEM_REQUEST_PROPERTIES = new HashSet<>();

    static {
        CALENDAR_ITEM_REQUEST_PROPERTIES.addAll(EVENT_REQUEST_PROPERTIES);
        CALENDAR_ITEM_REQUEST_PROPERTIES.add("ismeeting");
        CALENDAR_ITEM_REQUEST_PROPERTIES.add("myresponsetype");
    }

    @Override
    protected Set<String> getItemProperties() {
        return ITEM_PROPERTIES;
    }

    protected EWSMethod.Item getEwsItem(String folderPath, String itemName, Set<String> itemProperties) throws IOException {
        EWSMethod.Item item = null;
        String urlcompname = convertItemNameToEML(itemName);
        // workaround for missing urlcompname in Exchange 2010
        if (isItemId(urlcompname)) {
            ItemId itemId = new ItemId(StringUtil.urlToBase64(urlcompname.substring(0, urlcompname.indexOf('.'))));
            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
            for (String attribute : itemProperties) {
                getItemMethod.addAdditionalProperty(Field.get(attribute));
            }
            executeMethod(getItemMethod);
            item = getItemMethod.getResponseItem();
        }
        // find item by urlcompname
        if (item == null) {
            List<EWSMethod.Item> responses = searchItems(folderPath, itemProperties, isEqualTo("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW, 0);
            if (!responses.isEmpty()) {
                item = responses.get(0);
            }
        }
        return item;
    }


    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = getEwsItem(folderPath, itemName, EVENT_REQUEST_PROPERTIES);
        if (item == null && isMainCalendar(folderPath)) {
            // look for item in task folder, replace extension first
            if (itemName.endsWith(".ics")) {
                item = getEwsItem(TASKS, itemName.substring(0, itemName.length() - 3) + "EML", EVENT_REQUEST_PROPERTIES);
            } else {
                item = getEwsItem(TASKS, itemName, EVENT_REQUEST_PROPERTIES);
            }
        }

        if (item == null) {
            throw new HttpNotFoundException(itemName + " not found in " + folderPath);
        }

        String itemType = item.type;
        if ("Contact".equals(itemType) || "DistributionList".equals(itemType)) {
            // retrieve Contact properties
            ItemId itemId = new ItemId(item);
            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
            Set<String> attributes = CONTACT_ATTRIBUTES;
            if ("DistributionList".equals(itemType)) {
                attributes = DISTRIBUTION_LIST_ATTRIBUTES;
            }
            for (String attribute : attributes) {
                getItemMethod.addAdditionalProperty(Field.get(attribute));
            }
            executeMethod(getItemMethod);
            item = getItemMethod.getResponseItem();
            if (item == null) {
                throw new HttpNotFoundException(itemName + " not found in " + folderPath);
            }
            return new Contact(item);
        } else if ("CalendarItem".equals(itemType)
                || "MeetingMessage".equals(itemType)
                || "MeetingRequest".equals(itemType)
                || "MeetingResponse".equals(itemType)
                || "MeetingCancellation".equals(itemType)
                || "Task".equals(itemType)
                // VTODOs appear as Messages
                || "Message".equals(itemType)) {
            Event event = new Event(folderPath, item);
            // force item name to client provided name (for tasks)
            event.setItemName(itemName);
            return event;
        } else {
            throw new HttpNotFoundException(itemName + " not found in " + folderPath);
        }

    }

    @Override
    public ContactPhoto getContactPhoto(ExchangeSession.Contact contact) throws IOException {
        ContactPhoto contactPhoto;

        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, ((EwsExchangeSession.Contact) contact).itemId, false);
        getItemMethod.addAdditionalProperty(Field.get("attachments"));
        executeMethod(getItemMethod);
        EWSMethod.Item item = getItemMethod.getResponseItem();
        if (item == null) {
            throw new IOException("Missing contact picture");
        }
        FileAttachment attachment = item.getAttachmentByName("ContactPicture.jpg");
        if (attachment == null) {
            throw new IOException("Missing contact picture");
        }
        // get attachment content
        GetAttachmentMethod getAttachmentMethod = new GetAttachmentMethod(attachment.attachmentId);
        executeMethod(getAttachmentMethod);

        contactPhoto = new ContactPhoto();
        contactPhoto.content = getAttachmentMethod.getResponseItem().get("Content");
        if (attachment.contentType == null) {
            contactPhoto.contentType = "image/jpeg";
        } else {
            contactPhoto.contentType = attachment.contentType;
        }

        return contactPhoto;
    }

    @Override
    public ContactPhoto getADPhoto(String email) {
        ContactPhoto contactPhoto = null;

        if (email != null) {
            try {
                GetUserPhotoMethod userPhotoMethod = new GetUserPhotoMethod(email, GetUserPhotoMethod.SizeRequested.HR240x240);
                executeMethod(userPhotoMethod);
                if (userPhotoMethod.getPictureData() != null) {
                    contactPhoto = new ContactPhoto();
                    contactPhoto.content = userPhotoMethod.getPictureData();
                    contactPhoto.contentType = userPhotoMethod.getContentType();
                    if (contactPhoto.contentType == null) {
                        contactPhoto.contentType = "image/jpeg";
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("Error loading contact image from AD " + e + " " + e.getMessage());
            }
        }

        return contactPhoto;
    }

    @Override
    public void deleteItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = getEwsItem(folderPath, itemName, EVENT_REQUEST_PROPERTIES);
        if (item != null && "CalendarItem".equals(item.type)) {
            // reload with calendar property
            if (serverVersion.compareTo("Exchange2013") >= 0) {
                CALENDAR_ITEM_REQUEST_PROPERTIES.add("isorganizer");
            }
            item = getEwsItem(folderPath, itemName, CALENDAR_ITEM_REQUEST_PROPERTIES);
        }
        if (item == null && isMainCalendar(folderPath)) {
            // look for item in task folder
            item = getEwsItem(TASKS, itemName, EVENT_REQUEST_PROPERTIES);
        }
        if (item != null) {
            boolean isMeeting = "true".equals(item.get(Field.get("ismeeting").getResponseName()));
            boolean isOrganizer;
            if (item.get(Field.get("isorganizer").getResponseName()) != null) {
                // Exchange 2013 or later
                isOrganizer = "true".equals(item.get(Field.get("isorganizer").getResponseName()));
            } else {
                isOrganizer = "Organizer".equals(item.get(Field.get("myresponsetype").getResponseName()));
            }
            boolean hasAttendees = item.get(Field.get("displayto").getResponseName()) != null
                    || item.get(Field.get("displaycc").getResponseName()) != null;

            if (isMeeting && isOrganizer && hasAttendees
                    && !isSharedFolder(folderPath)
                    && Settings.getBooleanProperty("davmail.caldavAutoSchedule", true)) {
                // cancel meeting
                SendMeetingInvitations sendMeetingInvitations = SendMeetingInvitations.SendToAllAndSaveCopy;
                MessageDisposition messageDisposition = MessageDisposition.SendAndSaveCopy;
                String body = null;
                // This is a meeting cancel, let user edit notification message
                if (Settings.getBooleanProperty("davmail.caldavEditNotifications")) {
                    String vEventSubject = item.get(Field.get("subject").getResponseName());
                    if (vEventSubject == null) {
                        vEventSubject = "";
                    }
                    String notificationSubject = (BundleMessage.format("CANCELLED") + vEventSubject);

                    NotificationDialog notificationDialog = new NotificationDialog(notificationSubject, "");
                    if (!notificationDialog.getSendNotification()) {
                        LOGGER.debug("Notification canceled by user");
                        sendMeetingInvitations = SendMeetingInvitations.SendToNone;
                        messageDisposition = MessageDisposition.SaveOnly;
                    }
                    // get description from dialog
                    body = notificationDialog.getBody();
                }
                EWSMethod.Item cancelItem = new EWSMethod.Item();
                cancelItem.type = "CancelCalendarItem";
                cancelItem.referenceItemId = new ItemId("ReferenceItemId", item);
                if (body != null && !body.isEmpty()) {
                    item.put("Body", body);
                }
                CreateItemMethod cancelItemMethod = new CreateItemMethod(messageDisposition,
                        sendMeetingInvitations,
                        getFolderId(SENT),
                        cancelItem
                );
                executeMethod(cancelItemMethod);

            } else {
                DeleteType deleteType = DeleteType.MoveToDeletedItems;
                if (isSharedFolder(folderPath)) {
                    // can't move event to trash in a shared mailbox
                    deleteType = DeleteType.HardDelete;
                }
                // delete item
                DeleteItemMethod deleteItemMethod = new DeleteItemMethod(new ItemId(item), deleteType, SendMeetingCancellations.SendToAllAndSaveCopy);
                executeMethod(deleteItemMethod);
            }
        }
    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = getEwsItem(folderPath, itemName, EVENT_REQUEST_PROPERTIES);
        if (item != null) {
            HashMap<String, String> localProperties = new HashMap<>();
            localProperties.put("processed", "1");
            localProperties.put("read", "1");
            UpdateItemMethod updateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                    ConflictResolution.AlwaysOverwrite,
                    SendMeetingInvitationsOrCancellations.SendToNone,
                    new ItemId(item), buildProperties(localProperties));
            executeMethod(updateItemMethod);
        }
    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        String itemName = UUID.randomUUID() + ".EML";
        byte[] mimeContent = new Event(DRAFTS, itemName, "urn:content-classes:calendarmessage", icsBody, null, null).createMimeContent();
        if (mimeContent == null) {
            // no recipients, cancel
            return HttpStatus.SC_NO_CONTENT;
        } else {
            sendMessage(null, mimeContent);
            return HttpStatus.SC_OK;
        }
    }

    @Override
    protected Contact buildContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) {
        return new Contact(folderPath, itemName, properties, StringUtil.removeQuotes(etag), noneMatch);
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        return new Event(folderPath, itemName, contentClass, icsBody, StringUtil.removeQuotes(etag), noneMatch).createOrUpdate();
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        return folderPath.startsWith("/") && !folderPath.toLowerCase().startsWith(currentMailboxPath);
    }

    @Override
    public boolean isMainCalendar(String folderPath) throws IOException {
        FolderId currentFolderId = getFolderId(folderPath);
        FolderId calendarFolderId = getFolderId("calendar");
        return calendarFolderId.name.equals(currentFolderId.name) && calendarFolderId.value.equals(currentFolderId.value);
    }

    @Override
    protected String getCalendarEmail(String folderPath) throws IOException {
        // retrieve mailbox from folder
        String calendarEmail = getFolderId(folderPath).mailbox;
        if (calendarEmail == null) {
            // default is current mailbox
            calendarEmail = email;
        }
        return calendarEmail;
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) {
        String result = null;
        GetUserAvailabilityMethod getUserAvailabilityMethod = new GetUserAvailabilityMethod(attendee, start, end, interval);
        try {
            executeMethod(getUserAvailabilityMethod);
            result = getUserAvailabilityMethod.getMergedFreeBusy();
        } catch (IOException e) {
            // ignore
        }
        return result;
    }

    @Override
    protected void loadVtimezone() {

        try {
            String timezoneId;
            timezoneId = Settings.getProperty("davmail.timezoneId");
            if (timezoneId == null && !"Exchange2007_SP1".equals(serverVersion)) {
                // On Exchange 2010, get user timezone from server
                GetUserConfigurationMethod getUserConfigurationMethod = new GetUserConfigurationMethod();
                executeMethod(getUserConfigurationMethod);
                EWSMethod.Item item = getUserConfigurationMethod.getResponseItem();
                if (item != null) {
                    timezoneId = item.get("timezone");
                }
            } else if (!directEws) {
                timezoneId = getTimezoneidFromOptions();
            }
            // failover: use timezone id from settings file
            // last failover: use GMT
            if (timezoneId == null) {
                LOGGER.warn("Unable to get user timezone, using GMT Standard Time. Set davmail.timezoneId setting to override this.");
                timezoneId = "GMT Standard Time";
            }

            // delete existing temp folder first to avoid errors
            deleteFolder("davmailtemp");
            createCalendarFolder("davmailtemp", null);
            EWSMethod.Item item = new EWSMethod.Item();
            item.type = "CalendarItem";
            if (!"Exchange2007_SP1".equals(serverVersion)) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
                dateFormatter.setTimeZone(GMT_TIMEZONE);
                Calendar cal = Calendar.getInstance();
                item.put("Start", dateFormatter.format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
                item.put("End", dateFormatter.format(cal.getTime()));
                item.put("StartTimeZone", timezoneId);
            } else {
                item.put("MeetingTimeZone", timezoneId);
            }
            CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId("davmailtemp"), item);
            executeMethod(createItemMethod);
            item = createItemMethod.getResponseItem();
            if (item == null) {
                throw new IOException("Empty timezone item");
            }
            VCalendar vCalendar = new VCalendar(getContent(new ItemId(item)), email, null);
            this.vTimezone = vCalendar.getVTimezone();
            // delete temporary folder
            deleteFolder("davmailtemp");
        } catch (IOException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        }
    }

    protected String getTimezoneidFromOptions() {
        String result = null;
        // get time zone setting from html body
        String optionsPath = "/owa/?ae=Options&t=Regional";
        GetRequest optionsMethod = new GetRequest(optionsPath);
        try (
                CloseableHttpResponse response = httpClient.execute(optionsMethod);
                BufferedReader optionsPageReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))
        ) {
            String line;
            // find timezone
            //noinspection StatementWithEmptyBody
            while ((line = optionsPageReader.readLine()) != null
                    && (!line.contains("tblTmZn"))
                    && (!line.contains("selTmZn"))) {
            }
            if (line != null) {
                if (line.contains("tblTmZn")) {
                    int start = line.indexOf("oV=\"") + 4;
                    int end = line.indexOf('\"', start);
                    result = line.substring(start, end);
                } else {
                    int end = line.lastIndexOf("\" selected>");
                    int start = line.lastIndexOf('\"', end - 1);
                    result = line.substring(start + 1, end);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing options page at " + optionsPath);
        }

        return result;
    }


    protected FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    protected static final String USERS_ROOT = "/users/";

    protected FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String lowerCaseFolderPath = folderPath.toLowerCase();
        if (lowerCaseFolderPath.equals(currentMailboxPath)) {
            return getSubFolderIdIfExists(null, "");
        } else if (lowerCaseFolderPath.startsWith(currentMailboxPath + '/')) {
            return getSubFolderIdIfExists(null, folderPath.substring(currentMailboxPath.length() + 1));
        } else if (folderPath.startsWith("/users/")) {
            int slashIndex = folderPath.indexOf('/', USERS_ROOT.length());
            String mailbox;
            String subFolderPath;
            if (slashIndex >= 0) {
                mailbox = folderPath.substring(USERS_ROOT.length(), slashIndex);
                subFolderPath = folderPath.substring(slashIndex + 1);
            } else {
                mailbox = folderPath.substring(USERS_ROOT.length());
                subFolderPath = "";
            }
            return getSubFolderIdIfExists(mailbox, subFolderPath);
        } else {
            return getSubFolderIdIfExists(null, folderPath);
        }
    }

    protected FolderId getSubFolderIdIfExists(String mailbox, String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;

        if ("/public".equals(folderPath)) {
            return DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.publicfoldersroot);
        } else if ("/archive".equals(folderPath)) {
            return DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.archivemsgfolderroot);
        } else if (isSubFolderOf(folderPath, PUBLIC_ROOT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.publicfoldersroot);
            folderNames = folderPath.substring(PUBLIC_ROOT.length()).split("/");
        } else if (isSubFolderOf(folderPath, ARCHIVE_ROOT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.archivemsgfolderroot);
            folderNames = folderPath.substring(ARCHIVE_ROOT.length()).split("/");
        } else if (isSubFolderOf(folderPath, INBOX) ||
                isSubFolderOf(folderPath, LOWER_CASE_INBOX) ||
                isSubFolderOf(folderPath, MIXED_CASE_INBOX)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.inbox);
            folderNames = folderPath.substring(INBOX.length()).split("/");
        } else if (isSubFolderOf(folderPath, CALENDAR)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.calendar);
            folderNames = folderPath.substring(CALENDAR.length()).split("/");
        } else if (isSubFolderOf(folderPath, TASKS)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.tasks);
            folderNames = folderPath.substring(TASKS.length()).split("/");
        } else if (isSubFolderOf(folderPath, CONTACTS)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.contacts);
            folderNames = folderPath.substring(CONTACTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, SENT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.sentitems);
            folderNames = folderPath.substring(SENT.length()).split("/");
        } else if (isSubFolderOf(folderPath, DRAFTS)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.drafts);
            folderNames = folderPath.substring(DRAFTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, TRASH)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.deleteditems);
            folderNames = folderPath.substring(TRASH.length()).split("/");
        } else if (isSubFolderOf(folderPath, JUNK)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.junkemail);
            folderNames = folderPath.substring(JUNK.length()).split("/");
        } else if (isSubFolderOf(folderPath, UNSENT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.outbox);
            folderNames = folderPath.substring(UNSENT.length()).split("/");
        } else {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.msgfolderroot);
            folderNames = folderPath.split("/");
        }
        for (String folderName : folderNames) {
            if (!folderName.isEmpty()) {
                currentFolderId = getSubFolderByName(currentFolderId, folderName);
                if (currentFolderId == null) {
                    break;
                }
            }
        }
        return currentFolderId;
    }

    /**
     * Check if folderPath is base folder or a sub folder path.
     *
     * @param folderPath folder path
     * @param baseFolder base folder
     * @return true if folderPath is under baseFolder
     */
    private boolean isSubFolderOf(String folderPath, String baseFolder) {
        if (PUBLIC_ROOT.equals(baseFolder) || ARCHIVE_ROOT.equals(baseFolder)) {
            return folderPath.startsWith(baseFolder);
        } else {
            return folderPath.startsWith(baseFolder)
                    && (folderPath.length() == baseFolder.length() || folderPath.charAt(baseFolder.length()) == '/');
        }
    }

    protected FolderId getSubFolderByName(FolderId parentFolderId, String folderName) throws IOException {
        FolderId folderId = null;
        FindFolderMethod findFolderMethod = new FindFolderMethod(
                FolderQueryTraversal.SHALLOW,
                BaseShape.ID_ONLY,
                parentFolderId,
                FOLDER_PROPERTIES,
                new TwoOperandExpression(TwoOperandExpression.Operator.IsEqualTo,
                        Field.get("folderDisplayName"), decodeFolderName(folderName)),
                0, 1
        );
        executeMethod(findFolderMethod);
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        if (item != null) {
            folderId = new FolderId(item);
        }
        return folderId;
    }

    public static String decodeFolderName(String folderName) {
        if (folderName.contains("_xF8FF_")) {
            return folderName.replaceAll("_xF8FF_", "/");
        }
        if (folderName.contains("_x003E_")) {
            return folderName.replaceAll("_x003E_", ">");
        }
        return folderName;
    }

    public static String encodeFolderName(String folderName) {
        if (folderName.contains("/")) {
            folderName = folderName.replaceAll("/", "_xF8FF_");
        }
        if (folderName.contains(">")) {
            folderName = folderName.replaceAll(">", "_x003E_");
        }
        return folderName;
    }

    long throttlingTimestamp = 0;

    protected int executeMethod(EWSMethod ewsMethod) throws IOException {
        long throttlingDelay = throttlingTimestamp - System.currentTimeMillis();
        try {
            if (throttlingDelay > 0) {
                LOGGER.warn("Throttling active on server, waiting " + (throttlingDelay / 1000) + " seconds");
                try {
                    Thread.sleep(throttlingDelay);
                } catch (InterruptedException e1) {
                    LOGGER.error("Throttling delay interrupted " + e1.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
            internalExecuteMethod(ewsMethod);
        } catch (EWSThrottlingException e) {
            // default throttling delay is one minute
            throttlingDelay = 60000;
            if (ewsMethod.backOffMilliseconds > 0) {
                // server provided a throttling delay, add 10 seconds
                throttlingDelay = ewsMethod.backOffMilliseconds + 10000;
            }
            throttlingTimestamp = System.currentTimeMillis() + throttlingDelay;

            LOGGER.warn("Throttling active on server, waiting " + (throttlingDelay / 1000) + " seconds");
            try {
                Thread.sleep(throttlingDelay);
            } catch (InterruptedException e1) {
                LOGGER.error("Throttling delay interrupted " + e1.getMessage());
                Thread.currentThread().interrupt();
            }
            // retry once
            internalExecuteMethod(ewsMethod);
        }
        return ewsMethod.getStatusCode();
    }

    protected void internalExecuteMethod(EWSMethod ewsMethod) throws IOException {
        ewsMethod.setServerVersion(serverVersion);
        if (token != null) {
            ewsMethod.setHeader("Authorization", "Bearer " + token.getAccessToken());
        }
        try (CloseableHttpResponse response = httpClient.execute(ewsMethod)) {
            ewsMethod.handleResponse(response);
        }
        if (serverVersion == null) {
            serverVersion = ewsMethod.getServerVersion();
        }
        ewsMethod.checkSuccess();
    }

    protected static final HashMap<String, String> GALFIND_ATTRIBUTE_MAP = new HashMap<>();

    static {
        GALFIND_ATTRIBUTE_MAP.put("imapUid", "Name");
        GALFIND_ATTRIBUTE_MAP.put("cn", "DisplayName");
        GALFIND_ATTRIBUTE_MAP.put("givenName", "GivenName");
        GALFIND_ATTRIBUTE_MAP.put("sn", "Surname");
        GALFIND_ATTRIBUTE_MAP.put("smtpemail1", "EmailAddress");

        GALFIND_ATTRIBUTE_MAP.put("roomnumber", "OfficeLocation");
        GALFIND_ATTRIBUTE_MAP.put("street", "BusinessStreet");
        GALFIND_ATTRIBUTE_MAP.put("l", "BusinessCity");
        GALFIND_ATTRIBUTE_MAP.put("o", "CompanyName");
        GALFIND_ATTRIBUTE_MAP.put("postalcode", "BusinessPostalCode");
        GALFIND_ATTRIBUTE_MAP.put("st", "BusinessState");
        GALFIND_ATTRIBUTE_MAP.put("co", "BusinessCountryOrRegion");

        GALFIND_ATTRIBUTE_MAP.put("manager", "Manager");
        GALFIND_ATTRIBUTE_MAP.put("middlename", "Initials");
        GALFIND_ATTRIBUTE_MAP.put("title", "JobTitle");
        GALFIND_ATTRIBUTE_MAP.put("department", "Department");

        GALFIND_ATTRIBUTE_MAP.put("otherTelephone", "OtherTelephone");
        GALFIND_ATTRIBUTE_MAP.put("telephoneNumber", "BusinessPhone");
        GALFIND_ATTRIBUTE_MAP.put("mobile", "MobilePhone");
        GALFIND_ATTRIBUTE_MAP.put("facsimiletelephonenumber", "BusinessFax");
        GALFIND_ATTRIBUTE_MAP.put("secretarycn", "AssistantName");

        GALFIND_ATTRIBUTE_MAP.put("homePhone", "HomePhone");
        GALFIND_ATTRIBUTE_MAP.put("pager", "Pager");
        GALFIND_ATTRIBUTE_MAP.put("msexchangecertificate", "MSExchangeCertificate");
        GALFIND_ATTRIBUTE_MAP.put("usersmimecertificate", "UserSMIMECertificate");
    }

    protected static final HashSet<String> IGNORE_ATTRIBUTE_SET = new HashSet<>();

    static {
        IGNORE_ATTRIBUTE_SET.add("ContactSource");
        IGNORE_ATTRIBUTE_SET.add("Culture");
        IGNORE_ATTRIBUTE_SET.add("AssistantPhone");
    }

    protected Contact buildGalfindContact(EWSMethod.Item response) {
        Contact contact = new Contact();
        contact.setName(response.get("Name"));
        contact.put("imapUid", response.get("Name"));
        contact.put("uid", response.get("Name"));
        if (LOGGER.isDebugEnabled()) {
            for (Map.Entry<String, String> entry : response.entrySet()) {
                String key = entry.getKey();
                if (!IGNORE_ATTRIBUTE_SET.contains(key) && !GALFIND_ATTRIBUTE_MAP.containsValue(key)) {
                    LOGGER.debug("Unsupported ResolveNames " + contact.getName() + " response attribute: " + key + " value: " + entry.getValue());
                }
            }
        }
        for (Map.Entry<String, String> entry : GALFIND_ATTRIBUTE_MAP.entrySet()) {
            String attributeValue = response.get(entry.getValue());
            if (attributeValue != null && !attributeValue.isEmpty()) {
                contact.put(entry.getKey(), attributeValue);
            }
        }
        return contact;
    }

    @Override
    public Map<String, ExchangeSession.Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        Map<String, ExchangeSession.Contact> contacts = new HashMap<>();
        if (condition instanceof MultiCondition) {
            List<Condition> conditions = ((ExchangeSession.MultiCondition) condition).getConditions();
            Operator operator = ((ExchangeSession.MultiCondition) condition).getOperator();
            if (operator == Operator.Or) {
                for (Condition innerCondition : conditions) {
                    contacts.putAll(galFind(innerCondition, returningAttributes, sizeLimit));
                }
            } else if (operator == Operator.And && !conditions.isEmpty()) {
                Map<String, ExchangeSession.Contact> innerContacts = galFind(conditions.get(0), returningAttributes, sizeLimit);
                for (ExchangeSession.Contact contact : innerContacts.values()) {
                    if (condition.isMatch(contact)) {
                        contacts.put(contact.getName().toLowerCase(), contact);
                    }
                }
            }
        } else if (condition instanceof AttributeCondition) {
            String mappedAttributeName = GALFIND_ATTRIBUTE_MAP.get(((ExchangeSession.AttributeCondition) condition).getAttributeName());
            if (mappedAttributeName != null) {
                String value = ((ExchangeSession.AttributeCondition) condition).getValue().toLowerCase();
                Operator operator = ((AttributeCondition) condition).getOperator();
                String searchValue = value;
                if (mappedAttributeName.startsWith("EmailAddress")) {
                    searchValue = "smtp:" + searchValue;
                }
                if (operator == Operator.IsEqualTo) {
                    searchValue = '=' + searchValue;
                }
                ResolveNamesMethod resolveNamesMethod = new ResolveNamesMethod(searchValue);
                executeMethod(resolveNamesMethod);
                List<EWSMethod.Item> responses = resolveNamesMethod.getResponseItems();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ResolveNames(" + searchValue + ") returned " + responses.size() + " results");
                }
                for (EWSMethod.Item response : responses) {
                    Contact contact = buildGalfindContact(response);
                    if (condition.isMatch(contact)) {
                        contacts.put(contact.getName().toLowerCase(), contact);
                    }
                }
            }
        }
        return contacts;
    }

    protected Date parseDateFromExchange(String exchangeDateValue) throws DavMailException {
        Date dateValue = null;
        if (exchangeDateValue != null) {
            try {
                dateValue = getExchangeZuluDateFormat().parse(exchangeDateValue);
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", exchangeDateValue);
            }
        }
        return dateValue;
    }

    protected String convertDateFromExchange(String exchangeDateValue) throws DavMailException {
        // yyyy-MM-dd'T'HH:mm:ss'Z' to yyyyMMdd'T'HHmmss'Z'
        if (exchangeDateValue == null) {
            return null;
        } else {
            if (exchangeDateValue.length() != 20) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", exchangeDateValue);
            }
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < exchangeDateValue.length(); i++) {
                if (i == 4 || i == 7 || i == 13 || i == 16) {
                    i++;
                }
                buffer.append(exchangeDateValue.charAt(i));
            }
            return buffer.toString();
        }
    }

    protected String convertCalendarDateToExchange(String vcalendarDateValue) throws DavMailException {
        String zuluDateValue = null;
        if (vcalendarDateValue != null) {
            try {
                SimpleDateFormat dateParser;
                if (vcalendarDateValue.length() == 8) {
                    dateParser = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
                } else {
                    dateParser = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH);
                }
                dateParser.setTimeZone(GMT_TIMEZONE);
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
                dateFormatter.setTimeZone(GMT_TIMEZONE);
                zuluDateValue = dateFormatter.format(dateParser.parse(vcalendarDateValue));
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", vcalendarDateValue);
            }
        }
        return zuluDateValue;
    }

    public static String convertDateFromExchangeToTaskDate(String exchangeDateValue) throws DavMailException {
        String zuluDateValue = null;
        if (exchangeDateValue != null) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
                dateFormat.setTimeZone(GMT_TIMEZONE);
                zuluDateValue = dateFormat.format(getExchangeZuluDateFormat().parse(exchangeDateValue));
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", exchangeDateValue);
            }
        }
        return zuluDateValue;
    }

    protected String convertTaskDateToZulu(String value) {
        String result = null;
        if (value != null && !value.isEmpty()) {
            try {
                SimpleDateFormat parser = ExchangeSession.getExchangeDateFormat(value);
                Calendar calendarValue = Calendar.getInstance(GMT_TIMEZONE);
                calendarValue.setTime(parser.parse(value));
                // zulu time: add 12 hours
                if (value.length() == 16) {
                    calendarValue.add(Calendar.HOUR, 12);
                }
                calendarValue.set(Calendar.HOUR, 0);
                calendarValue.set(Calendar.MINUTE, 0);
                calendarValue.set(Calendar.SECOND, 0);
                result = ExchangeSession.getExchangeZuluDateFormat().format(calendarValue.getTime());
            } catch (ParseException e) {
                LOGGER.warn("Invalid date: " + value);
            }
        }

        return result;
    }

    /**
     * Format date to exchange search format.
     *
     * @param date date object
     * @return formatted search date
     */
    @Override
    public String formatSearchDate(Date date) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormatter.setTimeZone(GMT_TIMEZONE);
        return dateFormatter.format(date);
    }

    /**
     * Check if itemName is long and base64 encoded.
     * User generated item names are usually short
     *
     * @param itemName item name
     * @return true if itemName is an EWS item id
     */
    protected static boolean isItemId(String itemName) {
        return itemName.length() >= 140
                // item name is base64url
                && itemName.matches("^([A-Za-z0-9-_]{4})*([A-Za-z0-9-_]{4}|[A-Za-z0-9-_]{3}=|[A-Za-z0-9-_]{2}==)\\.EML$")
                && itemName.indexOf(' ') < 0;
    }

    /**
     * Close session.
     * Shutdown http client connection manager
     */
    @Override
    public void close() {
        httpClient.close();
    }

}


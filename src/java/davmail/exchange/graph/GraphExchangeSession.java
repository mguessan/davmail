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

package davmail.exchange.graph;

import davmail.exception.DavMailException;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.auth.O365Token;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.exchange.ews.ExtendedFieldURI;
import davmail.exchange.ews.Field;
import davmail.exchange.ews.FieldURI;
import davmail.http.HttpClientAdapter;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Implement ExchangeSession based on Microsoft Graph
 */
public class GraphExchangeSession extends ExchangeSession {

    protected class Folder extends ExchangeSession.Folder {
        public FolderId folderId;
    }

    // special folders https://learn.microsoft.com/en-us/graph/api/resources/mailfolder
    @SuppressWarnings("SpellCheckingInspection")
    public enum WellKnownFolderName {
        archive,
        deleteditems,
        calendar, contacts, tasks,
        drafts, inbox, outbox, sentitems, junkemail,
        msgfolderroot,
        searchfolders
    }

    protected static final HashSet<FieldURI> IMAP_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("permanenturl"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("urlcompname"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("uid"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("messageSize"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("imapUid"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("junk"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("flagStatus"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("messageFlags"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("lastVerbExecuted"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("read"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("deleted"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("date"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("lastmodified"));
        // OSX IMAP requests content-class
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("contentclass"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("keywords"));
    }


    protected static class FolderId {
        protected String mailbox;
        protected String id;
        protected String folderClass;

        public FolderId() {
        }

        public FolderId(String mailbox, String id) {
            this.mailbox = mailbox;
            this.id = id;
        }

        public FolderId(String mailbox, WellKnownFolderName wellKnownFolderName) {
            this.mailbox = mailbox;
            this.id = wellKnownFolderName.name();
        }

        public FolderId(String mailbox, WellKnownFolderName wellKnownFolderName, String folderClass) {
            this.mailbox = mailbox;
            this.id = wellKnownFolderName.name();
            this.folderClass = folderClass;
        }
    }

    HttpClientAdapter httpClient;
    O365Token token;

    /**
     * Default folder properties list
     */
    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<>();

    static {
        FOLDER_PROPERTIES.add(Field.get("folderDisplayName"));
        FOLDER_PROPERTIES.add(Field.get("lastmodified"));
        FOLDER_PROPERTIES.add(Field.get("folderclass"));
        FOLDER_PROPERTIES.add(Field.get("ctag"));
        FOLDER_PROPERTIES.add(Field.get("uidNext"));
    }

    public GraphExchangeSession(HttpClientAdapter httpClient, O365Token token, String userName) {
        this.httpClient = httpClient;
        this.token = token;
        this.userName = userName;
    }

    @Override
    public void close() {
        httpClient.close();
    }

    @Override
    public String formatSearchDate(Date date) {
        return null;
    }

    @Override
    protected void buildSessionInfo(URI uri) throws IOException {

    }

    @Override
    public ExchangeSession.Message createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(baos);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
        baos.close();
        byte[] mimeContent = IOUtil.encodeBase64(baos.toByteArray());

        // https://learn.microsoft.com/en-us/graph/api/user-post-messages

        FolderId folderId = getFolderId(folderPath);

        // create message in default place first
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod("POST")
                .setContentType("text/plain")
                .setMimeContent(mimeContent)
                .setChildType("messages");

        JSONObject draftJsonResponse = executeJsonRequest(httpRequestBuilder);

        JSONObject jsonResponse;

        // TODO refactor
        // unset draft flag on returned draft message properties
        try {
            draftJsonResponse.put("singleValueExtendedProperties",
                    new JSONArray().put(new JSONObject()
                            .put("id", Field.get("messageFlags").getGraphId())
                            .put("value", "4")));


            // now use this to recreate message
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod("POST")
                    .setObjectType("mailFolders")
                    .setMailbox(folderId.mailbox)
                    .setObjectId(folderId.id)
                    .setJsonBody(draftJsonResponse)
                    .setChildType("messages");

            jsonResponse = executeJsonRequest(httpRequestBuilder);

        } catch (JSONException e) {
            throw new IOException(e);
        } finally {
            try {
                // delete draft message
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod("DELETE")
                        .setObjectType("messages")
                        .setObjectId(draftJsonResponse.getString("id")));
            } catch (JSONException e) {
                LOGGER.warn("Unable to delete draft message " + draftJsonResponse.optString("id"));
            }
        }

        return buildMessage(executeJsonRequest(new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("messages")
                .setMailbox(folderId.mailbox)
                .setObjectId(jsonResponse.optString("id"))
                .setExpandFields(IMAP_MESSAGE_ATTRIBUTES)));
    }

    protected Message getMessageById(String folderPath, String id) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        return buildMessage(executeJsonRequest(new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("messages")
                .setMailbox(folderId.mailbox)
                .setObjectId(id)
                .setExpandFields(IMAP_MESSAGE_ATTRIBUTES)));
    }

    class Message extends ExchangeSession.Message {
        protected String id;
        protected String changeKey;

        @Override
        public String getPermanentId() {
            return id;
        }

        @Override
        protected InputStream getMimeHeaders() {
            throw new UnsupportedOperationException();
        }
    }

    private Message buildMessage(JSONObject response) {
        Message message = new Message();

        try {
            // get item id
            message.id = response.getString("id");
            message.changeKey = response.getString("changeKey");

            message.read = response.getBoolean("isRead");
            message.draft = response.getBoolean("isDraft");
            message.date = convertDateFromExchange(response.getString("receivedDateTime"));

            String lastmodified = convertDateFromExchange(response.optString("lastModifiedDateTime"));
            message.recent = !message.read && lastmodified != null && lastmodified.equals(message.date);

        } catch (JSONException | DavMailException e) {
            LOGGER.warn("Error parsing message " + e.getMessage(), e);
        }

        JSONArray singleValueExtendedProperties = response.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties != null) {
            for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                try {
                    JSONObject responseValue = singleValueExtendedProperties.getJSONObject(i);
                    String responseId = responseValue.optString("id");
                    if (Field.get("imapUid").getGraphId().equals(responseId)) {
                        message.imapUid = responseValue.getLong("value");
                    //}
                    // message flag does not exactly match field, replace with isDraft
                    //else if ("Integer 0xe07".equals(responseId)) {
                        //message.draft = (responseValue.getLong("value") & 8) != 0;
                    //} else if ("SystemTime 0xe06".equals(responseId)) {
                        // use receivedDateTime instead
                        //message.date = convertDateFromExchange(responseValue.getString("value"));
                    } else if ("SystemTime 0xe08".equals(responseId)) {
                        message.size = responseValue.getInt("value");
                    } else if ("Binary 0xff9".equals(responseId)) {
                        message.uid = responseValue.getString("value");

                    } else if ("String 0x670E".equals(responseId)) {
                        // probably not available over graph
                        message.permanentUrl = responseValue.getString("value");
                    } else if ("Integer 0x1081".equals(responseId)) {
                        String lastVerbExecuted = responseValue.getString("value");
                        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
                        message.forwarded = "104".equals(lastVerbExecuted);
                    } else if ("String {00020386-0000-0000-C000-000000000046} Name content-class".equals(responseId)) {
                        // TODO: test this
                        message.contentClass = responseValue.getString("value");
                    } else if ("Integer 0x1083".equals(responseId)) {
                        message.junk = responseValue.getBoolean("value");
                    } else if ("Integer 0x1090".equals(responseId)) {
                        message.flagged = "2".equals(responseValue.getString("value"));
                    } else if ("Integer {00062008-0000-0000-c000-000000000046} Name 0x8570".equals(responseId)) {
                        message.deleted = "1".equals(responseValue.getString("value"));
                    }

                } catch (JSONException e) {
                    LOGGER.warn("Error parsing json response value");
                }
            }
        }

        JSONArray multiValueExtendedProperties = response.optJSONArray("multiValueExtendedProperties");
        if (multiValueExtendedProperties != null) {
            for (int i = 0; i < multiValueExtendedProperties.length(); i++) {
                try {
                    JSONObject responseValue = multiValueExtendedProperties.getJSONObject(i);
                    String responseId = responseValue.optString("id");
                    if (Field.get("keywords").getGraphId().equals(responseId)) {
                        JSONArray keywordsJsonArray = responseValue.getJSONArray("value");
                        HashSet<String> keywords = new HashSet<>();
                        for (int j=0;j<keywordsJsonArray.length();j++) {
                            keywords.add(keywordsJsonArray.getString(j));
                        }
                        message.keywords = StringUtil.join(keywords, ",");
                    }

                } catch (JSONException e) {
                    LOGGER.warn("Error parsing json response value");
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("Message");
            if (message.imapUid != 0) {
                buffer.append(" IMAP uid: ").append(message.imapUid);
            }
            if (message.uid != null) {
                buffer.append(" uid: ").append(message.uid);
            }
            buffer.append(" ItemId: ").append(message.id);
            buffer.append(" ChangeKey: ").append(message.changeKey);
            LOGGER.debug(buffer.toString());
        }

        return message;

    }

    /**
     * Duplicate from EWSEchangeSession.
     * TODO refactor
     * @param exchangeDateValue date returned from O365
     * @return converted date
     * @throws DavMailException on error
     */
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

    @Override
    public void updateMessage(ExchangeSession.Message message, Map<String, String> properties) throws IOException {

    }

    @Override
    public void deleteMessage(ExchangeSession.Message message) throws IOException {

    }

    @Override
    protected byte[] getContent(ExchangeSession.Message message) throws IOException {
        GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("messages")
                .setObjectId(message.getPermanentId())
                .setChildType("$value")
                .setAccessToken(token.getAccessToken());

        // TODO review mime content handling
        byte[] mimeContent;
        try (
                CloseableHttpResponse response = httpClient.execute(graphRequestBuilder.build());
                InputStream inputStream = response.getEntity().getContent()
        ) {
                if (HttpClientAdapter.isGzipEncoded(response)) {
                    mimeContent = IOUtil.readFully(new GZIPInputStream(inputStream));
                } else {
                    mimeContent = IOUtil.readFully(inputStream);
                }
        }
        return mimeContent;
    }

    @Override
    public MessageList searchMessages(String folderName, Set<String> attributes, Condition condition) throws IOException {
        MessageList messageList = new MessageList();
        FolderId folderId = getFolderIdIfExists(folderName);

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("mailFolders")
                .setMailbox(folderId.mailbox)
                .setObjectId(folderId.id)
                .setChildType("messages")
                .setExpandFields(IMAP_MESSAGE_ATTRIBUTES);
        LOGGER.debug("searchMessages " + folderId.mailbox + " " + folderName);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Message message = buildMessage(graphIterator.next());
            message.messageList = messageList;
            messageList.add(message);
        }

        return messageList;
    }

    static class AttributeCondition extends ExchangeSession.AttributeCondition {

        protected AttributeCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
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

        private String convertOperator(Operator operator) {
            if (Operator.IsEqualTo.equals(operator)) {
                return "eq";
            }
            // TODO other operators
            return operator.toString();
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            FieldURI fieldURI = getFieldURI();
            if (Operator.StartsWith.equals(operator)) {
                buffer.append("startswith(").append(getFieldURI().getGraphId()).append(",'").append(StringUtil.davSearchEncode(value)).append("')");
            } else if (fieldURI instanceof ExtendedFieldURI) {
                buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(getFieldURI().getGraphId())
                        .append("' and ep/value ").append(convertOperator(operator)).append(" '").append(StringUtil.davSearchEncode(value)).append("')");
            } else {
                buffer.append(getFieldURI().getGraphId()).append(" ").append(convertOperator(operator)).append(" '").append(StringUtil.davSearchEncode(value)).append("'");
            }
        }


        @Override
        public boolean isMatch(Contact contact) {
            return false;
        }
    }

    @Override
    public MultiCondition and(Condition... condition) {
        return null;
    }

    @Override
    public MultiCondition or(Condition... condition) {
        return null;
    }

    @Override
    public Condition not(Condition condition) {
        return null;
    }

    @Override
    public Condition isEqualTo(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, value);
    }

    @Override
    public Condition isEqualTo(String attributeName, int value) {
        return null;
    }

    @Override
    public Condition headerIsEqualTo(String headerName, String value) {
        return null;
    }

    @Override
    public Condition gte(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition gt(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition lt(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition lte(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition contains(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition startsWith(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.StartsWith, value);
    }

    @Override
    public Condition isNull(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "null");
    }

    @Override
    public Condition exists(String attributeName) {
        return null;
    }

    @Override
    public Condition isTrue(String attributeName) {
        return null;
    }

    @Override
    public Condition isFalse(String attributeName) {
        return null;
    }

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

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("mailFolders")
                .setMailbox(parentFolderId.mailbox)
                .setObjectId(parentFolderId.id)
                .setChildType("childFolders")
                .setExpandFields(FOLDER_PROPERTIES);
        LOGGER.debug("appendSubFolders " + parentFolderId.mailbox + parentFolderPath);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        // TODO handle paging
        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Folder folder = buildFolder(graphIterator.next());
            folder.folderId.mailbox = parentFolderId.mailbox;
            if (!parentFolderPath.isEmpty()) {
                if (parentFolderPath.endsWith("/")) {
                    folder.folderPath = parentFolderPath + folder.displayName;
                } else {
                    folder.folderPath = parentFolderPath + '/' + folder.displayName;
                }
                // TODO folderIdMap?
            } else {
                folder.folderPath = folder.displayName;
            }
            folders.add(folder);
            if (recursive && folder.hasChildren) {
                appendSubFolders(folders, folder.folderPath, folder.folderId, condition, true);
            }
        }

    }


    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {

    }

    @Override
    protected Folder internalGetFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);

        // base folder get https://graph.microsoft.com/v1.0/me/mailFolders/inbox
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("mailFolders")
                .setMailbox(folderId.mailbox)
                .setObjectId(folderId.id)
                .setExpandFields(FOLDER_PROPERTIES);

        JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);

        // todo check missing folder
        //throw new HttpNotFoundException("Folder " + folderPath + " not found");

        Folder folder = buildFolder(jsonResponse);
        folder.folderPath = folderPath;

        return folder;
    }

    private Folder buildFolder(JSONObject jsonResponse) throws IOException {
        try {
            Folder folder = new Folder();
            folder.folderId = new FolderId();
            folder.folderId.id = jsonResponse.getString("id");
            // TODO: reevaluate folder name encoding over graph
            folder.displayName = EwsExchangeSession.encodeFolderName(jsonResponse.optString("displayName"));
            folder.count = jsonResponse.getInt("totalItemCount");
            folder.unreadCount = jsonResponse.getInt("unreadItemCount");
            // fake recent value
            folder.recent = folder.unreadCount;
            // hassubs computed from childFolderCount
            folder.hasChildren = jsonResponse.getInt("childFolderCount") > 0;

            // retrieve property values
            JSONArray singleValueExtendedProperties = jsonResponse.optJSONArray("singleValueExtendedProperties");
            if (singleValueExtendedProperties != null) {
                for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                    JSONObject singleValueProperty = singleValueExtendedProperties.getJSONObject(i);
                    String singleValueId = singleValueProperty.getString("id");
                    String singleValue = singleValueProperty.getString("value");
                    if (Field.get("lastmodified").getGraphId().equals(singleValueId)) {
                        folder.etag = singleValue;
                    } else if (Field.get("folderclass").getGraphId().equals(singleValueId)) {
                        folder.folderClass = singleValue;
                    } else if (Field.get("uidNext").getGraphId().equals(singleValueId)) {
                        folder.uidNext = Long.parseLong(singleValue);
                    } else if (Field.get("ctag").getGraphId().equals(singleValueId)) {
                        folder.ctag = singleValue;
                    }

                }
            }

            return folder;
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Compute folderId from folderName
     * @param folderPath folder name (path)
     * @return folder id
     */
    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    protected static final String USERS_ROOT = "/users/";
    protected static final String ARCHIVE_ROOT = "/archive/";


    private FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String lowerCaseFolderPath = folderPath.toLowerCase();
        if (lowerCaseFolderPath.equals(currentMailboxPath)) {
            return getSubFolderIdIfExists(null, "");
        } else if (lowerCaseFolderPath.startsWith(currentMailboxPath + '/')) {
            return getSubFolderIdIfExists(null, folderPath.substring(currentMailboxPath.length() + 1));
        } else if (folderPath.startsWith(USERS_ROOT)) {
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

    private FolderId getSubFolderIdIfExists(String mailbox, String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;

        // TODO test various use cases
        if ("/public".equals(folderPath)) {
            throw new UnsupportedOperationException("public folders not supported on Graph");
        } else if ("/archive".equals(folderPath)) {
            return new FolderId(mailbox, WellKnownFolderName.archive);
        } else if (isSubFolderOf(folderPath, PUBLIC_ROOT)) {
            throw new UnsupportedOperationException("public folders not supported on Graph");
        } else if (isSubFolderOf(folderPath, ARCHIVE_ROOT)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.archive);
            folderNames = folderPath.substring(ARCHIVE_ROOT.length()).split("/");
        } else if (isSubFolderOf(folderPath, INBOX) ||
                isSubFolderOf(folderPath, LOWER_CASE_INBOX) ||
                isSubFolderOf(folderPath, MIXED_CASE_INBOX)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.inbox);
            folderNames = folderPath.substring(INBOX.length()).split("/");
        } else if (isSubFolderOf(folderPath, CALENDAR)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.calendar, "IPF.Appointment");
            // TODO subfolders not supported with graph
            folderNames = folderPath.substring(CALENDAR.length()).split("/");
        } else if (isSubFolderOf(folderPath, TASKS)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.tasks, "IPF.Task");
            folderNames = folderPath.substring(TASKS.length()).split("/");
        } else if (isSubFolderOf(folderPath, CONTACTS)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.contacts, "IPF.Contact");
            // TODO subfolders not supported with graph
            folderNames = folderPath.substring(CONTACTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, SENT)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.sentitems);
            folderNames = folderPath.substring(SENT.length()).split("/");
        } else if (isSubFolderOf(folderPath, DRAFTS)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.drafts);
            folderNames = folderPath.substring(DRAFTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, TRASH)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.deleteditems);
            folderNames = folderPath.substring(TRASH.length()).split("/");
        } else if (isSubFolderOf(folderPath, JUNK)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.junkemail);
            folderNames = folderPath.substring(JUNK.length()).split("/");
        } else if (isSubFolderOf(folderPath, UNSENT)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.outbox);
            folderNames = folderPath.substring(UNSENT.length()).split("/");
        } else {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.msgfolderroot);
            folderNames = folderPath.split("/");
        }
        if (currentFolderId != null) {
            String folderClass = currentFolderId.folderClass;
            for (String folderName : folderNames) {
                if (!folderName.isEmpty()) {
                    currentFolderId = getSubFolderByName(currentFolderId, folderName);
                    if (currentFolderId == null) {
                        break;
                    }
                    currentFolderId.folderClass = folderClass;
                }
            }
        }
        return currentFolderId;
    }

    /**
     * Search subfolder by name, return null when no folders found
     * @param currentFolderId parent folder id
     * @param folderName child folder name
     * @return child folder id if exists
     * @throws IOException on error
     */
    protected FolderId getSubFolderByName(FolderId currentFolderId, String folderName) throws IOException {
        // TODO rename davSearchEncode
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod("GET")
                .setObjectType("mailFolders")
                .setMailbox(currentFolderId.mailbox)
                .setObjectId(currentFolderId.id)
                .setChildType("childFolders")
                .setExpandFields(FOLDER_PROPERTIES)
                .setFilter("displayName eq '" + StringUtil.davSearchEncode(EwsExchangeSession.decodeFolderName(folderName)) + "'");

        JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);

        FolderId folderId = null;
        try {
            JSONArray values = jsonResponse.getJSONArray("value");
            if (values.length() > 0) {
                folderId = new FolderId(currentFolderId.mailbox, values.getJSONObject(0).getString("id"));
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }

        return folderId;
    }

    private boolean isSubFolderOf(String folderPath, String baseFolder) {
        if (PUBLIC_ROOT.equals(baseFolder) || ARCHIVE_ROOT.equals(baseFolder)) {
            return folderPath.startsWith(baseFolder);
        } else {
            return folderPath.startsWith(baseFolder)
                    && (folderPath.length() == baseFolder.length() || folderPath.charAt(baseFolder.length()) == '/');
        }
    }

    @Override
    public int createFolder(String folderName, String folderClass, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public int updateFolder(String folderName, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public void deleteFolder(String folderName) throws IOException {

    }

    @Override
    public void copyMessage(ExchangeSession.Message message, String targetFolder) throws IOException {

    }

    @Override
    public void moveMessage(ExchangeSession.Message message, String targetFolder) throws IOException {

    }

    @Override
    public void moveFolder(String folderName, String targetName) throws IOException {

    }

    @Override
    public void moveItem(String sourcePath, String targetPath) throws IOException {

    }

    @Override
    protected void moveToTrash(ExchangeSession.Message message) throws IOException {

    }

    @Override
    protected Set<String> getItemProperties() {
        return null;
    }

    @Override
    public List<Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException {
        return null;
    }

    @Override
    public List<Event> getEventMessages(String folderPath) throws IOException {
        return null;
    }

    @Override
    protected Condition getCalendarItemCondition(Condition dateCondition) {
        return null;
    }

    @Override
    public List<Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        return null;
    }

    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        return null;
    }

    @Override
    public ContactPhoto getContactPhoto(Contact contact) throws IOException {
        return null;
    }

    @Override
    public void deleteItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        return 0;
    }

    @Override
    protected Contact buildContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) throws IOException {
        return null;
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        return null;
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        return false;
    }

    @Override
    public boolean isMainCalendar(String folderPath) throws IOException {
        return false;
    }

    @Override
    public Map<String, Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        return null;
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException {
        return null;
    }

    @Override
    protected void loadVtimezone() {

    }

    class GraphIterator {

        private JSONObject jsonObject;
        private JSONArray values;
        private String nextLink;
        private int index;

        public GraphIterator(JSONObject jsonObject) throws JSONException {
            this.jsonObject = jsonObject;
            nextLink = jsonObject.optString("@odata.nextLink", null);
            values = jsonObject.getJSONArray("value");
        }

        public boolean hasNext() {
            return nextLink != null || index < values.length();
        }

        public JSONObject next() throws IOException {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                if (index >= values.length() && nextLink != null) {
                    fetchNextPage();
                }
                return values.getJSONObject(index++);
            } catch (JSONException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        private void fetchNextPage() throws IOException, JSONException {
            HttpGet request = new HttpGet(nextLink);
            request.setHeader("Authorization", "Bearer " + token.getAccessToken());
            try (
                    CloseableHttpResponse response = httpClient.execute(request)
            ) {
                jsonObject = new JsonResponseHandler().handleResponse(response);
                nextLink = jsonObject.optString("@odata.nextLink", null);
                values = jsonObject.getJSONArray("value");
                index = 0;
            }
        }
    }

    private GraphIterator executeSearchRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        try {
            JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);
            return new GraphIterator(jsonResponse);
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private JSONObject executeJsonRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        // TODO handle throttling
        HttpRequestBase request = httpRequestBuilder
                .setAccessToken(token.getAccessToken())
                .build();

        // DEBUG only, disable gzip encoding
        //request.setHeader("Accept-Encoding", "");
        JSONObject jsonResponse;
        try (
                CloseableHttpResponse response = httpClient.execute(request)
        ) {
            if (response.getStatusLine().getStatusCode() == 400) {
                LOGGER.warn("Request returned " + response.getStatusLine());
            }
            jsonResponse = new JsonResponseHandler().handleResponse(response);
        }
        return jsonResponse;
    }

}

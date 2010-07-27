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

import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;

/**
 * EWS Exchange adapter.
 * Compatible with Exchange 2007 and hopefully 2010.
 */
public class EwsExchangeSession extends ExchangeSession {

    protected Map<String, String> folderIdMap;

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

    /**
     * @inheritDoc
     */
    public EwsExchangeSession(String url, String userName, String password) throws IOException {
        super(url, userName, password);
    }

    @Override
    public boolean isExpired() throws NoRouteToHostException, UnknownHostException {
        // TODO: implement
        return false;
    }

    @Override
    protected void buildSessionInfo(HttpMethod method) throws DavMailException {
        // nothing to do, mailPath not used in EWS mode
        // check EWS access
        HttpMethod getMethod = new GetMethod("/ews/exchange.asmx");
        try {
            getMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, getMethod);
            if (getMethod.getStatusCode() != HttpStatus.SC_OK) {
                throw DavGatewayHttpClientFacade.buildHttpException(getMethod);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        } finally {
            getMethod.releaseConnection();
        }

        try {
            folderIdMap = new HashMap<String, String>();
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

        // also need to retrieve email and alias
        alias = getAliasFromOptions();
        email = getEmailFromOptions();
    }

    class Message extends ExchangeSession.Message {
        // message item id
        ItemId itemId;
    }

    /**
     * Message create/update properties
     *
     * @param properties flag values map
     * @return field values
     */
    protected Set<FieldUpdate> buildProperties(Map<String, String> properties) {
        HashSet<FieldUpdate> list = new HashSet<FieldUpdate>();
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
            }
        }
        return list;
    }

    @Override
    public void createMessage(String folderPath, String messageName, HashMap<String, String> properties, byte[] messageBody) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
        item.mimeContent = Base64.encodeBase64(messageBody);

        Set<FieldUpdate> fieldUpdates = buildProperties(properties);
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
    }

    @Override
    public void updateMessage(ExchangeSession.Message message, Map<String, String> properties) throws IOException {
        UpdateItemMethod updateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                ConflictResolution.AlwaysOverwrite,
                SendMeetingInvitationsOrCancellations.SendToNone,
                ((EwsExchangeSession.Message) message).itemId, buildProperties(properties));
        executeMethod(updateItemMethod);
    }

    @Override
    public void deleteMessage(ExchangeSession.Message message) throws IOException {
        LOGGER.debug("Delete " + message.permanentUrl);
        DeleteItemMethod deleteItemMethod = new DeleteItemMethod(((EwsExchangeSession.Message) message).itemId, DeleteType.HardDelete);
        executeMethod(deleteItemMethod);
    }

    @Override
    public void sendMessage(byte[] messageBody) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
        item.mimeContent = Base64.encodeBase64(messageBody);

        CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SendAndSaveCopy, getFolderId(SENT), item);
        executeMethod(createItemMethod);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected byte[] getContent(ExchangeSession.Message message) throws IOException {
        return getContent(((EwsExchangeSession.Message) message).itemId);
    }

    /**
     * Get item MIME content.
     *
     * @param itemId EWS item id
     * @return item content as byte array
     * @throws IOException on error
     */
    protected byte[] getContent(ItemId itemId) throws IOException {
        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, true);
        executeMethod(getItemMethod);
        return getItemMethod.getMimeContent();
    }

    protected Message buildMessage(EWSMethod.Item response) throws DavMailException {
        Message message = new Message();

        // get item id
        message.itemId = new ItemId(response);

        message.permanentUrl = response.get(Field.get("permanenturl").getResponseName());

        message.size = response.getInt(Field.get("messageSize").getResponseName());
        message.uid = response.get(Field.get("uid").getResponseName());
        message.imapUid = response.getLong(Field.get("imapUid").getResponseName());
        message.read = response.getBoolean(Field.get("read").getResponseName());
        message.junk = response.getBoolean(Field.get("junk").getResponseName());
        message.flagged = "2".equals(response.get(Field.get("flagStatus").getResponseName()));
        message.draft = "9".equals(response.get(Field.get("messageFlags").getResponseName())) || "8".equals(response.get(Field.get("messageFlags").getResponseName()));
        String lastVerbExecuted = response.get(Field.get("lastVerbExecuted").getResponseName());
        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
        message.forwarded = "104".equals(lastVerbExecuted);
        message.date = convertDateFromExchange(response.get(Field.get("date").getResponseName()));
        message.deleted = "1".equals(response.get(Field.get("deleted").getResponseName()));

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
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition, FolderQueryTraversal.SHALLOW);

        for (EWSMethod.Item response : responses) {
            Message message = buildMessage(response);
            message.messageList = messages;
            messages.add(message);
        }
        Collections.sort(messages);
        return messages;
    }

    protected List<EWSMethod.Item> searchItems(String folderPath, Set<String> attributes, Condition condition, FolderQueryTraversal folderQueryTraversal) throws IOException {
        FindItemMethod findItemMethod = new FindItemMethod(folderQueryTraversal, BaseShape.ID_ONLY, getFolderId(folderPath));
        for (String attribute : attributes) {
            findItemMethod.addAdditionalProperty(Field.get(attribute));
        }
        if (condition != null && !condition.isEmpty()) {
            findItemMethod.setSearchExpression((SearchExpression) condition);
        }
        executeMethod(findItemMethod);
        return findItemMethod.getResponseItems();
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
                    buffer.append("</t:").append(operator.toString()).append('>');
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

        protected FieldURI getFieldURI(String attributeName) {
            FieldURI fieldURI = Field.get(attributeName);
            if (fieldURI == null) {
                throw new IllegalArgumentException("Unknown field: " + attributeName);
            }
            return fieldURI;
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
            FieldURI fieldURI = getFieldURI(attributeName);
            fieldURI.appendTo(buffer);

            buffer.append("<t:FieldURIOrConstant><t:Constant Value=\"");
            // encode urlcompname
            if (fieldURI instanceof ExtendedFieldURI && "0x10f3".equals(((ExtendedFieldURI) fieldURI).propertyTag)) {
                buffer.append(StringUtil.xmlEncode(StringUtil.encodeUrlcompname(value)));
            } else {
                buffer.append(StringUtil.xmlEncode(value));
            }
            buffer.append("\"/></t:FieldURIOrConstant>");

            buffer.append("</t:").append(operator.toString()).append('>');
        }
    }

    protected static class HeaderCondition extends AttributeCondition {

        protected HeaderCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
        }

        @Override
        protected FieldURI getFieldURI(String attributeName) {
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
    public Condition equals(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, value);
    }

    @Override
    public Condition equals(String attributeName, int value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, String.valueOf(value));
    }

    @Override
    public Condition headerEquals(String headerName, String value) {
        return new HeaderCondition(headerName, Operator.IsEqualTo, value);
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
    public Condition isTrue(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "true");
    }

    @Override
    public Condition isFalse(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "false");
    }

    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<FieldURI>();

    static {
        FOLDER_PROPERTIES.add(Field.get("urlcompname"));
        FOLDER_PROPERTIES.add(Field.get("folderDisplayName"));
        FOLDER_PROPERTIES.add(Field.get("lastmodified"));
        FOLDER_PROPERTIES.add(Field.get("folderclass"));
        FOLDER_PROPERTIES.add(Field.get("ctag"));
        FOLDER_PROPERTIES.add(Field.get("unread"));
        FOLDER_PROPERTIES.add(Field.get("hassubs"));
        FOLDER_PROPERTIES.add(Field.get("uidNext"));
        FOLDER_PROPERTIES.add(Field.get("highestUid"));
    }

    protected Folder buildFolder(EWSMethod.Item item) {
        Folder folder = new Folder();
        folder.folderId = new FolderId(item);
        folder.displayName = item.get(Field.get("folderDisplayName").getResponseName());
        folder.folderClass = item.get(Field.get("folderclass").getResponseName());
        folder.etag = item.get(Field.get("lastmodified").getResponseName());
        folder.ctag = item.get(Field.get("ctag").getResponseName());
        folder.unreadCount = item.getInt(Field.get("unread").getResponseName());
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
        List<ExchangeSession.Folder> folders = new ArrayList<ExchangeSession.Folder>();
        appendSubFolders(folders, baseFolderPath, getFolderId(folderPath), condition, recursive);
        return folders;
    }

    protected void appendSubFolders(List<ExchangeSession.Folder> folders,
                                    String parentFolderPath, FolderId parentFolderId,
                                    Condition condition, boolean recursive) throws IOException {
        FindFolderMethod findFolderMethod = new FindFolderMethod(FolderQueryTraversal.SHALLOW,
                BaseShape.ID_ONLY, parentFolderId, FOLDER_PROPERTIES, (SearchExpression) condition);
        executeMethod(findFolderMethod);
        for (EWSMethod.Item item : findFolderMethod.getResponseItems()) {
            Folder folder = buildFolder(item);
            if (parentFolderPath.length() > 0) {
                folder.folderPath = parentFolderPath + '/' + item.get(Field.get("urlcompname").getResponseName());
            } else if (folderIdMap.get(folder.folderId.value) != null) {
                folder.folderPath = folderIdMap.get(folder.folderId.value);
            } else {
                folder.folderPath = item.get(Field.get("urlcompname").getResponseName());
            }
            folders.add(folder);
            if (recursive && folder.hasChildren) {
                appendSubFolders(folders, folder.folderPath, folder.folderId, condition, recursive);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public ExchangeSession.Folder getFolder(String folderPath) throws IOException {
        return internalGetFolder(folderPath);
    }

    /**
     * Get folder by path.
     *
     * @param folderPath folder path
     * @return folder object
     * @throws IOException on error
     */
    protected EwsExchangeSession.Folder internalGetFolder(String folderPath) throws IOException {
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ID_ONLY, getFolderId(folderPath), FOLDER_PROPERTIES);
        executeMethod(getFolderMethod);
        EWSMethod.Item item = getFolderMethod.getResponseItem();
        Folder folder = null;
        if (item != null) {
            folder = buildFolder(item);
            folder.folderPath = folderPath;
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
        folder.put("DisplayName", path.folderName);
        folder.put("FolderClass", folderClass);
        // TODO: handle properties
        CreateFolderMethod createFolderMethod = new CreateFolderMethod(getFolderId(path.parentPath), folder);
        executeMethod(createFolderMethod);
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
    public void copyMessage(ExchangeSession.Message message, String targetFolder) throws IOException {
        CopyItemMethod copyItemMethod = new CopyItemMethod(((EwsExchangeSession.Message) message).itemId, getFolderId(targetFolder));
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
            Set<FieldUpdate> updates = new HashSet<FieldUpdate>();
            updates.add(new FieldUpdate(Field.get("folderDisplayName"), targetPath.folderName));
            UpdateFolderMethod updateFolderMethod = new UpdateFolderMethod(folderId, updates);
            executeMethod(updateFolderMethod);
        }
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
            itemName = response.get(Field.get("urlcompname").getResponseName());
            for (String attributeName : CONTACT_ATTRIBUTES) {
                String value = response.get(Field.get(attributeName).getResponseName());
                if (value != null) {
                    if ("bday".equals(attributeName) || "anniversary".equals(attributeName) || "lastmodified".equals(attributeName) || "datereceived".equals(attributeName)) {
                        value = convertDateFromExchange(value);
                    }
                    put(attributeName, value);
                }
            }
        }

        /**
         * @inheritDoc
         */
        protected Contact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) {
            super(folderPath, itemName, properties, etag, noneMatch);
        }

        protected Set<FieldUpdate> buildProperties() {
            HashSet<FieldUpdate> list = new HashSet<FieldUpdate>();
            for (Map.Entry<String, String> entry : entrySet()) {
                if ("photo".equals(entry.getKey())) {
                    list.add(Field.createFieldUpdate("haspicture", "true"));
                } else {
                    list.add(Field.createFieldUpdate(entry.getKey(), entry.getValue()));
                }
            }
            // force urlcompname
            list.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
            return list;
        }


        /**
         * Create or update contact
         *
         * @return action result
         * @throws IOException on error
         */
        public ItemResult createOrUpdate() throws IOException {
            String photo = get("photo");

            ItemResult itemResult = new ItemResult();
            EWSMethod createOrUpdateItemMethod;

            // first try to load existing event
            String urlcompname = convertItemNameToEML(itemName);
            String currentEtag = null;
            ItemId currentItemId = null;
            FileAttachment currentFileAttachment = null;
            List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, EwsExchangeSession.this.equals("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW);
            if (!responses.isEmpty()) {
                EWSMethod.Item response = responses.get(0);
                currentItemId = new ItemId(response);
                currentEtag = response.get(Field.get("etag").getResponseName());

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

            if (currentItemId != null) {
                // update
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AlwaysOverwrite,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        currentItemId, buildProperties());
            } else {
                // create
                EWSMethod.Item newItem = new EWSMethod.Item();
                newItem.type = "Contact";
                newItem.setFieldUpdates(buildProperties());
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

            // first delete current picture
            if (currentFileAttachment != null) {
                DeleteAttachmentMethod deleteAttachmentMethod = new DeleteAttachmentMethod(currentFileAttachment.attachmentId);
                executeMethod(deleteAttachmentMethod);
            }

            if (photo != null) {
                // convert image to jpeg
                byte[] resizedImageBytes = IOUtil.resizeImage(Base64.decodeBase64(photo.getBytes()), 90);

                // TODO: handle photo update, fix attachment mapi properties (available only with Exchange 2010)
                FileAttachment attachment = new FileAttachment("ContactPicture.jpg", "image/jpeg", new String(Base64.encodeBase64(resizedImageBytes)));
                // update photo attachment
                CreateAttachmentMethod createAttachmentMethod = new CreateAttachmentMethod(newItemId, attachment);
                executeMethod(createAttachmentMethod);
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

        protected Event(EWSMethod.Item response) {
            itemId = new ItemId(response);

            permanentUrl = response.get(Field.get("permanenturl").getResponseName());
            etag = response.get(Field.get("etag").getResponseName());
            displayName = response.get(Field.get("displayname").getResponseName());
            itemName = response.get(Field.get("urlcompname").getResponseName());
        }

        /**
         * @inheritDoc
         */
        protected Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) {
            super(folderPath, itemName, contentClass, itemBody, etag, noneMatch);
        }

        @Override
        public ItemResult createOrUpdate() throws IOException {
            return createOrUpdate(fixICS(itemBody, false).getBytes("UTF-8"));
        }


        @Override
        protected ItemResult createOrUpdate(byte[] mimeContent) throws IOException {
            ItemResult itemResult = new ItemResult();
            EWSMethod createOrUpdateItemMethod;

            // first try to load existing event
            String urlcompname = convertItemNameToEML(itemName);
            String currentEtag = null;
            ItemId currentItemId = null;
            List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, EwsExchangeSession.this.equals("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW);
            if (!responses.isEmpty()) {
                EWSMethod.Item response = responses.get(0);
                currentItemId = new ItemId(response);
                currentEtag = response.get(Field.get("etag").getResponseName());
            }
            if ("*".equals(noneMatch)) {
                // create requested
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

            if (currentItemId != null) {
                Set<FieldUpdate> updates = new HashSet<FieldUpdate>();
                updates.add(new FieldUpdate(Field.get("mimeContent"), String.valueOf(Base64.encodeBase64(mimeContent))));
                // update
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AlwaysOverwrite,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        currentItemId, updates);
            } else {
                // create
                EWSMethod.Item newItem = new EWSMethod.Item();
                newItem.type = "CalendarItem";
                newItem.mimeContent = Base64.encodeBase64(mimeContent);
                HashSet<FieldUpdate> updates = new HashSet<FieldUpdate>();
                // force urlcompname
                updates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                //updates.add(Field.createFieldUpdate("outlookmessageclass", "IPM.Appointment"));
                newItem.setFieldUpdates(updates);
                createOrUpdateItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId(folderPath), newItem);
            }

            executeMethod(createOrUpdateItemMethod);

            itemResult.status = createOrUpdateItemMethod.getStatusCode();
            if (itemResult.status == HttpURLConnection.HTTP_OK) {
                //noinspection VariableNotUsedInsideIf
                if (etag == null) {
                    itemResult.status = HttpStatus.SC_CREATED;
                    LOGGER.debug("Updated event " + getHref());
                } else {
                    LOGGER.warn("Overwritten event " + getHref());
                }
            }

            ItemId newItemId = new ItemId(createOrUpdateItemMethod.getResponseItem());
            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, newItemId, false);
            getItemMethod.addAdditionalProperty(Field.get("etag"));
            executeMethod(getItemMethod);
            itemResult.etag = getItemMethod.getResponseItem().get(Field.get("etag").getResponseName());

            return itemResult;

        }

        @Override
        public String getBody() throws HttpException {
            String result;
            LOGGER.debug("Get event: " + permanentUrl);
            try {
                byte[] content = getContent(itemId);
                result = new String(content);
            } catch (IOException e) {
                throw buildHttpException(e);
            }
            return result;
        }
    }

    @Override
    public List<ExchangeSession.Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        List<ExchangeSession.Contact> contacts = new ArrayList<ExchangeSession.Contact>();
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition,
                FolderQueryTraversal.SHALLOW);

        for (EWSMethod.Item response : responses) {
            contacts.add(new Contact(response));
        }
        return contacts;
    }

    @Override
    public List<ExchangeSession.Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        List<ExchangeSession.Event> events = new ArrayList<ExchangeSession.Event>();
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes,
                condition,
                FolderQueryTraversal.SHALLOW);
        for (EWSMethod.Item response : responses) {
            events.add(new Event(response));
        }

        return events;
    }

    protected static final HashSet<String> EVENT_REQUEST_PROPERTIES = new HashSet<String>();

    static {
        EVENT_REQUEST_PROPERTIES.add("permanenturl");
        EVENT_REQUEST_PROPERTIES.add("etag");
        EVENT_REQUEST_PROPERTIES.add("displayname");
        EVENT_REQUEST_PROPERTIES.add("urlcompname");
    }


    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        String urlcompname = convertItemNameToEML(itemName);
        List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, equals("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW);
        if (responses.isEmpty()) {
            throw new DavMailException("EXCEPTION_ITEM_NOT_FOUND");
        }
        String itemType = responses.get(0).type;
        if ("Contact".equals(itemType)) {
            // retrieve Contact properties
            List<ExchangeSession.Contact> contacts = searchContacts(folderPath, CONTACT_ATTRIBUTES, equals("urlcompname", urlcompname));
            if (contacts.isEmpty()) {
                throw new DavMailException("EXCEPTION_ITEM_NOT_FOUND");
            }
            return contacts.get(0);
        } else if ("CalendarItem".equals(itemType)
                || "MeetingRequest".equals(itemType)) {
            return new Event(responses.get(0));
        } else {
            throw new DavMailException("EXCEPTION_ITEM_NOT_FOUND");
        }
    }

    @Override
    public ContactPhoto getContactPhoto(ExchangeSession.Contact contact) throws IOException {
        ContactPhoto contactPhoto = null;

        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, ((EwsExchangeSession.Contact) contact).itemId, false);
        getItemMethod.addAdditionalProperty(Field.get("attachments"));
        executeMethod(getItemMethod);
        EWSMethod.Item item = getItemMethod.getResponseItem();
        if (item != null) {
            FileAttachment attachment = item.getAttachmentByName("ContactPicture.jpg");
            if (attachment != null) {
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

            }

        }

        return contactPhoto;
    }

    @Override
    public void deleteItem(String folderPath, String itemName) throws IOException {
        String urlcompname = convertItemNameToEML(itemName);
        List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, equals("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW);
        if (!responses.isEmpty()) {
            DeleteItemMethod deleteItemMethod = new DeleteItemMethod(new ItemId(responses.get(0)), DeleteType.HardDelete);
            executeMethod(deleteItemMethod);
        }
    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {
        String urlcompname = convertItemNameToEML(itemName);
        List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, equals("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW);
        if (!responses.isEmpty()) {
            HashMap<String, String> localProperties = new HashMap<String, String>();
            localProperties.put("processed", "1");
            localProperties.put("read", "1");
            UpdateItemMethod updateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                    ConflictResolution.AlwaysOverwrite,
                    SendMeetingInvitationsOrCancellations.SendToNone,
                    new ItemId(responses.get(0)), buildProperties(localProperties));
            executeMethod(updateItemMethod);
        }
    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemResult internalCreateOrUpdateContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) throws IOException {
        return new Contact(folderPath, itemName, properties, etag, noneMatch).createOrUpdate();
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        return new Event(folderPath, itemName, contentClass, icsBody, etag, noneMatch).createOrUpdate();
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        // TODO
        return false;
    }

    @Override
    protected void loadVtimezone() {
        throw new UnsupportedOperationException();
    }


    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    private FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;
        String currentMailboxPath = "/users/" + email;
        if (currentMailboxPath.equals(folderPath)) {
            return DistinguishedFolderId.MSGFOLDERROOT;
        } else if (folderPath.startsWith(currentMailboxPath + '/')) {
            return getFolderIdIfExists(folderPath.substring(currentMailboxPath.length() + 1));
        }
        if (folderPath.startsWith(PUBLIC_ROOT)) {
            currentFolderId = DistinguishedFolderId.PUBLICFOLDERSROOT;
            folderNames = folderPath.substring(PUBLIC_ROOT.length()).split("/");
        } else if (folderPath.startsWith(INBOX) || folderPath.startsWith(LOWER_CASE_INBOX)) {
            currentFolderId = DistinguishedFolderId.INBOX;
            folderNames = folderPath.substring(INBOX.length()).split("/");
        } else if (folderPath.startsWith(CALENDAR)) {
            currentFolderId = DistinguishedFolderId.CALENDAR;
            folderNames = folderPath.substring(CALENDAR.length()).split("/");
        } else if (folderPath.startsWith(CONTACTS)) {
            currentFolderId = DistinguishedFolderId.CONTACTS;
            folderNames = folderPath.substring(CONTACTS.length()).split("/");
        } else if (folderPath.startsWith(SENT)) {
            currentFolderId = DistinguishedFolderId.SENTITEMS;
            folderNames = folderPath.substring(SENT.length()).split("/");
        } else if (folderPath.startsWith(DRAFTS)) {
            currentFolderId = DistinguishedFolderId.DRAFTS;
            folderNames = folderPath.substring(DRAFTS.length()).split("/");
        } else if (folderPath.startsWith(TRASH)) {
            currentFolderId = DistinguishedFolderId.DELETEDITEMS;
            folderNames = folderPath.substring(TRASH.length()).split("/");
        } else if (folderPath.startsWith(JUNK)) {
            currentFolderId = DistinguishedFolderId.JUNKEMAIL;
            folderNames = folderPath.substring(JUNK.length()).split("/");
        } else if (folderPath.startsWith(UNSENT)) {
            currentFolderId = DistinguishedFolderId.OUTBOX;
            folderNames = folderPath.substring(UNSENT.length()).split("/");
        } else {
            currentFolderId = DistinguishedFolderId.MSGFOLDERROOT;
            folderNames = folderPath.split("/");
        }
        for (String folderName : folderNames) {
            if (folderName.length() > 0) {
                currentFolderId = getSubFolderByName(currentFolderId, folderName);
                if (currentFolderId == null) {
                    break;
                }
            }
        }
        return currentFolderId;
    }

    protected FolderId getSubFolderByName(FolderId parentFolderId, String folderName) throws IOException {
        FolderId folderId = null;
        FindFolderMethod findFolderMethod = new FindFolderMethod(
                FolderQueryTraversal.SHALLOW,
                BaseShape.ID_ONLY,
                parentFolderId,
                FOLDER_PROPERTIES,
                new TwoOperandExpression(TwoOperandExpression.Operator.IsEqualTo,
                        Field.get("folderDisplayName"), folderName)
        );
        executeMethod(findFolderMethod);
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        if (item != null) {
            folderId = new FolderId(item);
        }
        return folderId;
    }

    protected void executeMethod(EWSMethod ewsMethod) throws IOException {
        try {
            httpClient.executeMethod(ewsMethod);
            ewsMethod.checkSuccess();
        } finally {
            ewsMethod.releaseConnection();
        }
    }

    protected String convertDateFromExchange(String exchangeDateValue) throws DavMailException {
        String zuluDateValue = null;
        if (exchangeDateValue != null) {
            try {
                zuluDateValue = getZuluDateFormat().format(getExchangeZuluDateFormat().parse(exchangeDateValue));
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", exchangeDateValue);
            }
        }
        return zuluDateValue;
    }

}


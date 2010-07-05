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
import davmail.exchange.ExchangeSession;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.HeadMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
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
        protected String parentPath;
        protected String folderName;

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
        HttpMethod headMethod = new HeadMethod("/ews/services.wsdl");
        try {
            headMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, headMethod);
            if (headMethod.getStatusCode() != HttpStatus.SC_OK) {
                throw DavGatewayHttpClientFacade.buildHttpException(headMethod);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        } finally {
            headMethod.releaseConnection();
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
                list.add(Field.createFieldUpdate("read", entry.getValue()));
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
            } else if ("bcc".equals(entry.getKey())) {
                list.add(Field.createFieldUpdate("bcc", entry.getValue()));
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
    public void createMessage(String folderPath, String messageName, HashMap<String, String> properties, String messageBody) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
        item.mimeContent = Base64.encodeBase64(messageBody.getBytes());
        Set<FieldUpdate> fieldUpdates = buildProperties(properties);
        if (!properties.containsKey("draft")) {
            // need to force draft flag to false
            fieldUpdates.add(Field.createFieldUpdate("messageFlags", "0"));
        }
        item.setFieldUpdates(fieldUpdates);
        CreateItemMethod createItemMethod = new CreateItemMethod(getFolderId(folderPath), item);
        createItemMethod.messageDisposition = MessageDisposition.SaveOnly;
        executeMethod(createItemMethod);
        // TODO: do we need to update message after to force some properties ?
    }

    @Override
    public void updateMessage(ExchangeSession.Message message, Map<String, String> properties) throws IOException {
        UpdateItemMethod updateItemMethod = new UpdateItemMethod(ConflictResolution.AlwaysOverwrite, ((EwsExchangeSession.Message)message).itemId, buildProperties(properties));
        updateItemMethod.messageDisposition = MessageDisposition.SaveOnly;
        executeMethod(updateItemMethod);
    }

    @Override
    public void deleteMessage(ExchangeSession.Message message) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    public void sendMessage(HashMap<String, String> properties, String messageBody) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    protected BufferedReader getContentReader(ExchangeSession.Message message) throws IOException {
        throw new UnsupportedOperationException();
    }

    protected Message buildMessage(EWSMethod.Item response) throws URIException {
        Message message = new Message();

        // get item id
        message.itemId = new ItemId(response.get("ItemId"), response.get("ChangeKey"));

        message.size = response.getInt(Field.get("messageSize").getResponseName());
        message.uid = response.get(Field.get("uid").getResponseName());
        message.imapUid = response.getLong(Field.get("imapUid").getResponseName());
        message.read = response.getBoolean(Field.get("read").getResponseName());
        message.junk = response.getBoolean(Field.get("junk").getResponseName());
        message.flagged = "2".equals(response.get(Field.get("flagStatus").getResponseName()));
        message.draft = "9".equals(response.get(Field.get("messageFlags").getResponseName()));
        String lastVerbExecuted = response.get(Field.get("lastVerbExecuted").getResponseName());
        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
        message.forwarded = "104".equals(lastVerbExecuted);
        message.date = response.get(Field.get("date").getResponseName());
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
            getFieldURI(attributeName).appendTo(buffer);

            buffer.append("<t:FieldURIOrConstant><t:Constant Value=\"");
            buffer.append(StringUtil.xmlEncode(value));
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
        protected String attributeName;

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
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "True");
    }

    @Override
    public Condition isFalse(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "False");
    }

    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<FieldURI>();

    static {
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_URL_COMP_NAME);
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME);
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_CONTAINER_CLASS);
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_LOCAL_COMMIT_TIME_MAX);
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_CONTENT_UNREAD);
        FOLDER_PROPERTIES.add(ExtendedFieldURI.PR_SUBFOLDERS);
    }

    protected Folder buildFolder(EWSMethod.Item item) {
        Folder folder = new Folder();
        folder.folderId = new FolderId(item.get("FolderId"), item.get("ChangeKey"));
        folder.displayName = item.get(ExtendedFieldURI.PR_URL_COMP_NAME.getPropertyTag());
        folder.folderClass = item.get(ExtendedFieldURI.PR_CONTAINER_CLASS.getPropertyTag());
        folder.etag = item.get(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME.getPropertyTag());
        folder.ctag = item.get(ExtendedFieldURI.PR_LOCAL_COMMIT_TIME_MAX.getPropertyTag());
        folder.unreadCount = item.getInt(ExtendedFieldURI.PR_CONTENT_UNREAD.getPropertyTag());
        folder.hasChildren = item.getBoolean(ExtendedFieldURI.PR_SUBFOLDERS.getPropertyTag());
        // noInferiors not implemented
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<ExchangeSession.Folder> getSubFolders(String folderPath, Condition condition, boolean recursive) throws IOException {
        List<ExchangeSession.Folder> folders = new ArrayList<ExchangeSession.Folder>();
        appendSubFolders(folders, folderPath, getFolderId(folderPath), condition, recursive);
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
                folder.folderPath = parentFolderPath + '/' + item.get(ExtendedFieldURI.PR_URL_COMP_NAME.getPropertyTag());
            } else if (folderIdMap.get(folder.folderId.value) != null) {
                folder.folderPath = folderIdMap.get(folder.folderId.value);
            } else {
                folder.folderPath = item.get(ExtendedFieldURI.PR_URL_COMP_NAME.getPropertyTag());
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
    public void createFolder(String folderPath, String folderClass) throws IOException {
        FolderPath path = new FolderPath(folderPath);
        EWSMethod.Item folder = new EWSMethod.Item();
        folder.type = "Folder";
        folder.put("DisplayName", path.folderName);
        folder.put("FolderClass", folderClass);
        CreateFolderMethod createFolderMethod = new CreateFolderMethod(getFolderId(path.parentPath), folder);
        executeMethod(createFolderMethod);
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
        throw new UnsupportedOperationException();
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
            updates.add(new FieldUpdate(UnindexedFieldURI.FOLDER_DISPLAYNAME, targetPath.folderName));
            UpdateFolderMethod updateFolderMethod = new UpdateFolderMethod(folderId, updates);
            executeMethod(updateFolderMethod);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void moveToTrash(ExchangeSession.Message message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Contact> searchContacts(String folderName, Set<String> attributes, Condition condition) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteItem(String folderPath, String itemName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemResult internalCreateOrUpdateContact(String messageUrl, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String messageUrl, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void loadVtimezone() {
        throw new UnsupportedOperationException();
    }


    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new DavMailException("EXCEPTION_FOLDER_NOT_FOUND", folderPath);
        }
        return folderId;
    }

    private FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;
        if (folderPath.startsWith(PUBLIC_ROOT)) {
            currentFolderId = DistinguishedFolderId.PUBLICFOLDERSROOT;
            folderNames = folderPath.substring(PUBLIC_ROOT.length()).split("/");
        } else if (folderPath.startsWith(INBOX)) {
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
                        ExtendedFieldURI.PR_URL_COMP_NAME, folderName)
        );
        executeMethod(findFolderMethod);
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        if (item != null) {
            folderId = new FolderId(item.get("FolderId"), item.get("ChangeKey"));
        }
        return folderId;
    }

    protected int executeMethod(EWSMethod ewsMethod) throws IOException {
        int status;
        try {
            status = httpClient.executeMethod(ewsMethod);
            ewsMethod.checkSuccess();
        } finally {
            ewsMethod.releaseConnection();
        }
        return status;
    }

}

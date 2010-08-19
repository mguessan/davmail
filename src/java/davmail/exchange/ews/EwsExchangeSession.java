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
import davmail.exchange.VCalendar;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * EWS Exchange adapter.
 * Compatible with Exchange 2007 and hopefully 2010.
 */
public class EwsExchangeSession extends ExchangeSession {

    protected Map<String, String> folderIdMap;
    protected String serverVersion;

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
    protected void buildSessionInfo(HttpMethod method) throws DavMailException {
        // nothing to do, mailPath not used in EWS mode
        // check EWS access
        HttpMethod getMethod = new GetMethod("/ews/exchange.asmx");
        getMethod.setFollowRedirects(false);
        try {
            int status = DavGatewayHttpClientFacade.executeNoRedirect(httpClient, getMethod);
            if (status != HttpStatus.SC_MOVED_TEMPORARILY) {
                throw DavGatewayHttpClientFacade.buildHttpException(getMethod);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        } finally {
            getMethod.releaseConnection();
        }

        // also need to retrieve email and alias
        alias = getAliasFromOptions();
        email = getEmailFromOptions();
        if (email == null || alias == null) {
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        }
        currentMailboxPath = "/users/" + email.toLowerCase();

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
    public void createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException {
        EWSMethod.Item item = new EWSMethod.Item();
        item.type = "Message";
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(baos);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
        baos.close();
        item.mimeContent = Base64.encodeBase64(baos.toByteArray());

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
        DeleteItemMethod deleteItemMethod = new DeleteItemMethod(((EwsExchangeSession.Message) message).itemId, DeleteType.HardDelete, SendMeetingCancellations.SendToNone);
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

    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        sendMessage(baos.toByteArray());
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
        message.draft = (response.getInt(Field.get("messageFlags").getResponseName()) & 8) != 0;
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
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition, FolderQueryTraversal.SHALLOW, 0);

        for (EWSMethod.Item response : responses) {
            Message message = buildMessage(response);
            message.messageList = messages;
            messages.add(message);
        }
        Collections.sort(messages);
        return messages;
    }

    protected List<EWSMethod.Item> searchItems(String folderPath, Set<String> attributes, Condition condition, FolderQueryTraversal folderQueryTraversal, int maxCount) throws IOException {
        FindItemMethod findItemMethod = new FindItemMethod(folderQueryTraversal, BaseShape.ID_ONLY, getFolderId(folderPath), maxCount);
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

        protected FieldURI getFieldURI() {
            FieldURI fieldURI = Field.get(attributeName);
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
                buffer.append(StringUtil.xmlEncode(StringUtil.encodeUrlcompname(value)));
            } else {
                buffer.append(StringUtil.xmlEncode(value));
            }
            buffer.append("\"/>");
            if (operator != Operator.Contains) {
                buffer.append("</t:FieldURIOrConstant>");
            }

            buffer.append("</t:").append(operator.toString()).append('>');
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

        protected HeaderCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
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

    protected Folder buildFolder(String mailbox, EWSMethod.Item item) {
        Folder folder = new Folder();
        folder.folderId = new FolderId(mailbox, item);
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
            Folder folder = buildFolder(parentFolderId.mailbox, item);
            if (parentFolderPath.length() > 0) {
                folder.folderPath = parentFolderPath + '/' + item.get(Field.get("folderDisplayName").getResponseName());
            } else if (folderIdMap.get(folder.folderId.value) != null) {
                folder.folderPath = folderIdMap.get(folder.folderId.value);
            } else {
                folder.folderPath = item.get(Field.get("folderDisplayName").getResponseName());
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
        FolderId folderId = getFolderId(folderPath);
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ID_ONLY, folderId, FOLDER_PROPERTIES);
        executeMethod(getFolderMethod);
        EWSMethod.Item item = getFolderMethod.getResponseItem();
        Folder folder;
        if (item != null) {
            folder = buildFolder(folderId.mailbox, item);
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
        folder.put("DisplayName", path.folderName);
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
            // workaround for missing urlcompname in Exchange 2010
            if (itemName == null) {
                itemName = StringUtil.base64ToUrl(itemId.id) + ".EML";
            }
            for (String attributeName : CONTACT_ATTRIBUTES) {
                String value = response.get(Field.get(attributeName).getResponseName());
                if (value != null && value.length() > 0) {
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

        /**
         * Empty constructor for GalFind
         */
        protected Contact() {
        }

        protected Set<FieldUpdate> buildProperties() {
            HashSet<FieldUpdate> list = new HashSet<FieldUpdate>();
            for (Map.Entry<String, String> entry : entrySet()) {
                if ("photo".equals(entry.getKey())) {
                    list.add(Field.createFieldUpdate("haspicture", "true"));
                } else if (!entry.getKey().startsWith("email")) {
                    list.add(Field.createFieldUpdate(entry.getKey(), entry.getValue()));
                }
            }
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
            String currentEtag = null;
            ItemId currentItemId = null;
            FileAttachment currentFileAttachment = null;
            EWSMethod.Item currentItem = getEwsItem(folderPath, itemName);
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

            Set<FieldUpdate> properties = buildProperties();
            if (currentItemId != null) {
                // update
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AlwaysOverwrite,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        currentItemId, properties);
            } else {
                // create
                EWSMethod.Item newItem = new EWSMethod.Item();
                newItem.type = "Contact";
                // force urlcompname on create
                properties.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
                newItem.setFieldUpdates(properties);
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
            if ("Exchange2010".equals(serverVersion)) {
                // first delete current picture
                if (currentFileAttachment != null) {
                    DeleteAttachmentMethod deleteAttachmentMethod = new DeleteAttachmentMethod(currentFileAttachment.attachmentId);
                    executeMethod(deleteAttachmentMethod);
                }

                if (photo != null) {
                    // convert image to jpeg
                    byte[] resizedImageBytes = IOUtil.resizeImage(Base64.decodeBase64(photo.getBytes()), 90);

                    FileAttachment attachment = new FileAttachment("ContactPicture.jpg", "image/jpeg", new String(Base64.encodeBase64(resizedImageBytes)));
                    if ("Exchange2010".equals(serverVersion)) {
                        attachment.setIsContactPhoto(true);
                    }
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

        protected Event(EWSMethod.Item response) {
            itemId = new ItemId(response);

            permanentUrl = response.get(Field.get("permanenturl").getResponseName());
            etag = response.get(Field.get("etag").getResponseName());
            displayName = response.get(Field.get("displayname").getResponseName());
            itemName = response.get(Field.get("urlcompname").getResponseName());
            // workaround for missing urlcompname in Exchange 2010
            if (itemName == null) {
                itemName = StringUtil.base64ToUrl(itemId.id) + ".EML";
            }
        }

        /**
         * @inheritDoc
         */
        protected Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) throws IOException {
            super(folderPath, itemName, contentClass, itemBody, etag, noneMatch);
        }

        @Override
        public ItemResult createOrUpdate() throws IOException {
            byte[] itemContent = Base64.encodeBase64(createMimeContent());

            ItemResult itemResult = new ItemResult();
            EWSMethod createOrUpdateItemMethod;

            // first try to load existing event
            String currentEtag = null;
            ItemId currentItemId = null;
            EWSMethod.Item currentItem = getEwsItem(folderPath, itemName);
            if (currentItem != null) {
                currentItemId = new ItemId(currentItem);
                currentEtag = currentItem.get(Field.get("etag").getResponseName());
                LOGGER.debug("Existing item found with etag: " + currentEtag + " id: " + currentItemId.id);
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
                /*Set<FieldUpdate> updates = new HashSet<FieldUpdate>();
                updates.add(new FieldUpdate(Field.get("mimeContent"), new String(Base64.encodeBase64(itemContent))));
                // update
                createOrUpdateItemMethod = new UpdateItemMethod(MessageDisposition.SaveOnly,
                        ConflictResolution.AutoResolve,
                        SendMeetingInvitationsOrCancellations.SendToNone,
                        currentItemId, updates);*/
                // hard method: delete/create on update
                DeleteItemMethod deleteItemMethod = new DeleteItemMethod(currentItemId, DeleteType.HardDelete, SendMeetingCancellations.SendToNone);
                executeMethod(deleteItemMethod);
            } //else {
            // create
            EWSMethod.Item newItem = new EWSMethod.Item();
            newItem.type = "CalendarItem";
            newItem.mimeContent = itemContent;
            HashSet<FieldUpdate> updates = new HashSet<FieldUpdate>();
            // force urlcompname
            updates.add(Field.createFieldUpdate("urlcompname", convertItemNameToEML(itemName)));
            //updates.add(Field.createFieldUpdate("outlookmessageclass", "IPM.Appointment"));
            newItem.setFieldUpdates(updates);
            createOrUpdateItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId(folderPath), newItem);
            //}

            executeMethod(createOrUpdateItemMethod);

            itemResult.status = createOrUpdateItemMethod.getStatusCode();
            if (itemResult.status == HttpURLConnection.HTTP_OK) {
                //noinspection VariableNotUsedInsideIf
                if (currentItemId == null) {
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
        public byte[] getEventContent() throws IOException {
            byte[] content;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Get event: " + folderPath + '/' + itemName);
            }
            try {
                content = getContent(itemId);
            } catch (IOException e) {
                throw buildHttpException(e);
            }
            return content;
        }
    }

    @Override
    public List<ExchangeSession.Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException {
        List<ExchangeSession.Contact> contacts = new ArrayList<ExchangeSession.Contact>();
        List<EWSMethod.Item> responses = searchItems(folderPath, attributes, condition,
                FolderQueryTraversal.SHALLOW, maxCount);

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
                FolderQueryTraversal.SHALLOW, 0);
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

    protected EWSMethod.Item getEwsItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = null;
        String urlcompname = convertItemNameToEML(itemName);
        // workaround for missing urlcompname in Exchange 2010
        if (isItemId(urlcompname)) {
            ItemId itemId = new ItemId(StringUtil.urlToBase64(urlcompname.substring(0, 152)));
            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
            for (String attribute : EVENT_REQUEST_PROPERTIES) {
                getItemMethod.addAdditionalProperty(Field.get(attribute));
            }
            executeMethod(getItemMethod);
            item = getItemMethod.getResponseItem();
        } else {
            List<EWSMethod.Item> responses = searchItems(folderPath, EVENT_REQUEST_PROPERTIES, isEqualTo("urlcompname", urlcompname), FolderQueryTraversal.SHALLOW, 0);
            if (!responses.isEmpty()) {
                item = responses.get(0);
            }
        }
        return item;
    }


    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = getEwsItem(folderPath, itemName);
        if (item == null) {
            throw new DavMailException("EXCEPTION_ITEM_NOT_FOUND");
        }

        String itemType = item.type;
        if ("Contact".equals(itemType)) {
            // retrieve Contact properties
            ItemId itemId = new ItemId(item);
            GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, itemId, false);
            for (String attribute : CONTACT_ATTRIBUTES) {
                getItemMethod.addAdditionalProperty(Field.get(attribute));
            }
            executeMethod(getItemMethod);
            item = getItemMethod.getResponseItem();
            if (item == null) {
                throw new DavMailException("EXCEPTION_ITEM_NOT_FOUND");
            }
            return new Contact(item);
        } else if ("CalendarItem".equals(itemType)
                || "MeetingRequest".equals(itemType)) {
            return new Event(item);
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
        EWSMethod.Item item = getEwsItem(folderPath, itemName);
        if (item != null) {
            DeleteItemMethod deleteItemMethod = new DeleteItemMethod(new ItemId(item), DeleteType.HardDelete, SendMeetingCancellations.SendToNone);
            executeMethod(deleteItemMethod);
        }
    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {
        EWSMethod.Item item = getEwsItem(folderPath, itemName);
        if (item != null) {
            HashMap<String, String> localProperties = new HashMap<String, String>();
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
        String itemName = UUID.randomUUID().toString() + ".EML";
        byte[] mimeContent = new Event(DRAFTS, itemName, "urn:content-classes:calendarmessage", icsBody, null, null).createMimeContent();
        if (mimeContent == null) {
            // no recipients, cancel
            return HttpStatus.SC_NO_CONTENT;
        } else {
            sendMessage(mimeContent);
            return HttpStatus.SC_OK;
        }
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
        return folderPath.startsWith("/") && !folderPath.toLowerCase().startsWith(currentMailboxPath);
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException {
        GetUserAvailabilityMethod getUserAvailabilityMethod = new GetUserAvailabilityMethod(attendee, start, end, interval);
        executeMethod(getUserAvailabilityMethod);
        return getUserAvailabilityMethod.getMergedFreeBusy();
    }

    @Override
    protected void loadVtimezone() {

        try {
            String timezoneId = null;
            if ("Exchange2010".equals(serverVersion)) {
                GetUserConfigurationMethod getUserConfigurationMethod = new GetUserConfigurationMethod();
                executeMethod(getUserConfigurationMethod);
                EWSMethod.Item item = getUserConfigurationMethod.getResponseItem();
                if (item != null) {
                    timezoneId = item.get("timezone");
                }
            } else {
                getTimezoneidFromOptions();
            }
            if (timezoneId != null) {
                createCalendarFolder("davmailtemp", null);
                EWSMethod.Item item = new EWSMethod.Item();
                item.type = "CalendarItem";
                if ("Exchange2010".equals(serverVersion)) {
                    item.put("StartTimeZone", timezoneId);
                } else {
                    item.put("MeetingTimeZone", timezoneId);
                }
                CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId("davmailtemp"), item);
                executeMethod(createItemMethod);
                item = createItemMethod.getResponseItem();
                VCalendar vCalendar = new VCalendar(getContent(new ItemId(item)), email, null);
                this.vTimezone = vCalendar.getVTimezone();
                // delete temporary folder
                deleteFolder("davmailtemp");
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        }
    }

    protected static final String TMZN = "tblTmZn";

    protected String getTimezoneidFromOptions() {
        String result = null;
        // get user mail URL from html body
        BufferedReader optionsPageReader = null;
        GetMethod optionsMethod = new GetMethod("/owa/?ae=Options&t=Regional");
        try {
            DavGatewayHttpClientFacade.executeGetMethod(httpClient, optionsMethod, false);
            optionsPageReader = new BufferedReader(new InputStreamReader(optionsMethod.getResponseBodyAsStream()));
            String line;
            // find email
            //noinspection StatementWithEmptyBody
            while ((line = optionsPageReader.readLine()) != null
                    && (line.indexOf(TMZN) == -1)) {
            }
            if (line != null) {
                int start = line.indexOf("oV=\"") + 4;
                int end = line.indexOf('\"', start);
                result = line.substring(start, end);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
        } finally {
            if (optionsPageReader != null) {
                try {
                    optionsPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
                }
            }
            optionsMethod.releaseConnection();
        }

        return result;
    }


    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    protected static final String USERS_ROOT = "/users/";

    protected FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String lowerCaseFolderPath = folderPath.toLowerCase();
        if (currentMailboxPath.equals(lowerCaseFolderPath)) {
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

        if (folderPath.startsWith(PUBLIC_ROOT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.publicfoldersroot);
            folderNames = folderPath.substring(PUBLIC_ROOT.length()).split("/");
        } else if (folderPath.startsWith(INBOX) || folderPath.startsWith(LOWER_CASE_INBOX)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.inbox);
            folderNames = folderPath.substring(INBOX.length()).split("/");
        } else if (folderPath.startsWith(CALENDAR)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.calendar);
            folderNames = folderPath.substring(CALENDAR.length()).split("/");
        } else if (folderPath.startsWith(CONTACTS)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.contacts);
            folderNames = folderPath.substring(CONTACTS.length()).split("/");
        } else if (folderPath.startsWith(SENT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.sentitems);
            folderNames = folderPath.substring(SENT.length()).split("/");
        } else if (folderPath.startsWith(DRAFTS)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.drafts);
            folderNames = folderPath.substring(DRAFTS.length()).split("/");
        } else if (folderPath.startsWith(TRASH)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.deleteditems);
            folderNames = folderPath.substring(TRASH.length()).split("/");
        } else if (folderPath.startsWith(JUNK)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.junkemail);
            folderNames = folderPath.substring(JUNK.length()).split("/");
        } else if (folderPath.startsWith(UNSENT)) {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.outbox);
            folderNames = folderPath.substring(UNSENT.length()).split("/");
        } else {
            currentFolderId = DistinguishedFolderId.getInstance(mailbox, DistinguishedFolderId.Name.msgfolderroot);
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
            folderId = new FolderId(parentFolderId.mailbox, item);
        }
        return folderId;
    }

    protected void executeMethod(EWSMethod ewsMethod) throws IOException {
        try {
            ewsMethod.setServerVersion(serverVersion);
            httpClient.executeMethod(ewsMethod);
            if (serverVersion == null) {
                serverVersion = ewsMethod.getServerVersion();
            }
            ewsMethod.checkSuccess();
        } finally {
            ewsMethod.releaseConnection();
        }
    }

    protected static final HashMap<String, String> GALFIND_ATTRIBUTE_MAP = new HashMap<String, String>();

    static {
        GALFIND_ATTRIBUTE_MAP.put("uid", "Name");
        GALFIND_ATTRIBUTE_MAP.put("cn", "DisplayName");
        GALFIND_ATTRIBUTE_MAP.put("givenName", "GivenName");
        GALFIND_ATTRIBUTE_MAP.put("sn", "Surname");
        GALFIND_ATTRIBUTE_MAP.put("email1", "EmailAddress1");
        GALFIND_ATTRIBUTE_MAP.put("email2", "EmailAddress2");
        GALFIND_ATTRIBUTE_MAP.put("email3", "EmailAddress3");
    }

    protected Contact buildGalfindContact(EWSMethod.Item response) {
        Contact contact = new Contact();
        contact.setName(response.get("Name"));
        contact.put("uid", response.get("Name"));
        for (Map.Entry<String, String> entry : GALFIND_ATTRIBUTE_MAP.entrySet()) {
            String attributeValue = response.get(entry.getValue());
            if (attributeValue != null) {
                contact.put(entry.getKey(), attributeValue);
            }
        }
        return contact;
    }

    @Override
    public Map<String, ExchangeSession.Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        Map<String, ExchangeSession.Contact> contacts = new HashMap<String, ExchangeSession.Contact>();
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
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ResolveNames(" + searchValue + ')');
                }
                ResolveNamesMethod resolveNamesMethod = new ResolveNamesMethod(searchValue);
                executeMethod(resolveNamesMethod);
                List<EWSMethod.Item> responses = resolveNamesMethod.getResponseItems();
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

    protected static boolean isItemId(String itemName) {
        return itemName.length() == 156;
    }
}


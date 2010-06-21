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
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
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
            folderIdMap.put(getFolder(INBOX).folderId.value, INBOX);
            folderIdMap.put(getFolder(CALENDAR).folderId.value, CALENDAR);
            folderIdMap.put(getFolder(CONTACTS).folderId.value, CONTACTS);
            folderIdMap.put(getFolder(SENT).folderId.value, SENT);
            folderIdMap.put(getFolder(DRAFTS).folderId.value, DRAFTS);
            folderIdMap.put(getFolder(TRASH).folderId.value, TRASH);
            folderIdMap.put(getFolder(JUNK).folderId.value, JUNK);
            folderIdMap.put(getFolder(UNSENT).folderId.value, UNSENT);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DavMailAuthenticationException("EXCEPTION_EWS_NOT_AVAILABLE");
        }
    }

    @Override
    public void createMessage(String folderPath, String messageName, HashMap<String, String> properties, String messageBody) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    public void updateMessage(Message message, Map<String, String> properties) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    public void deleteMessage(Message message) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    public void sendMessage(HashMap<String, String> properties, String messageBody) throws IOException {
        throw new UnsupportedOperationException();

    }

    @Override
    protected BufferedReader getContentReader(Message message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageList searchMessages(String folderName, List<String> attributes, Condition condition) throws IOException {
        throw new UnsupportedOperationException();
    }

    protected static class MultiCondition extends ExchangeSession.MultiCondition implements SearchExpression {
        protected MultiCondition(Operator operator, Condition... condition) {
            super(operator, condition);
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:").append(operator.toString()).append('>');

            for (Condition condition : conditions) {
                condition.appendTo(buffer);
            }

            buffer.append("</t:").append(operator.toString()).append('>');
        }
    }

    protected static class NotCondition extends ExchangeSession.NotCondition implements SearchExpression {
        protected NotCondition(Condition condition) {
            super(condition);
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:Not>");
            condition.appendTo(buffer);
            buffer.append("</t:Not>");
        }
    }

    static final Map<String, FieldURI> attributeMap = new HashMap<String, FieldURI>();

    static {
        attributeMap.put("folderclass", ExtendedFieldURI.PR_CONTAINER_CLASS);
        attributeMap.put("read", ExtendedFieldURI.PR_READ);
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
            FieldURI fieldURI = attributeMap.get(attributeName);
            if (fieldURI == null) {
                throw new IllegalArgumentException("Unknown field: " + attributeName);
            }
            return fieldURI;
        }

        @Override
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

    protected static class IsNullCondition extends ExchangeSession.Condition implements SearchExpression {
        protected String attributeName;

        protected IsNullCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:Not><t:Exists>");
            attributeMap.get(attributeName).appendTo(buffer);
            buffer.append("</t:Exists></t:Not>");
        }
    }

    @Override
    public MultiCondition and(Condition... condition) {
        return new MultiCondition(Operator.And, condition);
    }

    @Override
    public MultiCondition or(Condition... condition) {
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
    public Condition like(String attributeName, String value) {
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
        folder.folderId = new FolderId(item.get("FolderId"));
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
        try {
            httpClient.executeMethod(findFolderMethod);
        } finally {
            findFolderMethod.releaseConnection();
        }
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
    public EwsExchangeSession.Folder getFolder(String folderPath) throws IOException {
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ID_ONLY, getFolderId(folderPath), FOLDER_PROPERTIES);
        try {
            httpClient.executeMethod(getFolderMethod);
        } finally {
            getFolderMethod.releaseConnection();
        }
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
    public void createFolder(String folderName, String folderClass) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deleteFolder(String folderName) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void copyMessage(Message message, String targetFolder) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void moveFolder(String folderName, String targetName) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void moveToTrash(Message message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<Contact> searchContacts(String folderName, List<String> attributes, Condition condition) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<Event> searchEvents(String folderPath, List<String> attributes, Condition condition) throws IOException {
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
            }
        }
        return currentFolderId;
    }

    protected FolderId getSubFolderByName(FolderId parentFolderId, String folderName) throws IOException {
        FindFolderMethod findFolderMethod = new FindFolderMethod(
                FolderQueryTraversal.SHALLOW,
                BaseShape.ID_ONLY,
                parentFolderId,
                FOLDER_PROPERTIES,
                new TwoOperandExpression(TwoOperandExpression.Operator.IsEqualTo,
                        ExtendedFieldURI.PR_URL_COMP_NAME, folderName)
        );
        try {
            httpClient.executeMethod(findFolderMethod);
        } finally {
            findFolderMethod.releaseConnection();
        }
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        if (item == null) {
            throw new DavMailException("EXCEPTION_FOLDER_NOT_FOUND", folderName);
        }
        return new FolderId(item.get("FolderId"));
    }

}

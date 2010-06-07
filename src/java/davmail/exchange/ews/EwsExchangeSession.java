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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EWS Exchange adapter.
 * Compatible with Exchange 2007 and hopefully 2010.
 */
public class EwsExchangeSession extends ExchangeSession {

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
    }

    protected static class AttributeCondition extends ExchangeSession.AttributeCondition implements SearchExpression {
        protected AttributeCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append("<t:").append(operator.toString()).append('>');
            attributeMap.get(attributeName).appendTo(buffer);

            buffer.append("<t:FieldURIOrConstant><t:Constant Value=\"");
            buffer.append(StringUtil.xmlEncode(value));
            buffer.append("\"/></t:FieldURIOrConstant>");

            buffer.append("</t:").append(operator.toString()).append('>');
        }
    }

    @Override
    protected Condition and(Condition... condition) {
        return new MultiCondition(Operator.And, condition);
    }

    @Override
    protected Condition or(Condition... condition) {
        return new MultiCondition(Operator.Or, condition);
    }

    @Override
    protected Condition not(Condition condition) {
        return new NotCondition(condition);
    }

    @Override
    protected AttributeCondition equals(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, value);
    }

    protected Folder buildFolder(EWSMethod.Item item) {
        Folder folder = new Folder();
        folder.folderId = new FolderId(item.get("FolderId"));
        folder.folderClass = item.get(ExtendedFieldURI.PR_CONTAINER_CLASS.getPropertyTag());
        folder.etag = item.get(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME.getPropertyTag());
        // TODO: implement ctag
        folder.ctag = String.valueOf(System.currentTimeMillis());
        // TODO: implement contentClass, noInferiors
        folder.unreadCount = item.getInt("UnreadCount");
        folder.hasChildren = item.getInt("ChildFolderCount") != 0;
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
        FindFolderMethod findFolderMethod = new FindFolderMethod(FolderQueryTraversal.SHALLOW, BaseShape.ALL_PROPERTIES, parentFolderId);
        findFolderMethod.setSearchExpression((SearchExpression) condition);
        findFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_URL_COMP_NAME);
        findFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME);
        findFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_CONTAINER_CLASS);
        try {
            httpClient.executeMethod(findFolderMethod);
        } finally {
            findFolderMethod.releaseConnection();
        }
        for (EWSMethod.Item item : findFolderMethod.getResponseItems()) {
            Folder folder = buildFolder(item);
            if (parentFolderPath.length() > 0) {
                folder.folderPath = parentFolderPath + '/' + item.get(ExtendedFieldURI.PR_URL_COMP_NAME.getPropertyTag());
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
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ALL_PROPERTIES, getFolderId(folderPath));
        getFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_URL_COMP_NAME);
        getFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME);
        //getFolderMethod.addAdditionalProperty(new ExtendedFieldURI("0x65E2", ExtendedFieldURI.PropertyType.Binary));
        //getFolderMethod.addAdditionalProperty(new ExtendedFieldURI("00062040-0000-0000-C000-000000000046", 0x8A23, ExtendedFieldURI.PropertyType.SystemTime));
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

    protected static final String PUBLIC_ROOT = "/public";

    private FolderId getFolderId(String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;
        if (folderPath.startsWith("/public")) {
            currentFolderId  = DistinguishedFolderId.PUBLICFOLDERSROOT;
            folderNames = folderPath.substring(PUBLIC_ROOT.length()).split("/");
        } else {
            currentFolderId  = DistinguishedFolderId.MSGFOLDERROOT;
           folderNames = folderPath.split("/");
        }
        for (String folderName : folderNames) {
            if ("INBOX".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.INBOX;
            } else if ("Sent".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.SENTITEMS;
            } else if ("Drafts".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.DRAFTS;
            } else if ("Trash".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.DELETEDITEMS;
            } else if (folderName.length() > 0) {
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
                new TwoOperandExpression(TwoOperandExpression.Operator.IsEqualTo,
                        ExtendedFieldURI.PR_URL_COMP_NAME, folderName)
        );
        try {
            httpClient.executeMethod(findFolderMethod);
        } finally {
            findFolderMethod.releaseConnection();
        }
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        // TODO: handle not found error
        return new FolderId(item.get("FolderId"));
    }

}

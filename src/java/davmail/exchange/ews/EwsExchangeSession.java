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

import davmail.exception.DavMailException;
import davmail.exchange.ExchangeSession;
import org.apache.commons.httpclient.HttpMethod;

import java.io.IOException;
import java.util.List;

/**
 * EWS Exchange adapter.
 * Compatible with Exchange 2007 and hopefully 2010.
 */
public class EwsExchangeSession extends ExchangeSession {

    protected class EwsFolder extends Folder {
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
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<Folder> getSubFolders(String folderName, String filter, boolean recursive) throws IOException {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * @inheritDoc
     */
    @Override
    public Folder getFolder(String folderPath) throws IOException {
        GetFolderMethod getFolderMethod = new GetFolderMethod(BaseShape.ALL_PROPERTIES, getFolderId(folderPath));
        getFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_URL_COMP_NAME);
        getFolderMethod.addAdditionalProperty(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME);
        //getFolderMethod.addAdditionalProperty(new ExtendedFieldURI("0x65E2", ExtendedFieldURI.PropertyType.Binary));
        //getFolderMethod.addAdditionalProperty(new ExtendedFieldURI("00062040-0000-0000-C000-000000000046", 0x8A23, ExtendedFieldURI.PropertyType.SystemTime));

        httpClient.executeMethod(getFolderMethod);
        EWSMethod.Item item = getFolderMethod.getResponseItem();
        EwsFolder folder = null;
        if (item != null) {
            folder = new EwsFolder();
            folder.folderId = new FolderId(item.get("FolderId"));
            folder.folderName = folderPath;
            folder.etag = item.get(ExtendedFieldURI.PR_LAST_MODIFICATION_TIME.getPropertyTag());
            // TODO: implement ctag
            folder.ctag = String.valueOf(System.currentTimeMillis());
            // TODO: implement contentClass, unreadCount, hasChildren, noInferiors
        }
        return folder;
    }


    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId currentFolderId = DistinguishedFolderId.MSGFOLDERROOT;
        String[] folderNames = folderPath.split("/");
        for (String folderName : folderNames) {
            if ("INBOX".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.INBOX;
            } else if ("Sent".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.SENTITEMS;
            } else if ("Drafts".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.DRAFTS;
            } else if ("Trash".equals(folderName)) {
                currentFolderId = DistinguishedFolderId.DELETEDITEMS;
            } else {
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
        httpClient.executeMethod(findFolderMethod);
        EWSMethod.Item item = findFolderMethod.getResponseItem();
        return new FolderId(item.get("Id"));
    }

}

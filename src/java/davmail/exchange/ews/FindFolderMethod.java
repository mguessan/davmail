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

/**
 * EWS Find Folder.
 */
public class FindFolderMethod extends EWSMethod {

    /**
     * Find Exchange Folder.
     *
     * @param traversal      traversal type
     * @param baseShape      base shape
     * @param parentFolderId parent folder id
     */
    public FindFolderMethod(FolderQueryTraversal traversal, BaseShape baseShape, FolderId parentFolderId) {
        super("Folder", "FindFolder");
        this.traversal = traversal;
        this.baseShape = baseShape;
        this.parentFolderId = parentFolderId;
    }

    /**
     * Find Exchange Folder.
     *
     * @param traversal        traversal type
     * @param baseShape        base shape
     * @param parentFolderId   parent folder id
     * @param searchExpression search expression
     */
    public FindFolderMethod(FolderQueryTraversal traversal, BaseShape baseShape, FolderId parentFolderId,
                            SearchExpression searchExpression) {
        this(traversal, baseShape, parentFolderId);
        this.searchExpression = searchExpression;
    }
}

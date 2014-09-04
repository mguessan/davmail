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

import java.util.Set;

/**
 * EWS Find Folder.
 */
public class FindFolderMethod extends EWSMethod {

    /**
     * Find Exchange Folder.
     *
     * @param traversal            traversal type
     * @param baseShape            base shape
     * @param parentFolderId       parent folder id
     * @param additionalProperties folder properties
     * @param offset         start offset
     * @param maxCount       maximum result count
     */
    public FindFolderMethod(FolderQueryTraversal traversal, BaseShape baseShape, FolderId parentFolderId,
                            Set<FieldURI> additionalProperties, int offset, int maxCount) {
        super("Folder", "FindFolder");
        this.traversal = traversal;
        this.baseShape = baseShape;
        this.parentFolderId = parentFolderId;
        this.additionalProperties = additionalProperties;
        this.offset = offset;
        this.maxCount = maxCount;
    }

    /**
     * Find Exchange Folder.
     *
     * @param traversal            traversal type
     * @param baseShape            base shape
     * @param parentFolderId       parent folder id
     * @param additionalProperties folder properties
     * @param searchExpression     search expression
     * @param offset         start offset
     * @param maxCount       maximum result count
     */
    public FindFolderMethod(FolderQueryTraversal traversal, BaseShape baseShape, FolderId parentFolderId,
                            Set<FieldURI> additionalProperties, SearchExpression searchExpression, int offset, int maxCount) {
        this(traversal, baseShape, parentFolderId, additionalProperties, offset, maxCount);
        this.searchExpression = searchExpression;
    }
}

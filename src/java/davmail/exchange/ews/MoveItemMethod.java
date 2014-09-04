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

import java.util.List;

/**
 * Move Item method.
 */
public class MoveItemMethod extends EWSMethod {
    /**
     * Move item to target folder.
     *
     * @param itemId     item id
     * @param toFolderId target folder id
     */
    public MoveItemMethod(ItemId itemId, FolderId toFolderId) {
        super("Item", "MoveItem");
        this.itemId = itemId;
        this.toFolderId = toFolderId;
    }

    /**
     * Move items to target folder.
     *
     * @param itemIds    item id list
     * @param toFolderId target folder id
     */
    public MoveItemMethod(List<ItemId> itemIds, FolderId toFolderId) {
        super("Item", "MoveItem");
        this.itemIds = itemIds;
        this.toFolderId = toFolderId;
    }
}

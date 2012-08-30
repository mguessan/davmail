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

import java.io.IOException;
import java.io.Writer;

/**
 * Item id.
 */
public class ItemId {
    protected final String name;
    protected final String id;
    protected final String changeKey;

    /**
     * Build Item id from response item.
     *
     * @param item response item
     */
    public ItemId(EWSMethod.Item item) {
        this("ItemId", item);
    }

    /**
     * Build Item id object from item id.
     *
     * @param itemId item id
     */
    public ItemId(String itemId) {
        this("ItemId", itemId);
    }

    /**
     * Build Item id from response item.
     *
     * @param item response item
     */
    public ItemId(String name, EWSMethod.Item item) {
        this.name = name;
        this.id = item.get("ItemId");
        this.changeKey = item.get("ChangeKey");
    }

    /**
     * Build Item id object from item id.
     *
     * @param itemId item id
     */
    public ItemId(String name, String itemId) {
        this.name = name;
        this.id = itemId;
        this.changeKey = null;
    }

    /**
     * Build Item id object from item id and change key.
     *
     * @param itemId item id
     */
    public ItemId(String name, String itemId, String changeKey) {
        this.name = name;
        this.id = itemId;
        this.changeKey = changeKey;
    }

    /**
     * Write item id as XML.
     *
     * @param writer request writer
     * @throws IOException on error
     */
    public void write(Writer writer) throws IOException {
        writer.write("<t:");
        writer.write(name);
        writer.write(" Id=\"");
        writer.write(id);
        if (changeKey != null) {
            writer.write("\" ChangeKey=\"");
            writer.write(changeKey);
        }
        writer.write("\"/>");
    }
}

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
import java.util.HashSet;
import java.util.Set;

/**
 * Field update with multiple values.
 */
public class IndexedFieldUpdate extends FieldUpdate {
    final Set<FieldUpdate> updates = new HashSet<FieldUpdate>();
    protected final String collectionName;

    /**
     * Create indexed field update object.
     *
     * @param collectionName values collection name e.g. EmailAddresses
     */
    public IndexedFieldUpdate(String collectionName) {
        this.collectionName = collectionName;
    }

    /**
     * Add indexed field value.
     *
     * @param fieldUpdate field update object
     */
    public void addFieldValue(FieldUpdate fieldUpdate) {
        updates.add(fieldUpdate);
    }

    /**
     * Write field to request writer.
     *
     * @param itemType item type
     * @param writer   request writer
     * @throws IOException on error
     */
    @Override
    public void write(String itemType, Writer writer) throws IOException {
        if (itemType == null) {
            // use collection name on create
            writer.write("<t:");
            writer.write(collectionName);
            writer.write(">");

            StringBuilder buffer = new StringBuilder();
            for (FieldUpdate fieldUpdate : updates) {
                fieldUpdate.fieldURI.appendValue(buffer, null, fieldUpdate.value);
            }
            writer.write(buffer.toString());

            writer.write("</t:");
            writer.write(collectionName);
            writer.write(">");
        } else {
            // on update, write each fieldupdate
            for (FieldUpdate fieldUpdate : updates) {
                fieldUpdate.write(itemType, writer);
            }
        }
    }

}

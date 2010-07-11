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
 * Field update
 */
public class FieldUpdate {
    FieldURI fieldURI;
    String value;

    /**
     * Create field update with value.
     *
     * @param fieldURI target field
     * @param value    field value
     */
    public FieldUpdate(FieldURI fieldURI, String value) {
        this.fieldURI = fieldURI;
        this.value = value;
    }

    /**
     * Write field to request writer.
     *
     * @param itemType item type
     * @param writer   request writer
     * @throws IOException on error
     */
    public void write(String itemType, Writer writer) throws IOException {
        String action;
        //noinspection VariableNotUsedInsideIf
        if (value == null) {
            action = "Delete";
        } else {
            action = "Set";
        }
        if (itemType != null) {
            writer.write("<t:");
            writer.write(action);
            writer.write(itemType);
            writer.write("Field>");
        }

        StringBuilder buffer = new StringBuilder();
        if (value == null) {
            fieldURI.appendTo(buffer);
        } else {
            fieldURI.appendValue(buffer, itemType, value);
        }
        writer.write(buffer.toString());

        if (itemType != null) {
            writer.write("</t:");
            writer.write(action);
            writer.write(itemType);
            writer.write("Field>");
        }
    }
}

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
import java.util.ArrayList;

/**
 * Specific field update class to handle multiple attendee values
 */
public class MultiValuedFieldUpdate extends FieldUpdate {
    ArrayList<String> values = new ArrayList<>();

    /**
     * Create field update with value.
     *
     * @param fieldURI target field
     */
    public MultiValuedFieldUpdate(FieldURI fieldURI) {
        this.fieldURI = fieldURI;
    }

    /**
     * Add single value
     *
     * @param value value
     */
    public void addValue(String value) {
        values.add(value);
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
        String action;
        //noinspection VariableNotUsedInsideIf
        if (values.isEmpty()) {
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

        // do not try to set empty value on create
        if (itemType != null || (!values.isEmpty())) {
            StringBuilder buffer = new StringBuilder();
            ((UnindexedFieldURI)fieldURI).appendValues(buffer, itemType, values);
            writer.write(buffer.toString());
        }

        if (itemType != null) {
            writer.write("</t:");
            writer.write(action);
            writer.write(itemType);
            writer.write("Field>");
        }
    }

}

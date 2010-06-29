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
 * Unindexed Field URI
 */
public class UnindexedFieldURI implements FieldURI {
    protected final String fieldURI;
    protected final String fieldName;

    public UnindexedFieldURI(String fieldURI) {
        this.fieldURI = fieldURI;
        int colonIndex = fieldURI.indexOf(':');
        if (colonIndex < 0) {
            fieldName = fieldURI;
        } else {
            fieldName = fieldURI.substring(colonIndex+1);
        }
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:FieldURI FieldURI=\"").append(fieldURI).append("\"/>");
    }

    public void appendValue(StringBuilder buffer, String itemType, String value) {
        appendTo(buffer);
        buffer.append("<t:");
        buffer.append(itemType);
        buffer.append('>');
        buffer.append("<t:");
        buffer.append(fieldName);
        buffer.append('>');
        buffer.append(value);
        buffer.append("</t:");
        buffer.append(fieldName);
        buffer.append('>');
        buffer.append("</t:");
        buffer.append(itemType);
        buffer.append('>');
    }

    public static final UnindexedFieldURI DATE_TIME_SENT = new UnindexedFieldURI("item:DateTimeSent");
    public static final UnindexedFieldURI FOLDER_DISPLAYNAME = new UnindexedFieldURI("folder:DisplayName");

}

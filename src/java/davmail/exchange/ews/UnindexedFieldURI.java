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

import davmail.util.StringUtil;

/**
 * Unindexed Field URI
 */
public class UnindexedFieldURI implements FieldURI {
    protected final String fieldURI;
    protected final String fieldName;

    /**
     * Create unindexed field URI.
     *
     * @param fieldURI field name
     */
    public UnindexedFieldURI(String fieldURI) {
        this.fieldURI = fieldURI;
        int colonIndex = fieldURI.indexOf(':');
        if (colonIndex < 0) {
            fieldName = fieldURI;
        } else {
            fieldName = fieldURI.substring(colonIndex + 1);
        }
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:FieldURI FieldURI=\"").append(fieldURI).append("\"/>");
    }

    public void appendValue(StringBuilder buffer, String itemType, String value) {
        if (fieldURI.startsWith("message") && itemType != null) {
            itemType = "Message";
        } else if (fieldURI.startsWith("calendar") && itemType != null) {
            itemType = "CalendarItem";
        }
        if (itemType != null) {
            appendTo(buffer);
            buffer.append("<t:");
            buffer.append(itemType);
            buffer.append('>');
        }
        if ("MeetingTimeZone".equals(fieldName)) {
            buffer.append("<t:MeetingTimeZone TimeZoneName=\"");
            buffer.append(StringUtil.xmlEncodeAttribute(value));
            buffer.append("\"></t:MeetingTimeZone>");
        } else if ("StartTimeZone".equals(fieldName)) {
            buffer.append("<t:StartTimeZone Id=\"");
            buffer.append(StringUtil.xmlEncodeAttribute(value));
            buffer.append("\"></t:StartTimeZone>");
        } else {
            buffer.append("<t:");
            buffer.append(fieldName);
            buffer.append('>');
            buffer.append(StringUtil.xmlEncodeAttribute(value));
            buffer.append("</t:");
            buffer.append(fieldName);
            buffer.append('>');
        }
        if (itemType != null) {
            buffer.append("</t:");
            buffer.append(itemType);
            buffer.append('>');
        }
    }

    public String getResponseName() {
        return fieldName;
    }

}

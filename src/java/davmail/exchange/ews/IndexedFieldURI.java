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
 * Indexed FieldURI
 */
public class IndexedFieldURI implements FieldURI {
    protected final String fieldURI;
    protected final String fieldIndex;

    /**
     * Create indexed field uri.
     *
     * @param fieldURI   base field uri
     * @param fieldIndex field name
     */
    public IndexedFieldURI(String fieldURI, String fieldIndex) {
        this.fieldURI = fieldURI;
        this.fieldIndex = fieldIndex;
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:IndexedFieldURI FieldURI=\"").append(fieldURI);
        buffer.append("\" FieldIndex=\"").append(fieldIndex);
        buffer.append("\"/>");
    }

    public void appendValue(StringBuilder buffer, String itemType, String value) {
        buffer.append("<t:Entry Key=\"").append(fieldIndex).append("\">");
        buffer.append(StringUtil.xmlEncode(value));
        buffer.append("</t:Entry>");
    }

    public String getResponseName() {
        return fieldIndex;
    }
}

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

import java.util.HashMap;
import java.util.Map;

/**
 * EWS MAPI fields;
 */
public class Field {
    protected static final Map<String, FieldURI> FIELD_MAP = new HashMap<String, FieldURI>();

    static {
        FIELD_MAP.put("uid", new ExtendedFieldURI(0x300b, ExtendedFieldURI.PropertyType.Binary));
        FIELD_MAP.put("messageFlags", new ExtendedFieldURI(0x0e07, ExtendedFieldURI.PropertyType.Integer));//PR_MESSAGE_FLAGS
        FIELD_MAP.put("imapUid", new ExtendedFieldURI(0x0e23, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("flagStatus", new ExtendedFieldURI(0x1090, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("lastVerbExecuted", new ExtendedFieldURI(0x1081, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("read", new ExtendedFieldURI(0x0e69, ExtendedFieldURI.PropertyType.Boolean));
        FIELD_MAP.put("messageSize", new ExtendedFieldURI(0x0e08, ExtendedFieldURI.PropertyType.Long));
        FIELD_MAP.put("date", new ExtendedFieldURI(0x0e06, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("deleted", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Common, 0x8570, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("junk", new ExtendedFieldURI(0x1083, ExtendedFieldURI.PropertyType.Long));

        FIELD_MAP.put("iconIndex", new ExtendedFieldURI(0x1080, ExtendedFieldURI.PropertyType.Long));// PR_ICON_INDEX
        FIELD_MAP.put("datereceived", new ExtendedFieldURI(0x0e06, ExtendedFieldURI.PropertyType.SystemTime));// PR_MESSAGE_DELIVERY_TIME
        FIELD_MAP.put("bcc", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "bcc"));

        FIELD_MAP.put("folderclass", new ExtendedFieldURI(0x3613, ExtendedFieldURI.PropertyType.String));
    }

    /**
     * Get Field by alias.
     *
     * @param alias field alias
     * @return field
     */
    public static FieldURI get(String alias) {
        FieldURI field = FIELD_MAP.get(alias);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field: " + alias);
        }
        return field;
    }

    /**
     * Get Mime header field.
     *
     * @param headerName header name
     * @return field object
     */
    public static FieldURI getHeader(String headerName) {
        return new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, headerName);
    }

    /**
     * Create property update field
     *
     * @param alias property alias
     * @param value property value
     * @return field update
     */
    public static FieldUpdate createFieldUpdate(String alias, String value) {
        return new FieldUpdate(Field.get(alias), value);
    }

}

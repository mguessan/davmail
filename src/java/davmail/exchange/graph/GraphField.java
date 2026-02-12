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

package davmail.exchange.graph;

import davmail.exchange.ews.ExtendedFieldURI;

import java.util.HashMap;
import java.util.Map;

/**
 * Map field names to actual graph properties.
 * Properties can be native graph properties or extended properties mapped from MAPI ids.
 * Some properties are searchable only through MAPI, others are straightforward.
 */
public class GraphField {

    @SuppressWarnings({"UnusedDeclaration"})
    protected enum PropertyType {
        ApplicationTime, ApplicationTimeArray, Binary, BinaryArray, Boolean, CLSID, CLSIDArray, Currency, CurrencyArray,
        Double, DoubleArray, Error, Float, FloatArray, Integer, IntegerArray, Long, LongArray, Null, Object,
        ObjectArray, Short, ShortArray, SystemTime, SystemTimeArray, String, StringArray
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public enum DistinguishedPropertySetType {
        Meeting, Appointment, Common, PublicStrings, Address, InternetHeaders, CalendarAssistant, UnifiedMessaging, Task
    }

    private static final Map<String, GraphField> FIELD_MAP = new HashMap<>();

    static {
        // folder extended properties
        FIELD_MAP.put("lastmodified", new GraphField("lastmodified", 0x3008, PropertyType.SystemTime));
        FIELD_MAP.put("folderclass", new GraphField("folderclass", 0x3613, PropertyType.String));
        FIELD_MAP.put("ctag", new GraphField("ctag", 0x670a, PropertyType.SystemTime)); // PR_LOCAL_COMMIT_TIME_MAX
        FIELD_MAP.put("uidNext", new GraphField("uidNext", 0x6751, PropertyType.Integer)); // PR_ARTICLE_NUM_NEXT

        // message extended properties
        FIELD_MAP.put("uid", new GraphField("uid", 0x0FF9, PropertyType.Binary)); // PR_RECORD_KEY
        FIELD_MAP.put("messageFlags", new GraphField("messageFlags", 0x0e07, PropertyType.Integer)); // PR_MESSAGE_FLAGS
        FIELD_MAP.put("imapUid", new GraphField("imapUid", 0x0e23, PropertyType.Integer));
        FIELD_MAP.put("messageSize", new GraphField("messageSize", 0x0e08, PropertyType.Integer));
        FIELD_MAP.put("etag", new GraphField("etag", 0x3008, PropertyType.SystemTime));
        FIELD_MAP.put("contentclass", new GraphField("contentclass", DistinguishedPropertySetType.InternetHeaders, "content-class"));

        // TODO test this
        FIELD_MAP.put("permanenturl", new GraphField("permanenturl", 0x670E, PropertyType.String)); //PR_FLAT_URL_NAME
        FIELD_MAP.put("lastVerbExecuted", new GraphField("lastVerbExecuted", 0x1081, PropertyType.Integer));
        FIELD_MAP.put("junk", new GraphField("junk", 0x1083, PropertyType.Integer));
        FIELD_MAP.put("flagStatus", new GraphField("flagStatus", 0x1090, PropertyType.Integer));
        FIELD_MAP.put("deleted", new GraphField("deleted", DistinguishedPropertySetType.Common, 0x8570, PropertyType.Integer)); // PidLidImapDeleted

        FIELD_MAP.put("urlcompname", new GraphField("urlcompname", 0x10f3, PropertyType.String));
    }

    protected String alias;
    protected String graphId;

    protected String propertyName;
    protected int propertyId;
    protected String propertyTag;
    protected PropertyType propertyType;

    protected DistinguishedPropertySetType distinguishedPropertySetId;

    private boolean extended = false;

    private boolean indexed;

    /**
     * Basic graph field.
     * @param alias property alias
     */
    public GraphField(String alias) {
        this.alias = alias;
    }

    /**
     * Header field or categories field.
     * @param alias property alias
     * @param distinguishedPropertySetId property type
     */
    public GraphField(String alias, DistinguishedPropertySetType distinguishedPropertySetId, String propertyName) {
        this.alias = alias;
        this.propertyType = PropertyType.String;
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyName = propertyName;
        this.extended = true;
        this.graphId = buildGraphId();
    }

    /**
     * Create extended field.
     *
     * @param alias          property alias
     * @param intPropertyTag property tag as int
     * @param propertyType   property type
     */
    protected GraphField(String alias, int intPropertyTag, PropertyType propertyType) {
        this.alias = alias;
        this.extended = true;
        this.propertyTag = "0x" + Integer.toHexString(intPropertyTag);
        this.propertyType = propertyType;
        this.graphId = buildGraphId();
    }

    public GraphField(String alias, DistinguishedPropertySetType distinguishedPropertySetId, int propertyId, PropertyType propertyType) {
        this.alias = alias;
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyType = propertyType;
        this.propertyId = propertyId;
        this.extended = true;
        this.graphId = buildGraphId();
    }

    private String buildGraphId() {
        // PropertyId values may only be in one of the following formats:
        // 'MapiPropertyType namespaceGuid Name propertyName', 'MapiPropertyType namespaceGuid Id propertyId' or 'MapiPropertyType propertyTag'.

        String namespaceGuid = null;
        if (distinguishedPropertySetId != null) {
            switch (distinguishedPropertySetId) {
                case PublicStrings:
                    namespaceGuid = "{00020329-0000-0000-c000-000000000046}";
                    break;
                case InternetHeaders:
                    namespaceGuid = "{00020386-0000-0000-c000-000000000046}";
                    break;
                case Common:
                    namespaceGuid = "{00062008-0000-0000-c000-000000000046}";
                    break;
                case Address:
                    namespaceGuid = "{00062004-0000-0000-c000-000000000046}";
                    break;
                case Task:
                    namespaceGuid = "{00062003-0000-0000-c000-000000000046}";
                    break;
            }
        }


        StringBuilder buffer = new StringBuilder();
        if (namespaceGuid != null) {
            buffer.append(propertyType.name()).append(" ").append(namespaceGuid);
            if (propertyName != null) {
                buffer.append(" Name ").append(propertyName);
            } else {
                buffer.append(" Id ").append("0x").append(Integer.toHexString(propertyId));
            }
        } else if (propertyTag != null) {
            buffer.append(propertyType.name()).append(" ").append(propertyTag);
        }
        return buffer.toString();
    }

    public boolean isExtended() {
        return extended;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public String getGraphId() {
        if (graphId != null) {
            return graphId;
        }
        throw new IllegalStateException("Graph id not set on field " + alias);
        //return alias;
    }

    public boolean isMultiValued() {
        return propertyType == PropertyType.StringArray;
    }

    public boolean isNumber() {
        return propertyType == PropertyType.Short || propertyType == PropertyType.Integer || propertyType == PropertyType.Long || propertyType == PropertyType.Double;
    }

    public boolean isBinary() {
        return propertyType == PropertyType.Binary;
    }

    public boolean isBoolean() {
        return propertyType == PropertyType.Boolean;
    }


    /**
     * Get field by alias.
     * @param alias property alias
     * @return field definition
     */
    public static GraphField get(String alias) {
        return FIELD_MAP.computeIfAbsent(alias, GraphField::new);
    }

    public static String getGraphId(String alias) {
        return get(alias).getGraphId();
    }

}

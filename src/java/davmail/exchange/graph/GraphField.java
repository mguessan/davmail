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
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Map field names to actual graph properties.
 * Properties can be native graph properties or extended properties mapped from MAPI ids.
 * Some properties are searchable only through MAPI, others are straightforward.
 */
public class GraphField {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.graph.GraphField");

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
        addFieldMap("id");

        // folder extended properties
        addFieldMap("lastmodified", 0x3008, PropertyType.SystemTime);
        addFieldMap("folderclass", 0x3613, PropertyType.String);
        addFieldMap("ctag", 0x670a, PropertyType.SystemTime); // PR_LOCAL_COMMIT_TIME_MAX
        addFieldMap("uidNext", 0x6751, PropertyType.Integer); // PR_ARTICLE_NUM_NEXT

        // message extended properties
        addFieldMap("uid", 0x0FF9, PropertyType.Binary); // PR_RECORD_KEY
        addFieldMap("messageFlags", 0x0e07, PropertyType.Integer); // PR_MESSAGE_FLAGS
        addFieldMap("imapUid", 0x0e23, PropertyType.Integer);
        addFieldMap("messageSize", 0x0e08, PropertyType.Integer);
        addFieldMap("etag", 0x3008, PropertyType.SystemTime);
        addFieldMap("contentclass", DistinguishedPropertySetType.InternetHeaders, "content-class");

        addFieldMap("keywords", "keywords"); // special case, mapped to categories array

        // TODO test this
        addFieldMap("@odata.etag");

        addFieldMap("read", "isRead");
        addFieldMap("messageheaders", 0x007D, PropertyType.String);
        addFieldMap("to", DistinguishedPropertySetType.InternetHeaders, "to");
        addFieldMap("date", 0x0e06, PropertyType.SystemTime);

        addFieldMap("permanenturl", 0x670E, PropertyType.String); //PR_FLAT_URL_NAME
        addFieldMap("lastVerbExecuted", 0x1081, PropertyType.Integer);
        addFieldMap("junk", 0x1083, PropertyType.Integer);
        addFieldMap("flagStatus", 0x1090, PropertyType.Integer);
        addFieldMap("deleted", DistinguishedPropertySetType.Common, 0x8570, PropertyType.Integer); // PidLidImapDeleted

        addFieldMap("urlcompname", 0x10f3, PropertyType.String);

        // contacts https://learn.microsoft.com/en-us/graph/api/resources/contact
        addFieldMap("displayname", "displayName"); // MAPI addFieldMap("displayname", 0x3001, PropertyType.String);

        addFieldMap("cn", "displayName");
        addFieldMap("sn", "surname");
        addFieldMap("givenName", "givenName");
        addFieldMap("middlename", "middleName");
        addFieldMap("personaltitle", "title"); // MAPI addFieldMap("personaltitle", 0x3A45, PropertyType.String);
        addFieldMap("title", "jobTitle"); // MAPI /addFieldMap("title", 0x3A17, PropertyType.String);

        addFieldMap("description", "personalNotes");// MAPI addFieldMap("description", 0x1000, PropertyType.String);

        addFieldMap("namesuffix", "generation");
        addFieldMap("nickname", "nickName");
        addFieldMap("mobile", 0x3A1C, PropertyType.String);
        addFieldMap("telephoneNumber", 0x3A08, PropertyType.String);
        addFieldMap("facsimiletelephonenumber", 0x3A24, PropertyType.String);
        addFieldMap("pager", 0x3A21, PropertyType.String);

        addFieldMap("homeCity", 0x3A59, PropertyType.String);
        addFieldMap("homeCountry", 0x3A5A, PropertyType.String);
        addFieldMap("homePhone", 0x3A09, PropertyType.String);
        addFieldMap("homePostalCode", 0x3A5B, PropertyType.String);
        addFieldMap("homeState", 0x3A5C, PropertyType.String);
        addFieldMap("homeStreet", 0x3A5D, PropertyType.String);
        addFieldMap("homepostofficebox", 0x3A5E, PropertyType.String);

        addFieldMap("postofficebox", DistinguishedPropertySetType.Address, 0x804A, PropertyType.String);
        addFieldMap("roomnumber", "officeLocation"); // MAPI addFieldMap("roomnumber", 0x3A19, PropertyType.String);
        addFieldMap("street", DistinguishedPropertySetType.Address, 0x8045, PropertyType.String);

        addFieldMap("l", DistinguishedPropertySetType.Address, 0x8046, PropertyType.String);
        addFieldMap("st", DistinguishedPropertySetType.Address, 0x8047, PropertyType.String);
        addFieldMap("postalcode", DistinguishedPropertySetType.Address, 0x8048, PropertyType.String);
        addFieldMap("co", DistinguishedPropertySetType.Address, 0x8049, PropertyType.String);

        // addFieldMap("o", DistinguishedPropertySetType.Address, 0x3A16, PropertyType.String); ??
        //addFieldMap("o", DistinguishedPropertySetType.Address, 0x3A18, PropertyType.String); // department
        addFieldMap("o", "companyName"); // department

        addFieldMap("department", "department");// MAPI addFieldMap("department",  0x3A18, PropertyType.String);

        addFieldMap("businesshomepage", 0x3A51, PropertyType.String);
        addFieldMap("personalHomePage", 0x3A50, PropertyType.String);


        addFieldMap("extensionattribute1", DistinguishedPropertySetType.Address, 0x804F, PropertyType.String);
        addFieldMap("extensionattribute2", DistinguishedPropertySetType.Address, 0x8050, PropertyType.String);
        addFieldMap("extensionattribute3", DistinguishedPropertySetType.Address, 0x8051, PropertyType.String);
        addFieldMap("extensionattribute4", DistinguishedPropertySetType.Address, 0x8052, PropertyType.String);

        addFieldMap("bday", "birthday"); // MAPI addFieldMap("bday", DistinguishedPropertySetType.Address, 0x3A42, PropertyType.SystemTime);
        addFieldMap("anniversary", "weddingAnniversary");  // MAPI addFieldMap("anniversary", DistinguishedPropertySetType.Address, 0x3A41, PropertyType.SystemTime);

        addFieldMap("otherstreet", 0x3A63, PropertyType.String);
        addFieldMap("otherstate", 0x3A62, PropertyType.String);
        addFieldMap("otherpostofficebox", 0x3A64, PropertyType.String);
        addFieldMap("otherpostalcode", 0x3A61, PropertyType.String);
        addFieldMap("othercountry", 0x3A60, PropertyType.String);
        addFieldMap("othercity", 0x3A5F, PropertyType.String);

        addFieldMap("secretarycn", "assistantName"); // MAPI addFieldMap("secretarycn", 0x3A30, PropertyType.String);
        addFieldMap("spousecn", "spouseName"); // MAPI addFieldMap("spousecn", 0x3A48, PropertyType.String);

        addFieldMap("private", DistinguishedPropertySetType.Common, 0x8506, PropertyType.Boolean);

        addFieldMap("im", DistinguishedPropertySetType.Address, 0x8062, PropertyType.String);
        addFieldMap("fburl", DistinguishedPropertySetType.Address, 0x80D8, PropertyType.String);

        addFieldMap("haspicture", DistinguishedPropertySetType.Address, 0x8015, PropertyType.Boolean);

        addFieldMap("manager");// MAPI addFieldMap("manager", 0x3A4E, PropertyType.String);
        addFieldMap("profession"); // MAPI addFieldMap("profession", 0x3A46, PropertyType.String);

        // addFieldMap("keywords", DistinguishedPropertySetType.PublicStrings, "Keywords", PropertyType.StringArray); // TODO multivalued

        addFieldMap("othermobile", 0x3A1E, PropertyType.String);
        addFieldMap("otherTelephone", 0x3A21, PropertyType.String);

        addFieldMap("gender");// MAPI addFieldMap("gender", 0x3A4D, PropertyType.Short);

        addFieldMap("sensitivity", 0x0036, PropertyType.Integer);

        // does not map to anything over graph
        addFieldMap("msexchangecertificate");
        addFieldMap("usersmimecertificate");

    }

    protected static void addFieldMap(String alias, GraphField field) {
        if (FIELD_MAP.containsKey(alias)) {
            throw new IllegalArgumentException("Duplicate field alias: " + alias);
        }
        FIELD_MAP.put(alias, field);
    }

    protected static void addFieldMap(String alias) {
        addFieldMap(alias, new GraphField(alias));
    }

    protected static void addFieldMap(String alias, String graphId) {
        addFieldMap(alias, new GraphField(alias, graphId));
    }

    protected static void addFieldMap(String alias, int intPropertyTag, PropertyType propertyType) {
        addFieldMap(alias, new GraphField(alias, intPropertyTag, propertyType));
    }

    protected static void addFieldMap(String alias, DistinguishedPropertySetType distinguishedPropertySetId, int intPropertyTag, PropertyType propertyType) {
        addFieldMap(alias, new GraphField(alias, distinguishedPropertySetId, intPropertyTag, propertyType));
    }

    protected static void addFieldMap(String alias, DistinguishedPropertySetType distinguishedPropertySetId, String propertyName) {
        addFieldMap(alias, new GraphField(alias, distinguishedPropertySetId, propertyName));
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
        this.graphId = alias;
    }

    public GraphField(String alias, String graphId) {
        this.alias = alias;
        this.graphId = graphId;
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

    protected GraphField(String alias, DistinguishedPropertySetType distinguishedPropertySetId, int propertyId, PropertyType propertyType) {
        this.alias = alias;
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyType = propertyType;
        this.propertyId = propertyId;
        this.extended = true;
        this.graphId = buildGraphId();
    }

    protected GraphField(String alias, DistinguishedPropertySetType distinguishedPropertySetId, String propertyName, PropertyType propertyType) {
        this.alias = alias;
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyType = propertyType;
        this.propertyName = propertyName;
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
        if (!FIELD_MAP.containsKey(alias)) {
            LOGGER.warn("Missing mapping for " + alias);
            return new GraphField(alias);
        }
        return FIELD_MAP.get(alias);
    }

    public static String getGraphId(String alias) {
        return get(alias).getGraphId();
    }

}

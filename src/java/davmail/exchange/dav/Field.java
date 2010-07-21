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
package davmail.exchange.dav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebDav Field
 */
public class Field {

    protected static final Map<DistinguishedPropertySetType, String> distinguishedPropertySetMap = new HashMap<DistinguishedPropertySetType, String>();

    static {
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.Meeting, "6ed8da90-450b-101b-98da-00aa003f1305");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.Appointment, "00062002-0000-0000-c000-000000000046");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.Common, "00062008-0000-0000-c000-000000000046");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.PublicStrings, "00020329-0000-0000-c000-000000000046");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.Address, "00062004-0000-0000-c000-000000000046");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.InternetHeaders, "00020386-0000-0000-c000-000000000046");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.UnifiedMessaging, "4442858e-a9e3-4e80-b900-317a210cc15b");
        distinguishedPropertySetMap.put(DistinguishedPropertySetType.Task, "00062003-0000-0000-c000-000000000046");
    }

    protected static final Namespace EMPTY = Namespace.getNamespace("");
    protected static final Namespace XML = Namespace.getNamespace("xml:");
    protected static final Namespace DAV = Namespace.getNamespace("DAV:");
    protected static final Namespace URN_SCHEMAS_HTTPMAIL = Namespace.getNamespace("urn:schemas:httpmail:");
    protected static final Namespace URN_SCHEMAS_MAILHEADER = Namespace.getNamespace("urn:schemas:mailheader:");

    protected static final Namespace SCHEMAS_EXCHANGE = Namespace.getNamespace("http://schemas.microsoft.com/exchange/");
    protected static final Namespace SCHEMAS_MAPI = Namespace.getNamespace("http://schemas.microsoft.com/mapi/");
    protected static final Namespace SCHEMAS_MAPI_PROPTAG = Namespace.getNamespace("http://schemas.microsoft.com/mapi/proptag/");
    protected static final Namespace SCHEMAS_MAPI_ID = Namespace.getNamespace("http://schemas.microsoft.com/mapi/id/");
    protected static final Namespace SCHEMAS_MAPI_STRING = Namespace.getNamespace("http://schemas.microsoft.com/mapi/string/");
    protected static final Namespace SCHEMAS_REPL = Namespace.getNamespace("http://schemas.microsoft.com/repl/");
    protected static final Namespace URN_SCHEMAS_CONTACTS = Namespace.getNamespace("urn:schemas:contacts:");
    protected static final Namespace URN_SCHEMAS_CALENDAR = Namespace.getNamespace("urn:schemas:calendar:");

    protected static final Namespace SCHEMAS_MAPI_STRING_INTERNET_HEADERS =
            Namespace.getNamespace(SCHEMAS_MAPI_STRING.getURI() +
                    '{' + distinguishedPropertySetMap.get(DistinguishedPropertySetType.InternetHeaders) + "}/");


    /**
     * Property type list from EWS
     */
    @SuppressWarnings({"UnusedDeclaration"})
    protected static enum PropertyType {
        ApplicationTime, ApplicationTimeArray, Binary, BinaryArray, Boolean, CLSID, CLSIDArray, Currency, CurrencyArray,
        Double, DoubleArray, Error, Float, FloatArray, Integer, IntegerArray, Long, LongArray, Null, Object,
        ObjectArray, Short, ShortArray, SystemTime, SystemTimeArray, String, StringArray
    }

    protected static final Map<PropertyType, String> propertyTypeMap = new HashMap<PropertyType, String>();

    static {
        propertyTypeMap.put(PropertyType.Long, "0003");
        propertyTypeMap.put(PropertyType.Boolean, "000b");
        propertyTypeMap.put(PropertyType.SystemTime, "0040");
        propertyTypeMap.put(PropertyType.String, "001f"); // 001f is PT_UNICODE_STRING, 001E is PT_STRING
        propertyTypeMap.put(PropertyType.Binary, "0102");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    protected static enum DistinguishedPropertySetType {
        Meeting, Appointment, Common, PublicStrings, Address, InternetHeaders, CalendarAssistant, UnifiedMessaging, Task
    }


    protected static final Map<String, Field> fieldMap = new HashMap<String, Field>();

    static {
        // well known folders
        createField(URN_SCHEMAS_HTTPMAIL, "inbox");
        createField(URN_SCHEMAS_HTTPMAIL, "deleteditems");
        createField(URN_SCHEMAS_HTTPMAIL, "sentitems");
        createField(URN_SCHEMAS_HTTPMAIL, "sendmsg");
        createField(URN_SCHEMAS_HTTPMAIL, "drafts");
        createField(URN_SCHEMAS_HTTPMAIL, "calendar");
        createField(URN_SCHEMAS_HTTPMAIL, "contacts");
        createField(URN_SCHEMAS_HTTPMAIL, "outbox");


        // folder
        createField("folderclass", SCHEMAS_EXCHANGE, "outlookfolderclass");
        createField(DAV, "hassubs");
        createField(DAV, "nosubs");
        createField(URN_SCHEMAS_HTTPMAIL, "unreadcount");
        createField(SCHEMAS_REPL, "contenttag");

        createField(DAV, "isfolder");

        // item uid, do not use as search parameter, see http://support.microsoft.com/kb/320749
        createField(DAV, "uid"); // based on PR_RECORD_KEY

        // POP and IMAP message
        createField("messageSize", 0x0e08, PropertyType.Long);//PR_MESSAGE_SIZE
        createField("imapUid", 0x0e23, PropertyType.Long);//PR_INTERNET_ARTICLE_NUMBER
        createField("junk", 0x1083, PropertyType.Long);
        createField("flagStatus", 0x1090, PropertyType.Long);//PR_FLAG_STATUS
        createField("messageFlags", 0x0e07, PropertyType.Long);//PR_MESSAGE_FLAGS
        createField("lastVerbExecuted", 0x1081, PropertyType.Long);//PR_LAST_VERB_EXECUTED
        createField("iconIndex", 0x1080, PropertyType.Long);//PR_ICON_INDEX
        createField(URN_SCHEMAS_HTTPMAIL, "read");
        //createField("read", 0x0e69, PropertyType.Boolean);//PR_READ
        createField("deleted", DistinguishedPropertySetType.Common, 0x8570, "deleted", PropertyType.String);

        //createField(URN_SCHEMAS_HTTPMAIL, "date");//PR_CLIENT_SUBMIT_TIME, 0x0039
        createField("date", 0x0e06, PropertyType.SystemTime);//PR_MESSAGE_DELIVERY_TIME
        createField(URN_SCHEMAS_MAILHEADER, "bcc");//PS_INTERNET_HEADERS/bcc
        createField(URN_SCHEMAS_HTTPMAIL, "datereceived");//PR_MESSAGE_DELIVERY_TIME, 0x0E06


        // IMAP search

        createField(URN_SCHEMAS_HTTPMAIL, "subject"); // DistinguishedPropertySetType.InternetHeaders/Subject/String
        //createField("subject", 0x0037, PropertyType.String);//PR_SUBJECT
        createField("body", 0x1000, PropertyType.String);//PR_BODY
        createField(URN_SCHEMAS_HTTPMAIL, "from");
        //createField("from", DistinguishedPropertySetType.PublicStrings, 0x001f);//urn:schemas:httpmail:from
        createField(URN_SCHEMAS_MAILHEADER, "to"); // DistinguishedPropertySetType.InternetHeaders/To/String
        createField(URN_SCHEMAS_MAILHEADER, "cc"); // DistinguishedPropertySetType.InternetHeaders/To/String

        createField("lastmodified", DAV, "getlastmodified"); // PR_LAST_MODIFICATION_TIME 0x3008 SystemTime

        // failover search
        createField(DAV, "displayname");
        createField("urlcompname", 0x10f3, PropertyType.String); //PR_URL_COMP_NAME

        // items
        createField("etag", DAV, "getetag");

        // calendar
        createField(SCHEMAS_EXCHANGE, "permanenturl");
        createField(URN_SCHEMAS_CALENDAR, "instancetype"); // DistinguishedPropertySetType.PublicStrings/urn:schemas:calendar:instancetype/Integer
        createField(URN_SCHEMAS_CALENDAR, "dtstart"); // 0x10C3 SystemTime
        createField(SCHEMAS_EXCHANGE, "sensitivity"); // PR_SENSITIVITY 0x0036 Integer
        createField(URN_SCHEMAS_CALENDAR, "timezoneid"); // DistinguishedPropertySetType.PublicStrings/urn:schemas:calendar:timezoneid/Integer
        createField("processed", 0x65e8, PropertyType.Boolean);// PR_MESSAGE_PROCESSED

        createField(DAV, "contentclass");
        createField("internetContent", 0x6659, PropertyType.Binary);

        // contact

        createField(SCHEMAS_EXCHANGE, "outlookmessageclass");
        createField(URN_SCHEMAS_HTTPMAIL, "subject");

        createField(URN_SCHEMAS_CONTACTS, "middlename"); // PR_MIDDLE_NAME 0x3A44
        createField(URN_SCHEMAS_CONTACTS, "fileas"); // urn:schemas:contacts:fileas PS_PUBLIC_STRINGS

        //createField("id", 0x0ff6, PropertyType.Binary); // PR_INSTANCE_KEY http://support.microsoft.com/kb/320749

        createField(URN_SCHEMAS_CONTACTS, "homepostaladdress"); // homeAddress DistinguishedPropertySetType.Address/0x0000801A/String
        createField(URN_SCHEMAS_CONTACTS, "otherpostaladdress"); // otherAddress DistinguishedPropertySetType.Address/0x0000801C/String
        createField(URN_SCHEMAS_CONTACTS, "mailingaddressid"); // postalAddressId DistinguishedPropertySetType.Address/0x00008022/String
        createField(URN_SCHEMAS_CONTACTS, "workaddress"); // workAddress DistinguishedPropertySetType.Address/0x0000801B/String

        createField(URN_SCHEMAS_CONTACTS, "alternaterecipient"); // alternaterecipient DistinguishedPropertySetType.PublicStrings/urn:schemas:contacts:alternaterecipient/String

        createField(SCHEMAS_EXCHANGE, "extensionattribute1"); // DistinguishedPropertySetType.Address/0x0000804F/String
        createField(SCHEMAS_EXCHANGE, "extensionattribute2"); // DistinguishedPropertySetType.Address/0x00008050/String
        createField(SCHEMAS_EXCHANGE, "extensionattribute3"); // DistinguishedPropertySetType.Address/0x00008051/String
        createField(SCHEMAS_EXCHANGE, "extensionattribute4"); // DistinguishedPropertySetType.Address/0x00008052/String

        createField(URN_SCHEMAS_CONTACTS, "bday"); // PR_BIRTHDAY 0x3A42 SystemTime
        createField("anniversary", URN_SCHEMAS_CONTACTS, "weddinganniversary"); // WeddingAnniversary
        createField(URN_SCHEMAS_CONTACTS, "businesshomepage"); // PR_BUSINESS_HOME_PAGE 0x3A51 String
        createField(URN_SCHEMAS_CONTACTS, "personalHomePage"); // PR_PERSONAL_HOME_PAGE 0x3A50 String
        //createField(URN_SCHEMAS_CONTACTS, "c"); // country DistinguishedPropertySetType.PublicStrings/urn:schemas:contacts:c/String
        createField(URN_SCHEMAS_CONTACTS, "cn"); // PR_DISPLAY_NAME 0x3001 String
        createField(URN_SCHEMAS_CONTACTS, "co"); // workAddressCountry DistinguishedPropertySetType.Address/0x00008049/String
        createField(URN_SCHEMAS_CONTACTS, "department"); // PR_DEPARTMENT_NAME 0x3A18 String
        // email with display name
        createField("writeemail1", URN_SCHEMAS_CONTACTS, "email1"); // DistinguishedPropertySetType.PublicStrings/urn:schemas:contacts:email1/String
        createField("writeemail2", URN_SCHEMAS_CONTACTS, "email2"); // DistinguishedPropertySetType.PublicStrings/urn:schemas:contacts:email2/String
        createField("writeemail3", URN_SCHEMAS_CONTACTS, "email3"); // DistinguishedPropertySetType.PublicStrings/urn:schemas:contacts:email3/String
        // email only
        createField("email1", DistinguishedPropertySetType.Address, 0x8084, "email1"); // Email1OriginalDisplayName
        createField("email2", DistinguishedPropertySetType.Address, 0x8094, "email2"); // Email2OriginalDisplayName
        createField("email3", DistinguishedPropertySetType.Address, 0x80A4, "email3"); // Email3OriginalDisplayName

        createField(URN_SCHEMAS_CONTACTS, "facsimiletelephonenumber"); // PR_BUSINESS_FAX_NUMBER 0x3A24 String
        createField(URN_SCHEMAS_CONTACTS, "givenName"); // PR_GIVEN_NAME 0x3A06 String
        createField(URN_SCHEMAS_CONTACTS, "homepostofficebox"); // PR_HOME_ADDRESS_POST_OFFICE_BOX 0x3A5E String
        createField(URN_SCHEMAS_CONTACTS, "homeCity"); // PR_HOME_ADDRESS_CITY 0x3A59 String
        createField(URN_SCHEMAS_CONTACTS, "homeCountry"); // PR_HOME_ADDRESS_COUNTRY 0x3A5A String
        createField(URN_SCHEMAS_CONTACTS, "homePhone"); // PR_HOME_TELEPHONE_NUMBER 0x3A09 String
        createField(URN_SCHEMAS_CONTACTS, "homePostalCode"); // PR_HOME_ADDRESS_POSTAL_CODE 0x3A5B String
        createField(URN_SCHEMAS_CONTACTS, "homeState"); // PR_HOME_ADDRESS_STATE_OR_PROVINCE 0x3A5C String
        createField(URN_SCHEMAS_CONTACTS, "homeStreet"); // PR_HOME_ADDRESS_STREET 0x3A5D String
        createField(URN_SCHEMAS_CONTACTS, "l"); // workAddressCity DistinguishedPropertySetType.Address/0x00008046/String
        createField(URN_SCHEMAS_CONTACTS, "manager"); // PR_MANAGER_NAME 0x3A4E String
        createField(URN_SCHEMAS_CONTACTS, "mobile"); // PR_MOBILE_TELEPHONE_NUMBER 0x3A1C String
        createField(URN_SCHEMAS_CONTACTS, "namesuffix"); // PR_GENERATION 0x3A05 String
        createField(URN_SCHEMAS_CONTACTS, "nickname"); // PR_NICKNAME 0x3A4F String
        createField(URN_SCHEMAS_CONTACTS, "o"); // PR_COMPANY_NAME 0x3A16 String
        createField(URN_SCHEMAS_CONTACTS, "pager"); // PR_PAGER_TELEPHONE_NUMBER 0x3A21 String
        createField(URN_SCHEMAS_CONTACTS, "personaltitle"); // PR_DISPLAY_NAME_PREFIX 0x3A45 String
        createField(URN_SCHEMAS_CONTACTS, "postalcode"); // workAddressPostalCode DistinguishedPropertySetType.Address/0x00008048/String
        createField(URN_SCHEMAS_CONTACTS, "postofficebox"); // workAddressPostOfficeBox DistinguishedPropertySetType.Address/0x0000804A/String
        createField(URN_SCHEMAS_CONTACTS, "profession"); // PR_PROFESSION 0x3A46 String
        createField(URN_SCHEMAS_CONTACTS, "roomnumber"); // PR_OFFICE_LOCATION 0x3A19 String
        createField(URN_SCHEMAS_CONTACTS, "secretarycn"); // PR_ASSISTANT 0x3A30 String
        createField(URN_SCHEMAS_CONTACTS, "sn"); // PR_SURNAME 0x3A11 String
        createField(URN_SCHEMAS_CONTACTS, "spousecn"); // PR_SPOUSE_NAME 0x3A48 String
        createField(URN_SCHEMAS_CONTACTS, "st"); // workAddressState DistinguishedPropertySetType.Address/0x00008047/String
        createField(URN_SCHEMAS_CONTACTS, "street"); // workAddressStreet DistinguishedPropertySetType.Address/0x00008045/String
        createField(URN_SCHEMAS_CONTACTS, "telephoneNumber"); // PR_BUSINESS_TELEPHONE_NUMBER 0x3A08 String
        createField(URN_SCHEMAS_CONTACTS, "title"); // PR_TITLE 0x3A17 String
        createField("description", URN_SCHEMAS_HTTPMAIL, "textdescription"); // PR_BODY 0x1000 String
        createField("im", SCHEMAS_MAPI, "InstMsg"); // InstantMessagingAddress DistinguishedPropertySetType.Address/0x00008062/String
        createField(URN_SCHEMAS_CONTACTS, "othermobile"); // PR_CAR_TELEPHONE_NUMBER 0x3A1E String


        createField(URN_SCHEMAS_CONTACTS, "otherstreet"); // PR_OTHER_ADDRESS_STREET 0x3A63 String
        createField(URN_SCHEMAS_CONTACTS, "otherstate"); // PR_OTHER_ADDRESS_STATE_OR_PROVINCE 0x3A62 String
        createField(URN_SCHEMAS_CONTACTS, "otherpostofficebox"); // PR_OTHER_ADDRESS_POST_OFFICE_BOX 0x3A64 String
        createField(URN_SCHEMAS_CONTACTS, "otherpostalcode"); // PR_OTHER_ADDRESS_POSTAL_CODE 0x3A61 String
        createField(URN_SCHEMAS_CONTACTS, "othercountry"); // PR_OTHER_ADDRESS_COUNTRY 0x3A60 String
        createField(URN_SCHEMAS_CONTACTS, "othercity"); // PR_OTHER_ADDRESS_CITY 0x3A5F String

        createField(URN_SCHEMAS_CONTACTS, "gender"); // PR_GENDER 0x3A4D Integer16

        createField("keywords", SCHEMAS_EXCHANGE, "keywords-utf8", PropertyType.StringArray); // PS_PUBLIC_STRINGS Keywords String
        //createField("keywords", DistinguishedPropertySetType.PublicStrings, "Keywords", ); // PS_PUBLIC_STRINGS Keywords String

        // contact private flags
        createField("private", DistinguishedPropertySetType.Common, 0x8506, "private", PropertyType.Boolean); // True/False
        createField("sensitivity", 0x0036, PropertyType.Long); // PR_SENSITIVITY SENSITIVITY_PRIVATE = 2, SENSITIVITY_PERSONAL = 1, SENSITIVITY_NONE = 0

        createField("haspicture", DistinguishedPropertySetType.Address, 0x8015, "haspicture", PropertyType.Boolean); // True/False

        // OWA settings
        createField("messageclass", 0x001a, PropertyType.String);
        createField("roamingxmlstream", 0x7c08, PropertyType.Binary);
        createField("roamingdictionary", 0x7c07, PropertyType.Binary);

        createField(DAV, "ishidden");

        createField("attachmentContactPhoto", 0x7FFF, PropertyType.Boolean); // PR_ATTACHMENT_CONTACTPHOTO
        createField("renderingPosition", 0x370B, PropertyType.Long);// PR_RENDERING_POSITION
    }

    protected static String toHexString(int propertyTag) {
        StringBuilder hexValue = new StringBuilder(Integer.toHexString(propertyTag));
        while (hexValue.length() < 4) {
            hexValue.insert(0, '0');
        }
        return hexValue.toString();
    }

    protected static void createField(String alias, int propertyTag, PropertyType propertyType) {
        String name = 'x' + toHexString(propertyTag) + propertyTypeMap.get(propertyType);
        Field field;
        if (propertyType == PropertyType.Binary) {
            field = new Field(alias, SCHEMAS_MAPI_PROPTAG, name, propertyType, null, "bin.base64", name);
        } else {
            field = new Field(alias, SCHEMAS_MAPI_PROPTAG, name, propertyType);
        }
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, DistinguishedPropertySetType propertySetType, int propertyTag, String responseAlias) {
        createField(alias, propertySetType, propertyTag, responseAlias, null);
    }

    protected static void createField(String alias, DistinguishedPropertySetType propertySetType, int propertyTag, String responseAlias, PropertyType propertyType) {
        String name;
        String updateAlias;
        if (propertySetType == DistinguishedPropertySetType.Address) {
            // Address namespace expects integer names
            name = String.valueOf(propertyTag);
        } else {
            // Common namespace expects hex names
            name = "0x" + toHexString(propertyTag);
        }
        updateAlias = "_x0030_x" + toHexString(propertyTag);
        Field field = new Field(alias, Namespace.getNamespace(SCHEMAS_MAPI_ID.getURI() +
                '{' + distinguishedPropertySetMap.get(propertySetType) + "}/"), name, propertyType, responseAlias, null, updateAlias);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(Namespace namespace, String name) {
        Field field = new Field(namespace, name);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, Namespace namespace, String name) {
        Field field = new Field(alias, namespace, name, null);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, Namespace namespace, String name, PropertyType propertyType) {
        Field field = new Field(alias, namespace, name, propertyType);
        fieldMap.put(field.alias, field);
    }

    private final DavPropertyName davPropertyName;
    protected final String alias;
    protected final String uri;
    protected final String requestPropertyString;
    protected final DavPropertyName responsePropertyName;
    protected final DavPropertyName updatePropertyName;
    protected final String cast;
    protected final boolean isIntValue;
    protected final boolean isMultivalued;
    protected final boolean isBooleanValue;

    /**
     * Create field for namespace and name, use name as alias.
     *
     * @param namespace Exchange namespace
     * @param name      Exchange name
     */
    protected Field(Namespace namespace, String name) {
        this(name, namespace, name, null);
    }

    /**
     * Create field for namespace and name of type propertyType.
     *
     * @param alias        logical name in DavMail
     * @param namespace    Exchange namespace
     * @param name         Exchange name
     * @param propertyType property type
     */
    protected Field(String alias, Namespace namespace, String name, PropertyType propertyType) {
        this(alias, namespace, name, propertyType, null, null, name);
    }

    /**
     * Create field for namespace and name of type propertyType.
     *
     * @param alias         logical name in DavMail
     * @param namespace     Exchange namespace
     * @param name          Exchange name
     * @param propertyType  property type
     * @param responseAlias property name in SEARCH response (as responsealias in request)
     * @param cast          response cast type (e.g. bin.base64)
     * @param updateAlias   some properties use a different alias in PROPPATCH requests
     */
    protected Field(String alias, Namespace namespace, String name, PropertyType propertyType, String responseAlias, String cast, String updateAlias) {
        this.alias = alias;

        // property name in PROPFIND requests
        davPropertyName = DavPropertyName.create(name, namespace);
        // property name in PROPPATCH requests
        updatePropertyName = DavPropertyName.create(updateAlias, namespace);

        // a few type based flags
        isMultivalued = propertyType != null && propertyType.toString().endsWith("Array");
        isIntValue = propertyType == PropertyType.Integer || propertyType == PropertyType.Long || propertyType == PropertyType.Short;
        isBooleanValue = propertyType == PropertyType.Boolean;

        this.uri = namespace.getURI() + name;
        if (responseAlias == null) {
            this.requestPropertyString = '"' + uri + '"';
            this.responsePropertyName = davPropertyName;
        } else {
            this.requestPropertyString = '"' + uri + "\" as " + responseAlias;
            this.responsePropertyName = DavPropertyName.create(responseAlias, EMPTY);
        }
        this.cast = cast;
    }

    /**
     * Property uri.
     *
     * @return uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * Integer value property type.
     *
     * @return true if the field value is integer
     */
    public boolean isIntValue() {
        return isIntValue;
    }

    /**
     * Get Field by alias.
     *
     * @param alias field alias
     * @return field
     */
    public static Field get(String alias) {
        Field field = fieldMap.get(alias);
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
    public static Field getHeader(String headerName) {
        return new Field(SCHEMAS_MAPI_STRING_INTERNET_HEADERS, headerName);
    }

    /**
     * Create DavProperty object for field alias and value.
     *
     * @param alias DavMail field alias
     * @param value field value
     * @return DavProperty with value or DavPropertyName for null values
     */
    public static DavConstants createDavProperty(String alias, String value) {
        Field field = Field.get(alias);
        if (value == null) {
            // return DavPropertyName to remove property
            return field.updatePropertyName;
        } else if (field.isMultivalued) {
            // multivalued field, split values separated by \n
            List<XmlSerializable> valueList = new ArrayList<XmlSerializable>();
            String[] values = value.split("\n");
            for (final String singleValue : values) {
                valueList.add(new XmlSerializable() {
                    public Element toXml(Document document) {
                        return DomUtil.createElement(document, "v", XML, singleValue);
                    }
                });
            }

            return new DefaultDavProperty(field.updatePropertyName, valueList);
        } else if (field.isBooleanValue) {
            if ("true".equals(value)) {
                return new DefaultDavProperty(field.updatePropertyName, "1");
            } else if ("false".equals(value)) {
                return new DefaultDavProperty(field.updatePropertyName, "0");
            } else {
                throw new RuntimeException("Invalid value for " + field.alias + ": " + value);
            }
        } else {
            return new DefaultDavProperty(field.updatePropertyName, value);
        }
    }

    /**
     * SEARCH request property name for alias
     *
     * @param alias field alias
     * @return request property string
     */
    public static String getRequestPropertyString(String alias) {
        return Field.get(alias).requestPropertyString;
    }

    /**
     * PROPFIND request property name
     *
     * @param alias field alias
     * @return request property name
     */
    public static DavPropertyName getPropertyName(String alias) {
        return Field.get(alias).davPropertyName;
    }

    /**
     * SEARCH response property name
     *
     * @param alias field alias
     * @return response property name
     */
    public static DavPropertyName getResponsePropertyName(String alias) {
        return Field.get(alias).responsePropertyName;
    }
}

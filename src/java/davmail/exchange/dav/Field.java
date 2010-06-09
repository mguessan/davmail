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

import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.util.HashMap;
import java.util.Map;

/**
 * WebDav Field
 */
public class Field {
    protected static final Namespace DAV = Namespace.getNamespace("DAV:");
    protected static final Namespace URN_SCHEMAS_HTTPMAIL = Namespace.getNamespace("urn:schemas:httpmail:");
    protected static final Namespace URN_SCHEMAS_MAILHEADER = Namespace.getNamespace("urn:schemas:mailheader:");

    protected static final Namespace SCHEMAS_EXCHANGE = Namespace.getNamespace("http://schemas.microsoft.com/exchange/");
    protected static final Namespace SCHEMAS_MAPI_PROPTAG = Namespace.getNamespace("http://schemas.microsoft.com/mapi/proptag/");
    protected static final Namespace SCHEMAS_MAPI_ID = Namespace.getNamespace("http://schemas.microsoft.com/mapi/id/");
    protected static final Namespace SCHEMAS_MAPI_STRING = Namespace.getNamespace("http://schemas.microsoft.com/mapi/string/");
    protected static final Namespace URN_SCHEMAS_CONTACTS = Namespace.getNamespace("urn:schemas:contacts:");

    protected static enum PropertyType {
        ApplicationTime, ApplicationTimeArray, Binary, BinaryArray, Boolean, CLSID, CLSIDArray, Currency, CurrencyArray,
        Double, DoubleArray, Error, Float, FloatArray, Integer, IntegerArray, Long, LongArray, Null, Object,
        ObjectArray, Short, ShortArray, SystemTime, SystemTimeArray, String, StringArray
    }

    protected static final Map<PropertyType, String> propertyTypeMap = new HashMap<PropertyType, String>();

    static {
        propertyTypeMap.put(PropertyType.Integer, "0003");
        propertyTypeMap.put(PropertyType.Boolean, "000b");
        propertyTypeMap.put(PropertyType.SystemTime, "0040");
        propertyTypeMap.put(PropertyType.String, "001f");
    }

    protected static enum DistinguishedPropertySetType {
        Meeting, Appointment, Common, PublicStrings, Address, InternetHeaders, CalendarAssistant, UnifiedMessaging, Task
    }

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

        // POP and IMAP message
        createField(DAV, "uid");
        createField("messageSize", 0x0e08, PropertyType.Integer);//PR_MESSAGE_SIZE
        createField("imapUid", 0x0e23, PropertyType.Integer);//PR_INTERNET_ARTICLE_NUMBER
        createField("junk", 0x1083, PropertyType.Integer);
        createField("flagStatus", 0x1090, PropertyType.Integer);//PR_FLAG_STATUS
        createField("messageFlags", 0x0e07, PropertyType.Integer);//PR_MESSAGE_FLAGS
        createField("lastVerbExecuted", 0x1081, PropertyType.Integer);//PR_LAST_VERB_EXECUTED
        createField("iconIndex", 0x1080, PropertyType.Integer);//PR_ICON_INDEX        
        createField(URN_SCHEMAS_HTTPMAIL, "read");
        //createField("read", 0x0e69, PropertyType.Boolean);//PR_READ
        createField("deleted", DistinguishedPropertySetType.Common, 0x8570);
        createField("writedeleted", DistinguishedPropertySetType.Common, 0x8570, PropertyType.Integer);
        createField(URN_SCHEMAS_HTTPMAIL, "date");//PR_CLIENT_SUBMIT_TIME, 0x0039
        //createField("date", 0x0e06, PropertyType.SystemTime);//PR_MESSAGE_DELIVERY_TIME
        createField(URN_SCHEMAS_MAILHEADER, "bcc");//PS_INTERNET_HEADERS/bcc

        // IMAP search

        createField(URN_SCHEMAS_HTTPMAIL, "subject");
        //createField("subject", 0x0037, PropertyType.String);//PR_SUBJECT
        createField("body", 0x1000, PropertyType.String);//PR_BODY
        createField(URN_SCHEMAS_HTTPMAIL, "from");
        //createField("from", DistinguishedPropertySetType.PublicStrings, 0x001f);//urn:schemas:httpmail:from
        createField(URN_SCHEMAS_MAILHEADER, "to");
        createField(URN_SCHEMAS_MAILHEADER, "cc");

        createField("lastmodified", 0x3008, PropertyType.SystemTime);//PR_LAST_MODIFICATION_TIME DAV:getlastmodified


        // failover search
        createField(DAV, "displayname");
    }

    protected static void createField(String alias, int propertyTag, PropertyType propertyType) {
        String name = 'x' + Integer.toHexString(propertyTag) + propertyTypeMap.get(propertyType);
        Field field = new Field(alias, SCHEMAS_MAPI_PROPTAG, name);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, DistinguishedPropertySetType propertySetType, int propertyTag) {
        String name = '{' + distinguishedPropertySetMap.get(propertySetType) + "}/0x" + Integer.toHexString(propertyTag);
        Field field = new Field(alias, SCHEMAS_MAPI_ID, name);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, DistinguishedPropertySetType propertySetType, int propertyTag, PropertyType propertyType) {
        String name = '{' + distinguishedPropertySetMap.get(propertySetType) + "}/_x" +propertyTypeMap.get(propertyType)+"_x"+Integer.toHexString(propertyTag);
        Field field = new Field(alias, SCHEMAS_MAPI_ID, name);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(Namespace namespace, String name) {
        Field field = new Field(namespace, name);
        fieldMap.put(field.alias, field);
    }

    protected static void createField(String alias, Namespace namespace, String name) {
        Field field = new Field(alias, namespace, name);
        fieldMap.put(field.alias, field);
    }

    protected final DavPropertyName davPropertyName;
    protected final String alias;
    protected final String uri;

    public Field(Namespace namespace, String name) {
        this(name, namespace, name);
    }

    public Field(String alias, Namespace namespace, String name) {
        davPropertyName = DavPropertyName.create(name, namespace);
        this.alias = alias;
        this.uri = namespace.getURI()+name;
    }

    public String getUri() {
        return uri;
    }

    public String getAlias() {
        return alias;
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
     * Get Mime header fieks.
     *
     * @param alias field alias
     * @return field
     */
    public static Field getHeader(String headerName) {
        String name = '{' + distinguishedPropertySetMap.get(DistinguishedPropertySetType.InternetHeaders) + "}/" + headerName;
        return new Field(SCHEMAS_MAPI_STRING, name);
    }

    public static DefaultDavProperty createDavProperty(String alias, String value) {
        return new DefaultDavProperty(Field.get(alias).davPropertyName, value);
    }
}

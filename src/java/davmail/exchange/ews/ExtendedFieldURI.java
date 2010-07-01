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
 * Extended MAPI property.
 */
public class ExtendedFieldURI implements FieldURI {
    protected enum PropertyType {
        ApplicationTime, ApplicationTimeArray, Binary, BinaryArray, Boolean, CLSID, CLSIDArray, Currency, CurrencyArray,
        Double, DoubleArray, Error, Float, FloatArray, Integer, IntegerArray, Long, LongArray, Null, Object,
        ObjectArray, Short, ShortArray, SystemTime, SystemTimeArray, String, StringArray
    }

    protected static enum DistinguishedPropertySetType {
        Meeting, Appointment, Common, PublicStrings, Address, InternetHeaders, CalendarAssistant, UnifiedMessaging, Task
    }


    protected String propertyTag;
    protected DistinguishedPropertySetType distinguishedPropertySetId;
    protected String propertySetId;
    protected String propertyName;
    protected int propertyId;
    protected PropertyType propertyType;

    public ExtendedFieldURI(int intPropertyTag, PropertyType propertyType) {
        this.propertyTag = "0x" + Integer.toHexString(intPropertyTag);
        this.propertyType = propertyType;
    }

    public ExtendedFieldURI(DistinguishedPropertySetType distinguishedPropertySetId, int propertyId, PropertyType propertyType) {
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyId = propertyId;
        this.propertyType = propertyType;
    }

    public ExtendedFieldURI(DistinguishedPropertySetType distinguishedPropertySetId, String propertyName) {
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyName = propertyName;
    }

    public String getPropertyTag() {
        return propertyTag;
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:ExtendedFieldURI ");
        if (propertyTag != null) {
            buffer.append("PropertyTag=\"").append(propertyTag).append("\" ");
        }
        if (distinguishedPropertySetId != null) {
            buffer.append("DistinguishedPropertySetId=\"").append(distinguishedPropertySetId).append("\" ");
        }
        if (propertySetId != null) {
            buffer.append("PropertySetId=\"").append(propertySetId).append("\" ");
        }
        if (propertyName != null) {
            buffer.append("propertyName=\"").append(propertyName).append("\" ");
        }
        if (propertyId != 0) {
            buffer.append("PropertyId=\"").append(String.valueOf(propertyId)).append("\" ");
        }
        if (propertyType != null) {
            buffer.append("PropertyType=\"").append(propertyType.toString()).append("\"/>");
        }
    }

    public void appendValue(StringBuilder buffer, String itemType, String value) {
        appendTo(buffer);
        buffer.append("<t:");
        buffer.append(itemType);
        buffer.append('>');
        buffer.append("<t:ExtendedProperty>");
        appendTo(buffer);
        buffer.append("<t:Value>");
        buffer.append(value);
        buffer.append("</t:Value>");
        buffer.append("</t:ExtendedProperty>");
        buffer.append("</t:");
        buffer.append(itemType);
        buffer.append('>');
    }

    public String getResponseName() {
        return propertyTag;
    }

    public static final ExtendedFieldURI PR_INSTANCE_KEY = new ExtendedFieldURI(0xff6, PropertyType.Binary);
    public static final ExtendedFieldURI PR_MESSAGE_SIZE = new ExtendedFieldURI(0xe08, PropertyType.Integer);
    public static final ExtendedFieldURI PR_INTERNET_ARTICLE_NUMBER = new ExtendedFieldURI(0xe23, PropertyType.Integer);
    public static final ExtendedFieldURI JUNK_FLAG = new ExtendedFieldURI(0x1083, PropertyType.Integer);
    public static final ExtendedFieldURI PR_FLAG_STATUS = new ExtendedFieldURI(0x1090, PropertyType.Integer);
    public static final ExtendedFieldURI PR_MESSAGE_FLAGS = new ExtendedFieldURI(0x0e07, PropertyType.Integer);
    public static final ExtendedFieldURI PR_ACTION_FLAG = new ExtendedFieldURI(0x1081, PropertyType.Integer);
    public static final ExtendedFieldURI PR_URL_COMP_NAME = new ExtendedFieldURI(0x10f3, PropertyType.String);
    public static final ExtendedFieldURI PR_CONTAINER_CLASS = new ExtendedFieldURI(0x3613, PropertyType.String);

    public static final ExtendedFieldURI PR_LAST_MODIFICATION_TIME = new ExtendedFieldURI(0x3008, PropertyType.SystemTime);
    public static final ExtendedFieldURI PR_LOCAL_COMMIT_TIME_MAX = new ExtendedFieldURI(0x670a, PropertyType.SystemTime);
    public static final ExtendedFieldURI PR_SUBFOLDERS = new ExtendedFieldURI(0x360a, PropertyType.Boolean);
    public static final ExtendedFieldURI PR_CONTENT_UNREAD = new ExtendedFieldURI(0x3603, PropertyType.Integer);

    // message properties
    public static final ExtendedFieldURI PR_READ = new ExtendedFieldURI(0xe69, PropertyType.Boolean);

}


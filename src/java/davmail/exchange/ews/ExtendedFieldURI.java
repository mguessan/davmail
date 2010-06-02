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

    protected String propertyTag;
    protected String propertySetId;
    protected int propertyId;
    protected final PropertyType propertyType;

    public ExtendedFieldURI(String propertyTag, PropertyType propertyType) {
        this.propertyTag = propertyTag;
        this.propertyType = propertyType;
    }

    public ExtendedFieldURI(String propertySetId, int propertyId, PropertyType propertyType) {
        this.propertySetId = propertySetId;
        this.propertyId = propertyId;
        this.propertyType = propertyType;
    }

    public String getPropertyTag() {
        return propertyTag;
    }

    public void write(Writer writer) throws IOException {
        writer.write("<t:ExtendedFieldURI ");
        if (propertyTag != null) {
            writer.write("PropertyTag=\"");
            writer.write(propertyTag);
            writer.write("\" ");
        }
        if (propertySetId != null) {
            writer.write("PropertySetId=\"");
            writer.write(propertySetId);
            writer.write("\" ");
        }
        if (propertyId != 0) {
            writer.write("PropertyId=\"");
            writer.write(String.valueOf(propertyId));
            writer.write("\" ");
        }
        writer.write("PropertyType=\"");
        writer.write(propertyType.toString());
        writer.write("\"/>");
    }

    public static final ExtendedFieldURI PR_INSTANCE_KEY = new ExtendedFieldURI("0x0FF6", PropertyType.Binary);
    public static final ExtendedFieldURI PR_MESSAGE_SIZE = new ExtendedFieldURI("0x0E08", PropertyType.Integer);
    public static final ExtendedFieldURI PR_INTERNET_ARTICLE_NUMBER = new ExtendedFieldURI("0x0E23", PropertyType.Integer);
    public static final ExtendedFieldURI JUNK_FLAG = new ExtendedFieldURI("0x1083", PropertyType.Integer);
    public static final ExtendedFieldURI PR_FLAG_STATUS = new ExtendedFieldURI("0x1090", PropertyType.Integer);
    public static final ExtendedFieldURI PR_MESSAGE_FLAGS = new ExtendedFieldURI("0x0E07", PropertyType.Integer);
    public static final ExtendedFieldURI PR_ACTION_FLAG = new ExtendedFieldURI("0x1081", PropertyType.Integer);
    public static final ExtendedFieldURI PR_URL_COMP_NAME = new ExtendedFieldURI("0x10F3", PropertyType.String);

    public static final ExtendedFieldURI PR_LAST_MODIFICATION_TIME = new ExtendedFieldURI("0x3008", PropertyType.SystemTime);


}


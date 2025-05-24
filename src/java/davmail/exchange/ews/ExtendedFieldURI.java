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
 * Extended MAPI property.
 */
public class ExtendedFieldURI implements FieldURI {

    @SuppressWarnings({"UnusedDeclaration"})
    protected enum PropertyType {
        ApplicationTime, ApplicationTimeArray, Binary, BinaryArray, Boolean, CLSID, CLSIDArray, Currency, CurrencyArray,
        Double, DoubleArray, Error, Float, FloatArray, Integer, IntegerArray, Long, LongArray, Null, Object,
        ObjectArray, Short, ShortArray, SystemTime, SystemTimeArray, String, StringArray
    }

    @SuppressWarnings({"UnusedDeclaration"})
    protected enum DistinguishedPropertySetType {
        Meeting, Appointment, Common, PublicStrings, Address, InternetHeaders, CalendarAssistant, UnifiedMessaging, Task
    }


    protected String propertyTag;
    protected DistinguishedPropertySetType distinguishedPropertySetId;
    protected String propertyName;
    protected int propertyId;
    protected final PropertyType propertyType;

    private String graphId;

    /**
     * Create extended field uri.
     *
     * @param intPropertyTag property tag as int
     * @param propertyType   property type
     */
    public ExtendedFieldURI(int intPropertyTag, PropertyType propertyType) {
        this.propertyTag = "0x" + Integer.toHexString(intPropertyTag);
        this.propertyType = propertyType;
    }

    /**
     * Create extended field uri.
     *
     * @param distinguishedPropertySetId distinguished property set id
     * @param propertyId                 property id
     * @param propertyType               property type
     */
    public ExtendedFieldURI(DistinguishedPropertySetType distinguishedPropertySetId, int propertyId, PropertyType propertyType) {
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyId = propertyId;
        this.propertyType = propertyType;
    }

    /**
     * Create extended field uri.
     *
     * @param distinguishedPropertySetId distinguished property set id
     * @param propertyName               property name
     */
    public ExtendedFieldURI(DistinguishedPropertySetType distinguishedPropertySetId, String propertyName) {
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyName = propertyName;
        this.propertyType = PropertyType.String;
    }

    /**
     * Create extended field uri.
     *
     * @param distinguishedPropertySetId distinguished property set id
     * @param propertyName               property name
     * @param propertyType               property type
     */
    public ExtendedFieldURI(DistinguishedPropertySetType distinguishedPropertySetId, String propertyName, PropertyType propertyType) {
        this.distinguishedPropertySetId = distinguishedPropertySetId;
        this.propertyName = propertyName;
        this.propertyType = propertyType;
    }

    public ExtendedFieldURI(int intPropertyTag, PropertyType propertyType, String graphId) {
        this(intPropertyTag, propertyType);
        this.graphId = graphId;
    }

    public void appendTo(StringBuilder buffer) {
        buffer.append("<t:ExtendedFieldURI");
        if (propertyTag != null) {
            buffer.append(" PropertyTag=\"").append(propertyTag).append('"');
        }
        if (distinguishedPropertySetId != null) {
            buffer.append(" DistinguishedPropertySetId=\"").append(distinguishedPropertySetId).append('"');
        }
        if (propertyName != null) {
            buffer.append(" PropertyName=\"").append(propertyName).append('"');
        }
        if (propertyId != 0) {
            buffer.append(" PropertyId=\"").append(propertyId).append('"');
        }
        if (propertyType != null) {
            buffer.append(" PropertyType=\"").append(propertyType.toString()).append('"');
        }
        buffer.append("/>");
    }

    public void appendValue(StringBuilder buffer, String itemType, String value) {
        if (itemType != null) {
            appendTo(buffer);
            buffer.append("<t:");
            buffer.append(itemType);
            buffer.append('>');
        }
        buffer.append("<t:ExtendedProperty>");
        appendTo(buffer);
        if (propertyType == PropertyType.StringArray) {
            buffer.append("<t:Values>");
            String[] values = value.split(",");
            for (final String singleValue : values) {
                buffer.append("<t:Value>");
                buffer.append(StringUtil.xmlEncode(singleValue));
                buffer.append("</t:Value>");

            }
            buffer.append("</t:Values>");
        } else {
            buffer.append("<t:Value>");
            if ("0x10f3".equals(propertyTag)) {
                buffer.append(StringUtil.xmlEncode(StringUtil.encodeUrlcompname(value)));
            } else {
                buffer.append(StringUtil.xmlEncode(value));
            }
            buffer.append("</t:Value>");
        }
        buffer.append("</t:ExtendedProperty>");
        if (itemType != null) {
            buffer.append("</t:");
            buffer.append(itemType);
            buffer.append('>');
        }
    }

    /**
     * Field name in EWS response.
     *
     * @return field name in response
     */
    public String getResponseName() {
        if (propertyTag != null) {
            return propertyTag;
        } else if (propertyName != null) {
            return propertyName;
        } else {
            return String.valueOf(propertyId);
        }
    }

    @Override
    public String getGraphId() {
        if (graphId != null) {
            return graphId;
        }
        // PropertyId values may only be in one of the following formats:
        // 'MapiPropertyType namespaceGuid Name propertyName', 'MapiPropertyType namespaceGuid Id propertyId' or 'MapiPropertyType propertyTag'.

        String namespaceGuid = null;

        if (distinguishedPropertySetId == DistinguishedPropertySetType.PublicStrings) {
            namespaceGuid = "{00020329-0000-0000-c000-000000000046}";
        }
        if (distinguishedPropertySetId == DistinguishedPropertySetType.InternetHeaders) {
            namespaceGuid = "{00020386-0000-0000-c000-000000000046}";
        }
        if (distinguishedPropertySetId == DistinguishedPropertySetType.Common) {
            namespaceGuid = "{00062008-0000-0000-c000-000000000046}";
        }
        if (distinguishedPropertySetId == DistinguishedPropertySetType.Address) {
            namespaceGuid = "{00062004-0000-0000-c000-000000000046}";
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
        } else {
            throw new IllegalStateException("Unsupported graph property for graph "+getResponseName());
        }
        return buffer.toString();
    }

    @Override
    public boolean isMultiValued() {
        return propertyType == PropertyType.StringArray;
    }

    public boolean isNumber() {
        return propertyType == PropertyType.Short || propertyType == PropertyType.Integer || propertyType == PropertyType.Long || propertyType == PropertyType.Double;
    }

}


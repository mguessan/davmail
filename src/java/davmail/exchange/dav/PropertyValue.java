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

/**
 * Property value.
 */
public class PropertyValue {
    protected final String namespaceUri;
    protected final String name;
    protected final String xmlEncodedValue;
    protected final PropertyType type;

    /**
     * Create Dav property value.
     *
     * @param namespaceUri property namespace
     * @param name         property name
     */
    public PropertyValue(String namespaceUri, String name) {
        this(namespaceUri, name, null, null);
    }

    /**
     * Create Dav property value.
     *
     * @param namespaceUri    property namespace
     * @param name            property name
     * @param xmlEncodedValue xml encoded value
     */
    public PropertyValue(String namespaceUri, String name, String xmlEncodedValue) {
        this(namespaceUri, name, xmlEncodedValue, null);
    }

    /**
     * Create Dav property value.
     *
     * @param namespaceUri    property namespace
     * @param name            property name
     * @param xmlEncodedValue xml encoded value
     * @param type            property type
     */
    public PropertyValue(String namespaceUri, String name, String xmlEncodedValue, PropertyType type) {
        this.namespaceUri = namespaceUri;
        this.name = name;
        this.xmlEncodedValue = xmlEncodedValue;
        this.type = type;
    }

    /**
     * Get property namespace.
     *
     * @return property namespace
     */
    public String getNamespaceUri() {
        return namespaceUri;
    }

    /**
     * Get xml encoded value.
     *
     * @return Xml encoded value
     */
    public String getXmlEncodedValue() {
        return xmlEncodedValue;
    }

    /**
     * Get property type.
     *
     * @return property type
     */
    public PropertyType getType() {
        return type;
    }

    /**
     * Get property name.
     *
     * @return property name
     */
    public String getName() {
        return name;
    }
}

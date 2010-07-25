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

    public PropertyValue(String namespaceUri, String name) {
         this(namespaceUri, name, null, null);
    }

    public PropertyValue(String namespaceUri, String name, String xmlEncodedValue) {
         this(namespaceUri, name, xmlEncodedValue, null);
    }

    public PropertyValue(String namespaceUri, String name, String xmlEncodedValue, PropertyType type) {
        this.namespaceUri = namespaceUri;
        this.name = name;
        this.xmlEncodedValue = xmlEncodedValue;
        this.type = type;
    }

    public String getNamespaceUri() {
        return namespaceUri;
    }

    public String getXmlEncodedValue() {
        return xmlEncodedValue;
    }

    public PropertyType getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}

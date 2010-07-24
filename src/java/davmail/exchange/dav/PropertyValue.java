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

import org.apache.jackrabbit.webdav.xml.Namespace;


/**
 * Property value.
 */
public class PropertyValue {
    protected Namespace namespace;
    protected String name;
    protected String value;
    protected PropertyType type;

    public PropertyValue(Namespace namespace, String name) {
         this(namespace, name, null, null);
    }

    public PropertyValue(Namespace namespace, String name, String value) {
         this(namespace, name, value, null);
    }

    public PropertyValue(Namespace namespace, String name, String value, PropertyType type) {
        this.namespace = namespace;
        this.name = name;
        this.value = value;
        this.type = type;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public String getValue() {
        return value;
    }

    public PropertyType getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}

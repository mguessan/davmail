/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2011  Mickael Guessant
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

import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Exchange PROPFIND method.
 * Does not load full DOM in memory.
 */
public class ExchangePropFindMethod extends ExchangeDavMethod {
    protected static final Logger LOGGER = Logger.getLogger(ExchangePropFindMethod.class);

    protected final DavPropertyNameSet propertyNameSet;

    public ExchangePropFindMethod(String uri) {
        this(uri, null, DepthHeader.DEPTH_INFINITY);
    }

    public ExchangePropFindMethod(String uri, DavPropertyNameSet propertyNameSet, int depth) {
        super(uri);
        this.propertyNameSet = propertyNameSet;
        DepthHeader dh = new DepthHeader(depth);
        setRequestHeader(dh.getHeaderName(), dh.getHeaderValue());
    }

    protected byte[] generateRequestContent() {
        try {
            // build namespace map
            int currentChar = 'e';
            final Map<String, Integer> nameSpaceMap = new HashMap<String, Integer>();
            nameSpaceMap.put("DAV:", (int) 'D');
            if (propertyNameSet != null) {
                DavPropertyNameIterator propertyNameIterator = propertyNameSet.iterator();
                while (propertyNameIterator.hasNext()) {
                    DavPropertyName davPropertyName = propertyNameIterator.nextPropertyName();

                    davPropertyName.getName();
                    // property namespace
                    String namespaceUri = davPropertyName.getNamespace().getURI();
                    if (!nameSpaceMap.containsKey(namespaceUri)) {
                        nameSpaceMap.put(namespaceUri, currentChar++);
                    }
                }
            }
            // <D:propfind xmlns:D="DAV:"><D:prop><D:displayname/></D:prop></D:propfind>
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<D:propfind ");
            for (Map.Entry<String, Integer> mapEntry : nameSpaceMap.entrySet()) {
                writer.write(" xmlns:");
                writer.write((char) mapEntry.getValue().intValue());
                writer.write("=\"");
                writer.write(mapEntry.getKey());
                writer.write("\"");
            }
            writer.write(">");
            if (propertyNameSet == null || propertyNameSet.isEmpty()) {
                writer.write("<D:allprop/>");
            } else {
                writer.write("<D:prop>");
                DavPropertyNameIterator propertyNameIterator = propertyNameSet.iterator();
                while (propertyNameIterator.hasNext()) {
                    DavPropertyName davPropertyName = propertyNameIterator.nextPropertyName();
                    char nameSpaceChar = (char) nameSpaceMap.get(davPropertyName.getNamespace().getURI()).intValue();
                    writer.write('<');
                    writer.write(nameSpaceChar);
                    writer.write(':');
                    writer.write(davPropertyName.getName());
                    writer.write("/>");
                }
                writer.write("</D:prop>");
            }
            writer.write("</D:propfind>");
            writer.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getName() {
        return "PROPFIND";
    }

}

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

import davmail.util.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;

/**
 * Custom Exchange PROPPATCH method.
 * Supports extended property update with type.
 */
public class ExchangePropPatchMethod extends EntityEnclosingMethod {
    protected static final Logger logger = Logger.getLogger(ExchangePropPatchMethod.class);

    static final Namespace TYPE_NAMESPACE = Namespace.getNamespace("urn:schemas-microsoft-com:datatypes");
    final Set<PropertyValue> propertyValues;

    /**
     * Create PROPPATCH method.
     *
     * @param path           path
     * @param propertyValues property values
     */
    public ExchangePropPatchMethod(String path, Set<PropertyValue> propertyValues) {
        super(path);
        this.propertyValues = propertyValues;
        setRequestEntity(new RequestEntity() {
            byte[] content;

            public boolean isRepeatable() {
                return true;
            }

            public void writeRequest(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = generateRequestContent();
                }
                outputStream.write(content);
            }

            public long getContentLength() {
                if (content == null) {
                    content = generateRequestContent();
                }
                return content.length;
            }

            public String getContentType() {
                return "text/xml;charset=UTF-8";
            }
        });
    }

    protected byte[] generateRequestContent() {
        try {
            // build namespace map
            int currentChar = 'e';
            final Map<Namespace, Integer> nameSpaceMap = new HashMap<Namespace, Integer>();
            final Set<PropertyValue> setPropertyValues = new HashSet<PropertyValue>();
            final Set<PropertyValue> deletePropertyValues = new HashSet<PropertyValue>();
            for (PropertyValue propertyValue : propertyValues) {
                // data type namespace
                if (!nameSpaceMap.containsKey(TYPE_NAMESPACE) && propertyValue.getType() != null) {
                    nameSpaceMap.put(TYPE_NAMESPACE, currentChar++);
                }
                // property namespace
                Namespace namespace = propertyValue.getNamespace();
                if (!nameSpaceMap.containsKey(namespace)) {
                    nameSpaceMap.put(namespace, currentChar++);
                }
                if (propertyValue.getValue() == null) {
                    deletePropertyValues.add(propertyValue);
                } else {
                    setPropertyValues.add(propertyValue);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<D:propertyupdate xmlns:D=\"DAV:\"");
            for (Map.Entry<Namespace, Integer> mapEntry : nameSpaceMap.entrySet()) {
                writer.write(" xmlns:");
                writer.write((char) mapEntry.getValue().intValue());
                writer.write("=\"");
                writer.write(mapEntry.getKey().getURI());
                writer.write("\"");
            }
            writer.write(">");
            if (!setPropertyValues.isEmpty()) {
                writer.write("<D:set><D:prop>");
                for (PropertyValue propertyValue : setPropertyValues) {
                    PropertyType propertyType = propertyValue.getType();
                    char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespace()).intValue();
                    writer.write('<');
                    writer.write(nameSpaceChar);
                    writer.write(':');
                    writer.write(propertyValue.getName());
                    if (propertyType != null) {
                        writer.write(' ');
                        writer.write(nameSpaceMap.get(TYPE_NAMESPACE));
                        writer.write(":dt=\"");
                        writer.write(propertyType.toString().toLowerCase());
                        writer.write("\"");
                    }
                    writer.write('>');
                    writer.write(StringUtil.xmlEncode(propertyValue.getValue()));
                    writer.write("</");
                    writer.write(nameSpaceChar);
                    writer.write(':');
                    writer.write(propertyValue.getName());
                    writer.write('>');
                }
                writer.write("</D:prop></D:set>");
            }
            if (!deletePropertyValues.isEmpty()) {
                writer.write("<D:remove><D:prop>");
                for (PropertyValue propertyValue : deletePropertyValues) {
                    char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespace()).intValue();
                    writer.write('<');
                    writer.write(nameSpaceChar);
                    writer.write(':');
                    writer.write(propertyValue.getName());
                    writer.write("/>");
                }
                writer.write("</D:prop></D:remove>");
            }
            writer.write("</D:propertyupdate>");
            writer.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getName() {
        return "PROPPATCH";
    }

    /**
     * Build a new XMLInputFactory.
     *
     * @return XML input factory
     */
    public static XMLInputFactory getXmlInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        return inputFactory;
    }

    protected boolean isStartTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.START_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    protected boolean isEndTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.END_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    List<MultiStatusResponse> responses;

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        Header contentTypeHeader = getResponseHeader("Content-Type");
        if (contentTypeHeader != null && "text/xml".equals(contentTypeHeader.getValue())) {
            responses = new ArrayList<MultiStatusResponse>();
            XMLStreamReader reader;
            try {
                XMLInputFactory xmlInputFactory = getXmlInputFactory();
                reader = xmlInputFactory.createXMLStreamReader(new FilterInputStream(getResponseBodyAsStream()) {
                    @Override
                    public int read() throws IOException {
                        return in.read();
                    }

                });
                while (reader.hasNext()) {
                    reader.next();
                    if (isStartTag(reader, "response")) {
                        handleResponse(reader);
                    }
                }

            } catch (IOException e) {
                logger.error("Error while parsing soap response: " + e, e);
            } catch (XMLStreamException e) {
                logger.error("Error while parsing soap response: " + e, e);
            }
        }
    }

    protected void handleResponse(XMLStreamReader reader) throws XMLStreamException {
        MultiStatusResponse multiStatusResponse = null;
        int currentStatus = 0;
        while (reader.hasNext() && !isEndTag(reader, "response")) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                if ("href".equals(tagLocalName)) {
                    multiStatusResponse = new MultiStatusResponse(reader.getElementText(), "");
                } else if ("status".equals(tagLocalName)) {
                    if ("HTTP/1.1 200 OK".equals(reader.getElementText())) {
                        currentStatus = HttpStatus.SC_OK;
                    }
                } else if ("prop".equals(tagLocalName) && currentStatus == HttpStatus.SC_OK) {
                    handleProperty(reader, multiStatusResponse);
                }
            }
        }

    }

    protected void handleProperty(XMLStreamReader reader, MultiStatusResponse multiStatusResponse) throws XMLStreamException {
        while (reader.hasNext() && !isEndTag(reader, "prop")) {
            try {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagLocalName = reader.getLocalName();
                    Namespace namespace = Namespace.getNamespace(reader.getNamespaceURI());
                    multiStatusResponse.add(new DefaultDavProperty(tagLocalName, reader.getElementText(), namespace));
                }
            } catch (XMLStreamException e) {
                // ignore, exchange invalid response
                logger.debug("Ignore invalid response tag name");
            }
        }
    }

    /**
     * Get Multistatus responses.
     *
     * @return responses
     * @throws HttpException on error
     */
    public List<MultiStatusResponse> getResponses() throws HttpException {
        if (responses == null) {
            throw new HttpException(getStatusLine().toString());
        }
        return responses;
    }

}

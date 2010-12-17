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

import davmail.exchange.XMLStreamUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;

/**
 * Custom Exchange PROPPATCH method.
 * Supports extended property update with type.
 */
public class ExchangePropPatchMethod extends PostMethod {
    protected static final Logger LOGGER = Logger.getLogger(ExchangePropPatchMethod.class);

    static final String TYPE_NAMESPACE = "urn:schemas-microsoft-com:datatypes";
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
            final Map<String, Integer> nameSpaceMap = new HashMap<String, Integer>();
            final Set<PropertyValue> setPropertyValues = new HashSet<PropertyValue>();
            final Set<PropertyValue> deletePropertyValues = new HashSet<PropertyValue>();
            for (PropertyValue propertyValue : propertyValues) {
                // data type namespace
                if (!nameSpaceMap.containsKey(TYPE_NAMESPACE) && propertyValue.getType() != null) {
                    nameSpaceMap.put(TYPE_NAMESPACE, currentChar++);
                }
                // property namespace
                String namespaceUri = propertyValue.getNamespaceUri();
                if (!nameSpaceMap.containsKey(namespaceUri)) {
                    nameSpaceMap.put(namespaceUri, currentChar++);
                }
                if (propertyValue.getXmlEncodedValue() == null) {
                    deletePropertyValues.add(propertyValue);
                } else {
                    setPropertyValues.add(propertyValue);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<D:propertyupdate xmlns:D=\"DAV:\"");
            for (Map.Entry<String, Integer> mapEntry : nameSpaceMap.entrySet()) {
                writer.write(" xmlns:");
                writer.write((char) mapEntry.getValue().intValue());
                writer.write("=\"");
                writer.write(mapEntry.getKey());
                writer.write("\"");
            }
            writer.write(">");
            if (!setPropertyValues.isEmpty()) {
                writer.write("<D:set><D:prop>");
                for (PropertyValue propertyValue : setPropertyValues) {
                    PropertyType propertyType = propertyValue.getType();
                    char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespaceUri()).intValue();
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
                    writer.write(propertyValue.getXmlEncodedValue());
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
                    char nameSpaceChar = (char) nameSpaceMap.get(propertyValue.getNamespaceUri()).intValue();
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

    List<MultiStatusResponse> responses;

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        Header contentTypeHeader = getResponseHeader("Content-Type");
        if (contentTypeHeader != null && "text/xml".equals(contentTypeHeader.getValue())) {
            responses = new ArrayList<MultiStatusResponse>();
            XMLStreamReader reader;
            try {
                reader = XMLStreamUtil.createXMLStreamReader(new FilterInputStream(getResponseBodyAsStream()) {
                    final byte[] lastbytes = new byte[3];

                    @Override
                    public int read(byte[] bytes, int off, int len) throws IOException {
                        int count = in.read(bytes, off, len);
                        // patch invalid element name
                        for (int i = 0; i < count; i++) {
                            byte currentByte = bytes[off + i];
                            if ((lastbytes[0] == '<') && (currentByte >= '0' && currentByte <= '9')) {
                                // move invalid first tag char to valid range
                                bytes[off + i] = (byte) (currentByte + 49);
                            }
                            lastbytes[0] = lastbytes[1];
                            lastbytes[1] = lastbytes[2];
                            lastbytes[2] = currentByte;
                        }
                        return count;
                    }

                });
                while (reader.hasNext()) {
                    reader.next();
                    if (XMLStreamUtil.isStartTag(reader, "response")) {
                        handleResponse(reader);
                    }
                }

            } catch (IOException e) {
                LOGGER.error("Error while parsing soap response: " + e, e);
            } catch (XMLStreamException e) {
                LOGGER.error("Error while parsing soap response: " + e, e);
            }
        }
    }

    protected void handleResponse(XMLStreamReader reader) throws XMLStreamException {
        String href = null;
        String responseStatus = "";
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "response")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("href".equals(tagLocalName)) {
                    href = reader.getElementText();
                } else if ("status".equals(tagLocalName)) {
                    responseStatus = reader.getElementText();
                } else if ("propstat".equals(tagLocalName)) {
                    MultiStatusResponse multiStatusResponse = new MultiStatusResponse(href, responseStatus);
                    handlePropstat(reader, multiStatusResponse);
                    responses.add(multiStatusResponse);
                }
            }
        }

    }

    protected void handlePropstat(XMLStreamReader reader, MultiStatusResponse multiStatusResponse) throws XMLStreamException {
        int propstatStatus = 0;
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "propstat")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("status".equals(tagLocalName)) {
                    if ("HTTP/1.1 200 OK".equals(reader.getElementText())) {
                        propstatStatus = HttpStatus.SC_OK;
                    } else {
                        propstatStatus = 0;
                    }
                } else if ("prop".equals(tagLocalName) && propstatStatus == HttpStatus.SC_OK) {
                    handleProperty(reader, multiStatusResponse);
                }
            }
        }

    }


    protected void handleProperty(XMLStreamReader reader, MultiStatusResponse multiStatusResponse) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "prop")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                Namespace namespace = Namespace.getNamespace(reader.getNamespaceURI());
                multiStatusResponse.add(new DefaultDavProperty(tagLocalName, reader.getElementText(), namespace));
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

    /**
     * Get single Multistatus response.
     *
     * @return response
     * @throws HttpException on error
     */
    public MultiStatusResponse getResponse() throws HttpException {
        if (responses == null || responses.size() != 1) {
            throw new HttpException(getStatusLine().toString());
        }
        return responses.get(0);
    }

    /**
     * Return method http status code.
     *
     * @return http status code
     * @throws HttpException on error
     */
    public int getResponseStatusCode() throws HttpException {
        String responseDescription = getResponse().getResponseDescription();
        if ("HTTP/1.1 201 Created".equals(responseDescription)) {
            return HttpStatus.SC_CREATED;
        } else {
            return HttpStatus.SC_OK;
        }
    }
}

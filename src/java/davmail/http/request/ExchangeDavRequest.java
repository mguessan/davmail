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

package davmail.http.request;

import davmail.exchange.XMLStreamUtil;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class ExchangeDavRequest extends HttpPost implements ResponseHandler<List<MultiStatusResponse>> {
    protected static final Logger LOGGER = Logger.getLogger(ExchangeDavRequest.class);
    private static final String XML_CONTENT_TYPE = "text/xml; charset=UTF-8";

    private HttpResponse response;
    private List<MultiStatusResponse> responses;

    /**
     * Create PROPPATCH method.
     *
     * @param path path
     */
    public ExchangeDavRequest(String path) {
        super(path);
        AbstractHttpEntity httpEntity = new AbstractHttpEntity() {
            byte[] content;

            @Override
            public boolean isRepeatable() {
                return true;
            }

            @Override
            public long getContentLength() {
                if (content == null) {
                    content = generateRequestContent();
                }
                return content.length;
            }

            @Override
            public InputStream getContent() throws UnsupportedOperationException {
                if (content == null) {
                    content = generateRequestContent();
                }
                return new ByteArrayInputStream(content);
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = generateRequestContent();
                }
                outputStream.write(content);
            }

            @Override
            public boolean isStreaming() {
                return false;
            }
        };

        httpEntity.setContentType(XML_CONTENT_TYPE);
        setEntity(httpEntity);
    }

    /**
     * Generate request content from property values.
     *
     * @return request content as byte array
     */
    protected abstract byte[] generateRequestContent();

    @Override
    public List<MultiStatusResponse> handleResponse(HttpResponse response) {
        this.response = response;
        Header contentTypeHeader = response.getFirstHeader("Content-Type");
        if (contentTypeHeader != null && "text/xml".equals(contentTypeHeader.getValue())) {
            responses = new ArrayList<>();
            XMLStreamReader reader;
            try {
                reader = XMLStreamUtil.createXMLStreamReader(new FilterInputStream(response.getEntity().getContent()) {
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

            } catch (IOException | XMLStreamException e) {
                LOGGER.error("Error while parsing soap response: " + e, e);
            }
        }
        return responses;
    }

    protected void handleResponse(XMLStreamReader reader) throws XMLStreamException {
        MultiStatusResponse multiStatusResponse = null;
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
                    if (multiStatusResponse == null) {
                        multiStatusResponse = new MultiStatusResponse(href, responseStatus);
                    }
                    handlePropstat(reader, multiStatusResponse);
                }
            }
        }
        if (multiStatusResponse != null) {
            responses.add(multiStatusResponse);
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
                Namespace namespace = Namespace.getNamespace(reader.getNamespaceURI());
                String tagLocalName = reader.getLocalName();
                if (reader.getAttributeCount() > 0 && "mv.string".equals(reader.getAttributeValue(0))) {
                    handleMultiValuedProperty(reader, multiStatusResponse);
                } else {
                    String tagContent = getTagContent(reader);
                    if (tagContent != null) {
                        multiStatusResponse.add(new DefaultDavProperty<>(tagLocalName, tagContent, namespace));
                    }
                }
            }
        }
    }

    protected void handleMultiValuedProperty(XMLStreamReader reader, MultiStatusResponse multiStatusResponse) throws XMLStreamException {
        String tagLocalName = reader.getLocalName();
        Namespace namespace = Namespace.getNamespace(reader.getNamespaceURI());
        ArrayList<String> values = new ArrayList<>();
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, tagLocalName)) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagContent = getTagContent(reader);
                if (tagContent != null) {
                    values.add(tagContent);
                }
            }
        }
        multiStatusResponse.add(new DefaultDavProperty<>(tagLocalName, values, namespace));
    }

    protected String getTagContent(XMLStreamReader reader) throws XMLStreamException {
        String value = null;
        String tagLocalName = reader.getLocalName();
        while (reader.hasNext() &&
                !((reader.getEventType() == XMLStreamConstants.END_ELEMENT) && tagLocalName.equals(reader.getLocalName()))) {
            reader.next();
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
                value = reader.getText();
            }
        }
        // empty tag
        if (!reader.hasNext()) {
            throw new XMLStreamException("End element for " + tagLocalName + " not found");
        }
        return value;
    }

    /**
     * Get Multistatus responses.
     *
     * @return responses
     * @throws HttpResponseException on error
     */
    public MultiStatusResponse[] getResponses() throws HttpResponseException {
        if (responses == null) {
            // TODO: compare with native HttpClient error handling
            throw new HttpResponseException(response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase());
        }
        return responses.toArray(new MultiStatusResponse[0]);
    }

    /**
     * Get single Multistatus response.
     *
     * @return response
     * @throws HttpResponseException on error
     */
    public MultiStatusResponse getResponse() throws HttpResponseException {
        if (responses == null || responses.size() != 1) {
            throw new HttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        }
        return responses.get(0);
    }

    /**
     * Return method http status code.
     *
     * @return http status code
     * @throws HttpResponseException on error
     */
    public int getResponseStatusCode() throws HttpResponseException {
        String responseDescription = getResponse().getResponseDescription();
        if ("HTTP/1.1 201 Created".equals(responseDescription)) {
            return HttpStatus.SC_CREATED;
        } else {
            return HttpStatus.SC_OK;
        }
    }

}

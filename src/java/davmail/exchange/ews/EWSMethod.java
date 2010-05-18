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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * EWS SOAP method.
 */
public abstract class EWSMethod extends PostMethod {
    protected static final Logger logger = Logger.getLogger(EWSMethod.class);

    protected FolderQueryTraversalType traversal;
    protected BaseShapeType baseShape;
    protected FolderIdType folderId;
    protected FolderIdType parentFolderId;


    /**
     * Build EWS method
     */
    public EWSMethod() {
        super("/ews/exchange.asmx");
        setRequestEntity(new RequestEntity() {
            byte[] content;

            public boolean isRepeatable() {
                return true;
            }

            public void writeRequest(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = generateSoapEnvelope();
                }
                outputStream.write(content);
            }

            public long getContentLength() {
                if (content == null) {
                    content = generateSoapEnvelope();
                }
                return content.length;
            }

            public String getContentType() {
                return "text/xml;charset=UTF-8";
            }
        });
    }


    @Override
    public String getName() {
        return "POST";
    }

    protected void setBaseShape(BaseShapeType baseShapeType) {
        this.baseShape = baseShapeType;
    }

    protected void setFolderId(FolderIdType folderId) {
        this.folderId = folderId;
    }

    protected void setParentFolderId(FolderIdType folderId) {
        this.parentFolderId = folderId;
    }

    protected void writeShape(Writer writer) throws IOException {
        if (baseShape != null) {
            writer.write("<m:");
            writer.write(getItemType());
            writer.write("Shape>");
            baseShape.write(writer);
            writer.write("</m:");
            writer.write(getItemType());
            writer.write("Shape>");
        }
    }

    protected void writeFolderId(Writer writer) throws IOException {
        if (folderId != null) {
            writer.write("<m:FolderIds>");
            folderId.write(writer);
            writer.write("</m:FolderIds>");
        }
    }

    protected void writeParentFolderId(Writer writer) throws IOException {
        if (parentFolderId != null) {
            writer.write("<m:ParentFolderIds>");
            parentFolderId.write(writer);
            writer.write("</m:ParentFolderIds>");
        }
    }

    protected byte[] generateSoapEnvelope() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "xmlns:t=\"http://schemas.microsoft.com/exchange/services/2006/types\" " +
                    "xmlns:m=\"http://schemas.microsoft.com/exchange/services/2006/messages\">" +
                    "<soap:Body>");
            writer.write("<m:");
            writer.write(getMethodName());
            if (traversal != null) {
                traversal.write(writer);
            }
            writer.write(">");
            writeSoapBody(writer);
            writer.write("</m:");
            writer.write(getMethodName());
            writer.write(">");
            writer.write("</soap:Body>" +
                    "</soap:Envelope>");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    protected void writeSoapBody(Writer writer) throws IOException {
        writeShape(writer);
        writeParentFolderId(writer);
        writeFolderId(writer);
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

    class Item extends HashMap {
        public String id;
        public String changeKey;
        public String displayName;
        public String type;

        @Override
        public String toString() {
            return "type: " + type + " id: " + id + " changeKey:" + changeKey + " displayName:" + displayName + ' ' + super.toString();
        }
    }

    protected List<Item> responseItems;
    protected String errorDetail;

    protected abstract String getMethodName();

    protected abstract String getItemType();

    protected abstract String getResponseItemId();

    protected abstract String getResponseCollectionName();

    public List<Item> getResponseItems() {
        return responseItems;
    }

    protected String handleTag(XMLStreamReader reader, String localName) throws XMLStreamException {
        String result = null;
        int event = reader.getEventType();
        if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) {
            while (reader.hasNext() &&
                    !((event == XMLStreamConstants.END_ELEMENT && localName.equals(reader.getLocalName())))) {
                event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS) {
                    result = reader.getText();
                }
            }
        }
        return result;
    }

    protected void handleErrors(XMLStreamReader reader) throws XMLStreamException {
        String result = handleTag(reader, "ResponseCode");
        if (result != null && !"NoError".equals(result)) {
            errorDetail = result;
        }
        result = handleTag(reader, "faultstring");
        if (result != null) {
            errorDetail = result;
        }
    }

    protected boolean isStartTag(XMLStreamReader reader) {
        return (reader.getEventType() == XMLStreamConstants.START_ELEMENT);
    }

    protected boolean isStartTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.START_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    protected boolean isEndTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.END_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    protected Item handleItem(XMLStreamReader reader) throws XMLStreamException {
        Item item = new Item();
        item.type = reader.getLocalName();
        while (reader.hasNext() && !isEndTag(reader, item.type)) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                String value = null;
                if (tagLocalName.endsWith("Id")) {
                    value = getIdAttributeValue(reader);
                }
                if (value == null) {
                    value = getTagContent(reader);
                }
                if (value != null) {
                    item.put(tagLocalName, value);
                }
            }
        }
        return item;
    }

    private String getTagContent(XMLStreamReader reader) throws XMLStreamException {
        String tagLocalName = reader.getLocalName();
        while (reader.hasNext() && !(reader.getEventType() == XMLStreamConstants.END_ELEMENT)) {
            reader.next();
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
                return reader.getText();
            }
        }
        // empty tag
        if (reader.hasNext()) {
            return null;
        } else {
            throw new XMLStreamException("End element for " + tagLocalName + " not found");
        }
    }

    protected String getIdAttributeValue(XMLStreamReader reader) throws XMLStreamException {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("Id".equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        Header contentTypeHeader = getResponseHeader("Content-Type");
        if (contentTypeHeader != null && "text/xml; charset=utf-8".equals(contentTypeHeader.getValue())) {
            responseItems = new ArrayList<Item>();
            XMLStreamReader reader = null;
            try {
                XMLInputFactory xmlInputFactory = getXmlInputFactory();
                reader = xmlInputFactory.createXMLStreamReader(getResponseBodyAsStream());
                while (reader.hasNext()) {
                    reader.next();
                    handleErrors(reader);
                    if (isStartTag(reader, getResponseCollectionName())) {
                        handleItems(reader);
                    }
                }

            } catch (IOException e) {
                logger.error("Error while parsing soap response: " + e, e);
            } catch (XMLStreamException e) {
                logger.error("Error while parsing soap response: " + e, e);
            }
            if (errorDetail != null) {
                logger.error(errorDetail);
            }
        }
    }

    private void handleItems(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext() && !isEndTag(reader, getResponseCollectionName())) {
            reader.next();
            if (isStartTag(reader)) {
                responseItems.add(handleItem(reader));
            }
        }

    }

}

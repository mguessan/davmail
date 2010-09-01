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

import davmail.exchange.XMLStreamUtil;
import davmail.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;

/**
 * EWS SOAP method.
 */
public abstract class EWSMethod extends PostMethod {
    protected static final Logger logger = Logger.getLogger(EWSMethod.class);

    protected FolderQueryTraversal traversal;
    protected BaseShape baseShape;
    protected boolean includeMimeContent;
    protected FolderId folderId;
    protected FolderId savedItemFolderId;
    protected FolderId toFolderId;
    protected FolderId parentFolderId;
    protected ItemId itemId;
    protected ItemId parentItemId;
    protected Set<FieldURI> additionalProperties;
    protected Disposal deleteType;
    protected Set<AttributeOption> methodOptions;
    protected ElementOption unresolvedEntry;
    protected int maxCount;

    protected Set<FieldUpdate> updates;

    protected FileAttachment attachment;

    protected String attachmentId;

    protected final String itemType;
    protected final String methodName;
    protected final String responseCollectionName;

    protected List<Item> responseItems;
    protected String errorDetail;
    protected Item item;

    protected SearchExpression searchExpression;

    protected String serverVersion;

    /**
     * Build EWS method
     *
     * @param itemType   item type
     * @param methodName method name
     */
    public EWSMethod(String itemType, String methodName) {
        this(itemType, methodName, itemType + 's');
    }

    /**
     * Build EWS method
     *
     * @param itemType               item type
     * @param methodName             method name
     * @param responseCollectionName item response collection name
     */
    public EWSMethod(String itemType, String methodName, String responseCollectionName) {
        super("/ews/exchange.asmx");
        this.itemType = itemType;
        this.methodName = methodName;
        this.responseCollectionName = responseCollectionName;
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
                return "text/xml; charset=UTF-8";
            }
        });
    }


    @Override
    public String getName() {
        return "POST";
    }

    protected void addAdditionalProperty(FieldURI additionalProperty) {
        if (additionalProperties == null) {
            additionalProperties = new HashSet<FieldURI>();
        }
        additionalProperties.add(additionalProperty);
    }

    protected void addMethodOption(AttributeOption attributeOption) {
        if (methodOptions == null) {
            methodOptions = new HashSet<AttributeOption>();
        }
        methodOptions.add(attributeOption);
    }

    protected void setSearchExpression(SearchExpression searchExpression) {
        this.searchExpression = searchExpression;
    }

    protected void writeShape(Writer writer) throws IOException {
        if (baseShape != null) {
            writer.write("<m:");
            writer.write(itemType);
            writer.write("Shape>");
            baseShape.write(writer);
            if (includeMimeContent) {
                writer.write("<t:IncludeMimeContent>true</t:IncludeMimeContent>");
            }
            if (additionalProperties != null) {
                writer.write("<t:AdditionalProperties>");
                StringBuilder buffer = new StringBuilder();
                for (FieldURI fieldURI : additionalProperties) {
                    fieldURI.appendTo(buffer);
                }
                writer.write(buffer.toString());
                writer.write("</t:AdditionalProperties>");
            }
            writer.write("</m:");
            writer.write(itemType);
            writer.write("Shape>");
        }
    }

    protected void writeItemId(Writer writer) throws IOException {
        if (itemId != null) {
            if (updates == null) {
                writer.write("<m:ItemIds>");
            }
            itemId.write(writer);
            if (updates == null) {
                writer.write("</m:ItemIds>");
            }
        }
    }

    protected void writeParentItemId(Writer writer) throws IOException {
        if (parentItemId != null) {
            writer.write("<m:ParentItemId Id=\"");
            writer.write(parentItemId.id);
            if (parentItemId.changeKey != null) {
                writer.write("\" ChangeKey=\"");
                writer.write(parentItemId.changeKey);
            }
            writer.write("\"/>");
        }
    }

    protected void writeFolderId(Writer writer) throws IOException {
        if (folderId != null) {
            if (updates == null) {
                writer.write("<m:FolderIds>");
            }
            folderId.write(writer);
            if (updates == null) {
                writer.write("</m:FolderIds>");
            }
        }
    }

    protected void writeSavedItemFolderId(Writer writer) throws IOException {
        if (savedItemFolderId != null) {
            writer.write("<m:SavedItemFolderId>");
            savedItemFolderId.write(writer);
            writer.write("</m:SavedItemFolderId>");
        }
    }

    protected void writeToFolderId(Writer writer) throws IOException {
        if (toFolderId != null) {
            writer.write("<m:ToFolderId>");
            toFolderId.write(writer);
            writer.write("</m:ToFolderId>");
        }
    }

    protected void writeParentFolderId(Writer writer) throws IOException {
        if (parentFolderId != null) {
            writer.write("<m:ParentFolderId");
            if (item == null) {
                writer.write("s");
            }
            writer.write(">");
            parentFolderId.write(writer);
            writer.write("</m:ParentFolderId");
            if (item == null) {
                writer.write("s");
            }
            writer.write(">");
        }
    }

    protected void writeItem(Writer writer) throws IOException {
        if (item != null) {
            writer.write("<m:");
            writer.write(itemType);
            writer.write("s>");
            item.write(writer);
            writer.write("</m:");
            writer.write(itemType);
            writer.write("s>");
        }
    }

    protected void writeRestriction(Writer writer) throws IOException {
        if (searchExpression != null) {
            writer.write("<m:Restriction>");
            StringBuilder buffer = new StringBuilder();
            searchExpression.appendTo(buffer);
            writer.write(buffer.toString());
            writer.write("</m:Restriction>");
        }
    }

    protected void startChanges(Writer writer) throws IOException {
        //noinspection VariableNotUsedInsideIf
        if (updates != null) {
            writer.write("<m:");
            writer.write(itemType);
            writer.write("Changes>");
            writer.write("<t:");
            writer.write(itemType);
            writer.write("Change>");
        }
    }

    protected void writeUpdates(Writer writer) throws IOException {
        if (updates != null) {
            writer.write("<t:Updates>");
            // write extended properties first
            for (FieldUpdate fieldUpdate : updates) {
                if (fieldUpdate.fieldURI instanceof ExtendedFieldURI) {
                    fieldUpdate.write(itemType, writer);
                }
            }
            for (FieldUpdate fieldUpdate : updates) {
                if (!(fieldUpdate.fieldURI instanceof ExtendedFieldURI)) {
                    fieldUpdate.write(itemType, writer);
                }
            }
            writer.write("</t:Updates>");
        }
    }

    protected void writeUnresolvedEntry(Writer writer) throws IOException {
        if (unresolvedEntry != null) {
            unresolvedEntry.write(writer);
        }
    }

    protected void endChanges(Writer writer) throws IOException {
        //noinspection VariableNotUsedInsideIf
        if (updates != null) {
            writer.write("</t:");
            writer.write(itemType);
            writer.write("Change>");
            writer.write("</m:");
            writer.write(itemType);
            writer.write("Changes>");
        }
    }

    protected byte[] generateSoapEnvelope() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "xmlns:t=\"http://schemas.microsoft.com/exchange/services/2006/types\" " +
                    "xmlns:m=\"http://schemas.microsoft.com/exchange/services/2006/messages\">" +
                    "");
            if (serverVersion != null) {
                writer.write("<soap:Header><t:RequestServerVersion Version=\"");
                writer.write(serverVersion);
                writer.write("\"/></soap:Header>");
            }

            writer.write("<soap:Body>");
            writer.write("<m:");
            writer.write(methodName);
            if (traversal != null) {
                traversal.write(writer);
            }
            if (deleteType != null) {
                deleteType.write(writer);
            }
            if (methodOptions != null) {
                for (AttributeOption attributeOption : methodOptions) {
                    attributeOption.write(writer);
                }
            }
            writer.write(">");
            writeSoapBody(writer);
            writer.write("</m:");
            writer.write(methodName);
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
        startChanges(writer);
        writeShape(writer);
        writeIndexedPageItemView(writer);
        writeRestriction(writer);
        writeParentFolderId(writer);
        writeToFolderId(writer);
        writeItemId(writer);
        writeParentItemId(writer);
        writeAttachments(writer);
        writeAttachmentId(writer);
        writeFolderId(writer);
        writeSavedItemFolderId(writer);
        writeItem(writer);
        writeUpdates(writer);
        writeUnresolvedEntry(writer);
        endChanges(writer);
    }


    protected void writeIndexedPageItemView(Writer writer) throws IOException {
        if (maxCount > 0) {
            writer.write("<m:IndexedPageItemView MaxEntriesReturned=\"");
            writer.write(String.valueOf(maxCount));
            writer.write("\" Offset=\"0\" BasePoint=\"Beginning\"/>");
        }
    }

    protected void writeAttachmentId(Writer writer) throws IOException {
        if (attachmentId != null) {
            if ("CreateAttachment".equals(methodName)) {
                writer.write("<m:AttachmentShape>");
                writer.write("<t:IncludeMimeContent>true</t:IncludeMimeContent>");
                writer.write("</m:AttachmentShape>");
            }
            writer.write("<m:AttachmentIds>");
            writer.write("<t:AttachmentId Id=\"");
            writer.write(attachmentId);
            writer.write("\"/>");
            writer.write("</m:AttachmentIds>");
        }
    }

    protected void writeAttachments(Writer writer) throws IOException {
        if (attachment != null) {
            writer.write("<m:Attachments>");
            attachment.write(writer);
            writer.write("</m:Attachments>");
        }
    }

    /**
     * Get Exchange server version, Exchange2010 or Exchange2007_SP1
     *
     * @return server version
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Set Exchange server version, Exchange2010 or Exchange2007_SP1
     *
     * @param serverVersion server version
     */
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * Item
     */
    public static class Item extends HashMap<String, String> {
        /**
         * Item type.
         */
        public String type;
        protected byte[] mimeContent;
        protected Set<FieldUpdate> fieldUpdates;
        protected List<FileAttachment> attachments;
        protected List<String> fieldNames = new ArrayList<String>();

        @Override
        public String toString() {
            return "type: " + type + ' ' + super.toString();
        }

        @Override
        public String put(String key, String value) {
            if (get(key) == null) {
                fieldNames.add(key);
            }
            return super.put(key, value);
        }

        /**
         * Write XML content to writer.
         *
         * @param writer writer
         * @throws IOException on error
         */
        public void write(Writer writer) throws IOException {
            writer.write("<t:");
            writer.write(type);
            writer.write(">");
            // write ordered fields
            for (String key:fieldNames) {
                if ("MeetingTimeZone".equals(key)) {
                    writer.write("<t:MeetingTimeZone TimeZoneName=\"");
                    writer.write(StringUtil.xmlEncode(get(key)));
                    writer.write("\"></t:MeetingTimeZone>");
                } else if ("StartTimeZone".equals(key)) {
                    writer.write("<t:StartTimeZone Id=\"");
                    writer.write(StringUtil.xmlEncode(get(key)));
                    writer.write("\"></t:StartTimeZone>");
                } else {
                    writer.write("<t:");
                    writer.write(key);
                    writer.write(">");
                    writer.write(StringUtil.xmlEncode(get(key)));
                    writer.write("</t:");
                    writer.write(key);
                    writer.write(">");
                }
            }
            if (mimeContent != null) {
                writer.write("<t:MimeContent>");
                writer.write(new String(mimeContent));
                writer.write("</t:MimeContent>");
            }
            if (fieldUpdates != null) {
                for (FieldUpdate fieldUpdate : fieldUpdates) {
                    fieldUpdate.write(null, writer);
                }
            }
            writer.write("</t:");
            writer.write(type);
            writer.write(">");
        }

        /**
         * Field updates.
         *
         * @param fieldUpdates field updates
         */
        public void setFieldUpdates(Set<FieldUpdate> fieldUpdates) {
            this.fieldUpdates = fieldUpdates;
        }

        /**
         * Get property value as int
         *
         * @param key property response name
         * @return property value
         */
        public int getInt(String key) {
            int result = 0;
            String value = get(key);
            if (value != null && value.length() > 0) {
                result = Integer.parseInt(value);
            }
            return result;
        }

        /**
         * Get property value as long
         *
         * @param key property response name
         * @return property value
         */
        public long getLong(String key) {
            long result = 0;
            String value = get(key);
            if (value != null && value.length() > 0) {
                result = Long.parseLong(value);
            }
            return result;
        }


        /**
         * Get property value as boolean
         *
         * @param key property response name
         * @return property value
         */
        public boolean getBoolean(String key) {
            boolean result = false;
            String value = get(key);
            if (value != null && value.length() > 0) {
                result = Boolean.parseBoolean(value);
            }
            return result;
        }

        /**
         * Get file attachment by file name
         *
         * @param attachmentName attachment name
         * @return attachment
         */
        public FileAttachment getAttachmentByName(String attachmentName) {
            FileAttachment result = null;
            if (attachments != null) {
                for (FileAttachment attachment : attachments) {
                    if (attachmentName.equals(attachment.name)) {
                        result = attachment;
                        break;
                    }
                }
            }
            return result;
        }
    }

    /**
     * Check method success.
     *
     * @throws EWSException on error
     */
    public void checkSuccess() throws EWSException {
        if (errorDetail != null) {
            if (!"ErrorAccessDenied".equals(errorDetail)
                    && !"ErrorMailRecipientNotFound".equals(errorDetail)) {
                try {
                    throw new EWSException(errorDetail + "\n request: " + new String(generateSoapEnvelope(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new EWSException(e.getMessage());
                }
            }
        }
    }

    @Override
    public int getStatusCode() {
        if ("ErrorAccessDenied".equals(errorDetail)) {
            return HttpStatus.SC_FORBIDDEN;
        } else {
            return super.getStatusCode();
        }
    }

    /**
     * Get response items.
     *
     * @return response items
     * @throws EWSException on error
     */
    public List<Item> getResponseItems() throws EWSException {
        checkSuccess();
        if (responseItems != null) {
            return responseItems;
        } else {
            return new ArrayList<Item>();
        }
    }

    /**
     * Get single response item.
     *
     * @return response item
     * @throws EWSException on error
     */
    public Item getResponseItem() throws EWSException {
        checkSuccess();
        if (responseItems != null && responseItems.size() == 1) {
            return responseItems.get(0);
        } else {
            return null;
        }
    }

    /**
     * Get response mime content.
     *
     * @return mime content
     * @throws EWSException on error
     */
    public byte[] getMimeContent() throws EWSException {
        checkSuccess();
        Item responseItem = getResponseItem();
        if (responseItem != null) {
            return responseItem.mimeContent;
        } else {
            return null;
        }
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
        if (errorDetail == null && result != null
                && !"NoError".equals(result)
                && !"ErrorNameResolutionMultipleResults".equals(result)
                && !"ErrorNameResolutionNoResults".equals(result)
                && !"ErrorFolderExists".equals(result)
                ) {
            errorDetail = result;
        }
        if (XMLStreamUtil.isStartTag(reader, "faultstring")) {
            errorDetail = reader.getElementText();
        }
    }

    protected Item handleItem(XMLStreamReader reader) throws XMLStreamException {
        Item responseItem = new Item();
        responseItem.type = reader.getLocalName();
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, responseItem.type)) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                String value = null;
                if ("ExtendedProperty".equals(tagLocalName)) {
                    addExtendedPropertyValue(reader, responseItem);
                } else if (tagLocalName.endsWith("MimeContent")) {
                    handleMimeContent(reader, responseItem);
                } else if ("Attachments".equals(tagLocalName)) {
                    responseItem.attachments = handleAttachments(reader);
                } else {
                    if (tagLocalName.endsWith("Id")) {
                        value = getAttributeValue(reader, "Id");
                        // get change key
                        responseItem.put("ChangeKey", getAttributeValue(reader, "ChangeKey"));
                    }
                    if (value == null) {
                        value = getTagContent(reader);
                    }
                    if (value != null) {
                        responseItem.put(tagLocalName, value);
                    }
                }
            }
        }
        return responseItem;
    }

    protected List<FileAttachment> handleAttachments(XMLStreamReader reader) throws XMLStreamException {
        List<FileAttachment> attachments = new ArrayList<FileAttachment>();
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "Attachments"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("FileAttachment".equals(tagLocalName)) {
                    attachments.add(handleFileAttachment(reader));
                }
            }
        }
        return attachments;
    }

    protected FileAttachment handleFileAttachment(XMLStreamReader reader) throws XMLStreamException {
        FileAttachment fileAttachment = new FileAttachment();
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "FileAttachment"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("AttachmentId".equals(tagLocalName)) {
                    fileAttachment.attachmentId = getAttributeValue(reader, "Id");
                } else if ("Name".equals(tagLocalName)) {
                    fileAttachment.name = getTagContent(reader);
                } else if ("ContentType".equals(tagLocalName)) {
                    fileAttachment.contentType = getTagContent(reader);
                }
            }
        }
        return fileAttachment;
    }


    protected void handleMimeContent(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        byte[] base64MimeContent = reader.getElementText().getBytes();
        responseItem.mimeContent = Base64.decodeBase64(base64MimeContent);
    }

    protected void addExtendedPropertyValue(XMLStreamReader reader, Item item) throws XMLStreamException {
        String propertyTag = null;
        String propertyValue = null;
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "ExtendedProperty"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("ExtendedFieldURI".equals(tagLocalName)) {
                    propertyTag = getAttributeValue(reader, "PropertyTag");
                    // property name is in PropertyId or PropertyName with DistinguishedPropertySetId
                    if (propertyTag == null) {
                        propertyTag = getAttributeValue(reader, "PropertyId");
                    }
                    if (propertyTag == null) {
                        propertyTag = getAttributeValue(reader, "PropertyName");
                    }
                } else if ("Value".equals(tagLocalName)) {
                    propertyValue = reader.getElementText();
                } else if ("Values".equals(tagLocalName)) {
                    StringBuilder buffer = new StringBuilder();
                    while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "Values"))) {
                        reader.next();
                        if (XMLStreamUtil.isStartTag(reader)) {

                            if (buffer.length() > 0) {
                                buffer.append(',');
                            }
                            buffer.append(reader.getElementText());
                        }
                    }
                    propertyValue = buffer.toString();
                }
            }
        }
        if ((propertyTag != null) && (propertyValue != null)) {
            item.put(propertyTag, propertyValue);
        }
    }

    protected String getTagContent(XMLStreamReader reader) throws XMLStreamException {
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

    protected String getAttributeValue(XMLStreamReader reader, String attributeName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (attributeName.equals(reader.getAttributeLocalName(i))) {
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
            XMLStreamReader reader;
            try {
                reader = XMLStreamUtil.createXMLStreamReader(getResponseBodyAsStream());
                while (reader.hasNext()) {
                    reader.next();
                    handleErrors(reader);
                    if (serverVersion == null && XMLStreamUtil.isStartTag(reader, "ServerVersionInfo")) {
                        String majorVersion = getAttributeValue(reader, "MajorVersion");
                        if ("14".equals(majorVersion)) {
                            serverVersion = "Exchange2010";
                        } else {
                            serverVersion = "Exchange2007_SP1";
                        }
                    } else if (XMLStreamUtil.isStartTag(reader, responseCollectionName)) {
                        handleItems(reader);
                    } else {
                        handleCustom(reader);
                    }
                }

            } catch (IOException e) {
                logger.error("Error while parsing soap response: " + e, e);
            } catch (XMLStreamException e) {
                logger.error("Error while parsing soap response: " + e, e);
            }
            if (errorDetail != null) {
                logger.debug(errorDetail);
            }
        }
    }

    @SuppressWarnings({"NoopMethodInAbstractClass"})
    protected void handleCustom(XMLStreamReader reader) throws XMLStreamException {
        // override to handle custom content
    }

    private void handleItems(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, responseCollectionName)) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                responseItems.add(handleItem(reader));
            }
        }

    }

}

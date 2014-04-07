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

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exchange.XMLStreamUtil;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.StringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.codehaus.stax2.typed.TypedXMLStreamReader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * EWS SOAP method.
 */
@SuppressWarnings("Since15")
public abstract class EWSMethod extends PostMethod {
    protected static final Logger LOGGER = Logger.getLogger(EWSMethod.class);

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

    // paging request
    protected int maxCount;
    protected int offset;
    // paging response
    protected boolean includesLastItemInRange;

    protected List<FieldUpdate> updates;

    protected FileAttachment attachment;

    protected String attachmentId;

    protected final String itemType;
    protected final String methodName;
    protected final String responseCollectionName;

    protected List<Item> responseItems;
    protected String errorDetail;
    protected String errorDescription;
    protected Item item;

    protected SearchExpression searchExpression;
    protected FieldOrder fieldOrder;

    protected String serverVersion;
    protected String timezoneContext;

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
        if (Settings.getBooleanProperty("davmail.acceptEncodingGzip", true) &&
                !Level.DEBUG.toString().equals(Settings.getProperty("log4j.logger.httpclient.wire"))) {
            setRequestHeader("Accept-Encoding", "gzip");
        }
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

    protected void setFieldOrder(FieldOrder fieldOrder) {
        this.fieldOrder = fieldOrder;
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

    protected void writeSortOrder(Writer writer) throws IOException {
        if (fieldOrder != null) {
            writer.write("<m:SortOrder>");
            StringBuilder buffer = new StringBuilder();
            fieldOrder.appendTo(buffer);
            writer.write(buffer.toString());
            writer.write("</m:SortOrder>");
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
            for (FieldUpdate fieldUpdate : updates) {
                fieldUpdate.write(itemType, writer);
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
            writer.write("<soap:Header>");
            if (serverVersion != null) {
                writer.write("<t:RequestServerVersion Version=\"");
                writer.write(serverVersion);
                writer.write("\"/>");
            }
            if (timezoneContext != null) {
                writer.write("<t:TimeZoneContext><t:TimeZoneDefinition Id=\"");
                writer.write(timezoneContext);
                writer.write("\"/></t:TimeZoneContext>");
            }
            writer.write("</soap:Header>");

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
        writeSortOrder(writer);
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
            writer.write("\" Offset=\"");
            writer.write(String.valueOf(offset));
            writer.write("\" BasePoint=\"Beginning\"/>");

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
     * Get Exchange server version, Exchange2013, Exchange2010 or Exchange2007_SP1
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
     * Set Exchange timezone context
     *
     * @param timezoneContext user timezone context
     */
    public void setTimezoneContext(String timezoneContext) {
        this.timezoneContext = timezoneContext;
    }

    /**
     * Meeting attendee object
     */
    public static class Attendee {
        /**
         * attendee role
         */
        public String role;
        /**
         * attendee email address
         */
        public String email;
        /**
         * attendee participation status
         */
        public String partstat;
        /**
         * attendee fullname
         */
        public String name;
            }

        /**
     * Recurring event occurrence
     */
    public static class Occurrence {
        /**
         * Original occurence start date
         */
        public String originalStart;

        /**
         * Occurence itemid
         */
        public ItemId itemId;
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
        protected List<FieldUpdate> fieldUpdates;
        protected List<FileAttachment> attachments;
        protected List<Attendee> attendees;
        protected final List<String> fieldNames = new ArrayList<String>();
        protected List<Occurrence> occurrences;
        protected ItemId referenceItemId;

        @Override
        public String toString() {
            return "type: " + type + ' ' + super.toString();
        }

        @Override
        public String put(String key, String value) {
            if (value != null) {
                if (get(key) == null) {
                    fieldNames.add(key);
                }
                return super.put(key, value);
            } else {
                return null;
            }
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
            if (mimeContent != null) {
                writer.write("<t:MimeContent>");
                for (byte c : mimeContent) {
                    writer.write(c);
                }
                writer.write("</t:MimeContent>");
            }
            if (referenceItemId != null) {
                referenceItemId.write(writer);
            }
            // write ordered fields
            for (String key : fieldNames) {
                if ("MeetingTimeZone".equals(key)) {
                    writer.write("<t:MeetingTimeZone TimeZoneName=\"");
                    writer.write(StringUtil.xmlEncodeAttribute(get(key)));
                    writer.write("\"></t:MeetingTimeZone>");
                } else if ("StartTimeZone".equals(key)) {
                    writer.write("<t:StartTimeZone Id=\"");
                    writer.write(StringUtil.xmlEncodeAttribute(get(key)));
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
        public void setFieldUpdates(List<FieldUpdate> fieldUpdates) {
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
                for (FileAttachment fileAttachment : attachments) {
                    if (attachmentName.equals(fileAttachment.name)) {
                        result = fileAttachment;
                        break;
                    }
                }
            }
            return result;
        }

        /**
         * Get all attendees.
         *
         * @return all attendees
         */
        public List<Attendee> getAttendees() {
            return attendees;
        }

        /**
         * Add attendee.
         *
         * @param attendee attendee object
         */
        public void addAttendee(Attendee attendee) {
            if (attendees == null) {
                attendees = new ArrayList<Attendee>();
            }
            attendees.add(attendee);
        }

        /**
         * Add occurrence.
         *
         * @param occurrence event occurence
         */
        public void addOccurrence(Occurrence occurrence) {
            if (occurrences == null) {
                occurrences = new ArrayList<Occurrence>();
            }
            occurrences.add(occurrence);
        }

        /**
         * Get occurences.
         *
         * @return event occurences
         */
        public List<Occurrence> getOccurrences() {
            return occurrences;
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
                    && !"ErrorMailRecipientNotFound".equals(errorDetail)
                    && !"ErrorItemNotFound".equals(errorDetail)
                    ) {
                try {
                    throw new EWSException(errorDetail + ' ' +((errorDescription!=null)?errorDescription:"")+ "\n request: " + new String(generateSoapEnvelope(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new EWSException(e.getMessage());
                }
            }
        }
        if (getStatusCode() == HttpStatus.SC_BAD_REQUEST || getStatusCode() == HttpStatus.SC_INSUFFICIENT_STORAGE ) {
             throw new EWSException(getStatusText());
        }
    }

    @Override
    public int getStatusCode() {
        if ("ErrorAccessDenied".equals(errorDetail)) {
            return HttpStatus.SC_FORBIDDEN;
        } else if ("ErrorItemNotFound".equals(errorDetail)) {
            return HttpStatus.SC_NOT_FOUND;
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
        if (responseItems != null && responseItems.size() > 0) {
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
        // store error description;
        String messageText = handleTag(reader, "MessageText");
        if (messageText != null) {
            errorDescription = messageText;
        }
        if (errorDetail == null && result != null
                && !"NoError".equals(result)
                && !"ErrorNameResolutionMultipleResults".equals(result)
                && !"ErrorNameResolutionNoResults".equals(result)
                && !"ErrorFolderExists".equals(result)
                ) {
            errorDetail = result;
        }
        if (XMLStreamUtil.isStartTag(reader, "faultstring")) {
            errorDetail = XMLStreamUtil.getElementText(reader);
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
                } else if ("EmailAddresses".equals(tagLocalName)) {
                    handleEmailAddresses(reader, responseItem);
                } else if ("RequiredAttendees".equals(tagLocalName) || "OptionalAttendees".equals(tagLocalName)) {
                    handleAttendees(reader, responseItem, tagLocalName);
                } else if ("ModifiedOccurrences".equals(tagLocalName)) {
                    handleModifiedOccurrences(reader, responseItem);
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

    protected void handleEmailAddresses(XMLStreamReader reader, Item item) throws XMLStreamException {
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "EmailAddresses"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    item.put(reader.getAttributeValue(null, "Key"), XMLStreamUtil.getElementText(reader));
                }
            }
        }
    }

    protected void handleAttendees(XMLStreamReader reader, Item item, String attendeeType) throws XMLStreamException {
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, attendeeType))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Attendee".equals(tagLocalName)) {
                    handleAttendee(reader, item, attendeeType);
                }
            }
        }
    }

    protected void handleModifiedOccurrences(XMLStreamReader reader, Item item) throws XMLStreamException {
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "ModifiedOccurrences"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Occurrence".equals(tagLocalName)) {
                    handleOccurrence(reader, item);
                }
            }
        }
    }

    protected void handleOccurrence(XMLStreamReader reader, Item item) throws XMLStreamException {
        Occurrence occurrence = new Occurrence();
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "Occurrence"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("ItemId".equals(tagLocalName)) {
                    occurrence.itemId = new ItemId("ItemId", getAttributeValue(reader, "Id"), getAttributeValue(reader, "ChangeKey"));
                }
                if ("OriginalStart".equals(tagLocalName)) {
                    occurrence.originalStart = XMLStreamUtil.getElementText(reader);
                }
            }
        }
        item.addOccurrence(occurrence);
    }

    /**
     * Convert response type to partstat value
     * @param responseType response type
     * @return partstat value
     */
    public static String responseTypeToPartstat(String responseType) {
        if ("Accept".equals(responseType) || "Organizer".equals(responseType)) {
            return "ACCEPTED";
        } else if ("Tentative".equals(responseType)) {
            return "TENTATIVE";
        } else if ("Decline".equals(responseType)) {
            return "DECLINED";
        } else {
            return "NEEDS-ACTION";
        }
    }

    protected void handleAttendee(XMLStreamReader reader, Item item, String attendeeType) throws XMLStreamException {
        Attendee attendee = new Attendee();
        if ("RequiredAttendees".equals(attendeeType)) {
            attendee.role = "REQ-PARTICIPANT";
        } else {
            attendee.role = "OPT-PARTICIPANT";
        }
        while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "Attendee"))) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("EmailAddress".equals(tagLocalName)) {
                    attendee.email = reader.getElementText();
                } else if ("Name".equals(tagLocalName)) {
                    attendee.name = XMLStreamUtil.getElementText(reader);
                } else if ("ResponseType".equals(tagLocalName)) {
                    String responseType = XMLStreamUtil.getElementText(reader);
                    attendee.partstat = responseTypeToPartstat(responseType);
                }
            }
        }
        item.addAttendee(attendee);
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
        if (reader instanceof TypedXMLStreamReader) {
            // Stax2 parser: use enhanced base64 conversion
            responseItem.mimeContent = ((TypedXMLStreamReader) reader).getElementAsBinary();
        } else {
            // failover: slow and memory consuming conversion
            try {
                responseItem.mimeContent = Base64.decodeBase64(reader.getElementText().getBytes("ASCII"));
            } catch (UnsupportedEncodingException e) {
                throw new XMLStreamException(e);
            }
        }
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
                    propertyValue = XMLStreamUtil.getElementText(reader);
                } else if ("Values".equals(tagLocalName)) {
                    StringBuilder buffer = new StringBuilder();
                    while (reader.hasNext() && !(XMLStreamUtil.isEndTag(reader, "Values"))) {
                        reader.next();
                        if (XMLStreamUtil.isStartTag(reader)) {

                            if (buffer.length() > 0) {
                                buffer.append(',');
                            }
                            String singleValue = XMLStreamUtil.getElementText(reader);
                            if (singleValue != null) {
                                buffer.append(singleValue);
                            }
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
            try {
                if (DavGatewayHttpClientFacade.isGzipEncoded(this)) {
                    processResponseStream(new GZIPInputStream(getResponseBodyAsStream()));
                } else {
                    processResponseStream(getResponseBodyAsStream());
                }
            } catch (IOException e) {
                LOGGER.error("Error while parsing soap response: " + e, e);
            }
        }
    }

    protected void processResponseStream(InputStream inputStream) {
        responseItems = new ArrayList<Item>();
        XMLStreamReader reader = null;
        try {
            inputStream = new FilterInputStream(inputStream) {
                int totalCount;
                int lastLogCount;

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = super.read(buffer, offset, length);
                    totalCount += count;
                    if (totalCount - lastLogCount > 1024 * 128) {
                        DavGatewayTray.debug(new BundleMessage("LOG_DOWNLOAD_PROGRESS", String.valueOf(totalCount / 1024), EWSMethod.this.getURI()));
                        DavGatewayTray.switchIcon();
                        lastLogCount = totalCount;
                    }
                    return count;
                }
            };
            reader = XMLStreamUtil.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                reader.next();
                handleErrors(reader);
                if (serverVersion == null && XMLStreamUtil.isStartTag(reader, "ServerVersionInfo")) {
                    String majorVersion = getAttributeValue(reader, "MajorVersion");
                    String minorVersion = getAttributeValue(reader, "MinorVersion");
                    if ("15".equals(majorVersion)) {
                        if ("0".equals(minorVersion)) {
                            serverVersion = "Exchange2013";
                        } else {
                            serverVersion = "Exchange2013_SP1";
                        }
                    } else if ("14".equals(majorVersion)) {
                        if ("0".equals(minorVersion)) {
                            serverVersion = "Exchange2010";
                        } else {
                            serverVersion = "Exchange2010_SP1";
                        }
                    } else {
                        serverVersion = "Exchange2007_SP1";
                    }
                } else if (XMLStreamUtil.isStartTag(reader, "RootFolder")) {
                    includesLastItemInRange = "true".equals(reader.getAttributeValue(null, "IncludesLastItemInRange"));
                } else if (XMLStreamUtil.isStartTag(reader, responseCollectionName)) {
                    handleItems(reader);
                } else {
                    handleCustom(reader);
                }
            }
        } catch (XMLStreamException e) {
            LOGGER.error("Error while parsing soap response: " + e, e);
            if (reader != null) {
                try {
                    LOGGER.error("Current text: " + reader.getText());
                } catch (IllegalStateException ise) {
                    LOGGER.error(e+" "+e.getMessage());
                }
            }
        }
        if (errorDetail != null) {
            LOGGER.debug(errorDetail);
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

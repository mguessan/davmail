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

package davmail.exchange.graph;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.DavMailException;
import davmail.exception.HttpForbiddenException;
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.VCalendar;
import davmail.exchange.VObject;
import davmail.exchange.VProperty;
import davmail.exchange.auth.O365Token;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.exchange.ews.ExtendedFieldURI;
import davmail.exchange.ews.Field;
import davmail.exchange.ews.FieldURI;
import davmail.exchange.ews.SearchExpression;
import davmail.http.HttpClientAdapter;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Implement ExchangeSession based on Microsoft Graph
 */
public class GraphExchangeSession extends ExchangeSession {

    /**
     * Graph folder is identified by mailbox and id
     */
    protected class Folder extends ExchangeSession.Folder {
        public FolderId folderId;
        protected String specialFlag = "";

        protected void setSpecialFlag(String specialFlag) {
            this.specialFlag = "\\" + specialFlag + " ";
        }

        /**
         * Get IMAP folder flags.
         *
         * @return folder flags in IMAP format
         */
        @Override
        public String getFlags() {
            if (noInferiors) {
                return specialFlag + "\\NoInferiors";
            } else if (hasChildren) {
                return specialFlag + "\\HasChildren";
            } else {
                return specialFlag + "\\HasNoChildren";
            }
        }
    }

    protected class Event extends ExchangeSession.Event {

        public FolderId folderId;

        public String id;

        protected GraphObject graphObject;

        public Event(FolderId folderId, GraphObject graphObject) {
            this.folderId = folderId;
            this.graphObject = graphObject;

            id = graphObject.optString("id");
            // TODO: test etag
            etag = graphObject.optString("@odata.etag");

            displayName = graphObject.optString("title");
            subject = graphObject.optString("title");

            itemName = StringUtil.base64ToUrl(id) + ".EML";
        }

        public Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) throws IOException {
            super(folderPath, itemName, contentClass, itemBody, etag, noneMatch);
            folderId = getFolderId(folderPath);
        }

        @Override
        public byte[] getEventContent() throws IOException {
            byte[] content;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Get event: " + itemName);
            }
            try {
                if ("IPF.Task".equals(folderId.folderClass)) {
                    VCalendar localVCalendar = new VCalendar();
                    VObject vTodo = new VObject();
                    vTodo.type = "VTODO";
                    localVCalendar.setTimezone(getVTimezone());
                    vTodo.setPropertyValue("LAST-MODIFIED", convertDateFromExchange(graphObject.optString("lastModifiedDateTime")));
                    vTodo.setPropertyValue("CREATED", convertDateFromExchange(graphObject.optString("createdDateTime")));
                    // use item id as uid
                    vTodo.setPropertyValue("UID", graphObject.optString("id"));
                    vTodo.setPropertyValue("TITLE", graphObject.optString("title"));
                    vTodo.setPropertyValue("SUMMARY", graphObject.optString("title"));
                    vTodo.setPropertyValue("DESCRIPTION", graphObject.optString("body", "content"));
                    // TODO refactor
                    vTodo.setPropertyValue("PRIORITY", convertPriorityFromExchange(graphObject.optString("importance")));
                    // not supported over graph
                    //vTodo.setPropertyValue("PERCENT-COMPLETE", );
                    vTodo.setPropertyValue("STATUS", taskTovTodoStatusMap.get(graphObject.optString("status")));

                    vTodo.setPropertyValue("DUE;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("dueDateTime")));
                    vTodo.setPropertyValue("DTSTART;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("startDateTime")));
                    vTodo.setPropertyValue("COMPLETED;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("completedDateTime")));

                    vTodo.setPropertyValue("CATEGORIES", graphObject.optString("categories"));

                    localVCalendar.addVObject(vTodo);
                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    content = new byte[0];
                    /*content = getItemMethod.getMimeContent();
                    if (content == null) {
                        throw new IOException("empty event body");
                    }
                    if (!"CalendarItem".equals(type)) {
                        content = getICS(new SharedByteArrayInputStream(content));
                    }
                    VCalendar localVCalendar = new VCalendar(content, email, getVTimezone());

                    String calendaruid = getItemMethod.getResponseItem().get(Field.get("calendaruid").getResponseName());

                    if ("Exchange2007_SP1".equals(serverVersion)) {
                        // remove additional reminder
                        if (!"true".equals(getItemMethod.getResponseItem().get(Field.get("reminderset").getResponseName()))) {
                            localVCalendar.removeVAlarm();
                        }
                        if (calendaruid != null) {
                            localVCalendar.setFirstVeventPropertyValue("UID", calendaruid);
                        }
                    }
                    fixAttendees(getItemMethod, localVCalendar.getFirstVevent());
                    // fix UID and RECURRENCE-ID, broken at least on Exchange 2007
                    List<EWSMethod.Occurrence> occurences = getItemMethod.getResponseItem().getOccurrences();
                    if (occurences != null) {
                        Iterator<VObject> modifiedOccurrencesIterator = localVCalendar.getModifiedOccurrences().iterator();
                        for (EWSMethod.Occurrence occurrence : occurences) {
                            if (modifiedOccurrencesIterator.hasNext()) {
                                VObject modifiedOccurrence = modifiedOccurrencesIterator.next();
                                // fix modified occurrences attendees
                                GetItemMethod getOccurrenceMethod = new GetItemMethod(BaseShape.ID_ONLY, occurrence.itemId, false);
                                getOccurrenceMethod.addAdditionalProperty(Field.get("requiredattendees"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("optionalattendees"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("modifiedoccurrences"));
                                getOccurrenceMethod.addAdditionalProperty(Field.get("lastmodified"));
                                executeMethod(getOccurrenceMethod);
                                fixAttendees(getOccurrenceMethod, modifiedOccurrence);
                                // LAST-MODIFIED is missing in event content
                                modifiedOccurrence.setPropertyValue("LAST-MODIFIED", convertDateFromExchange(getOccurrenceMethod.getResponseItem().get(Field.get("lastmodified").getResponseName())));

                                // fix uid, should be the same as main VEVENT
                                if (calendaruid != null) {
                                    modifiedOccurrence.setPropertyValue("UID", calendaruid);
                                }

                                VProperty recurrenceId = modifiedOccurrence.getProperty("RECURRENCE-ID");
                                if (recurrenceId != null) {
                                    recurrenceId.removeParam("TZID");
                                    recurrenceId.getValues().set(0, convertDateFromExchange(occurrence.originalStart));
                                }
                            }
                        }
                    }
                    // LAST-MODIFIED is missing in event content
                    localVCalendar.setFirstVeventPropertyValue("LAST-MODIFIED", convertDateFromExchange(getItemMethod.getResponseItem().get(Field.get("lastmodified").getResponseName())));

                    // restore mozilla invitations option
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-SEND-INVITATIONS",
                            getItemMethod.getResponseItem().get(Field.get("xmozsendinvitations").getResponseName()));
                    // restore mozilla alarm status
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-LASTACK",
                            getItemMethod.getResponseItem().get(Field.get("xmozlastack").getResponseName()));
                    localVCalendar.setFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME",
                            getItemMethod.getResponseItem().get(Field.get("xmozsnoozetime").getResponseName()));
                    // overwrite method
                    // localVCalendar.setPropertyValue("METHOD", "REQUEST");
                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);*/
                }
            } catch (Exception e) {
                // TODO test?
                throw buildHttpNotFoundException(e);
            }
            return content;
        }

        @Override
        public ItemResult createOrUpdate() throws IOException {
            ItemResult itemResult = new ItemResult();
            VObject vEvent = vCalendar.getFirstVevent();
            try {
                JSONObject jsonObject = new JSONObject();

                // TODO convert date and timezone
                VProperty dtStart = vEvent.getProperty("DTSTART");
                String dtStartTzid = dtStart.getParamValue("TZID");
                jsonObject.put("start", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(dtStart.getValue(), dtStartTzid)).put("timeZone", dtStartTzid));

                VProperty dtEnd = vEvent.getProperty("DTEND");
                String dtEndTzid = dtEnd.getParamValue("TZID");
                jsonObject.put("end", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(dtEnd.getValue(), dtEndTzid)).put("timeZone", dtEndTzid));

                GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder();
                if (id == null) {
                    graphRequestBuilder.setMethod(HttpPost.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("calendars")
                            .setObjectId(folderId.id)
                            .setChildType("events")
                            .setJsonBody(jsonObject);
                } else {
                    graphRequestBuilder.setMethod(HttpPatch.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("calendars")
                            .setObjectId(folderId.id)
                            .setChildType("events")
                            .setChildId(id)
                            .setJsonBody(jsonObject);
                }

                GraphObject graphResponse = executeGraphRequest(graphRequestBuilder);
                itemResult.status = graphResponse.statusCode;

                // TODO review itemName logic
                itemResult.itemName = graphResponse.optString("id");
                itemResult.etag = graphResponse.optString("etag");


            } catch (JSONException e) {
                throw new IOException(e);
            }

            // TODO handle exception occurrences

            return itemResult;
        }

    }

    private String convertDateTimeTimeZoneToTaskDate(Date exchangeDateValue) {
        String zuluDateValue = null;
        if (exchangeDateValue != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
            dateFormat.setTimeZone(GMT_TIMEZONE);
            zuluDateValue = dateFormat.format(exchangeDateValue);
        }
        return zuluDateValue;

    }

    protected class Contact extends ExchangeSession.Contact {
        // item id
        FolderId folderId;
        String id;

        protected Contact(GraphObject response) throws DavMailException {
            id = response.optString("id");
            etag = response.optString("etag");

            displayName = response.optString("displayname");
            // prefer urlcompname (client provided item name) for contacts
            itemName = StringUtil.decodeUrlcompname(response.optString("urlcompname"));
            // if urlcompname is empty, this is a server created item
            if (itemName == null) {
                itemName = StringUtil.base64ToUrl(id) + ".EML";
            }

            for (String attributeName : ExchangeSession.CONTACT_ATTRIBUTES) {
                if (!attributeName.startsWith("smtpemail")) {
                    String value = response.optString(attributeName);
                    if (value != null && !value.isEmpty()) {
                        if ("bday".equals(attributeName) || "anniversary".equals(attributeName) || "lastmodified".equals(attributeName) || "datereceived".equals(attributeName)) {
                            value = convertDateFromExchange(value);
                        }
                        put(attributeName, value);
                    }
                }
            }
            // TODO refactor
            //String keywords = response.optString("categories");
            //if (keywords != null) {
            //    put("keywords", keywords);
            //}

            JSONArray emailAddresses = response.optJSONArray("emailAddresses");
            for (int i = 0; i < emailAddresses.length(); i++) {
                JSONObject emailAddress = emailAddresses.optJSONObject(i);
                if (emailAddress != null) {
                    String email = emailAddress.optString("address");
                    String type = emailAddress.optString("type");
                    if (email != null && !email.isEmpty()) {
                        if ("other".equals(type)) {
                            put("smtpemail3", email);
                        } else if ("personal".equals(type)) {
                            put("smtpemail2", email);
                        } else if ("work".equals(type)) {
                            put("smtpemail1", email);
                        }
                    }
                }
            }
            // iterate a second time to fill unknown email types
            for (int i = 0; i < emailAddresses.length(); i++) {
                JSONObject emailAddress = emailAddresses.optJSONObject(i);
                if (emailAddress != null) {
                    String email = emailAddress.optString("address");
                    String type = emailAddress.optString("type");
                    if (email != null && !email.isEmpty()) {
                        if ("unknown".equals(type)) {
                            if (get("smtpemail1") == null) {
                                put("smtpemail1", email);
                            } else if (get("smtpemail2") == null) {
                                put("smtpemail2", email);
                            } else if (get("smtpemail3") == null) {
                                put("smtpemail3", email);
                            }
                        }
                    }
                }
            }
        }

        protected Contact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) {
            super(folderPath, itemName, properties, etag, noneMatch);
        }

        /**
         * Empty constructor for GalFind
         */
        protected Contact() {
        }

        /**
         * Create or update contact.
         * <a href="https://learn.microsoft.com/en-us/graph/api/user-post-contacts">user-post-contacts</a>
         *
         * @return action result
         * @throws IOException on error
         */
        @Override
        public ItemResult createOrUpdate() throws IOException {

            FolderId folderId = getFolderId(folderPath);
            String id = null;
            String currentEtag = null;
            JSONObject jsonContact = getContactIfExists(folderId, itemName);
            if (jsonContact != null) {
                id = jsonContact.optString("id", null);
                currentEtag = new GraphObject(jsonContact).optString("etag");
            }

            ItemResult itemResult = new ItemResult();
            if ("*".equals(noneMatch)) {
                // create requested but already exists
                if (id != null) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            } else if (etag != null) {
                // update requested
                if (id == null || !etag.equals(currentEtag)) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            }

            try {
                JSONObject jsonObject = new JSONObject();
                GraphObject graphObject = new GraphObject(jsonObject);
                for (Map.Entry<String, String> entry : entrySet()) {
                    if ("keywords".equals(entry.getKey())) {
                        graphObject.setCategories(entry.getValue());
                    } else if ("bday".equals(entry.getKey())) {
                        graphObject.put(entry.getKey(), convertZuluToIso(entry.getValue()));
                    } else if ("anniversary".equals(entry.getKey())) {
                        graphObject.put(entry.getKey(), convertZuluToDate(entry.getValue()));
                    } else if ("photo".equals(entry.getKey())) {
                        //
                        graphObject.put("haspicture", (get("photo") != null) ? "true" : "false");
                    } else if (!entry.getKey().startsWith("email") && !entry.getKey().startsWith("smtpemail")
                            && !"usersmimecertificate".equals(entry.getKey()) // not supported over Graph
                            && !"msexchangecertificate".equals(entry.getKey()) // not supported over Graph
                            && !"pager".equals(entry.getKey()) && !"otherTelephone".equals(entry.getKey()) // see below
                    ) {
                        //getSingleValueExtendedProperties(jsonObject).put(getSingleValue(entry.getKey(), entry.getValue()));
                        graphObject.put(entry.getKey(), entry.getValue());
                    }
                }

                // pager and otherTelephone is a single field
                String pager = get("pager");
                if (pager == null) {
                    pager = get("otherTelephone");
                }
                //getSingleValueExtendedProperties(jsonObject).put(getSingleValue("pager", pager));
                graphObject.put("pager", pager);

                // force urlcompname
                //getSingleValueExtendedProperties(jsonObject).put(getSingleValue("urlcompname", convertItemNameToEML(itemName)));
                graphObject.put("urlcompname", convertItemNameToEML(itemName));

                // handle emails
                JSONArray emailAddresses = new JSONArray();
                String smtpemail1 = get("smtpemail1");
                if (smtpemail1 != null) {
                    JSONObject emailAddress = new JSONObject();
                    emailAddress.put("address", smtpemail1);
                    emailAddress.put("type", "work");
                    emailAddresses.put(emailAddress);
                }

                String smtpemail2 = get("smtpemail2");
                if (smtpemail2 != null) {
                    JSONObject emailAddress = new JSONObject();
                    emailAddress.put("address", smtpemail2);
                    emailAddress.put("type", "personal");
                    emailAddresses.put(emailAddress);
                }

                String smtpemail3 = get("smtpemail3");
                if (smtpemail3 != null) {
                    JSONObject emailAddress = new JSONObject();
                    emailAddress.put("address", smtpemail3);
                    emailAddress.put("type", "other");
                    emailAddresses.put(emailAddress);
                }
                //jsonObject.put("emailAddresses", emailAddresses);
                graphObject.put("emailAddresses", emailAddresses);

                GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder();
                if (id == null) {
                    graphRequestBuilder.setMethod(HttpPost.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("contactFolders")
                            .setObjectId(folderId.id)
                            .setChildType("contacts")
                            .setJsonBody(jsonObject);
                } else {
                    graphRequestBuilder.setMethod(HttpPatch.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("contactFolders")
                            .setObjectId(folderId.id)
                            .setChildType("contacts")
                            .setChildId(id)
                            .setJsonBody(jsonObject);
                }

                GraphObject graphResponse = executeGraphRequest(graphRequestBuilder);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(graphResponse.toString(4));
                }

                itemResult.status = graphResponse.statusCode;

                updatePhoto(folderId, graphResponse.optString("id"));

                // reload to get latest etag
                graphResponse = new GraphObject(getContactIfExists(folderId, itemName));

                itemResult.itemName = graphResponse.optString("id");
                itemResult.etag = graphResponse.optString("etag");

            } catch (JSONException e) {
                throw new IOException(e);
            }
            if (itemResult.status == HttpStatus.SC_CREATED) {
                LOGGER.debug("Created contact " + getHref());
            } else {
                LOGGER.debug("Updated contact " + getHref());
            }

            return itemResult;
        }

        private void updatePhoto(FolderId folderId, String contactId) throws IOException {
            String photo = get("photo");
            if (photo != null) {
                // convert image to jpeg
                byte[] resizedImageBytes = IOUtil.resizeImage(IOUtil.decodeBase64(photo), 90);

                JSONObject jsonResponse = executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpPut.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("contactFolders")
                        .setObjectId(folderId.id)
                        .setChildType("contacts")
                        .setChildId(contactId)
                        .setChildSuffix("photo/$value")
                        .setContentType("image/jpeg")
                        .setMimeContent(resizedImageBytes));

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(jsonResponse);
                }
            } else {
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpDelete.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("contactFolders")
                        .setObjectId(folderId.id)
                        .setChildType("contacts")
                        .setChildId(contactId)
                        .setChildSuffix("photo"));
            }
        }
    }

    /**
     * Converts a Zulu date-time string to ISO format by removing unnecessary
     * precision in the fractional seconds if present.
     *
     * @param value a date-time string in Zulu format to be converted; may be null.
     * @return the converted date-time string in ISO format, or the original
     *         value if it was null.
     */
    private String convertZuluToIso(String value) {
        if (value != null) {
            return value.replace(".000Z", "Z");
        } else {
            return value;
        }
    }

    /**
     * Converts a Zulu date-time string to a basic day format by extracting the
     * date portion of the string before the "T" character.
     *
     * @param value a date-time string in Zulu format to be converted; may be null.
     *              The string is expected to contain a "T" character separating
     *              the date and time portions.
     * @return the extracted date portion as a string if the input contains "T",
     *         or the original input string if the "T" is not present or the input is null.
     */
    private String convertZuluToDate(String value) {
        if (value != null && value.contains("T")) {
            return value.substring(0, value.indexOf("T"));
        } else {
            return value;
        }
    }

    // special folders https://learn.microsoft.com/en-us/graph/api/resources/mailfolder
    @SuppressWarnings("SpellCheckingInspection")
    public enum WellKnownFolderName {
        archive,
        deleteditems,
        calendar, contacts, tasks,
        drafts, inbox, outbox, sentitems, junkemail,
        msgfolderroot,
        searchfolders
    }

    // https://www.rfc-editor.org/rfc/rfc6154.html map well-known names to special flags
    protected static HashMap<String, String> wellKnownFolderMap = new HashMap<>();

    static {
        wellKnownFolderMap.put(WellKnownFolderName.inbox.name(), ExchangeSession.INBOX);
        wellKnownFolderMap.put(WellKnownFolderName.archive.name(), ExchangeSession.ARCHIVE);
        wellKnownFolderMap.put(WellKnownFolderName.drafts.name(), ExchangeSession.DRAFTS);
        wellKnownFolderMap.put(WellKnownFolderName.junkemail.name(), ExchangeSession.JUNK);
        wellKnownFolderMap.put(WellKnownFolderName.sentitems.name(), ExchangeSession.SENT);
        wellKnownFolderMap.put(WellKnownFolderName.deleteditems.name(), ExchangeSession.TRASH);
    }

    protected static final HashSet<FieldURI> IMAP_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        // TODO: review, permanenturl is no lonver relevant
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("permanenturl"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("urlcompname"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("uid"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("messageSize"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("imapUid"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("junk"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("flagStatus"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("messageFlags"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("lastVerbExecuted"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("read"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("deleted"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("date"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("lastmodified"));
        // OSX IMAP requests content-class
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("contentclass"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("keywords"));

        // experimental, retrieve message headers (TODO remove)
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("to"));
        IMAP_MESSAGE_ATTRIBUTES.add(Field.get("messageheaders"));
    }

    protected static final HashSet<FieldURI> CONTACT_ATTRIBUTES = new HashSet<>();

    static {
        CONTACT_ATTRIBUTES.add(Field.get("imapUid"));
        CONTACT_ATTRIBUTES.add(Field.get("etag"));
        CONTACT_ATTRIBUTES.add(Field.get("urlcompname"));

        CONTACT_ATTRIBUTES.add(Field.get("extensionattribute1"));
        CONTACT_ATTRIBUTES.add(Field.get("extensionattribute2"));
        CONTACT_ATTRIBUTES.add(Field.get("extensionattribute3"));
        CONTACT_ATTRIBUTES.add(Field.get("extensionattribute4"));
        CONTACT_ATTRIBUTES.add(Field.get("bday"));
        CONTACT_ATTRIBUTES.add(Field.get("anniversary"));
        CONTACT_ATTRIBUTES.add(Field.get("businesshomepage"));
        CONTACT_ATTRIBUTES.add(Field.get("personalHomePage"));
        CONTACT_ATTRIBUTES.add(Field.get("cn"));
        CONTACT_ATTRIBUTES.add(Field.get("co"));
        CONTACT_ATTRIBUTES.add(Field.get("department"));
        //CONTACT_ATTRIBUTES.add(Field.get("smtpemail1"));
        //CONTACT_ATTRIBUTES.add(Field.get("smtpemail2"));
        //CONTACT_ATTRIBUTES.add(Field.get("smtpemail3"));
        CONTACT_ATTRIBUTES.add(Field.get("facsimiletelephonenumber"));
        CONTACT_ATTRIBUTES.add(Field.get("givenName"));
        CONTACT_ATTRIBUTES.add(Field.get("homeCity"));
        CONTACT_ATTRIBUTES.add(Field.get("homeCountry"));
        CONTACT_ATTRIBUTES.add(Field.get("homePhone"));
        CONTACT_ATTRIBUTES.add(Field.get("homePostalCode"));
        CONTACT_ATTRIBUTES.add(Field.get("homeState"));
        CONTACT_ATTRIBUTES.add(Field.get("homeStreet"));
        CONTACT_ATTRIBUTES.add(Field.get("homepostofficebox"));
        CONTACT_ATTRIBUTES.add(Field.get("l"));
        CONTACT_ATTRIBUTES.add(Field.get("manager"));
        CONTACT_ATTRIBUTES.add(Field.get("mobile"));
        CONTACT_ATTRIBUTES.add(Field.get("namesuffix"));
        CONTACT_ATTRIBUTES.add(Field.get("nickname"));
        CONTACT_ATTRIBUTES.add(Field.get("o"));
        CONTACT_ATTRIBUTES.add(Field.get("pager"));
        CONTACT_ATTRIBUTES.add(Field.get("personaltitle"));
        CONTACT_ATTRIBUTES.add(Field.get("postalcode"));
        CONTACT_ATTRIBUTES.add(Field.get("postofficebox"));
        CONTACT_ATTRIBUTES.add(Field.get("profession"));
        CONTACT_ATTRIBUTES.add(Field.get("roomnumber"));
        CONTACT_ATTRIBUTES.add(Field.get("secretarycn"));
        CONTACT_ATTRIBUTES.add(Field.get("sn"));
        CONTACT_ATTRIBUTES.add(Field.get("spousecn"));
        CONTACT_ATTRIBUTES.add(Field.get("st"));
        CONTACT_ATTRIBUTES.add(Field.get("street"));
        CONTACT_ATTRIBUTES.add(Field.get("telephoneNumber"));
        CONTACT_ATTRIBUTES.add(Field.get("title"));
        CONTACT_ATTRIBUTES.add(Field.get("description"));
        CONTACT_ATTRIBUTES.add(Field.get("im"));
        CONTACT_ATTRIBUTES.add(Field.get("middlename"));
        CONTACT_ATTRIBUTES.add(Field.get("lastmodified"));
        CONTACT_ATTRIBUTES.add(Field.get("otherstreet"));
        CONTACT_ATTRIBUTES.add(Field.get("otherstate"));
        CONTACT_ATTRIBUTES.add(Field.get("otherpostofficebox"));
        CONTACT_ATTRIBUTES.add(Field.get("otherpostalcode"));
        CONTACT_ATTRIBUTES.add(Field.get("othercountry"));
        CONTACT_ATTRIBUTES.add(Field.get("othercity"));
        CONTACT_ATTRIBUTES.add(Field.get("haspicture"));
        CONTACT_ATTRIBUTES.add(Field.get("keywords"));
        CONTACT_ATTRIBUTES.add(Field.get("othermobile"));
        CONTACT_ATTRIBUTES.add(Field.get("otherTelephone"));
        CONTACT_ATTRIBUTES.add(Field.get("gender"));
        CONTACT_ATTRIBUTES.add(Field.get("private"));
        CONTACT_ATTRIBUTES.add(Field.get("sensitivity"));
        CONTACT_ATTRIBUTES.add(Field.get("fburl"));
        //CONTACT_ATTRIBUTES.add(Field.get("msexchangecertificate"));
        //CONTACT_ATTRIBUTES.add(Field.get("usersmimecertificate"));
    }

    private static final Set<FieldURI> TODO_PROPERTIES = new HashSet<>();

    static {
        // TODO review new todo properties https://learn.microsoft.com/en-us/graph/api/resources/todotask
        /*TODO_PROPERTIES.add(Field.get("importance"));

        TODO_PROPERTIES.add(Field.get("subject"));
        TODO_PROPERTIES.add(Field.get("created"));
        TODO_PROPERTIES.add(Field.get("lastmodified"));
        TODO_PROPERTIES.add(Field.get("calendaruid"));
        TODO_PROPERTIES.add(Field.get("description"));
        TODO_PROPERTIES.add(Field.get("textbody"));
        TODO_PROPERTIES.add(Field.get("percentcomplete"));
        TODO_PROPERTIES.add(Field.get("taskstatus"));
        TODO_PROPERTIES.add(Field.get("startdate"));
        TODO_PROPERTIES.add(Field.get("duedate"));
        TODO_PROPERTIES.add(Field.get("datecompleted"));
        TODO_PROPERTIES.add(Field.get("keywords"));*/
    }


    protected static class FolderId {
        protected String mailbox;
        protected String id;
        protected String parentFolderId;
        protected String folderClass;

        public FolderId() {
        }

        public FolderId(String mailbox, String id) {
            this.mailbox = mailbox;
            this.id = id;
        }

        public FolderId(String mailbox, String id, String folderClass) {
            this.mailbox = mailbox;
            this.id = id;
            this.folderClass = folderClass;
        }

        public FolderId(String mailbox, WellKnownFolderName wellKnownFolderName) {
            this.mailbox = mailbox;
            this.id = wellKnownFolderName.name();
        }

        public FolderId(String mailbox, WellKnownFolderName wellKnownFolderName, String folderClass) {
            this.mailbox = mailbox;
            this.id = wellKnownFolderName.name();
            this.folderClass = folderClass;
        }
    }

    HttpClientAdapter httpClient;
    O365Token token;

    /**
     * Default folder properties list
     */
    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<>();

    static {
        // reference at https://learn.microsoft.com/en-us/graph/api/resources/mailfolder
        FOLDER_PROPERTIES.add(Field.get("lastmodified"));
        FOLDER_PROPERTIES.add(Field.get("folderclass"));
        FOLDER_PROPERTIES.add(Field.get("ctag"));
        FOLDER_PROPERTIES.add(Field.get("uidNext"));
    }

    public GraphExchangeSession(HttpClientAdapter httpClient, O365Token token, String userName) throws IOException {
        this.httpClient = httpClient;
        this.token = token;
        this.userName = userName;

        buildSessionInfo(httpClient.getUri());
    }

    @Override
    public void close() {
        httpClient.close();
    }

    /**
     * Format date to exchange search format.
     * TODO: review
     *
     * @param date date object
     * @return formatted search date
     */
    @Override
    public String formatSearchDate(Date date) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormatter.setTimeZone(GMT_TIMEZONE);
        return dateFormatter.format(date);
    }

    @Override
    protected void buildSessionInfo(URI uri) throws IOException {
        // TODO: review, current mailbox is available through /me
        currentMailboxPath = "/users/" + userName.toLowerCase();

        // assume email is username
        email = userName;
        alias = userName.substring(0, email.indexOf("@"));

        LOGGER.debug("Current user email is " + email + ", alias is " + alias);
    }

    @Override
    public ExchangeSession.Message createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException {
        byte[] mimeContent = IOUtil.encodeBase64(mimeMessage);

        // do we want created message to have draft flag?
        boolean isDraft = properties != null && "1".equals(properties.get("draft"));

        // https://learn.microsoft.com/en-us/graph/api/user-post-messages

        FolderId folderId = getFolderId(folderPath);

        // create message in default drafts folder first
        GraphObject graphResponse = executeGraphRequest(new GraphRequestBuilder()
                .setMethod(HttpPost.METHOD_NAME)
                .setContentType("text/plain")
                .setMimeContent(mimeContent)
                .setChildType("messages"));
        if (isDraft) {
            try {
                graphResponse = executeGraphRequest(new GraphRequestBuilder().setMethod(HttpPost.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(graphResponse.optString("id"))
                        .setChildType("move")
                        .setJsonBody(new JSONObject().put("destinationId", folderId.id)));

                // we have the message in the right folder, apply flags
                applyMessageProperties(graphResponse, properties);
                graphResponse = executeGraphRequest(new GraphRequestBuilder()
                        .setMethod(HttpPatch.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(graphResponse.optString("id"))
                        .setJsonBody(graphResponse.jsonObject));
            } catch (JSONException e) {
                throw new IOException(e);
            }
        } else {
            String draftMessageId = null;
            try {
                // save draft message id
                draftMessageId = graphResponse.getString("id");

                // unset draft flag on returned draft message properties
                // TODO handle other message flags
                graphResponse.put("singleValueExtendedProperties",
                        new JSONArray().put(new JSONObject()
                                .put("id", Field.get("messageFlags").getGraphId())
                                .put("value", "4")));
                applyMessageProperties(graphResponse, properties);

                // now use this to recreate message in the right folder
                graphResponse = executeGraphRequest(new GraphRequestBuilder()
                        .setMethod(HttpPost.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("mailFolders")
                        .setObjectId(folderId.id)
                        .setJsonBody(graphResponse.jsonObject)
                        .setChildType("messages"));

            } catch (JSONException e) {
                throw new IOException(e);
            } finally {
                // delete draft message
                if (draftMessageId != null) {
                    executeJsonRequest(new GraphRequestBuilder()
                            .setMethod(HttpDelete.METHOD_NAME)
                            .setObjectType("messages")
                            .setObjectId(draftMessageId));
                }
            }

        }
        return buildMessage(executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setObjectType("messages")
                .setMailbox(folderId.mailbox)
                .setObjectId(graphResponse.optString("id"))
                .setExpandFields(IMAP_MESSAGE_ATTRIBUTES)));
    }

    private void applyMessageProperties(GraphObject graphResponse, Map<String, String> properties) throws JSONException {
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                // TODO
                if ("read".equals(entry.getKey())) {
                    graphResponse.put(entry.getKey(), "1".equals(entry.getValue()));
                } else if ("junk".equals(entry.getKey())) {
                    graphResponse.put(entry.getKey(), entry.getValue());
                } else if ("flagged".equals(entry.getKey())) {
                    graphResponse.put("flagStatus", entry.getValue());
                } else if ("answered".equals(entry.getKey())) {
                    graphResponse.put("lastVerbExecuted", entry.getValue());
                    if ("102".equals(entry.getValue())) {
                        graphResponse.put("iconIndex", "261");
                    }
                } else if ("forwarded".equals(entry.getKey())) {
                    graphResponse.put("lastVerbExecuted", entry.getValue());
                    if ("104".equals(entry.getValue())) {
                        graphResponse.put("iconIndex", "262");
                    }
                } else if ("deleted".equals(entry.getKey())) {
                    graphResponse.put(entry.getKey(), entry.getValue());
                } else if ("datereceived".equals(entry.getKey())) {
                    graphResponse.put(entry.getKey(), entry.getValue());
                } else if ("keywords".equals(entry.getKey())) {
                    graphResponse.setCategories(entry.getValue());
                }
            }
        }
    }

    class Message extends ExchangeSession.Message {
        protected FolderId folderId;
        protected String id;
        protected String changeKey;

        @Override
        public String getPermanentId() {
            return id;
        }

        @Override
        protected InputStream getMimeHeaders() {
            InputStream result = null;
            try {
                HashSet<FieldURI> expandFields = new HashSet<>();
                // TODO: review from header (always empty?)
                expandFields.add(Field.get("from"));
                expandFields.add(Field.get("messageheaders"));

                JSONObject response = executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(id)
                        .setExpandFields(expandFields));

                String messageHeaders = null;

                JSONArray singleValueExtendedProperties = response.optJSONArray("singleValueExtendedProperties");
                if (singleValueExtendedProperties != null) {
                    for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                        try {
                            JSONObject responseValue = singleValueExtendedProperties.getJSONObject(i);
                            String responseId = responseValue.optString("id");
                            if (Field.get("messageheaders").getGraphId().equals(responseId)) {
                                messageHeaders = responseValue.optString("value");
                            }
                        } catch (JSONException e) {
                            LOGGER.warn("Error parsing json response value");
                        }
                    }
                }


                // alternative: use parsed headers response.optJSONArray("internetMessageHeaders");
                if (messageHeaders != null
                        // workaround for broken message headers on Exchange 2010
                        && messageHeaders.toLowerCase().contains("message-id:")) {
                    // workaround for messages in Sent folder
                    if (!messageHeaders.contains("From:")) {
                        // TODO revie
                        String from = response.optString("from");
                        messageHeaders = "From: " + from + '\n' + messageHeaders;
                    }

                    result = new ByteArrayInputStream(messageHeaders.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }

            return result;

        }
    }

    private Message buildMessage(JSONObject response) {
        Message message = new Message();

        try {
            // get item id
            message.id = response.getString("id");
            message.changeKey = response.getString("changeKey");

            message.read = response.getBoolean("isRead");
            message.draft = response.getBoolean("isDraft");
            message.date = convertDateFromExchange(response.getString("receivedDateTime"));

            String lastmodified = convertDateFromExchange(response.optString("lastModifiedDateTime"));
            message.recent = !message.read && lastmodified != null && lastmodified.equals(message.date);

        } catch (JSONException | DavMailException e) {
            LOGGER.warn("Error parsing message " + e.getMessage(), e);
        }

        JSONArray singleValueExtendedProperties = response.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties != null) {
            for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                try {
                    JSONObject responseValue = singleValueExtendedProperties.getJSONObject(i);
                    String responseId = responseValue.optString("id");
                    if (Field.get("imapUid").getGraphId().equals(responseId)) {
                        message.imapUid = responseValue.getLong("value");
                        //}
                        // message flag does not exactly match field, replace with isDraft
                        //else if ("Integer 0xe07".equals(responseId)) {
                        //message.draft = (responseValue.getLong("value") & 8) != 0;
                        //} else if ("SystemTime 0xe06".equals(responseId)) {
                        // use receivedDateTime instead
                        //message.date = convertDateFromExchange(responseValue.getString("value"));
                    } else if ("Integer 0xe08".equals(responseId)) {
                        message.size = responseValue.getInt("value");
                    } else if ("Binary 0xff9".equals(responseId)) {
                        message.uid = responseValue.getString("value");

                    } else if ("String 0x670E".equals(responseId)) {
                        // probably not available over graph
                        message.permanentUrl = responseValue.getString("value");
                    } else if ("Integer 0x1081".equals(responseId)) {
                        String lastVerbExecuted = responseValue.getString("value");
                        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
                        message.forwarded = "104".equals(lastVerbExecuted);
                    } else if ("String {00020386-0000-0000-C000-000000000046} Name content-class".equals(responseId)) {
                        // TODO: test this
                        message.contentClass = responseValue.getString("value");
                    } else if ("Integer 0x1083".equals(responseId)) {
                        message.junk = "1".equals(responseValue.getString("value"));
                    } else if ("Integer 0x1090".equals(responseId)) {
                        message.flagged = "2".equals(responseValue.getString("value"));
                    } else if ("Integer {00062008-0000-0000-c000-000000000046} Name 0x8570".equals(responseId)) {
                        message.deleted = "1".equals(responseValue.getString("value"));
                    }

                } catch (JSONException e) {
                    LOGGER.warn("Error parsing json response value");
                }
            }
        }

        JSONArray multiValueExtendedProperties = response.optJSONArray("multiValueExtendedProperties");
        if (multiValueExtendedProperties != null) {
            for (int i = 0; i < multiValueExtendedProperties.length(); i++) {
                try {
                    JSONObject responseValue = multiValueExtendedProperties.getJSONObject(i);
                    String responseId = responseValue.optString("id");
                    if (Field.get("keywords").getGraphId().equals(responseId)) {
                        JSONArray keywordsJsonArray = responseValue.getJSONArray("value");
                        HashSet<String> keywords = new HashSet<>();
                        for (int j = 0; j < keywordsJsonArray.length(); j++) {
                            keywords.add(keywordsJsonArray.getString(j));
                        }
                        message.keywords = StringUtil.join(keywords, ",");
                    }

                } catch (JSONException e) {
                    LOGGER.warn("Error parsing json response value");
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("Message");
            if (message.imapUid != 0) {
                buffer.append(" IMAP uid: ").append(message.imapUid);
            }
            if (message.uid != null) {
                buffer.append(" uid: ").append(message.uid);
            }
            buffer.append(" ItemId: ").append(message.id);
            buffer.append(" ChangeKey: ").append(message.changeKey);
            LOGGER.debug(buffer.toString());
        }

        return message;

    }

    /**
     * Lightweigt conversion method to avoid full string to date and back conversions.
     * Note: Duplicate from EWSEchangeSession, added nanosecond handling
     * @param exchangeDateValue date returned from O365
     * @return converted date
     * @throws DavMailException on error
     */
    protected String convertDateFromExchange(String exchangeDateValue) throws DavMailException {
        // yyyy-MM-dd'T'HH:mm:ss'Z' to yyyyMMdd'T'HHmmss'Z'
        if (exchangeDateValue == null) {
            return null;
        } else {
            StringBuilder buffer = new StringBuilder();
            if (exchangeDateValue.length() == 28 || exchangeDateValue.length() == 20 || exchangeDateValue.length() == 10) {
                for (int i = 0; i < exchangeDateValue.length(); i++) {
                    if (i == 4 || i == 7 || i == 13 || i == 16) {
                        i++;
                    } else if (exchangeDateValue.length() == 28 && i == 19) {
                        i = exchangeDateValue.length() - 1;
                    }
                    buffer.append(exchangeDateValue.charAt(i));
                }
                if (exchangeDateValue.length() == 10) {
                    buffer.append("T000000Z");
                }
            } else {
                throw new DavMailException("EXCEPTION_INVALID_DATE", exchangeDateValue);
            }
            return buffer.toString();
        }
    }

    @Override
    public void updateMessage(ExchangeSession.Message message, Map<String, String> properties) throws IOException {
        try {
            GraphObject graphObject = new GraphObject(new JSONObject());
            // we have the message in the right folder, apply flags
            applyMessageProperties(graphObject, properties);
            executeJsonRequest(new GraphRequestBuilder()
                    .setMethod(HttpPatch.METHOD_NAME)
                    .setMailbox(((Message) message).folderId.mailbox)
                    .setObjectType("messages")
                    .setObjectId(((Message) message).id)
                    .setJsonBody(graphObject.jsonObject));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void deleteMessage(ExchangeSession.Message message) throws IOException {
        executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpDelete.METHOD_NAME)
                .setMailbox(((Message) message).folderId.mailbox)
                .setObjectType("messages")
                .setObjectId(((Message) message).id));
    }

    @Override
    protected byte[] getContent(ExchangeSession.Message message) throws IOException {
        GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(((Message) message).folderId.mailbox)
                .setObjectType("messages")
                .setObjectId(message.getPermanentId())
                .setChildType("$value")
                .setAccessToken(token.getAccessToken());

        // TODO review mime content handling
        byte[] mimeContent;
        try (
                CloseableHttpResponse response = httpClient.execute(graphRequestBuilder.build());
                InputStream inputStream = response.getEntity().getContent()
        ) {
            // wrap inputstream to log progress
            FilterInputStream filterInputStream = new FilterInputStream(inputStream) {
                int totalCount;
                int lastLogCount;

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = super.read(buffer, offset, length);
                    totalCount += count;
                    if (totalCount - lastLogCount > 1024 * 128) {
                        DavGatewayTray.debug(new BundleMessage("LOG_DOWNLOAD_PROGRESS", String.valueOf(totalCount / 1024), message.getPermanentId()));
                        DavGatewayTray.switchIcon();
                        lastLogCount = totalCount;
                    }
                    /*if (count > 0 && LOGGER.isDebugEnabled()) {
                        LOGGER.debug(new String(buffer, offset, count, "UTF-8"));
                    }*/
                    return count;
                }
            };
            if (HttpClientAdapter.isGzipEncoded(response)) {
                mimeContent = IOUtil.readFully(new GZIPInputStream(filterInputStream));
            } else {
                mimeContent = IOUtil.readFully(filterInputStream);
            }
        }
        return mimeContent;
    }

    @Override
    public MessageList searchMessages(String folderName, Set<String> attributes, Condition condition) throws IOException {
        MessageList messageList = new MessageList();
        FolderId folderId = getFolderId(folderName);

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectType("mailFolders")
                .setObjectId(folderId.id)
                .setChildType("messages")
                .setExpandFields(IMAP_MESSAGE_ATTRIBUTES);
        LOGGER.debug("searchMessages " + folderId.mailbox + " " + folderName);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Message message = buildMessage(graphIterator.next());
            message.messageList = messageList;
            message.folderId = folderId;
            messageList.add(message);
        }
        Collections.sort(messageList);
        return messageList;
    }

    static class AttributeCondition extends ExchangeSession.AttributeCondition {

        protected AttributeCondition(String attributeName, Operator operator, String value) {
            super(attributeName, operator, value);
        }

        protected FieldURI getFieldURI() {
            FieldURI fieldURI = Field.get(attributeName);
            // check to detect broken field mapping
            //noinspection ConstantConditions
            if (fieldURI == null) {
                throw new IllegalArgumentException("Unknown field: " + attributeName);
            }
            return fieldURI;
        }

        private String convertOperator(Operator operator) {
            if (Operator.IsEqualTo.equals(operator)) {
                return "eq";
            }
            // TODO other operators
            return operator.toString();
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            FieldURI fieldURI = getFieldURI();
            if ("String {00020386-0000-0000-c000-000000000046} Name to".equals(fieldURI.getGraphId())) {
                // TODO: does not work need to switch to search instead of filter
                buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq 'String {00020386-0000-0000-c000-000000000046} Name to' and contains(ep/value,'")
                        .append(StringUtil.escapeQuotes(value)).append("'))");
            } else if (Operator.StartsWith.equals(operator)) {
                buffer.append("startswith(").append(getFieldURI().getGraphId()).append(",'").append(StringUtil.escapeQuotes(value)).append("')");
            } else if (Operator.Contains.equals(operator)) {
                buffer.append("contains(").append(getFieldURI().getGraphId()).append(",'").append(StringUtil.escapeQuotes(value)).append("')");
            } else if (fieldURI instanceof ExtendedFieldURI) {
                buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(getFieldURI().getGraphId())
                        .append("' and ep/value ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("')");
            } else {
                buffer.append(getFieldURI().getGraphId()).append(" ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("'");
            }
        }

        @Override
        public boolean isMatch(ExchangeSession.Contact contact) {
            return false;
        }
    }

    protected static class HeaderCondition extends AttributeCondition {

        protected HeaderCondition(String attributeName, String value) {
            super(attributeName, Operator.Contains, value);
        }

        @Override
        protected FieldURI getFieldURI() {
            return new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, attributeName);
        }
    }

    protected static class IsNullCondition implements ExchangeSession.Condition, SearchExpression {
        protected final String attributeName;

        protected IsNullCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        public void appendTo(StringBuilder buffer) {
            FieldURI fieldURI = Field.get(attributeName);
            if (fieldURI instanceof ExtendedFieldURI) {
                buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(fieldURI.getGraphId())
                        .append("' and ep/value eq null)");
            } else {
                buffer.append(fieldURI.getGraphId()).append(" eq null");
            }
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return actualValue == null;
        }

    }

    protected static class ExistsCondition implements ExchangeSession.Condition, SearchExpression {
        protected final String attributeName;

        protected ExistsCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        public void appendTo(StringBuilder buffer) {
            buffer.append(Field.get(attributeName).getGraphId()).append(" ne null");
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return actualValue == null;
        }

    }


    static class MultiCondition extends ExchangeSession.MultiCondition {

        protected MultiCondition(Operator operator, Condition... conditions) {
            super(operator, conditions);
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            int actualConditionCount = 0;
            for (Condition condition : conditions) {
                if (!condition.isEmpty()) {
                    actualConditionCount++;
                }
            }
            if (actualConditionCount > 0) {
                boolean isFirst = true;

                for (Condition condition : conditions) {
                    if (isFirst) {
                        isFirst = false;

                    } else {
                        buffer.append(" ").append(operator.toString()).append(" ");
                    }
                    condition.appendTo(buffer);
                }
            }
        }
    }

    static class NotCondition extends ExchangeSession.NotCondition {

        protected NotCondition(Condition condition) {
            super(condition);
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            buffer.append("not ");
            condition.appendTo(buffer);
        }
    }

    @Override
    public MultiCondition and(Condition... conditions) {
        return new MultiCondition(Operator.And, conditions);
    }

    @Override
    public MultiCondition or(Condition... conditions) {
        return new MultiCondition(Operator.Or, conditions);
    }

    @Override
    public Condition not(Condition condition) {
        return new NotCondition(condition);
    }

    @Override
    public Condition isEqualTo(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, value);
    }

    @Override
    public Condition isEqualTo(String attributeName, int value) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, String.valueOf(value));
    }

    @Override
    public Condition headerIsEqualTo(String headerName, String value) {
        return new HeaderCondition(headerName, value);
    }

    @Override
    public Condition gte(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsGreaterThanOrEqualTo, value);
    }

    @Override
    public Condition gt(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsGreaterThan, value);
    }

    @Override
    public Condition lt(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsLessThan, value);
    }

    @Override
    public Condition lte(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.IsLessThanOrEqualTo, value);
    }

    @Override
    public Condition contains(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.Contains, value);
    }

    @Override
    public Condition startsWith(String attributeName, String value) {
        return new AttributeCondition(attributeName, Operator.StartsWith, value);
    }

    @Override
    public Condition isNull(String attributeName) {
        return new IsNullCondition(attributeName);
    }

    @Override
    public Condition exists(String attributeName) {
        return new ExistsCondition(attributeName);
    }

    @Override
    public Condition isTrue(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "true");
    }

    @Override
    public Condition isFalse(String attributeName) {
        return new AttributeCondition(attributeName, Operator.IsEqualTo, "false");
    }

    @Override
    public List<ExchangeSession.Folder> getSubFolders(String folderPath, Condition condition, boolean recursive) throws IOException {
        String baseFolderPath = folderPath;
        if (baseFolderPath.startsWith("/users/")) {
            int index = baseFolderPath.indexOf('/', "/users/".length());
            if (index >= 0) {
                baseFolderPath = baseFolderPath.substring(index + 1);
            }
        }
        List<ExchangeSession.Folder> folders = new ArrayList<>();
        appendSubFolders(folders, baseFolderPath, getFolderId(folderPath), condition, recursive);
        return folders;
    }

    protected void appendSubFolders(List<ExchangeSession.Folder> folders,
                                    String parentFolderPath, FolderId parentFolderId,
                                    Condition condition, boolean recursive) throws IOException {

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setObjectType("mailFolders")
                .setMailbox(parentFolderId.mailbox)
                .setObjectId(parentFolderId.id)
                .setChildType("childFolders")
                .setExpandFields(FOLDER_PROPERTIES);
        LOGGER.debug("appendSubFolders " + (parentFolderId.mailbox != null ? parentFolderId.mailbox : "me") + " " + parentFolderPath);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Folder folder = buildFolder(graphIterator.next());
            folder.folderId.mailbox = parentFolderId.mailbox;
            // check parentFolder
            if (parentFolderId.id.equals(folder.folderId.parentFolderId)) {
                if (!parentFolderPath.isEmpty()) {
                    if (parentFolderPath.endsWith("/")) {
                        folder.folderPath = parentFolderPath + folder.displayName;
                    } else {
                        folder.folderPath = parentFolderPath + '/' + folder.displayName;
                    }
                    // TODO folderIdMap?
                } else {
                    folder.folderPath = folder.displayName;
                }
                folders.add(folder);
                if (recursive && folder.hasChildren) {
                    appendSubFolders(folders, folder.folderPath, folder.folderId, condition, true);
                }
            } else {
                LOGGER.debug("appendSubFolders skip " + folder.folderId.mailbox + " " + folder.folderId.id + " " + folder.displayName + " not a child of " + parentFolderPath);
            }
        }

    }


    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {
        // https://learn.microsoft.com/en-us/graph/api/user-sendmail
        executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpPost.METHOD_NAME)
                .setObjectType("sendMail")
                .setContentType("text/plain")
                .setMimeContent(IOUtil.encodeBase64(mimeMessage)));

    }

    @Override
    protected Folder internalGetFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);

        // base folder get https://graph.microsoft.com/v1.0/me/mailFolders/inbox
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectType("mailFolders")
                .setObjectId(folderId.id)
                .setExpandFields(FOLDER_PROPERTIES);
        if ("IPF.Appointment".equals(folderId.folderClass)) {
            httpRequestBuilder.setObjectType("calendars");
        } else if ("IPF.Contact".equals(folderId.folderClass)) {
            httpRequestBuilder.setObjectType("contactFolders");
        } else {
            httpRequestBuilder.setObjectType("mailFolders");
        }

        JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);

        Folder folder = buildFolder(jsonResponse);
        folder.folderPath = folderPath;

        return folder;
    }

    private Folder buildFolder(JSONObject jsonResponse) throws IOException {
        try {
            Folder folder = new Folder();
            folder.folderId = new FolderId();
            folder.folderId.id = jsonResponse.getString("id");
            folder.folderId.parentFolderId = jsonResponse.optString("parentFolderId", null);
            if (folder.folderId.parentFolderId == null) {
                // calendar
                folder.displayName = EwsExchangeSession.encodeFolderName(jsonResponse.optString("name"));
            } else {
                String wellKnownName = wellKnownFolderMap.get(jsonResponse.optString("wellKnownName"));
                if (ExchangeSession.INBOX.equals(wellKnownName)) {
                    folder.displayName = wellKnownName;
                } else {
                    if (wellKnownName != null) {
                        folder.setSpecialFlag(wellKnownName);
                    }

                    // TODO: reevaluate folder name encoding over graph
                    folder.displayName = EwsExchangeSession.encodeFolderName(jsonResponse.getString("displayName"));
                }

                folder.count = jsonResponse.optInt("totalItemCount");
                folder.unreadCount = jsonResponse.optInt("unreadItemCount");
                // fake recent value
                folder.recent = folder.unreadCount;
                // hassubs computed from childFolderCount
                folder.hasChildren = jsonResponse.optInt("childFolderCount") > 0;
            }

            // retrieve property values
            JSONArray singleValueExtendedProperties = jsonResponse.optJSONArray("singleValueExtendedProperties");
            if (singleValueExtendedProperties != null) {
                for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                    JSONObject singleValueProperty = singleValueExtendedProperties.getJSONObject(i);
                    String singleValueId = singleValueProperty.getString("id");
                    String singleValue = singleValueProperty.getString("value");
                    if (Field.get("lastmodified").getGraphId().equals(singleValueId)) {
                        folder.etag = singleValue;
                    } else if (Field.get("folderclass").getGraphId().equals(singleValueId)) {
                        folder.folderClass = singleValue;
                        folder.folderId.folderClass = folder.folderClass;
                    } else if (Field.get("uidNext").getGraphId().equals(singleValueId)) {
                        folder.uidNext = Long.parseLong(singleValue);
                    } else if (Field.get("ctag").getGraphId().equals(singleValueId)) {
                        folder.ctag = singleValue;
                    }

                }
            }

            return folder;
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Compute folderId from folderName
     * @param folderPath folder name (path)
     * @return folder id
     */
    private FolderId getFolderId(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    protected static final String USERS_ROOT = "/users/";
    protected static final String ARCHIVE_ROOT = "/archive/";


    private FolderId getFolderIdIfExists(String folderPath) throws IOException {
        String lowerCaseFolderPath = folderPath.toLowerCase();
        if (lowerCaseFolderPath.equals(currentMailboxPath)) {
            return getSubFolderIdIfExists(null, "");
        } else if (lowerCaseFolderPath.startsWith(currentMailboxPath + '/')) {
            return getSubFolderIdIfExists(null, folderPath.substring(currentMailboxPath.length() + 1));
        } else if (folderPath.startsWith(USERS_ROOT)) {
            int slashIndex = folderPath.indexOf('/', USERS_ROOT.length());
            String mailbox;
            String subFolderPath;
            if (slashIndex >= 0) {
                mailbox = folderPath.substring(USERS_ROOT.length(), slashIndex);
                subFolderPath = folderPath.substring(slashIndex + 1);
            } else {
                mailbox = folderPath.substring(USERS_ROOT.length());
                subFolderPath = "";
            }
            return getSubFolderIdIfExists(mailbox, subFolderPath);
        } else {
            return getSubFolderIdIfExists(null, folderPath);
        }
    }

    private FolderId getSubFolderIdIfExists(String mailbox, String folderPath) throws IOException {
        String[] folderNames;
        FolderId currentFolderId;

        // TODO test various use cases
        if ("/public".equals(folderPath)) {
            throw new UnsupportedOperationException("public folders not supported on Graph");
        } else if ("/archive".equals(folderPath)) {
            return getWellKnownFolderId(mailbox, WellKnownFolderName.archive);
        } else if (isSubFolderOf(folderPath, PUBLIC_ROOT)) {
            throw new UnsupportedOperationException("public folders not supported on Graph");
        } else if (isSubFolderOf(folderPath, ARCHIVE_ROOT)) {
            currentFolderId = getWellKnownFolderId(mailbox, WellKnownFolderName.archive);
            folderNames = folderPath.substring(ARCHIVE_ROOT.length()).split("/");
        } else if (isSubFolderOf(folderPath, INBOX) ||
                isSubFolderOf(folderPath, LOWER_CASE_INBOX) ||
                isSubFolderOf(folderPath, MIXED_CASE_INBOX)) {
            currentFolderId = getWellKnownFolderId(mailbox, WellKnownFolderName.inbox);
            folderNames = folderPath.substring(INBOX.length()).split("/");
        } else if (isSubFolderOf(folderPath, CALENDAR)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.calendar, "IPF.Appointment");
            // TODO subfolders not supported with graph
            folderNames = folderPath.substring(CALENDAR.length()).split("/");
        } else if (isSubFolderOf(folderPath, TASKS)) {
            currentFolderId = getWellKnownFolderId(mailbox, WellKnownFolderName.tasks);
            folderNames = folderPath.substring(TASKS.length()).split("/");
        } else if (isSubFolderOf(folderPath, CONTACTS)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.contacts, "IPF.Contact");
            folderNames = folderPath.substring(CONTACTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, SENT)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.sentitems);
            folderNames = folderPath.substring(SENT.length()).split("/");
        } else if (isSubFolderOf(folderPath, DRAFTS)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.drafts);
            folderNames = folderPath.substring(DRAFTS.length()).split("/");
        } else if (isSubFolderOf(folderPath, TRASH)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.deleteditems);
            folderNames = folderPath.substring(TRASH.length()).split("/");
        } else if (isSubFolderOf(folderPath, JUNK)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.junkemail);
            folderNames = folderPath.substring(JUNK.length()).split("/");
        } else if (isSubFolderOf(folderPath, UNSENT)) {
            currentFolderId = new FolderId(mailbox, WellKnownFolderName.outbox);
            folderNames = folderPath.substring(UNSENT.length()).split("/");
        } else {
            // TODO refactor
            currentFolderId = getWellKnownFolderId(mailbox, WellKnownFolderName.msgfolderroot);
            folderNames = folderPath.split("/");
        }
        String folderClass = currentFolderId.folderClass;
        for (String folderName : folderNames) {
            if (!folderName.isEmpty()) {
                currentFolderId = getSubFolderByName(currentFolderId, folderName);
                if (currentFolderId == null) {
                    break;
                }
                currentFolderId.folderClass = folderClass;
            }
        }
        return currentFolderId;
    }

    /**
     * Build folderId for well-known folders.
     * Set EWS folderClass values according to: <a href="https://learn.microsoft.com/en-us/exchange/client-developer/exchange-web-services/folders-and-items-in-ews-in-exchange">...</a>
     * @param mailbox user mailbox
     * @param wellKnownFolderName well-known value
     * @return folderId
     * @throws IOException on error
     */
    private FolderId getWellKnownFolderId(String mailbox, WellKnownFolderName wellKnownFolderName) throws IOException {
        if (wellKnownFolderName == WellKnownFolderName.tasks) {
            // retrieve folder id from todo endpoint
            GraphIterator graphIterator = executeSearchRequest(new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(mailbox)
                    .setObjectType("todo/lists"));
            while (graphIterator.hasNext()) {
                JSONObject jsonResponse = graphIterator.next();
                if (jsonResponse.optString("wellknownListName").equals("defaultList")) {
                    return new FolderId(mailbox, jsonResponse.optString("id"), "IPF.Task");
                }
            }
            // should not happen
            throw new HttpNotFoundException("Folder '" + wellKnownFolderName.name() + "' not found");

        } else {
            JSONObject jsonResponse = executeJsonRequest(new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(mailbox)
                    .setObjectType("mailFolders")
                    .setObjectId(wellKnownFolderName.name())
                    .setExpandFields(FOLDER_PROPERTIES));
            // TODO retrieve folderClass
            return new FolderId(mailbox, jsonResponse.optString("id"), "IPF.Note");
        }
    }

    /**
     * Search subfolder by name, return null when no folders found
     * @param currentFolderId parent folder id
     * @param folderName child folder name
     * @return child folder id if exists
     * @throws IOException on error
     */
    protected FolderId getSubFolderByName(FolderId currentFolderId, String folderName) throws IOException {
        // TODO rename escapeQuotes
        GraphRequestBuilder httpRequestBuilder;
        if ("IPF.Appointment".equals(currentFolderId.folderClass)) {
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(currentFolderId.mailbox)
                    .setObjectType("calendars")
                    .setExpandFields(FOLDER_PROPERTIES)
                    .setFilter("name eq '" + StringUtil.escapeQuotes(EwsExchangeSession.decodeFolderName(folderName)) + "'");
        } else if ("IPF.Task".equals(currentFolderId.folderClass)) {
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(currentFolderId.mailbox)
                    .setObjectType("todo/lists")
                    .setExpandFields(FOLDER_PROPERTIES)
                    .setFilter("displayName eq '" + StringUtil.escapeQuotes(EwsExchangeSession.decodeFolderName(folderName)) + "'");
        } else {
            String objectType = "mailFolders";
            if ("IPF.Contact".equals(currentFolderId.folderClass)) {
                objectType = "contactFolders";
            }
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(currentFolderId.mailbox)
                    .setObjectType(objectType)
                    .setObjectId(currentFolderId.id)
                    .setChildType("childFolders")
                    .setExpandFields(FOLDER_PROPERTIES)
                    .setFilter("displayName eq '" + StringUtil.escapeQuotes(EwsExchangeSession.decodeFolderName(folderName)) + "'");
        }

        JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);

        FolderId folderId = null;
        try {
            JSONArray values = jsonResponse.getJSONArray("value");
            if (values.length() > 0) {
                folderId = new FolderId(currentFolderId.mailbox, values.getJSONObject(0).getString("id"), currentFolderId.folderClass);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }

        return folderId;
    }

    private boolean isSubFolderOf(String folderPath, String baseFolder) {
        if (PUBLIC_ROOT.equals(baseFolder) || ARCHIVE_ROOT.equals(baseFolder)) {
            return folderPath.startsWith(baseFolder);
        } else {
            return folderPath.startsWith(baseFolder)
                    && (folderPath.length() == baseFolder.length() || folderPath.charAt(baseFolder.length()) == '/');
        }
    }

    @Override
    public int createFolder(String folderPath, String folderClass, Map<String, String> properties) throws IOException {
        if ("IPF.Appointment".equals(folderClass) && folderPath.startsWith("calendar/")) {
            // calendars/calendarName
            String calendarName = folderPath.substring(folderPath.indexOf('/') + 1);
            // create calendar
            try {
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpPost.METHOD_NAME)
                        // TODO mailbox?
                        //.setMailbox("")
                        .setObjectType("calendars")
                        .setJsonBody(new JSONObject().put("name", calendarName)));

            } catch (JSONException e) {
                throw new IOException(e);
            }
        } else {
            FolderId parentFolderId;
            String folderName;
            if (folderPath.contains("/")) {
                String parentFolderPath = folderPath.substring(0, folderPath.lastIndexOf('/'));
                parentFolderId = getFolderId(parentFolderPath);
                folderName = EwsExchangeSession.decodeFolderName(folderPath.substring(folderPath.lastIndexOf('/') + 1));
            } else {
                parentFolderId = getFolderId("");
                folderName = EwsExchangeSession.decodeFolderName(folderPath);
            }

            try {
                String objectType = "mailFolders";
                if ("IPF.Contact".equals(folderClass)) {
                    objectType = "contactFolders";
                }
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpPost.METHOD_NAME)
                        // TODO mailbox?
                        .setMailbox(parentFolderId.mailbox)
                        .setObjectType(objectType)
                        .setObjectId(parentFolderId.id)
                        .setChildType("childFolders")
                        .setJsonBody(new JSONObject().put("displayName", folderName)));

            } catch (JSONException e) {
                throw new IOException(e);
            }
        }

        return HttpStatus.SC_CREATED;

    }

    @Override
    public int updateFolder(String folderName, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public void deleteFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        if (folderPath.startsWith("calendar/")) {
            // TODO shared mailboxes
            if (folderId != null) {
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpDelete.METHOD_NAME)
                        //.setMailbox()
                        .setObjectType("calendars")
                        .setObjectId(folderId.id));
            }
        } else {
            if (folderId != null) {
                String objectType = "mailFolders";
                if ("IPF.Contact".equals(folderId.folderClass)) {
                    objectType = "contactFolders";
                }
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpDelete.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType(objectType)
                        .setObjectId(folderId.id));
            }
        }

    }

    @Override
    public void copyMessage(ExchangeSession.Message message, String targetFolder) throws IOException {
        try {
            FolderId targetFolderId = getFolderId(targetFolder);

            executeJsonRequest(new GraphRequestBuilder().setMethod(HttpPost.METHOD_NAME)
                    .setMailbox(((Message) message).folderId.mailbox)
                    .setObjectType("messages")
                    .setObjectId(((Message) message).id)
                    .setChildType("copy")
                    .setJsonBody(new JSONObject().put("destinationId", targetFolderId.id)));

        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void moveMessage(ExchangeSession.Message message, String targetFolder) throws IOException {
        try {
            FolderId targetFolderId = getFolderId(targetFolder);

            executeJsonRequest(new GraphRequestBuilder().setMethod(HttpPost.METHOD_NAME)
                    .setMailbox(((Message) message).folderId.mailbox)
                    .setObjectType("messages")
                    .setObjectId(((Message) message).id)
                    .setChildType("move")
                    .setJsonBody(new JSONObject().put("destinationId", targetFolderId.id)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void moveFolder(String folderPath, String targetFolderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);
        String targetFolderName;
        String targetFolderParentPath;
        if (targetFolderPath.contains("/")) {
            targetFolderParentPath = targetFolderPath.substring(0, targetFolderPath.lastIndexOf('/'));
            targetFolderName = EwsExchangeSession.decodeFolderName(targetFolderPath.substring(targetFolderPath.lastIndexOf('/') + 1));
        } else {
            targetFolderParentPath = "";
            targetFolderName = EwsExchangeSession.decodeFolderName(targetFolderPath);
        }
        FolderId targetFolderId = getFolderId(targetFolderParentPath);

        // rename
        try {
            executeJsonRequest(new GraphRequestBuilder().setMethod(HttpPatch.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("mailFolders")
                    .setObjectId(folderId.id)
                    .setJsonBody(new JSONObject().put("displayName", targetFolderName)));
        } catch (JSONException e) {
            throw new IOException(e);
        }

        try {
            executeJsonRequest(new GraphRequestBuilder().setMethod(HttpPost.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("mailFolders")
                    .setObjectId(folderId.id)
                    .setChildType("move")
                    .setJsonBody(new JSONObject().put("destinationId", targetFolderId.id)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void moveItem(String sourcePath, String targetPath) throws IOException {

    }

    @Override
    protected void moveToTrash(ExchangeSession.Message message) throws IOException {
        moveMessage(message, WellKnownFolderName.deleteditems.name());
    }


    /**
     * Common item properties
     */
    protected static final Set<String> ITEM_PROPERTIES = new HashSet<>();

    static {
        //ITEM_PROPERTIES.add("etag");
        //ITEM_PROPERTIES.add("displayname");
        // calendar CdoInstanceType
        //ITEM_PROPERTIES.add("instancetype");
        //ITEM_PROPERTIES.add("urlcompname");
        //ITEM_PROPERTIES.add("subject");
    }

    protected static final HashSet<String> EVENT_REQUEST_PROPERTIES = new HashSet<>();

    static {
        EVENT_REQUEST_PROPERTIES.add("permanenturl");
        EVENT_REQUEST_PROPERTIES.add("etag");
        EVENT_REQUEST_PROPERTIES.add("displayname");
        EVENT_REQUEST_PROPERTIES.add("subject");
        EVENT_REQUEST_PROPERTIES.add("urlcompname");
        EVENT_REQUEST_PROPERTIES.add("displayto");
        EVENT_REQUEST_PROPERTIES.add("displaycc");

        EVENT_REQUEST_PROPERTIES.add("xmozlastack");
        EVENT_REQUEST_PROPERTIES.add("xmozsnoozetime");
    }

    protected static final HashSet<String> CALENDAR_ITEM_REQUEST_PROPERTIES = new HashSet<>();

    static {
        CALENDAR_ITEM_REQUEST_PROPERTIES.addAll(EVENT_REQUEST_PROPERTIES);
        CALENDAR_ITEM_REQUEST_PROPERTIES.add("ismeeting");
        CALENDAR_ITEM_REQUEST_PROPERTIES.add("myresponsetype");
    }

    @Override
    protected Set<String> getItemProperties() {
        return ITEM_PROPERTIES;
    }

    @Override
    public List<ExchangeSession.Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException {
        ArrayList<ExchangeSession.Contact> contactList = new ArrayList<>();
        FolderId folderId = getFolderId(folderPath);

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectType("contactFolders")
                .setObjectId(folderId.id)
                .setChildType("contacts")
                .setExpandFields(CONTACT_ATTRIBUTES);
        LOGGER.debug("searchContacts " + folderId.mailbox + " " + folderPath);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Contact contact = new Contact(new GraphObject(graphIterator.next()));
            contact.folderId = folderId;
            contactList.add(contact);
        }

        return contactList;
    }

    @Override
    public List<ExchangeSession.Event> getEventMessages(String folderPath) throws IOException {
        return null;
    }

    @Override
    protected Condition getCalendarItemCondition(Condition dateCondition) {
        return null;
    }

    /**
     * Tasks folders are no longer supported, reroute to todos.
     * @param folderPath Exchange folder path
     * @return todos as events
     * @throws IOException on error
     */
    @Override
    public List<ExchangeSession.Event> searchTasksOnly(String folderPath) throws IOException {
        ArrayList<ExchangeSession.Event> eventList = new ArrayList<>();
        FolderId folderId = getFolderId(folderPath);

        // GET /me/todo/lists/{todoTaskListId}/tasks
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectType("todo/lists")
                .setObjectId(folderId.id)
                .setChildType("tasks")
                .setExpandFields(TODO_PROPERTIES);
        LOGGER.debug("searchTasksOnly " + folderId.mailbox + " " + folderPath);

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Event event = new Event(folderId, new GraphObject(graphIterator.next()));
            eventList.add(event);
        }

        return eventList;
    }

    @Override
    public List<ExchangeSession.Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        ArrayList<ExchangeSession.Event> eventList = new ArrayList<>();
        FolderId folderId = getFolderId(folderPath);

        // /users/{id | userPrincipalName}/calendars/{id}/events
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectType("calendars")
                .setObjectId(folderId.id)
                .setChildType("events")
                .setExpandFields(CONTACT_ATTRIBUTES);
        LOGGER.debug("searchEvents " + folderId.mailbox + " " + folderPath);
        if (condition != null && !condition.isEmpty()) {
            StringBuilder filter = new StringBuilder();
            condition.appendTo(filter);
            LOGGER.debug("search filter " + filter);
            httpRequestBuilder.setFilter(filter.toString());
        }

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Event event = new Event(folderId, new GraphObject(graphIterator.next()));
            eventList.add(event);
        }

        return eventList;

    }

    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        FolderId folderId = getFolderId(folderPath);

        if ("IPF.Contact".equals(folderId.folderClass)) {
            JSONObject jsonResponse = getContactIfExists(folderId, itemName);
            if (jsonResponse != null) {
                Contact contact = new Contact(new GraphObject(jsonResponse));
                contact.folderId = folderId;
                return contact;
            } else {
                throw new IOException("Item " + folderPath + " " + itemName + " not found");
            }
        } else {
            return null;
        }
    }

    private JSONObject getContactIfExists(FolderId folderId, String itemName) throws IOException {
        // TODO: build with AttributeCondition
        String filter = "singleValueExtendedProperties/Any(ep: ep/id eq '" + Field.get("urlcompname").getGraphId() + "' and ep/value eq '" + convertItemNameToEML(StringUtil.escapeQuotes(itemName)) + "')";
        JSONObject jsonResponse = executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setMailbox(folderId.mailbox)
                .setObjectType("contactFolders")
                .setObjectId(folderId.id)
                .setChildType("contacts")
                .setFilter(filter)
                .setExpandFields(CONTACT_ATTRIBUTES)
        );
        // need at least one value
        JSONArray values = jsonResponse.optJSONArray("value");
        if (values != null && values.length() > 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Contact " + values.optJSONObject(0));
            }
            return values.optJSONObject(0);
        }
        return null;
    }

    @Override
    public ContactPhoto getContactPhoto(ExchangeSession.Contact contact) throws IOException {
        // /me/contacts/{id}/photo/$value
        GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(((Contact) contact).folderId.mailbox)
                .setObjectType("contactFolders")
                .setObjectId(((Contact) contact).folderId.id)
                .setChildType("contacts")
                .setChildId(((Contact) contact).id)
                .setChildSuffix("photo/$value");

        byte[] contactPhotoBytes;
        try (
                CloseableHttpResponse response = httpClient.execute(graphRequestBuilder.build());
                InputStream inputStream = response.getEntity().getContent()
        ) {
            if (HttpClientAdapter.isGzipEncoded(response)) {
                contactPhotoBytes = IOUtil.readFully(new GZIPInputStream(inputStream));
            } else {
                contactPhotoBytes = IOUtil.readFully(inputStream);
            }
        }
        ContactPhoto contactPhoto = new ContactPhoto();
        contactPhoto.contentType = "image/jpeg";
        contactPhoto.content = IOUtil.encodeBase64AsString(contactPhotoBytes);

        return contactPhoto;
    }

    @Override
    public void deleteItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        return 0;
    }

    @Override
    protected Contact buildContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) throws IOException {
        return new Contact(folderPath, itemName, properties, StringUtil.removeQuotes(etag), noneMatch);
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        return new Event(folderPath, itemName, contentClass, icsBody, StringUtil.removeQuotes(etag), noneMatch).createOrUpdate();
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        return false;
    }

    @Override
    public boolean isMainCalendar(String folderPath) throws IOException {
        return false;
    }

    @Override
    public Map<String, ExchangeSession.Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        // https://learn.microsoft.com/en-us/graph/api/orgcontact-get
        return null;
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException {
        // https://learn.microsoft.com/en-us/graph/outlook-get-free-busy-schedule
        // POST /me/calendar/getschedule
        return null;
    }

    @Override
    protected void loadVtimezone() {
        try {
            // default from Davmail settings
            String timezoneId = Settings.getProperty("davmail.timezoneId", null);
            // use timezone from mailbox
            if (timezoneId == null) {
                try {
                    timezoneId = getMailboxSettings().optString("timeZone", null);
                } catch (HttpForbiddenException e) {
                    LOGGER.warn("token does not grant MailboxSettings.Read");
                }
            }
            // last failover: use GMT
            if (timezoneId == null) {
                LOGGER.warn("Unable to get user timezone, using GMT Standard Time. Set davmail.timezoneId setting to override this.");
                timezoneId = "GMT Standard Time";
            }
            this.vTimezone = new VObject(ResourceBundle.getBundle("vtimezones").getString(timezoneId));

        } catch (IOException | MissingResourceException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        }
    }

    private JSONObject getMailboxSettings() throws IOException {
        return executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setObjectType("mailboxSettings"));
    }

    class GraphIterator {

        private JSONObject jsonObject;
        private JSONArray values;
        private String nextLink;
        private int index;

        public GraphIterator(JSONObject jsonObject) throws JSONException {
            this.jsonObject = jsonObject;
            nextLink = jsonObject.optString("@odata.nextLink", null);
            values = jsonObject.getJSONArray("value");
        }

        public boolean hasNext() throws IOException {
            if (index < values.length()) {
                return true;
            } else if (nextLink != null) {
                fetchNextPage();
                return values.length() > 0;
            } else {
                return false;
            }
        }

        public JSONObject next() throws IOException {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                if (index >= values.length() && nextLink != null) {
                    fetchNextPage();
                }
                return values.getJSONObject(index++);
            } catch (JSONException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        private void fetchNextPage() throws IOException {
            HttpGet request = new HttpGet(nextLink);
            request.setHeader("Authorization", "Bearer " + token.getAccessToken());
            try (
                    CloseableHttpResponse response = httpClient.execute(request)
            ) {
                jsonObject = new JsonResponseHandler().handleResponse(response);
                nextLink = jsonObject.optString("@odata.nextLink", null);
                values = jsonObject.getJSONArray("value");
                index = 0;
            } catch (JSONException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    private GraphIterator executeSearchRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        try {
            return new GraphIterator(executeJsonRequest(httpRequestBuilder));
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private JSONObject executeJsonRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        // TODO handle throttling https://learn.microsoft.com/en-us/graph/throttling
        HttpRequestBase request = httpRequestBuilder
                .setAccessToken(token.getAccessToken())
                .build();

        // DEBUG only, disable gzip encoding
        //request.setHeader("Accept-Encoding", "");
        //request.setHeader("Prefer", "outlook.timezone=\"GMT Standard Time\"");
        JSONObject jsonResponse;
        try (
                CloseableHttpResponse response = httpClient.execute(request)
        ) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                LOGGER.warn("Request returned " + response.getStatusLine());
            }
            jsonResponse = new JsonResponseHandler().handleResponse(response);
        }
        return jsonResponse;
    }

    private GraphObject executeGraphRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        // TODO handle throttling https://learn.microsoft.com/en-us/graph/throttling
        HttpRequestBase request = httpRequestBuilder
                .setAccessToken(token.getAccessToken())
                .build();

        // DEBUG only, disable gzip encoding
        //request.setHeader("Accept-Encoding", "");
        GraphObject graphObject;
        try (
                CloseableHttpResponse response = httpClient.execute(request)
        ) {
            if (response.getStatusLine().getStatusCode() == 400) {
                LOGGER.warn("Request returned " + response.getStatusLine());
            }
            graphObject = new GraphObject(new JsonResponseHandler().handleResponse(response));
            graphObject.statusCode = response.getStatusLine().getStatusCode();
        }
        return graphObject;
    }

}

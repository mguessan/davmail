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
import davmail.http.HttpClientAdapter;
import davmail.http.URIUtil;
import davmail.ui.NotificationDialog;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.http.Header;
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
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static davmail.exchange.graph.GraphObject.convertTimezoneFromExchange;

/**
 * Implement ExchangeSession based on Microsoft Graph
 */
public class GraphExchangeSession extends ExchangeSession {

    static final Map<String, String> partstatToResponseMap = new HashMap<>();
    static final Map<String, String> responseTypeToPartstatMap = new HashMap<>();
    static final Map<String, String> statusToBusyStatusMap = new HashMap<>();

    static {
        partstatToResponseMap.put("ACCEPTED", "accepted");
        partstatToResponseMap.put("TENTATIVE", "tentativelyAccepted");
        partstatToResponseMap.put("DECLINED", "declined");
        partstatToResponseMap.put("NEEDS-ACTION", "notResponded");

        responseTypeToPartstatMap.put("accepted", "ACCEPTED");
        responseTypeToPartstatMap.put("organizer", "ACCEPTED");
        responseTypeToPartstatMap.put("tentativelyAccepted", "TENTATIVE");
        responseTypeToPartstatMap.put("declined", "DECLINED");
        responseTypeToPartstatMap.put("none", "NEEDS-ACTION");
        responseTypeToPartstatMap.put("notResponded", "NEEDS-ACTION");

        statusToBusyStatusMap.put("TENTATIVE", "Tentative");
        statusToBusyStatusMap.put("CONFIRMED", "Busy");
        // Unable to map CANCELLED: cancelled events are directly deleted on Exchange
    }

    protected Map<String, String> urlcompnameToIdMap = new HashMap<>();

    /**
     * Graph folder is identified by mailbox and id
     */
    protected class Folder extends ExchangeSession.Folder {
        public FolderId folderId;
        protected String specialFlag = "";

        protected boolean isDefaultCalendar = false;

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

        public Event(String folderPath, FolderId folderId, GraphObject graphObject) {
            this.folderPath = folderPath;
            this.folderId = folderId;

            if ("IPF.Task".equals(graphObject.optString("objecttype"))) {
                // replace folder on task items requested as part of the default calendar
                try {
                    this.folderId = getFolderId(TASKS);
                } catch (IOException e) {
                    LOGGER.warn("Unable to replace folder with tasks");
                }
                displayName = graphObject.optString("summary");
                subject = graphObject.optString("summary");
            } else {
                displayName = graphObject.optString("subject");
                subject = graphObject.optString("subject");
            }

            this.graphObject = graphObject;

            id = graphObject.optString("id");
            String urlcompname = graphObject.optString("urlcompname");
            etag = graphObject.optString("changeKey");

            // prefer id as itemName
            itemName = StringUtil.base64ToUrl(id) + ".EML";
        }

        public Event(String folderPath, String itemName, String contentClass, String itemBody, String etag, String noneMatch) throws IOException {
            super(folderPath, itemName, contentClass, itemBody, etag, noneMatch);
            folderId = getFolderId(folderPath);
        }

        public Event(FolderId folderId, byte[] content) throws IOException {
            vCalendar = new VCalendar(content, email, getVTimezone());
            this.folderId = folderId;
        }

        @Override
        public byte[] getEventContent() throws IOException {
            byte[] content;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Get event: " + itemName);
            }
            try {
                if (vCalendar != null) {
                    return vCalendar.toString().getBytes(StandardCharsets.UTF_8);
                } else if ("IPF.Task".equals(folderId.folderClass)) {
                    VCalendar localVCalendar = new VCalendar();
                    VObject vTodo = new VObject();
                    vTodo.type = "VTODO";
                    localVCalendar.setTimezone(getVTimezone());
                    vTodo.setPropertyValue("LAST-MODIFIED", graphObject.optString("lastModifiedDateTime"));
                    vTodo.setPropertyValue("CREATED", graphObject.optString("createdDateTime"));
                    // use item id as uid
                    vTodo.setPropertyValue("UID", graphObject.optString("id"));
                    vTodo.setPropertyValue("TITLE", graphObject.optString("summary"));
                    vTodo.setPropertyValue("SUMMARY", graphObject.optString("summary"));

                    vTodo.addProperty(convertBodyToVproperty("DESCRIPTION", graphObject));

                    vTodo.setPropertyValue("PRIORITY", graphObject.getTaskPriority());
                    // not supported over graph
                    //vTodo.setPropertyValue("PERCENT-COMPLETE", );
                    vTodo.setPropertyValue("STATUS", graphObject.getVTodoStatusFromTask());

                    vTodo.setPropertyValue("DUE;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("dueDateTime")));
                    vTodo.setPropertyValue("DTSTART;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("startDateTime")));
                    vTodo.setPropertyValue("COMPLETED;VALUE=DATE", convertDateTimeTimeZoneToTaskDate(graphObject.optDateTimeTimeZone("completedDateTime")));

                    vTodo.setPropertyValue("CATEGORIES", graphObject.optString("categories"));

                    // handleRecurrence(localVCalendar, graphObject); does not yet work on microsoft side

                    localVCalendar.addVObject(vTodo);
                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    // with graph API there is no way to directly retrieve the MIME content to access VCALENDAR object
                    // so implementation is based on graph and mapi (extended) properties

                    VCalendar localVCalendar = new VCalendar();
                    // set email on vcalendar object for shared calendars
                    localVCalendar.setEmail(getCalendarEmail(folderPath));
                    // set timezone based on start date timezone
                    String originalStarttimezone = graphObject.optString("originalStartTimeZone");
                    if (originalStarttimezone != null) {
                        localVCalendar.setTimezone(getVTimezone(originalStarttimezone));
                    } else {
                        localVCalendar.setTimezone(getVTimezone());
                    }
                    localVCalendar.addVObject(buildVEvent(graphObject));

                    handleException(localVCalendar, graphObject);

                    handleRecurrence(localVCalendar, graphObject);

                    content = localVCalendar.toString().getBytes(StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
            return content;
        }

        private void handleException(VCalendar localVCalendar, GraphObject graphObject) throws DavMailException, JSONException {
            JSONArray cancelledOccurrences = graphObject.optJSONArray("cancelledOccurrences");
            if (cancelledOccurrences != null) {
                HashSet<String> exDateValues = new HashSet<>();
                VProperty startDate = localVCalendar.getFirstVevent().getProperty("DTSTART");
                for (int i = 0; i < cancelledOccurrences.length(); i++) {
                    String cancelledOccurrence = null;
                    try {
                        cancelledOccurrence = cancelledOccurrences.getString(i);
                        cancelledOccurrence = cancelledOccurrence.substring(cancelledOccurrence.lastIndexOf('.') + 1);
                        String cancelledDate = convertDateFromExchange(cancelledOccurrence);

                        exDateValues.add(cancelledDate.substring(0, 8) + startDate.getValue().substring(8));

                    } catch (IndexOutOfBoundsException | JSONException e) {
                        LOGGER.warn("Invalid cancelled occurrence: " + cancelledOccurrence);
                    }
                }
                // add EXDATE values in a single property, will be converted back to multiple lines by fixICS
                VProperty exDate = new VProperty("EXDATE", StringUtil.join(exDateValues, ","));
                exDate.setParam("TZID", startDate.getParamValue("TZID"));
                localVCalendar.addFirstVeventProperty(exDate);
            }

            JSONArray exceptionOccurrences = graphObject.optJSONArray("exceptionOccurrences");
            if (exceptionOccurrences != null) {
                for (int i = 0; i < exceptionOccurrences.length(); i++) {
                    GraphObject exceptionOccurrence = new GraphObject(exceptionOccurrences.optJSONObject(i)
                            // need to override uid, iCalUid is different for each occurrence on server
                            .put("iCalUId", graphObject.optString("iCalUId")));
                    VObject vEvent = buildVEvent(exceptionOccurrence);
                    vEvent.addProperty(exceptionOccurrence.getRecurrenceId());
                    localVCalendar.addVObject(vEvent);
                }
            }
        }

        private VObject buildVEvent(GraphObject jsonEvent) throws DavMailException, JSONException {
            VObject vEvent = new VObject();
            vEvent.type = "VEVENT";
            // fetch custom iCalUId from transactionId
            String iCalUId = jsonEvent.optString("transactionId");
            if (iCalUId == null) {
                // default to O365 iCalUid
                iCalUId = jsonEvent.optString("iCalUId");
            }
            vEvent.setPropertyValue("UID", iCalUId);
            vEvent.setPropertyValue("SUMMARY", jsonEvent.optString("subject"));

            vEvent.addProperty(convertBodyToVproperty("DESCRIPTION", jsonEvent));

            vEvent.setPropertyValue("LAST-MODIFIED", jsonEvent.optString("lastModifiedDateTime"));
            vEvent.setPropertyValue("DTSTAMP", jsonEvent.optString("lastModifiedDateTime"));

            // retrieve original start timezone to restore original timezone on recurring events across DST
            String originalStartTimeZone = jsonEvent.optString("originalStartTimeZone");
            vEvent.addProperty(convertDateTimeTimeZoneToVproperty("DTSTART", jsonEvent.optJSONObject("start"), getVTimezone(originalStartTimeZone).getPropertyValue("TZID")));
            vEvent.addProperty(convertDateTimeTimeZoneToVproperty("DTEND", jsonEvent.optJSONObject("end"), getVTimezone(originalStartTimeZone).getPropertyValue("TZID")));

            vEvent.setPropertyValue("LOCATION", jsonEvent.optString("location", "displayName"));
            vEvent.setPropertyValue("CATEGORIES", jsonEvent.optString("categories"));

            vEvent.setPropertyValue("CLASS", convertClassFromExchange(jsonEvent.optString("sensitivity")));

            // custom microsoft properties
            String showAs = jsonEvent.optString("showAs");
            if (showAs != null) {
                vEvent.setPropertyValue("X-MICROSOFT-CDO-BUSYSTATUS", showAs.toUpperCase());
            }
            String isAllDay = jsonEvent.optString("isAllDay");
            if (isAllDay != null) {
                vEvent.setPropertyValue("X-MICROSOFT-CDO-ALLDAYEVENT", isAllDay.toUpperCase());
            }
            String responseRequested = jsonEvent.optString("responseRequested");
            if (responseRequested != null) {
                vEvent.setPropertyValue("X-MICROSOFT-CDO-ISRESPONSEREQUESTED", responseRequested.toUpperCase());
            }

            if (jsonEvent.optBoolean("isReminderOn")) {
                VObject vAlarm = new VObject();
                vAlarm.type = "VALARM";
                vAlarm.addPropertyValue("ACTION", "DISPLAY");
                int reminderMinutesBeforeStart = jsonEvent.optInt("reminderMinutesBeforeStart");
                if (reminderMinutesBeforeStart > 0) {
                    vAlarm.addPropertyValue("TRIGGER", "-PT" + reminderMinutesBeforeStart + "M");
                }
                vEvent.addVObject(vAlarm);
            }

            vEvent.setPropertyValue("X-MOZ-SEND-INVITATIONS", jsonEvent.optString("xmozsendinvitations"));
            vEvent.setPropertyValue("X-MOZ-LASTACK", jsonEvent.optString("xmozlastack"));
            vEvent.setPropertyValue("X-MOZ-SNOOZE-TIME", jsonEvent.optString("xmozsnoozetime"));

            setAttendees(vEvent, jsonEvent);

            return vEvent;
        }

        private void handleRecurrence(VCalendar localVCalendar, GraphObject graphObject) throws JSONException, DavMailException {

            JSONObject recurrence = graphObject.optJSONObject("recurrence");
            if (recurrence != null) {
                StringBuilder rruleValue = new StringBuilder();
                JSONObject pattern = recurrence.getJSONObject("pattern");
                JSONObject range = recurrence.getJSONObject("range");
                // daily, weekly, absoluteMonthly, relativeMonthly, absoluteYearly, relativeYearly
                String patternType = pattern.getString("type");
                int interval = pattern.getInt("interval");
                //  first, second, third, fourth, last
                String index = pattern.optString("index", null);
                // convert index
                if ("first".equals(index)) {
                    index = "1";
                } else if ("second".equals(index)) {
                    index = "2";
                } else if ("third".equals(index)) {
                    index = "3";
                } else if ("fourth".equals(index)) {
                    index = "4";
                } else if ("last".equals(index)) {
                    index = "-1";
                }
                // The month in which the event occurs
                String month = pattern.getString("month");
                if ("0".equals(month)) {
                    month = null;
                }
                // The first day of the week
                String firstDayOfWeek = pattern.getString("firstDayOfWeek");
                // The day of the month on which the event occurs
                String dayOfMonth = pattern.getString("dayOfMonth");
                if ("0".equals(dayOfMonth)) {
                    dayOfMonth = null;
                }
                // A collection of the days of the week on which the event occurs
                JSONArray daysOfWeek = pattern.optJSONArray("daysOfWeek");
                String rangeType = range.getString("type");

                rruleValue.append("FREQ=");
                if (patternType.startsWith("absolute") || patternType.startsWith("relative")) {
                    rruleValue.append(patternType.substring(8).toUpperCase());
                } else {
                    rruleValue.append(patternType.toUpperCase());
                }
                if (rangeType.equals("endDate")) {
                    String endDate = buildUntilDate(range.getString("endDate"), graphObject.optJSONObject("start"));
                    rruleValue.append(";UNTIL=").append(endDate);
                } else if (rangeType.equals("numbered")) {
                    int numberOfOccurrences = range.getInt("numberOfOccurrences");
                    rruleValue.append(";COUNT=").append(numberOfOccurrences);
                } // noEnd is third option
                if (interval > 0) {
                    rruleValue.append(";INTERVAL=").append(interval);
                }
                if (dayOfMonth != null && !dayOfMonth.isEmpty()) {
                    rruleValue.append(";BYMONTHDAY=").append(dayOfMonth);
                }
                if (month != null && !month.isEmpty()) {
                    rruleValue.append(";BYMONTH=").append(month);
                }
                if (daysOfWeek != null && daysOfWeek.length() > 0) {
                    ArrayList<String> days = new ArrayList<>();
                    for (int i = 0; i < daysOfWeek.length(); i++) {
                        StringBuilder byDay = new StringBuilder();
                        if (index != null && !"weekly".equals(patternType)) {
                            byDay.append(index);
                        }
                        byDay.append(daysOfWeek.getString(i).substring(0, 2).toUpperCase());
                        days.add(byDay.toString());
                    }
                    rruleValue.append(";BYDAY=").append(String.join(",", days));
                }
                // handle other frequencies
                if ("weekly".equals(patternType) && firstDayOfWeek.length() >= 2) {
                    rruleValue.append(";WKST=").append(firstDayOfWeek.substring(0, 2).toUpperCase());
                }

                localVCalendar.addFirstVeventProperty(new VProperty("RRULE", rruleValue.toString()));
            }
        }

        private String buildUntilDate(String date, JSONObject startDate) throws DavMailException {
            String result = null;
            if (date != null && date.length() == 10) {
                String startDateTimeZone = startDate.optString("timeZone");
                String startDateDateTime = startDate.optString("dateTime");
                // graph provided until date does not have time part, get value from startDate
                String untilDateTime = date + startDateDateTime.substring(10);

                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(startDateTimeZone)));
                try {
                    result = formatter.format(parser.parse(untilDateTime));
                } catch (ParseException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", date);
                }
            }
            return result;
        }

        private String convertOriginalStartDate(String originalStart, String originalStartTimeZone) throws DavMailException {
            String result = originalStart;
            // should not happen originalStart is supposed to be in UTC, except it is actually ISO8601
            if (originalStart != null && !originalStart.endsWith("Z")) {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(originalStartTimeZone)));
                try {
                    result = formatter.format(parser.parse(originalStart));
                } catch (ParseException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", originalStart);
                }
            }
            return result;
        }


        private void setAttendees(VObject vEvent, GraphObject jsonEvent) throws JSONException {
            // handle organizer
            JSONObject organizer = jsonEvent.optJSONObject("organizer");
            if (organizer != null) {
                vEvent.addProperty(convertEmailAddressToVproperty("ORGANIZER", organizer.optJSONObject("emailAddress")));
            }

            JSONArray attendees = jsonEvent.optJSONArray("attendees");
            if (attendees != null) {
                for (int i = 0; i < attendees.length(); i++) {
                    JSONObject attendee = attendees.getJSONObject(i);
                    JSONObject emailAddress = attendee.getJSONObject("emailAddress");
                    VProperty attendeeProperty = convertEmailAddressToVproperty("ATTENDEE", emailAddress);

                    // The response type. Possible values are: none, organizer, tentativelyAccepted, accepted, declined, notResponded.
                    String responseType = attendee.getJSONObject("status").optString("response");
                    String myResponseType = graphObject.optString("responseStatus", "response");

                    // TODO Test if applicable
                    if (email.equalsIgnoreCase(emailAddress.optString("address")) && myResponseType != null) {
                        attendeeProperty.addParam("PARTSTAT", responseTypeToPartstat(myResponseType));
                    } else {
                        attendeeProperty.addParam("PARTSTAT", responseTypeToPartstat(responseType));
                    }
                    // the attendee type: required, optional, resource.
                    String type = attendee.optString("type");
                    if ("required".equals(type)) {
                        attendeeProperty.addParam("ROLE", "REQ-PARTICIPANT");
                    } else if ("optional".equals(type)) {
                        attendeeProperty.addParam("ROLE", "OPT-PARTICIPANT");
                    }

                    vEvent.addProperty(attendeeProperty);
                }
            }
        }

        /**
         * Convert response type to partstat value
         *
         * @param responseType response type
         * @return partstat value
         */
        private String responseTypeToPartstat(String responseType) {
            // The response type. Possible values are: none, organizer, tentativelyAccepted, accepted, declined, notResponded.
            if ("accepted".equals(responseType) || "organizer".equals(responseType)) {
                return "ACCEPTED";
            } else if ("tentativelyAccepted".equals(responseType)) {
                return "TENTATIVE";
            } else if ("declined".equals(responseType)) {
                return "DECLINED";
            } else {
                return "NEEDS-ACTION";
            }
        }

        @Override
        public ItemResult createOrUpdate() throws IOException {
            if (vCalendar.isTodo() && isMainCalendar(folderPath)) {
                // task item, move to tasks folder
                folderId = getFolderId(TASKS);
            }

            String currentItemId = null;
            String currentEtag = null;
            boolean isMeetingResponse = false;
            boolean isMozSendInvitations = false;
            boolean isMozDismiss = false;

            JSONObject existingJsonEvent = getEventIfExists(folderId, itemName);
            if (existingJsonEvent != null) {
                GraphObject currentItem = new GraphObject(existingJsonEvent);
                currentItemId = existingJsonEvent.optString("id", null);
                currentEtag = new GraphObject(existingJsonEvent).optString("changeKey");

                String myResponseType = currentItem.optString("responseStatus", "response");

                String currentAttendeeStatus = responseTypeToPartstatMap.get(myResponseType);
                String newAttendeeStatus = vCalendar.getAttendeeStatus();

                isMeetingResponse = vCalendar.isMeeting() && !vCalendar.isMeetingOrganizer()
                        && newAttendeeStatus != null
                        && !newAttendeeStatus.equals(currentAttendeeStatus)
                        // avoid nullpointerexception on unknown status
                        && partstatToResponseMap.get(newAttendeeStatus) != null;

                // Check mozilla last ack and snooze
                String newmozlastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
                String currentmozlastack = currentItem.optString("xmozlastack");
                boolean ismozack = newmozlastack != null && !newmozlastack.equals(currentmozlastack);

                String newmozsnoozetime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
                String currentmozsnoozetime = currentItem.optString("xmozsnoozetime");
                boolean ismozsnooze = newmozsnoozetime != null && !newmozsnoozetime.equals(currentmozsnoozetime);

                isMozSendInvitations = (newmozlastack == null && newmozsnoozetime == null) // not thunderbird
                        || !(ismozack || ismozsnooze);
                isMozDismiss = ismozack || ismozsnooze;

                LOGGER.debug("Existing item found with etag: " + currentEtag + " client etag: " + etag + " id: " + currentItemId);
            }

            ItemResult itemResult = new ItemResult();
            if ("*".equals(noneMatch)) {
                // create requested but already exists
                if (currentItemId != null) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            } else if (etag != null) {
                // update requested
                if (currentItemId == null || !etag.equals(currentEtag)) {
                    itemResult.status = HttpStatus.SC_PRECONDITION_FAILED;
                    return itemResult;
                }
            }

            VObject vEvent = vCalendar.getFirstVevent();
            try {
                GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder();

                if (currentItemId != null && isMeetingResponse) {
                    // over graph always assume server side calendar management
                    String body = null;
                    boolean sendResponse = true;
                    // This is a meeting response, let user edit notification message
                    if (Settings.getBooleanProperty("davmail.caldavEditNotifications")) {
                        String vEventSubject = vCalendar.getFirstVeventPropertyValue("SUMMARY");
                        if (vEventSubject == null) {
                            vEventSubject = BundleMessage.format("MEETING_REQUEST");
                        }

                        String status = vCalendar.getAttendeeStatus();
                        String notificationSubject = (status != null) ? (BundleMessage.format(status) + vEventSubject) : subject;

                        NotificationDialog notificationDialog = new NotificationDialog(notificationSubject, "");
                        if (!notificationDialog.getSendNotification()) {
                            LOGGER.debug("Notification canceled by user");
                            sendResponse = false;
                        }
                        // get description from dialog
                        body = notificationDialog.getBody();
                    }
                    // Prepare request to accept/tentativelyAccept/decline meeting request
                    try {
                        JSONObject jsonBody = new JSONObject();
                        jsonBody.put("sendResponse", sendResponse);
                        if (body != null && !body.isEmpty()) {
                            jsonBody.put("comment", body);
                        }
                        String action = "accept";
                        String attendeeStatus = vCalendar.getAttendeeStatus();
                        if ("ACCEPTED".equals(attendeeStatus)) {
                            action = "accept";
                        } else if ("DECLINED".equals(attendeeStatus)) {
                            action = "decline";
                        } else if ("TENTATIVE".equals(attendeeStatus)) {
                            action = "tentativelyAccept";
                        }

                        graphRequestBuilder.setMethod(HttpPost.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("events")
                                .setObjectId(currentItemId)
                                .setAction(action)
                                .setJsonBody(jsonBody);
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }

                } else if (currentItemId != null && isMozDismiss) {
                    graphRequestBuilder.setMethod(HttpPost.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("events")
                            .setObjectId(currentItemId)
                            .setAction("dismissReminder");
                } else if ("IPF.Task".equals(folderId.folderClass)) {
                    JSONObject jsonTask = buildJsonTask(vEvent);
                    // handleRrule(jsonTask, vEvent.getProperty("RRULE")); does not yet work on microsoft side

                    if (currentItemId == null) {
                        graphRequestBuilder
                                .setMethod(HttpPost.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("todo/lists")
                                .setObjectId(folderId.id)
                                .setChildType("tasks")
                                .setChildId(currentItemId)
                                .setJsonBody(jsonTask);
                    } else {
                        graphRequestBuilder
                                .setMethod(HttpPatch.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("todo/lists")
                                .setObjectId(folderId.id)
                                .setChildType("tasks")
                                .setChildId(currentItemId)
                                .setJsonBody(jsonTask);
                    }
                } else {

                    JSONObject jsonEvent = buildJsonEvent(vEvent);

                    // urlcompname is an extended property, wrap in GraphObject
                    GraphObject localGraphObject = new GraphObject(jsonEvent);
                    String urlcompname = convertItemNameToEML(itemName);
                    localGraphObject.put("urlcompname", urlcompname);

                    // on event creation push iCalUId from event to transactionId
                    String iCalUId = vEvent.getPropertyValue("UID");
                    if (iCalUId != null && !iCalUId.isEmpty() && currentItemId == null) {
                        localGraphObject.put("transactionId", iCalUId);
                    }

                    // handle reminder configuration
                    jsonEvent.put("isReminderOn", vCalendar.hasVAlarm());
                    jsonEvent.put("reminderMinutesBeforeStart", vCalendar.getReminderMinutesBeforeStart());

                    handleRrule(jsonEvent, vEvent.getProperty("RRULE"));

                    if (currentItemId != null) {
                        // assume we can delete occurrence only on existing event
                        List<VProperty> exdateProperty = vEvent.getProperties("EXDATE");
                        if (exdateProperty != null && !exdateProperty.isEmpty()) {
                            JSONArray cancelledOccurrences = new JSONArray();
                            for (VProperty exdate : exdateProperty) {
                                String exdateTzid = exdate.getParamValue("TZID");
                                String exDateValue = vCalendar.convertCalendarDateToGraph(exdate.getValue(), exdateTzid);
                                deleteEventOccurrence(currentItemId, exDateValue);
                            }
                            jsonEvent.put("cancelledOccurrences", cancelledOccurrences);
                        }

                        handleModifiedOccurrences(vCalendar, existingJsonEvent);
                    }

                    // store mozilla invitations option
                    String xMozSendInvitations = vCalendar.getFirstVeventPropertyValue("X-MOZ-SEND-INVITATIONS");
                    if (xMozSendInvitations != null) {
                        localGraphObject.put("xmozsendinvitations", xMozSendInvitations);
                    }
                    // handle mozilla alarm
                    String xMozLastack = vCalendar.getFirstVeventPropertyValue("X-MOZ-LASTACK");
                    if (xMozLastack != null) {
                        localGraphObject.put("xmozlastack", xMozLastack);
                    }
                    String xMozSnoozeTime = vCalendar.getFirstVeventPropertyValue("X-MOZ-SNOOZE-TIME");
                    if (xMozSnoozeTime != null) {
                        localGraphObject.put("xmozsnoozetime", xMozSnoozeTime);
                    }

                    localGraphObject.put("isAllDay", vCalendar.isCdoAllDay());

                    // showAs: free, tentative, busy, oof, workingElsewhere, unknown
                    String status = vCalendar.getFirstVeventPropertyValue("STATUS");
                    if ("TENTATIVE".equals(status)) {
                        // this is a tentative event
                        localGraphObject.put("showAs", "tentative");
                    } else {
                        // otherwise, we use the same value as before, as received from the server
                        // however, the case matters, so we still have to transform it "BUSY" -> "Busy"
                        // TODO double check this
                        localGraphObject.put("showAs", "BUSY".equals(vCalendar.getFirstVeventPropertyValue("X-MICROSOFT-CDO-BUSYSTATUS")) ? "busy" : "free");
                    }

                    if (currentItemId == null) {
                        graphRequestBuilder.setMethod(HttpPost.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("calendars")
                                .setObjectId(folderId.id)
                                .setChildType("events")
                                .setJsonBody(jsonEvent);
                    } else {
                        graphRequestBuilder.setMethod(HttpPatch.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("events")
                                .setObjectId(currentItemId)
                                .setJsonBody(jsonEvent);
                    }
                }

                GraphObject graphResponse = executeGraphRequest(graphRequestBuilder);
                itemResult.status = graphResponse.statusCode;
                if (itemResult.status == HttpStatus.SC_ACCEPTED) {
                    LOGGER.debug("Sent meeting response");
                    itemResult.status = HttpStatus.SC_OK;
                }

                // TODO: Force etag check?
                itemResult.etag = graphResponse.optString("changeKey");
                /*if (!"IPF.Task".equals(folderId.folderClass)) {
                graphResponse = executeGraphRequest(new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("events")
                        .setObjectId(graphResponse.optString("id"))
                        .setSelect("id"));
                }*/

                // workaround for Thunderbird, keep a cache of itemName to id map
                urlcompnameToIdMap.put(itemName, graphResponse.optString("id"));

                itemResult.itemName = itemName; // preserve requested itemName
                itemResult.etag = graphResponse.optString("changeKey");

            } catch (JSONException e) {
                throw new IOException(e);
            }

            return itemResult;
        }

        /**
         * Convert vCalendar rrule to graph format.
         * @param jsonEvent graph json event
         * @param rrule vCalendar rrule
         * @throws JSONException on error
         * @throws DavMailException on error
         */
        private void handleRrule(JSONObject jsonEvent, VProperty rrule) throws JSONException, DavMailException {
            if (rrule != null) {
                JSONObject start = jsonEvent.optJSONObject("start");
                if (start == null) {
                    // failover for tasks
                    start = jsonEvent.optJSONObject("startDateTime");
                }
                String startDate = start.getString("dateTime").substring(0, 10); // start date in yyyy-MM-dd format
                String startTimeZone = start.optString("timeZone");

                // get information from rrule property
                Map<String, String> rrules = rrule.getValuesAsMap();
                String frequency = rrules.get("FREQ");
                String until = rrules.get("UNTIL");
                String count = rrules.get("COUNT");
                int interval = rrules.containsKey("INTERVAL") ? Integer.parseInt(rrules.get("INTERVAL")) : 1; // default interval is 1
                String byDay = rrules.get("BYDAY");
                String byMonthDay = rrules.get("BYMONTHDAY");
                String byMonth = rrules.get("BYMONTH");
                String wkst = rrules.get("WKST"); // week start day (sunday or monday)

                // build range
                JSONObject range;
                if (until != null) {
                    // endDate recurrenceRange
                    String endDate = convertUntilToEndDate(until, startTimeZone);
                    range = new JSONObject().put("type", "endDate").put("startDate", startDate)
                            .put("endDate", endDate).put("recurrenceTimeZone", startTimeZone);
                } else if (count != null) {
                    // numbered recurrenceRange
                    range = new JSONObject().put("type", "numbered").put("startDate", startDate)
                            .put("numberOfOccurrences", Integer.parseInt(count));
                } else {
                    range = new JSONObject().put("type", "noEnd").put("startDate", startDate).put("endDate", "0001-01-01");
                }

                // build pattern
                JSONObject pattern = new JSONObject().put("interval", interval);

                if ("DAILY".equals(frequency)) {
                    pattern.put("type", "daily").put("dayOfMonth", 0);
                } else if ("WEEKLY".equals(frequency)) {
                    pattern.put("type", "weekly").put("daysOfWeek", byDay != null ? convertByDayToArray(byDay) : new JSONArray().put(getDayOfWeek(startDate)));
                    if (wkst != null) { // TODO is this mandatory ?
                        pattern.put("firstDayOfWeek", convertCaldavDayToGraph(wkst));
                    }
                } else if ("MONTHLY".equals(frequency)) {
                    if (byDay != null) {
                        pattern.put("type", "relativeMonthly");
                        setRelativePattern(pattern, byDay);
                    } else {
                        pattern.put("type", "absoluteMonthly");
                        pattern.put("dayOfMonth", byMonthDay != null ? Integer.parseInt(byMonthDay) : Integer.parseInt(startDate.substring(8, 10)));
                    }
                } else if ("YEARLY".equals(frequency)) {
                    if (byDay != null) {
                        pattern.put("type", "relativeYearly");
                        setRelativePattern(pattern, byDay);
                    } else {
                        pattern.put("type", "absoluteYearly")
                                .put("dayOfMonth", byMonthDay != null ? Integer.parseInt(byMonthDay) : Integer.parseInt(startDate.substring(8, 10)));
                    }
                    if (byMonth != null) {
                        pattern.put("month", Integer.parseInt(byMonth));
                    } else {
                        pattern.put("month", Integer.parseInt(startDate.substring(5, 7)));
                    }
                }

                jsonEvent.put("recurrence", new JSONObject().put("pattern", pattern).put("range", range));
            }
        }

        private JSONArray convertByDayToArray(String byDay) {
            JSONArray daysOfWeek = new JSONArray();
            for (String day : byDay.split(",")) {
                // strip numeric prefix (e.g. "2MO" -> "MO", "-1FR" -> "FR")
                daysOfWeek.put(convertCaldavDayToGraph(day.replaceAll("^-?\\d+", "")));
            }
            return daysOfWeek;
        }

        private String convertCaldavDayToGraph(String weekDay) {
            switch (weekDay) {
                case "MO":
                    return "monday";
                case "TU":
                    return "tuesday";
                case "WE":
                    return "wednesday";
                case "TH":
                    return "thursday";
                case "FR":
                    return "friday";
                case "SA":
                    return "saturday";
                case "SU":
                    return "sunday";
                default:
                    return weekDay.toLowerCase();
            }
        }

        private void setRelativePattern(JSONObject pattern, String byDay) throws JSONException {
            // extract index from first BYDAY value (e.g. "2MO" -> index "second", "-1FR" -> index "last")
            String firstDay = byDay.split(",")[0];
            String indexStr = firstDay.replaceAll("[A-Z]+$", "");
            if (!indexStr.isEmpty()) {
                pattern.put("index", convertIndex(Integer.parseInt(indexStr)));
            }
            pattern.put("daysOfWeek", convertByDayToArray(byDay));
        }

        private String convertIndex(int index) {
            switch (index) {
                case 1:
                    return "first";
                case 2:
                    return "second";
                case 3:
                    return "third";
                case 4:
                    return "fourth";
                case -1:
                    return "last";
                default:
                    return "first";
            }
        }

        private String convertUntilToEndDate(String until, String timeZone) throws DavMailException {
            try {
                SimpleDateFormat parser;
                if (until.length() == 8) {
                    parser = new SimpleDateFormat("yyyyMMdd");
                    parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(timeZone)));
                } else if (until.endsWith("Z")) {
                    parser = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                } else {
                    parser = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                    parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(timeZone)));
                }
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                formatter.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(timeZone)));
                return formatter.format(parser.parse(until));
            } catch (ParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", until);
            }
        }

        private String getDayOfWeek(String date) throws DavMailException {
            if (date != null) {
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                    SimpleDateFormat formatter = new SimpleDateFormat("EEEE", Locale.ENGLISH);
                    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return formatter.format(parser.parse(date));
                } catch (ParseException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", date);
                }
            }
            return null;
        }

        private void handleModifiedOccurrences(VCalendar vCalendar, JSONObject existingJsonEvent) throws IOException, JSONException {
            for (VObject modifiedOccurrence : vCalendar.getModifiedOccurrences()) {
                VProperty originalDateProperty = modifiedOccurrence.getProperty("RECURRENCE-ID");
                String originalDateZulu;
                try {
                    originalDateZulu = vCalendar.convertCalendarDateToExchangeZulu(originalDateProperty.getValue(), originalDateProperty.getParamValue("TZID"));
                } catch (IOException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", originalDateProperty.getValue());
                }
                LOGGER.debug("Looking for occurrence " + originalDateZulu);
                // try to find modified occurrence in existing event
                JSONArray exceptionOccurrences = existingJsonEvent.optJSONArray("exceptionOccurrences");
                boolean occurrenceFound = false;
                if (exceptionOccurrences != null) {
                    for (int i = 0; i < exceptionOccurrences.length(); i++) {
                        JSONObject exceptionOccurrence = exceptionOccurrences.optJSONObject(i);
                        String exceptionOriginalStart = convertOriginalStartDate(exceptionOccurrence.optString("originalStart"), exceptionOccurrence.optString("originalStartTimeZone"));
                        LOGGER.debug("Looking at occurrence " + exceptionOriginalStart + " for " + originalDateZulu);
                        if (originalDateZulu.equals(exceptionOriginalStart)) {
                            updateExceptionOccurrence(modifiedOccurrence, exceptionOccurrence.getString("id"));
                            occurrenceFound = true;
                            break;
                        }
                    }
                }
                if (!occurrenceFound) {
                    createNewModifiedOccurrence(modifiedOccurrence, existingJsonEvent, originalDateZulu);
                }
            }
        }

        /**
         * Create a new modified occurrence on event.
         * @param modifiedOccurrence modified occurrence vEvent
         * @param existingJsonEvent master graph event
         * @param originalDateZulu original date in zulu format
         * @throws IOException on error
         * @throws JSONException on error
         */
        private void createNewModifiedOccurrence(VObject modifiedOccurrence, JSONObject existingJsonEvent, String originalDateZulu) throws IOException, JSONException {
            // assume instance is on same day in UTC timezone
            String startDateTime = originalDateZulu.substring(0, 10) + "T00:00:00.0000000";
            String endDateTime = originalDateZulu.substring(0, 10) + "T23:59:59.9999999";
            // search for occurrence in master event instances
            GraphObject graphResponse = executeGraphRequest(new GraphRequestBuilder().setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("events")
                    .setObjectId(existingJsonEvent.optString("id"))
                    .setChildType("instances")
                    .setStartDateTime(startDateTime)
                    .setEndDateTime(endDateTime));

            JSONArray occurrences = graphResponse.optJSONArray("value");
            if (occurrences != null && occurrences.length() > 0) {
                for (int i = 0; i < occurrences.length(); i++) {
                    JSONObject occurrence = occurrences.getJSONObject(i);
                    String occurrenceId = occurrence.optString("id");
                    if (occurrenceId != null) {
                        updateExceptionOccurrence(modifiedOccurrence, occurrenceId);
                    }
                }
            } else {
                LOGGER.warn("No occurrence found for " + originalDateZulu);
            }
        }

        private void updateExceptionOccurrence(VObject modifiedOccurrence, String exceptionOccurrenceId) throws IOException, JSONException {
            LOGGER.debug("Updating occurrence " + modifiedOccurrence.getPropertyValue("SUMMARY") + " " + modifiedOccurrence.getPropertyValue("RECURRENCE-ID"));

            JSONObject jsonEvent = buildJsonEvent(modifiedOccurrence);

            GraphObject graphResponse = executeGraphRequest(new GraphRequestBuilder().setMethod(HttpPatch.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("events")
                    .setObjectId(exceptionOccurrenceId)
                    .setJsonBody(jsonEvent));

            LOGGER.debug("Updated occurrence: " + graphResponse.jsonObject.toString());
        }

        private JSONObject buildJsonEvent(VObject vEvent) throws JSONException, IOException {
            JSONObject jsonEvent = new JSONObject();
            GraphObject localGraphObject = new GraphObject(jsonEvent);

            jsonEvent.put("subject", vEvent.getPropertyValue("SUMMARY"));

            VProperty dtStart = vEvent.getProperty("DTSTART");
            String dtStartTzid = dtStart.getParamValue("TZID");
            jsonEvent.put("start", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(dtStart.getValue(), dtStartTzid)).put("timeZone", dtStartTzid));

            VProperty dtEnd = vEvent.getProperty("DTEND");
            String dtEndTzid = dtEnd.getParamValue("TZID");
            jsonEvent.put("end", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(dtEnd.getValue(), dtEndTzid)).put("timeZone", dtEndTzid));

            VProperty descriptionProperty = vEvent.getProperty("DESCRIPTION");
            String description = null;
            if (descriptionProperty != null) {
                description = vEvent.getProperty("DESCRIPTION").getParamValue("ALTREP");
            }
            if (description != null && description.startsWith("data:text/html,")) {
                description = URIUtil.decode(description.replaceFirst("data:text/html,", ""));
                jsonEvent.put("body", new JSONObject().put("content", description).put("contentType", "html"));
            } else {
                description = vEvent.getPropertyValue("DESCRIPTION");
                jsonEvent.put("body", new JSONObject().put("content", description).put("contentType", "text"));
            }

            String location = vEvent.getPropertyValue("LOCATION");
            jsonEvent.put("location", new JSONObject().put("displayName", location));

            localGraphObject.setCategories(vEvent.getPropertyValue("CATEGORIES"));
            // Collect categories on multiple lines
            List<VProperty> categories = vEvent.getProperties("CATEGORIES");
            if (categories != null) {
                HashSet<String> categoryValues = new HashSet<>();
                for (VProperty category : categories) {
                    categoryValues.add(category.getValue());
                }
                localGraphObject.setCategories(StringUtil.join(categoryValues, ","));
            }

            if (vCalendar.isMeeting()) {
                // build attendee list
                JSONArray attendees = new JSONArray();
                jsonEvent.put("attendees", attendees);

                List<VProperty> attendeeProperties = vEvent.getProperties("ATTENDEE");
                if (attendeeProperties != null) {
                    for (VProperty property : attendeeProperties) {
                        JSONObject jsonAttendee = new JSONObject();
                        String attendeeEmail = vCalendar.getEmailValue(property);
                        if (attendeeEmail != null && attendeeEmail.indexOf('@') >= 0) {
                            String cn = property.getParamValue("CN");
                            jsonAttendee.put("emailAddress", new JSONObject().put("name", cn)
                                    .put("address", attendeeEmail));

                            String attendeeRole = property.getParamValue("ROLE");
                            if ("REQ-PARTICIPANT".equals(attendeeRole)) {
                                jsonAttendee.put("type", "required");
                            } else {
                                jsonAttendee.put("type", "optional");
                            }
                            attendees.put(jsonAttendee);
                        }
                    }
                }
            }

            return jsonEvent;
        }

        private void deleteEventOccurrence(String id, String exDateValue) throws IOException, JSONException {
            String startDateTime = exDateValue.substring(0, 10) + "T00:00:00.0000000";
            String endDateTime = exDateValue.substring(0, 10) + "T23:59:59.9999999";
            GraphObject graphResponse = executeGraphRequest(new GraphRequestBuilder().setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("events")
                    .setObjectId(id)
                    .setChildType("instances")
                    .setStartDateTime(startDateTime)
                    .setEndDateTime(endDateTime));

            JSONArray occurrences = graphResponse.optJSONArray("value");
            if (occurrences != null && occurrences.length() > 0) {
                for (int i = 0; i < occurrences.length(); i++) {
                    JSONObject occurrence = occurrences.getJSONObject(i);
                    String occurrenceId = occurrence.optString("id");
                    if (occurrenceId != null) {
                        executeJsonRequest(new GraphRequestBuilder().setMethod(HttpDelete.METHOD_NAME)
                                .setMailbox(folderId.mailbox)
                                .setObjectType("events")
                                .setObjectId(occurrenceId));
                    }
                }
            }
        }

        private JSONObject buildJsonTask(VObject vTodo) throws JSONException, IOException {
            JSONObject jsonEvent = new JSONObject();
            GraphObject localGraphObject = new GraphObject(jsonEvent);

            localGraphObject.put("summary", vTodo.getPropertyValue("SUMMARY"));

            localGraphObject.setTaskImportanceFromVTodo(vTodo);
            localGraphObject.setTaskStatusFromVTodo(vTodo);

            // TODO refactor duplicate code with event
            VProperty descriptionProperty = vTodo.getProperty("DESCRIPTION");
            String description = null;
            if (descriptionProperty != null) {
                description = vTodo.getProperty("DESCRIPTION").getParamValue("ALTREP");
            }
            if (description != null && description.startsWith("data:text/html,")) {
                description = URIUtil.decode(description.replaceFirst("data:text/html,", ""));
                jsonEvent.put("body", new JSONObject().put("content", description).put("contentType", "html"));
            } else {
                description = vTodo.getPropertyValue("DESCRIPTION");
                jsonEvent.put("body", new JSONObject().put("content", description).put("contentType", "text"));
            }

            VProperty dtStart = vTodo.getProperty("DTSTART");
            if (dtStart != null) {
                String dtStartTzid = dtStart.getParamValue("TZID");
                if (dtStartTzid == null) {
                    dtStartTzid = vCalendar.getVTimezone().getPropertyValue("TZID");
                }
                jsonEvent.put("startDateTime", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(dtStart.getValue(), dtStartTzid)).put("timeZone", dtStartTzid));
            }

            VProperty due = vTodo.getProperty("DUE");
            if (due != null) {
                String dueTzid = due.getParamValue("TZID");
                if (dueTzid == null) {
                    dueTzid = vCalendar.getVTimezone().getPropertyValue("TZID");
                }
                jsonEvent.put("dueDateTime", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(due.getValue(), dueTzid)).put("timeZone", dueTzid));
            }

            VProperty completed = vTodo.getProperty("COMPLETED");
            if (completed != null) {
                String completedTzid = completed.getParamValue("TZID");
                if (completedTzid == null) {
                    completedTzid = vCalendar.getVTimezone().getPropertyValue("TZID");
                }
                jsonEvent.put("completedDateTime", new JSONObject().put("dateTime", vCalendar.convertCalendarDateToGraph(completed.getValue(), completedTzid)).put("timeZone", completedTzid));
            }

            localGraphObject.setCategories(vTodo.getPropertyValue("CATEGORIES"));
            // Collect categories on multiple lines
            List<VProperty> categories = vTodo.getProperties("CATEGORIES");
            if (categories != null) {
                HashSet<String> categoryValues = new HashSet<>();
                for (VProperty category : categories) {
                    categoryValues.add(category.getValue());
                }
                localGraphObject.setCategories(StringUtil.join(categoryValues, ","));
            }

            return jsonEvent;
        }

    }

    private String convertHtmlToText(String htmlText) {
        StringBuilder builder = new StringBuilder();

        HtmlCleaner cleaner = new HtmlCleaner();
        cleaner.getProperties().setDeserializeEntities(true);
        try {
            TagNode node = cleaner.clean(new StringReader(htmlText));
            for (TagNode childNode : node.getAllElementsList(true)) {
                builder.append(childNode.getText());
            }
        } catch (IOException e) {
            LOGGER.error("Error converting html to text", e);
        }
        return builder.toString();
    }

    private VProperty convertBodyToVproperty(String propertyName, GraphObject graphObject) {
        JSONObject jsonBody = graphObject.optJSONObject("body");

        if (jsonBody == null) {
            return new VProperty(propertyName, "");
        } else {
            // body is html only over graph by default
            String content = jsonBody.optString("content");
            String contentType = jsonBody.optString("contentType");
            VProperty vProperty;

            if ("text".equals(contentType)) {
                vProperty = new VProperty(propertyName, content);
            } else {
                // html
                if (content != null) {
                    vProperty = new VProperty(propertyName, convertHtmlToText(content));
                    // remove CR LF from html content
                    content = content.replace("\n", "").replace("\r", "");
                    vProperty.addParam("ALTREP", "data:text/html," + URIUtil.encodeWithinQuery(content));
                } else {
                    vProperty = new VProperty(propertyName, null);
                }

            }
            return vProperty;
        }
    }

    private VProperty convertDateTimeTimeZoneToVproperty(String vPropertyName, JSONObject jsonDateTimeTimeZone, String originalStartTimeZone) throws DavMailException {

        if (jsonDateTimeTimeZone != null) {
            String timeZone = jsonDateTimeTimeZone.optString("timeZone");
            String dateTime = convertDateFromExchange(jsonDateTimeTimeZone.optString("dateTime"));

            if (originalStartTimeZone != null && !timeZone.equals(originalStartTimeZone)) {
                LOGGER.debug("originalStartTimeZone different from requested timeZone: " + originalStartTimeZone + " vs " + timeZone);
                // convert to original timezone
                SimpleDateFormat parser = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(timeZone)));
                formatter.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(originalStartTimeZone)));
                try {
                    dateTime = formatter.format(parser.parse(dateTime));
                    timeZone = originalStartTimeZone;
                } catch (ParseException e) {
                    LOGGER.warn("Unable to convert to original timezone: " + dateTime + ", " + originalStartTimeZone);
                }
            }

            VProperty vProperty = new VProperty(vPropertyName, dateTime);
            vProperty.addParam("TZID", timeZone);
            return vProperty;
        }
        return new VProperty(vPropertyName, null);
    }

    private VProperty convertEmailAddressToVproperty(String propertyName, JSONObject jsonEmailAddress) {
        VProperty attendeeProperty = new VProperty(propertyName, "mailto:" + jsonEmailAddress.optString("address"));
        attendeeProperty.addParam("CN", jsonEmailAddress.optString("name"));
        return attendeeProperty;
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
            etag = response.optString("@odata.etag");

            displayName = response.optString("displayname");
            // prefer urlcompname (client provided item name) for contacts
            itemName = StringUtil.decodeUrlcompname(response.optString("urlcompname"));
            // if urlcompname is empty, contact was created on the server side
            if (itemName == null) {
                itemName = StringUtil.base64ToUrl(id) + ".EML";
            }
            put("uid", response.optString("uid"));

            for (GraphField attribute : CONTACT_ATTRIBUTES) {
                String alias = attribute.getAlias();
                if (!alias.startsWith("smtpemail")) {
                    String value = response.optString(attribute);
                    if (value != null && !value.isEmpty()) {
                        put(alias, value);
                    }
                }
            }

            JSONArray emailAddresses = response.optJSONArray("emailAddresses");
            if (emailAddresses != null) {
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
                currentEtag = new GraphObject(jsonContact).optString("changeKey");
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
                        LOGGER.debug("Contact has a photo");
                    } else if (!entry.getKey().startsWith("email") && !entry.getKey().startsWith("smtpemail")
                            && !"usersmimecertificate".equals(entry.getKey()) // not supported over Graph
                            && !"msexchangecertificate".equals(entry.getKey()) // not supported over Graph
                            && !"pager".equals(entry.getKey()) && !"otherTelephone".equals(entry.getKey()) // see below
                            && !"fileas".equals(entry.getKey()) && !"outlookmessageclass".equals(entry.getKey())
                            && !"subject".equals(entry.getKey())
                    ) {
                        graphObject.put(entry.getKey(), entry.getValue());
                    }
                }

                // pager and otherTelephone is a single field
                String pager = get("pager");
                if (pager == null) {
                    pager = get("otherTelephone");
                }
                graphObject.put("pager", pager);

                // force urlcompname
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

                itemResult.itemName = itemName;
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

                // Upload resized image to contact photo endpoint
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
                // Delete the contact photo
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
            return null;
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

    protected static final HashSet<GraphField> IMAP_MESSAGE_ATTRIBUTES = new HashSet<>();

    static {
        // TODO: review, permanenturl is no longer relevant
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("permanenturl"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("changeKey"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("isDraft"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("isRead"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("receivedDateTime"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("lastModifiedDateTime"));

        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("urlcompname"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("uid"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("messageSize"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("imapUid"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("junk"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("flagStatus"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("messageFlags"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("lastVerbExecuted"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("read"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("deleted"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("date"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("lastmodified"));
        // OSX IMAP requests content-class
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("contentclass"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("keywords"));

        // experimental, retrieve message headers
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("messageheaders"));
        IMAP_MESSAGE_ATTRIBUTES.add(GraphField.get("outlookmessageclass"));
    }

    protected static final HashSet<GraphField> CONTACT_ATTRIBUTES = new HashSet<>();

    static {
        CONTACT_ATTRIBUTES.add(GraphField.get("uid"));

        CONTACT_ATTRIBUTES.add(GraphField.get("imapUid"));
        // CONTACT_ATTRIBUTES.add(GraphField.get("etag")); replace with @odata.etag
        CONTACT_ATTRIBUTES.add(GraphField.get("urlcompname"));
        CONTACT_ATTRIBUTES.add(GraphField.get("keywords"));

        CONTACT_ATTRIBUTES.add(GraphField.get("extensionattribute1"));
        CONTACT_ATTRIBUTES.add(GraphField.get("extensionattribute2"));
        CONTACT_ATTRIBUTES.add(GraphField.get("extensionattribute3"));
        CONTACT_ATTRIBUTES.add(GraphField.get("extensionattribute4"));
        CONTACT_ATTRIBUTES.add(GraphField.get("bday"));
        CONTACT_ATTRIBUTES.add(GraphField.get("anniversary"));
        CONTACT_ATTRIBUTES.add(GraphField.get("businesshomepage"));
        CONTACT_ATTRIBUTES.add(GraphField.get("personalHomePage"));
        CONTACT_ATTRIBUTES.add(GraphField.get("cn"));
        CONTACT_ATTRIBUTES.add(GraphField.get("co"));
        CONTACT_ATTRIBUTES.add(GraphField.get("department"));
        CONTACT_ATTRIBUTES.add(GraphField.get("smtpemail1"));
        CONTACT_ATTRIBUTES.add(GraphField.get("smtpemail2"));
        CONTACT_ATTRIBUTES.add(GraphField.get("smtpemail3"));
        CONTACT_ATTRIBUTES.add(GraphField.get("facsimiletelephonenumber"));
        CONTACT_ATTRIBUTES.add(GraphField.get("givenName"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homeCity"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homeCountry"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homePhone"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homePostalCode"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homeState"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homeStreet"));
        CONTACT_ATTRIBUTES.add(GraphField.get("homepostofficebox"));
        CONTACT_ATTRIBUTES.add(GraphField.get("l"));
        CONTACT_ATTRIBUTES.add(GraphField.get("manager"));
        CONTACT_ATTRIBUTES.add(GraphField.get("mobile"));
        CONTACT_ATTRIBUTES.add(GraphField.get("namesuffix"));
        CONTACT_ATTRIBUTES.add(GraphField.get("nickname"));
        CONTACT_ATTRIBUTES.add(GraphField.get("o"));
        CONTACT_ATTRIBUTES.add(GraphField.get("pager"));
        CONTACT_ATTRIBUTES.add(GraphField.get("personaltitle"));
        CONTACT_ATTRIBUTES.add(GraphField.get("postalcode"));
        CONTACT_ATTRIBUTES.add(GraphField.get("postofficebox"));
        CONTACT_ATTRIBUTES.add(GraphField.get("profession"));
        CONTACT_ATTRIBUTES.add(GraphField.get("roomnumber"));
        CONTACT_ATTRIBUTES.add(GraphField.get("secretarycn"));
        CONTACT_ATTRIBUTES.add(GraphField.get("sn"));
        CONTACT_ATTRIBUTES.add(GraphField.get("spousecn"));
        CONTACT_ATTRIBUTES.add(GraphField.get("st"));
        CONTACT_ATTRIBUTES.add(GraphField.get("street"));
        CONTACT_ATTRIBUTES.add(GraphField.get("telephoneNumber"));
        CONTACT_ATTRIBUTES.add(GraphField.get("title"));
        CONTACT_ATTRIBUTES.add(GraphField.get("description"));
        CONTACT_ATTRIBUTES.add(GraphField.get("im"));
        CONTACT_ATTRIBUTES.add(GraphField.get("middlename"));
        CONTACT_ATTRIBUTES.add(GraphField.get("lastmodified"));
        CONTACT_ATTRIBUTES.add(GraphField.get("otherstreet"));
        CONTACT_ATTRIBUTES.add(GraphField.get("otherstate"));
        CONTACT_ATTRIBUTES.add(GraphField.get("otherpostofficebox"));
        CONTACT_ATTRIBUTES.add(GraphField.get("otherpostalcode"));
        CONTACT_ATTRIBUTES.add(GraphField.get("othercountry"));
        CONTACT_ATTRIBUTES.add(GraphField.get("othercity"));
        CONTACT_ATTRIBUTES.add(GraphField.get("haspicture"));
        CONTACT_ATTRIBUTES.add(GraphField.get("othermobile"));
        CONTACT_ATTRIBUTES.add(GraphField.get("otherTelephone"));
        CONTACT_ATTRIBUTES.add(GraphField.get("gender"));
        CONTACT_ATTRIBUTES.add(GraphField.get("private"));
        CONTACT_ATTRIBUTES.add(GraphField.get("sensitivity"));
        CONTACT_ATTRIBUTES.add(GraphField.get("fburl"));
        // certificates not supported over graph
        // CONTACT_ATTRIBUTES.add(GraphField.get("msexchangecertificate"));
        // CONTACT_ATTRIBUTES.add(GraphField.get("usersmimecertificate"));
    }

    private static final Set<GraphField> TODO_PROPERTIES = new HashSet<>();

    static {
        // Task properties https://learn.microsoft.com/en-us/graph/api/resources/todotask
        TODO_PROPERTIES.add(GraphField.get("id"));
        TODO_PROPERTIES.add(GraphField.get("summary"));
        TODO_PROPERTIES.add(GraphField.get("body"));
        TODO_PROPERTIES.add(GraphField.get("lastModifiedDateTime"));
        TODO_PROPERTIES.add(GraphField.get("createdDateTime"));
        TODO_PROPERTIES.add(GraphField.get("importance"));
        TODO_PROPERTIES.add(GraphField.get("status"));
        TODO_PROPERTIES.add(GraphField.get("dueDateTime"));
        TODO_PROPERTIES.add(GraphField.get("startDateTime"));
        TODO_PROPERTIES.add(GraphField.get("completedDateTime"));
        TODO_PROPERTIES.add(GraphField.get("categories"));
    }

    /**
     * EVENT_ATTRIBUTES to retrieve all fields including modified occurrences, EVENT_LIST_ATTRIBUTES for search
     */
    protected static final HashSet<GraphField> EVENT_LIST_ATTRIBUTES = new HashSet<>();
    protected static final HashSet<GraphField> EVENT_ATTRIBUTES = new HashSet<>();

    static {
        EVENT_LIST_ATTRIBUTES.add(GraphField.get("id"));
        EVENT_LIST_ATTRIBUTES.add(GraphField.get("urlcompname"));
        EVENT_LIST_ATTRIBUTES.add(GraphField.get("changeKey"));

        EVENT_ATTRIBUTES.add(GraphField.get("urlcompname"));
        EVENT_ATTRIBUTES.add(GraphField.get("allowNewTimeProposals"));
        EVENT_ATTRIBUTES.add(GraphField.get("attendees"));
        EVENT_ATTRIBUTES.add(GraphField.get("bodyPreview"));
        EVENT_ATTRIBUTES.add(GraphField.get("body"));
        EVENT_ATTRIBUTES.add(GraphField.get("cancelledOccurrences"));
        EVENT_ATTRIBUTES.add(GraphField.get("categories"));
        EVENT_ATTRIBUTES.add(GraphField.get("changeKey"));
        EVENT_ATTRIBUTES.add(GraphField.get("createdDateTime"));
        EVENT_ATTRIBUTES.add(GraphField.get("end"));
        EVENT_ATTRIBUTES.add(GraphField.get("exceptionOccurrences"));
        EVENT_ATTRIBUTES.add(GraphField.get("hasAttachments"));
        EVENT_ATTRIBUTES.add(GraphField.get("iCalUId"));
        EVENT_ATTRIBUTES.add(GraphField.get("transactionId"));
        EVENT_ATTRIBUTES.add(GraphField.get("id"));
        EVENT_ATTRIBUTES.add(GraphField.get("importance"));
        EVENT_ATTRIBUTES.add(GraphField.get("isAllDay"));
        EVENT_ATTRIBUTES.add(GraphField.get("isOnlineMeeting"));
        EVENT_ATTRIBUTES.add(GraphField.get("isOrganizer"));
        EVENT_ATTRIBUTES.add(GraphField.get("isReminderOn"));
        EVENT_ATTRIBUTES.add(GraphField.get("lastModifiedDateTime"));
        EVENT_ATTRIBUTES.add(GraphField.get("location"));
        EVENT_ATTRIBUTES.add(GraphField.get("organizer"));
        EVENT_ATTRIBUTES.add(GraphField.get("originalStartTimeZone"));
        EVENT_ATTRIBUTES.add(GraphField.get("originalStart"));
        EVENT_ATTRIBUTES.add(GraphField.get("recurrence"));
        EVENT_ATTRIBUTES.add(GraphField.get("reminderMinutesBeforeStart"));
        EVENT_ATTRIBUTES.add(GraphField.get("responseRequested"));
        EVENT_ATTRIBUTES.add(GraphField.get("responseStatus"));
        EVENT_ATTRIBUTES.add(GraphField.get("sensitivity"));
        EVENT_ATTRIBUTES.add(GraphField.get("showAs"));
        EVENT_ATTRIBUTES.add(GraphField.get("start"));
        EVENT_ATTRIBUTES.add(GraphField.get("subject"));
        EVENT_ATTRIBUTES.add(GraphField.get("type"));
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

        public String getMailboxName() {
            if (mailbox == null) {
                return "me";
            } else {
                return mailbox;
            }
        }

        public boolean isCalendar() {
            return "IPF.Appointment".equals(folderClass);
        }
    }

    HttpClientAdapter httpClient;
    O365Token token;

    /**
     * Default folder properties list
     */
    protected static final HashSet<GraphField> FOLDER_PROPERTIES = new HashSet<>();

    static {
        // reference at https://learn.microsoft.com/en-us/graph/api/resources/mailfolder
        FOLDER_PROPERTIES.add(GraphField.get("folderlastmodified"));
        FOLDER_PROPERTIES.add(GraphField.get("folderclass"));
        FOLDER_PROPERTIES.add(GraphField.get("ctag"));
        FOLDER_PROPERTIES.add(GraphField.get("uidNext"));
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
        // draft is set for mapi PR_MESSAGE_FLAGS property combined with read flag by IMAPConnection, 8 means draft and 1 means read
        boolean isDraft = properties != null && ("8".equals(properties.get("draft")) || "9".equals(properties.get("draft")));

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
                // we have the message in the right folder, apply flags
                applyMessageProperties(graphResponse, properties);
                graphResponse = executeGraphRequest(new GraphRequestBuilder()
                        .setMethod(HttpPatch.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(graphResponse.optString("id"))
                        .setJsonBody(graphResponse.jsonObject));

                graphResponse = executeGraphRequest(new GraphRequestBuilder().setMethod(HttpPost.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(graphResponse.optString("id"))
                        .setChildType("move")
                        .setJsonBody(new JSONObject().put("destinationId", folderId.id)));
            } catch (JSONException e) {
                throw new IOException(e);
            }
        } else {
            String draftMessageId = null;
            try {
                // save draft message id
                draftMessageId = graphResponse.getString("id");

                // unset draft flag on returned draft message properties
                graphResponse.put("messageFlags", "4");
                // clear read flag by default
                graphResponse.put("read", false);
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
                .setSelectFields(IMAP_MESSAGE_ATTRIBUTES)));
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
                HashSet<GraphField> selectFields = new HashSet<>();
                selectFields.add(GraphField.get("from"));
                selectFields.add(GraphField.get("messageheaders"));

                GraphObject graphResponse = new GraphObject(executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("messages")
                        .setObjectId(id)
                        .setSelectFields(selectFields)));

                String messageHeaders = graphResponse.optString("messageheaders");

                // alternative: use parsed headers response.optJSONArray("internetMessageHeaders");
                if (messageHeaders != null
                        // workaround for broken message headers on Exchange 2010
                        && messageHeaders.toLowerCase().contains("message-id:")) {
                    String from = graphResponse.optString("from");
                    // workaround for messages in Sent folder
                    if (from != null && !messageHeaders.contains("From:")) {
                        messageHeaders = "From: " + MimeUtility.encodeText(from, "UTF-8", null) + '\r' + '\n' + messageHeaders;
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
        GraphObject graphResponse = new GraphObject(response);

        try {
            // get item id
            message.id = graphResponse.getString("id");
            message.changeKey = graphResponse.getString("changeKey");

            message.read = graphResponse.getBoolean("isRead");
            message.draft = graphResponse.getBoolean("isDraft");
            message.date = graphResponse.getString("receivedDateTime");

            String lastmodified = graphResponse.optString("lastModifiedDateTime");
            message.recent = !message.read && lastmodified != null && lastmodified.equals(message.date);

            message.keywords = graphResponse.optString("keywords");

        } catch (JSONException e) {
            LOGGER.warn("Error parsing message " + e.getMessage(), e);
        }

        JSONArray singleValueExtendedProperties = response.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties != null) {
            for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                try {
                    JSONObject responseValue = singleValueExtendedProperties.getJSONObject(i);
                    String responseId = responseValue.optString("id");
                    if (GraphField.getGraphId("imapUid").equals(responseId)) {
                        message.imapUid = responseValue.getLong("value");
                    } else if (GraphField.getGraphId("messageSize").equals(responseId)) {
                        message.size = responseValue.getInt("value");
                    } else if (GraphField.getGraphId("uid").equals(responseId)) {
                        message.uid = responseValue.getString("value");
                    } else if (GraphField.getGraphId("permanenturl").equals(responseId)) {
                        message.permanentUrl = responseValue.getString("value"); // always null
                    } else if (GraphField.getGraphId("lastVerbExecuted").equals(responseId)) {
                        String lastVerbExecuted = responseValue.getString("value");
                        message.answered = "102".equals(lastVerbExecuted) || "103".equals(lastVerbExecuted);
                        message.forwarded = "104".equals(lastVerbExecuted);
                    } else if (GraphField.getGraphId("contentclass").equals(responseId)) {
                        message.contentClass = responseValue.getString("value");
                    } else if (GraphField.getGraphId("junk").equals(responseId)) {
                        message.junk = "1".equals(responseValue.getString("value"));
                    } else if (GraphField.getGraphId("flagStatus").equals(responseId)) {
                        message.flagged = "2".equals(responseValue.getString("value"));
                    } else if (GraphField.getGraphId("deleted").equals(responseId)) {
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
                    if (GraphField.get("keywords").getGraphId().equals(responseId)) {
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
     * Lightweight conversion method to avoid full string to date and back conversions.
     * Note: Duplicate from EWSEchangeSession, added nanosecond handling
     * See <a href="https://learn.microsoft.com/en-us/graph/api/resources/datetimetimezone">datetimetimezone</a>
     * @param exchangeDateValue date returned from O365
     * @return converted date
     * @throws DavMailException on error
     */
    protected static String convertDateFromExchange(String exchangeDateValue) throws DavMailException {
        // yyyy-MM-dd'T'HH:mm:ss'Z' to yyyyMMdd'T'HHmmss'Z'
        if (exchangeDateValue == null) {
            return null;
        } else {
            StringBuilder buffer = new StringBuilder();
            if (exchangeDateValue.length() >= 21 || exchangeDateValue.length() == 20 || exchangeDateValue.length() == 10) {
                for (int i = 0; i < exchangeDateValue.length(); i++) {
                    // skip '-' and ':'
                    if (i == 4 || i == 7 || i == 13 || i == 16) {
                        i++;
                    }
                    if (i == 19) {
                        // optional append Zulu tag
                        if (exchangeDateValue.endsWith("Z")) {
                            buffer.append('Z');
                        }
                        break;
                    } else {
                        buffer.append(exchangeDateValue.charAt(i));
                    }
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

        // Load MIME content from $value endpoint
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
                .setSelectFields(IMAP_MESSAGE_ATTRIBUTES)
                .setFilter(condition);
        int maxCount = Settings.getIntProperty("davmail.folderSizeLimit", 0);
        if (maxCount == 0) {
            maxCount = Integer.MAX_VALUE;
        }
        LOGGER.debug("searchMessages " + folderId.getMailboxName() + " " + folderName);
        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext() && messageList.size() < maxCount) {
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

        protected Operator getOperator() {
            return operator;
        }

        protected GraphField getField() {
            GraphField fieldURI = GraphField.get(attributeName);
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
            } else if (Operator.IsGreaterThan.equals(operator)) {
                return "gt";
            } else if (Operator.IsGreaterThanOrEqualTo.equals(operator)) {
                return "ge";
            } else if (Operator.IsLessThan.equals(operator)) {
                return "lt";
            } else if (Operator.IsLessThanOrEqualTo.equals(operator)) {
                return "le";
            } else {
                LOGGER.warn("Unsupported operator: " + operator + ", switch to equals");
                return "eq";
            }
        }

        @Override
        public void appendTo(StringBuilder buffer) {
            GraphField field = getField();
            String graphId = field.getGraphId();
            if (field.isExtended()) {
                if (field.isInternetHeaders()) {
                    // header search does not work over graph, try to match full internet headers
                    buffer.append("singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, '")
                            .append(attributeName).append(": ").append(StringUtil.escapeQuotes(value)).append("'))");
                } else if (field.isNumber()) {
                    // check value
                    int intValue = 0;
                    try {
                        intValue = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        // invalid value, replace with 0
                        LOGGER.warn("Invalid integer value for " + graphId + " " + value);
                    }
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and cast(ep/value, Edm.Int32) ").append(convertOperator(operator)).append(" ").append(intValue).append(")");
                } else if (Operator.Contains.equals(operator)) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and contains(ep/value,'").append(StringUtil.escapeQuotes(value)).append("'))");
                } else if (Operator.StartsWith.equals(operator)) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and startswith(ep/value,'").append(StringUtil.escapeQuotes(value)).append("'))");
                } else if (field.isBinary()) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and cast(ep/value,Edm.Binary) ").append(convertOperator(operator)).append(" binary'").append(StringUtil.escapeQuotes(value)).append("')");
                } else if (field.isDate()) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and cast(ep/value,Edm.DateTimeOffset) ").append(convertOperator(operator)).append(" datetimeoffset'").append(StringUtil.escapeQuotes(value)).append("')");
                } else if (field.isBoolean()) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and cast(ep/value,Edm.Boolean) ").append(convertOperator(operator)).append(" ").append(value).append(")");
                } else {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphId)
                            .append("' and ep/value ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("')");
                }
            } else if (field.isMultiValued()) {
                buffer.append(graphId).append("/any(a:a ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("')");
            } else if ("body".equals(graphId)) {
                // only contains supported for body
                buffer.append("contains(").append(graphId).append("/content,'").append(StringUtil.escapeQuotes(value)).append("')");
            } else if (Operator.Contains.equals(operator)) {
                // search graph property
                buffer.append("contains(").append(graphId).append(",'").append(StringUtil.escapeQuotes(value)).append("')");
            } else if (Operator.StartsWith.equals(operator)) {
                buffer.append("startswith(").append(graphId).append(",'").append(StringUtil.escapeQuotes(value)).append("')");
            } else if (field.isDate() || field.isBoolean()) {
                buffer.append(graphId).append(" ").append(convertOperator(operator)).append(" ").append(value);
            } else if ("start".equals(graphId) || "end".equals(graphId)) { // TODO check date value
                buffer.append(graphId).append("/dateTime ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("'");
            } else {
                buffer.append(graphId).append(" ").append(convertOperator(operator)).append(" '").append(StringUtil.escapeQuotes(value)).append("'");
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
        protected GraphField getField() {
            return new GraphField(attributeName, GraphField.DistinguishedPropertySetType.InternetHeaders, attributeName);
        }

        /**
         * Graph field internetMessageHeader is not searchable, use MAPI property PR_TRANSPORT_MESSAGE_HEADERS directly instead.
         * @param buffer search filter buffer
         */
        public void appendTo(StringBuilder buffer) {
            buffer.append("singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, '")
                    .append(attributeName).append(": ").append(StringUtil.escapeQuotes(value)).append("'))");
        }
    }

    protected static class IsNullCondition implements ExchangeSession.Condition, SearchExpression {
        protected final String attributeName;

        protected IsNullCondition(String attributeName) {
            this.attributeName = attributeName;
        }

        public void appendTo(StringBuilder buffer) {
            GraphField graphField = GraphField.get(attributeName);
            if (graphField.isExtended()) {
                if (graphField.isNumber()) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphField.getGraphId())
                            .append("' and cast(ep/value, Edm.Int32) eq null)");
                } else if (graphField.isBoolean()) {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphField.getGraphId())
                            .append("' and cast(ep/value, Edm.Boolean) eq null)");
                } else {
                    buffer.append("singleValueExtendedProperties/Any(ep: ep/id eq '").append(graphField.getGraphId())
                            .append("' and ep/value eq null)");
                }
            } else {
                buffer.append(graphField.getGraphId()).append(" eq null");
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
            buffer.append(GraphField.get(attributeName).getGraphId()).append(" ne null");
        }

        public boolean isEmpty() {
            return false;
        }

        public boolean isMatch(ExchangeSession.Contact contact) {
            String actualValue = contact.get(attributeName);
            return actualValue != null;
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
            buffer.append("not (");
            condition.appendTo(buffer);
            buffer.append(")");
        }
    }

    @Override
    public ExchangeSession.MultiCondition and(Condition... conditions) {
        return new MultiCondition(Operator.And, conditions);
    }

    @Override
    public ExchangeSession.MultiCondition or(Condition... conditions) {
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
    public List<ExchangeSession.Folder> getSubCalendarFolders(String folderName, boolean recursive) throws IOException {
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder();
        // search calendars, ignore condition
        httpRequestBuilder.setMethod(HttpGet.METHOD_NAME)
                .setObjectType("calendars")
                .setSelectFields(FOLDER_PROPERTIES);

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);
        List<ExchangeSession.Folder> folders = new ArrayList<>();
        while (graphIterator.hasNext()) {
            Folder folder = buildFolder(graphIterator.next());
            folder.folderPath = folder.displayName;
            if (!folder.isDefaultCalendar) {
                folders.add(folder);
            }
        }
        return folders;
    }

    @Override
    public List<ExchangeSession.Folder> getSubFolders(String folderPath, Condition condition, boolean recursive) throws IOException {

        List<ExchangeSession.Folder> folders = new ArrayList<>();

        appendSubFolders(folders, getSubfolderPath(folderPath), getFolderId(folderPath), condition, recursive);
        return folders;
    }

    protected void appendSubFolders(List<ExchangeSession.Folder> folders,
                                    String parentFolderPath, FolderId parentFolderId,
                                    Condition condition, boolean recursive) throws IOException {
        LOGGER.debug("appendSubFolders " + (parentFolderId.mailbox != null ? parentFolderId.mailbox : "me") + " " + parentFolderPath);

        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setObjectType("mailFolders")
                .setMailbox(parentFolderId.mailbox)
                .setObjectId(parentFolderId.id)
                .setChildType("childFolders")
                .setSelectFields(FOLDER_PROPERTIES)
                .setFilter(condition);

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

    public void sendMessage(byte[] byteArray) throws IOException {
        // https://learn.microsoft.com/en-us/graph/api/user-sendmail
        executeJsonRequest(new GraphRequestBuilder()
                .setMethod(HttpPost.METHOD_NAME)
                .setObjectType("sendMail")
                .setContentType("text/plain")
                .setMimeContent(IOUtil.encodeBase64(byteArray)));
    }

    @Override
    protected Folder internalGetFolder(String folderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);

        // base folder get https://graph.microsoft.com/v1.0/me/mailFolders/inbox
        GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(folderId.mailbox)
                .setObjectId(folderId.id);
        if ("IPF.Appointment".equals(folderId.folderClass)) {
            httpRequestBuilder
                    .setSelectFields(FOLDER_PROPERTIES)
                    .setObjectType("calendars");
        } else if ("IPF.Task".equals(folderId.folderClass)) {
            httpRequestBuilder.setObjectType("todo/lists");
        } else if ("IPF.Contact".equals(folderId.folderClass)) {
            httpRequestBuilder
                    .setSelectFields(FOLDER_PROPERTIES)
                    .setObjectType("contactFolders");
        } else {
            httpRequestBuilder
                    .setSelectFields(FOLDER_PROPERTIES)
                    .setObjectType("mailFolders");
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
                folder.displayName = StringUtil.encodeFolderName(jsonResponse.optString("name"));
                folder.isDefaultCalendar = jsonResponse.optBoolean("isDefaultCalendar");
            } else {
                String wellKnownName = wellKnownFolderMap.get(jsonResponse.optString("wellKnownName"));
                if (ExchangeSession.INBOX.equals(wellKnownName)) {
                    folder.displayName = wellKnownName;
                } else {
                    if (wellKnownName != null) {
                        folder.setSpecialFlag(wellKnownName);
                    }

                    // TODO: reevaluate folder name encoding over graph
                    folder.displayName = StringUtil.encodeFolderName(jsonResponse.getString("displayName"));
                }

                folder.messageCount = jsonResponse.optInt("totalItemCount");
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
                    if (GraphField.get("folderlastmodified").getGraphId().equals(singleValueId)) {
                        folder.etag = singleValue;
                    } else if (GraphField.get("folderclass").getGraphId().equals(singleValueId)) {
                        folder.folderClass = singleValue;
                        folder.folderId.folderClass = folder.folderClass;
                    } else if (GraphField.get("uidNext").getGraphId().equals(singleValueId)) {
                        folder.uidNext = Long.parseLong(singleValue);
                    } else if (GraphField.get("ctag").getGraphId().equals(singleValueId)) {
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
                    .setSelect("id"));
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
        LOGGER.debug("getSubFolderByName " + currentFolderId.id + " " + folderName);
        GraphRequestBuilder httpRequestBuilder;
        if ("IPF.Appointment".equals(currentFolderId.folderClass)) {
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(currentFolderId.mailbox)
                    .setObjectType("calendars")
                    .setSelect("id")
                    .setFilter("name eq '" + StringUtil.escapeQuotes(StringUtil.decodeFolderName(folderName)) + "'");
        } else if ("IPF.Task".equals(currentFolderId.folderClass)) {
            httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(currentFolderId.mailbox)
                    .setObjectType("todo/lists")
                    .setSelect("id")
                    .setFilter("displayName eq '" + StringUtil.escapeQuotes(StringUtil.decodeFolderName(folderName)) + "'");
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
                    .setSelect("id")
                    .setFilter("displayName eq '" + StringUtil.escapeQuotes(StringUtil.decodeFolderName(folderName)) + "'");
        }

        JSONObject jsonResponse = executeJsonRequest(httpRequestBuilder);

        FolderId folderId = null;
        try {
            JSONArray values = jsonResponse.getJSONArray("value");
            if (values.length() > 0) {
                folderId = new FolderId(currentFolderId.mailbox, values.getJSONObject(0).getString("id"), currentFolderId.folderClass);
                folderId.parentFolderId = currentFolderId.id;
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
                folderName = StringUtil.decodeFolderName(folderPath.substring(folderPath.lastIndexOf('/') + 1));
            } else {
                parentFolderId = getFolderId("");
                folderName = StringUtil.decodeFolderName(folderPath);
            }

            try {
                String objectType = "mailFolders";
                if ("IPF.Contact".equals(folderClass)) {
                    objectType = "contactFolders";
                }
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpPost.METHOD_NAME)
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
            targetFolderName = StringUtil.decodeFolderName(targetFolderPath.substring(targetFolderPath.lastIndexOf('/') + 1));
        } else {
            targetFolderParentPath = "";
            targetFolderName = StringUtil.decodeFolderName(targetFolderPath);
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
                .setSelectFields(CONTACT_ATTRIBUTES)
                .setFilter(condition);
        LOGGER.debug("searchContacts " + folderId.getMailboxName() + "/" + folderPath + " " + httpRequestBuilder.select);

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext() && (maxCount == 0 || contactList.size() < maxCount)) {
            Contact contact = new Contact(new GraphObject(graphIterator.next()));
            contact.folderPath = folderPath;
            contact.folderId = folderId;
            contactList.add(contact);
        }

        return contactList;
    }

    @Override
    public List<ExchangeSession.Event> getEventMessages(String folderPath) throws IOException {
        return searchEvents(folderPath, ITEM_PROPERTIES,
                and(startsWith("outlookmessageclass", "IPM.Schedule.Meeting."),
                        or(isNull("processed"), isFalse("processed"))));
    }

    @Override
    protected Condition getCalendarItemCondition(Condition dateCondition) {
        return or(isTrue("isrecurring"),
                and(isFalse("isrecurring"), dateCondition));
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
                //.setSelectFields(TODO_PROPERTIES)
                ;
        LOGGER.debug("searchTasksOnly " + folderId.getMailboxName() + " " + folderPath);

        GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

        while (graphIterator.hasNext()) {
            Event event = new Event(folderPath, folderId, new GraphObject(graphIterator.next()));
            eventList.add(event);
        }

        return eventList;
    }

    @Override
    public List<ExchangeSession.Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        ArrayList<ExchangeSession.Event> eventList = new ArrayList<>();
        FolderId folderId = getFolderId(folderPath);

        if (folderId.isCalendar()) {
            // /users/{id | userPrincipalName}/calendars/{id}/events
            GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("calendars")
                    .setObjectId(folderId.id)
                    .setChildType("events")
                    .setSelectFields(EVENT_LIST_ATTRIBUTES)
                    .setTimezone(getVTimezone().getPropertyValue("TZID"))
                    .setFilter(condition);
            LOGGER.debug("searchEvents " + folderId.getMailboxName() + " " + folderPath);

            GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

            while (graphIterator.hasNext()) {
                Event event = new Event(folderPath, folderId, new GraphObject(graphIterator.next()));
                eventList.add(event);
            }
        } else {
            // event messages
            GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("mailFolders")
                    .setObjectId(folderId.id)
                    .setChildType("messages")
                    .setSelectFields(IMAP_MESSAGE_ATTRIBUTES)
                    .setFilter(condition);
            LOGGER.debug("searchEventMessages " + folderId.getMailboxName() + " " + folderPath);

            GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

            while (graphIterator.hasNext()) {
                JSONObject jsonResponse = graphIterator.next();
                GraphExchangeSession.Message message = buildMessage(jsonResponse);
                message.folderId = folderId;
                LOGGER.debug("searchEventMessages " + message.contentClass+" "+message.id);
                try {
                    byte[] content = getContent(message);
                    if (content == null) {
                        throw new IOException("empty event body");
                    }
                    content = getICS(new SharedByteArrayInputStream(content));

                    Event event = new Event(folderId, content);
                    eventList.add(event);

                } catch (IOException | MessagingException e) {
                    LOGGER.warn("searchEventMessages " + message.id, e);
                }
            }
        }

        return eventList;

    }

    // TODO refactor to avoid duplicate code

    protected static final String TEXT_CALENDAR = "text/calendar";
    protected static final String APPLICATION_ICS = "application/ics";

    protected boolean isCalendarContentType(String contentType) {
        return TEXT_CALENDAR.regionMatches(true, 0, contentType, 0, TEXT_CALENDAR.length()) ||
                APPLICATION_ICS.regionMatches(true, 0, contentType, 0, APPLICATION_ICS.length());
    }

    protected MimePart getCalendarMimePart(MimeMultipart multiPart) throws IOException, MessagingException {
        MimePart bodyPart = null;
        for (int i = 0; i < multiPart.getCount(); i++) {
            String contentType = multiPart.getBodyPart(i).getContentType();
            if (isCalendarContentType(contentType)) {
                bodyPart = (MimePart) multiPart.getBodyPart(i);
                break;
            } else if (contentType.startsWith("multipart")) {
                Object content = multiPart.getBodyPart(i).getContent();
                if (content instanceof MimeMultipart) {
                    bodyPart = getCalendarMimePart((MimeMultipart) content);
                }
            }
        }

        return bodyPart;
    }

    /**
     * Load ICS content from MIME message input stream
     *
     * @param mimeInputStream mime message input stream
     * @return mime message ics attachment body
     * @throws IOException        on error
     * @throws MessagingException on error
     */
    protected byte[] getICS(InputStream mimeInputStream) throws IOException, MessagingException {
        byte[] result;
        MimeMessage mimeMessage = new MimeMessage(null, mimeInputStream);
        String[] contentClassHeader = mimeMessage.getHeader("Content-class");
        // task item, return null
        if (contentClassHeader != null && contentClassHeader.length > 0 && "urn:content-classes:task".equals(contentClassHeader[0])) {
            return null;
        }
        Object mimeBody = mimeMessage.getContent();
        MimePart bodyPart = null;
        if (mimeBody instanceof MimeMultipart) {
            bodyPart = getCalendarMimePart((MimeMultipart) mimeBody);
        } else if (isCalendarContentType(mimeMessage.getContentType())) {
            // no multipart, single body
            bodyPart = mimeMessage;
        }


        if (bodyPart != null) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                bodyPart.getDataHandler().writeTo(baos);
                result = baos.toByteArray();
            }
        } else {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                mimeMessage.writeTo(baos);
                throw new DavMailException("EXCEPTION_INVALID_MESSAGE_CONTENT", new String(baos.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        FolderId folderId = getFolderId(folderPath);

        if ("IPF.Contact".equals(folderId.folderClass)) {
            JSONObject jsonResponse = getContactIfExists(folderId, itemName);
            if (jsonResponse != null) {
                Contact contact = new Contact(new GraphObject(jsonResponse));
                contact.folderPath = folderPath;
                contact.folderId = folderId;
                return contact;
            } else {
                throw new IOException("Item " + folderPath + " " + itemName + " not found");
            }
        } else if ("IPF.Appointment".equals(folderId.folderClass)) {
            JSONObject jsonResponse = getEventIfExists(folderId, itemName);
            if (jsonResponse != null) {
                return new Event(folderPath, folderId, new GraphObject(jsonResponse));
            } else {
                throw new IOException("Item " + folderPath + " " + itemName + " not found");
            }
        } else {
            throw new UnsupportedOperationException("Item type " + folderId.folderClass + " not supported");
        }
    }

    @Override
    protected String convertItemNameToEML(String itemName) {
        if (itemName.endsWith(".vcf") || itemName.endsWith(".ics")) {
            return itemName.substring(0, itemName.length() - 3) + "EML";
        } else {
            return itemName;
        }
    }

    protected String convertItemNameToItemId(String itemName) {
        return itemName.substring(0, itemName.length() - 4);
    }


    private JSONObject getEventIfExists(FolderId folderId, String itemName) throws IOException {
        String urlcompname = convertItemNameToEML(itemName);
        String itemId = null;
        if (isItemId(urlcompname)) {
            itemId = convertItemNameToItemId(urlcompname);
        } else {
            // try to retrieve item id by urlcompname
            try {
                if (urlcompnameToIdMap.containsKey(urlcompname)) {
                    // try to fetch id from cache
                    itemId = urlcompnameToIdMap.get(urlcompname);
                } else if (folderId.isCalendar()){
                    JSONObject jsonResponse = executeJsonRequest(new GraphRequestBuilder()
                            .setMethod(HttpGet.METHOD_NAME)
                            .setMailbox(folderId.mailbox)
                            .setObjectType("calendars")
                            .setObjectId(folderId.id)
                            .setChildType("events")
                            .setFilter(isEqualTo("urlcompname", urlcompname))
                            .setSelect("id") // retrieve id only
                    );

                    JSONArray values = jsonResponse.optJSONArray("value");
                    if (values != null && values.length() > 0) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Found event " + values.optJSONObject(0));
                        }
                        itemId = values.optJSONObject(0).optString("id");
                    }
                }

            } catch (HttpNotFoundException e) {
                LOGGER.debug("No event found for urlcompname " + urlcompname);
            }
        }
        // fetch item by id
        if (itemId != null) {
            try {
                return executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("events")
                        .setObjectId(itemId)
                        .setSelectFields(EVENT_ATTRIBUTES)
                        .setTimezone(getTimezoneId())
                );
            } catch (HttpNotFoundException e) {
                // this may be a task item
                FolderId taskFolderId = getFolderId(TASKS);
                try {
                    return executeJsonRequest(new GraphRequestBuilder()
                                    .setMethod(HttpGet.METHOD_NAME)
                                    .setMailbox(folderId.mailbox)
                                    .setObjectType("todo/lists")
                                    .setObjectId(taskFolderId.id)
                                    .setChildType("tasks")
                                    .setChildId(itemId)
                            //.setSelectFields(TODO_PROPERTIES) // bug on title breaks request
                    ).put("objecttype", "IPF.Task"); // mark object as task item
                } catch (JSONException jsonException) {
                    throw new IOException(jsonException.getMessage(), jsonException);
                }
            }
        }
        return null;
    }

    private JSONObject getContactIfExists(FolderId folderId, String itemName) throws IOException {
        String urlcompname = convertItemNameToEML(itemName);
        if (isItemId(urlcompname)) {
            // lookup item directly
            return executeJsonRequest(new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("contactFolders")
                    .setObjectId(folderId.id)
                    .setChildType("contacts")
                    .setChildId(convertItemNameToItemId(itemName))
                    .setSelectFields(CONTACT_ATTRIBUTES)
            );

        } else {
            JSONObject jsonResponse = executeJsonRequest(new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("contactFolders")
                    .setObjectId(folderId.id)
                    .setChildType("contacts")
                    .setFilter(isEqualTo("urlcompname", urlcompname))
                    .setSelectFields(CONTACT_ATTRIBUTES)
            );
            // need at least one value
            JSONArray values = jsonResponse.optJSONArray("value");
            if (values != null && values.length() > 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found contact " + values.optJSONObject(0));
                }
                return values.optJSONObject(0);
            }
        }
        return null;
    }

    @Override
    public ContactPhoto getContactPhoto(ExchangeSession.Contact contact) throws IOException {
        // don't fetch if haspicture flag is false
        if ("false".equals(contact.get("haspicture"))) {
            return null;
        }
        GraphRequestBuilder graphRequestBuilder = new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setMailbox(((Contact) contact).folderId.mailbox)
                .setObjectType("contactFolders")
                .setObjectId(((Contact) contact).folderId.id)
                .setChildType("contacts")
                .setChildId(((Contact) contact).id)
                .setChildSuffix("photo/$value")
                .setAccessToken(token.getAccessToken());

        byte[] contactPhotoBytes;
        try (
                CloseableHttpResponse response = httpClient.execute(graphRequestBuilder.build());
                InputStream inputStream = response.getEntity().getContent()
        ) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                throw new IOException("Unable to fetch photo" + response.getStatusLine().getReasonPhrase());
            }
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
        Item item = getItem(folderPath, itemName);
        if (item instanceof GraphExchangeSession.Contact) {
            FolderId folderId = ((Contact) item).folderId;
            executeJsonRequest(new GraphRequestBuilder()
                    .setMethod(HttpDelete.METHOD_NAME)
                    .setMailbox(folderId.mailbox)
                    .setObjectType("contactFolders")
                    .setObjectId(folderId.id)
                    .setChildType("contacts")
                    .setChildId(((Contact) item).id)
            );
        } else if (item instanceof GraphExchangeSession.Event) {
            FolderId folderId = ((Event) item).folderId;

            if (folderId.folderClass.equals("IPF.Appointment")) {
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpDelete.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("events")
                        .setObjectId(((Event) item).id));
            } else {
                executeJsonRequest(new GraphRequestBuilder()
                        .setMethod(HttpDelete.METHOD_NAME)
                        .setMailbox(folderId.mailbox)
                        .setObjectType("todo/lists")
                        .setObjectId(folderId.id)
                        .setChildType("tasks")
                        .setChildId(((Event) item).id)
                );
            }
        }
    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {
        // TODO mark event messages in inbox processed
    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        String itemName = UUID.randomUUID() + ".EML";
        byte[] mimeContent = new GraphExchangeSession.Event(DRAFTS, itemName, "urn:content-classes:calendarmessage", icsBody, null, null).createMimeContent();
        if (mimeContent == null) {
            // no recipients, cancel
            return HttpStatus.SC_NO_CONTENT;
        } else {
            sendMessage( mimeContent);
            return HttpStatus.SC_OK;
        }
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
        return folderPath.startsWith("/") && !folderPath.toLowerCase().startsWith(currentMailboxPath);
    }

    @Override
    public boolean isMainCalendar(String folderPath) throws IOException {
        FolderId folderId = getFolderIdIfExists(folderPath);
        return folderId.parentFolderId == null && WellKnownFolderName.calendar.name().equals(folderId.id);
    }

    @Override
    protected String getCalendarEmail(String folderPath) throws IOException {
        FolderId folderId = getFolderId(folderPath);
        if (folderId.mailbox == null) {
            return email;
        } else {
            return folderId.mailbox;
        }
    }

    /**
     * Map people attributes to LDAP
     */
    public static final HashMap<String, String> GALFIND_ATTRIBUTE_MAP = new HashMap<>();

    static {
        GALFIND_ATTRIBUTE_MAP.put("id", "uid"); // use id as uid

        GALFIND_ATTRIBUTE_MAP.put("displayName", "cn"); // common name
        GALFIND_ATTRIBUTE_MAP.put("surname", "sn");
        GALFIND_ATTRIBUTE_MAP.put("givenName", "givenname");
        GALFIND_ATTRIBUTE_MAP.put("personNotes", "description"); // map personNotes to description

        GALFIND_ATTRIBUTE_MAP.put("companyName", "company"); // company or o
        GALFIND_ATTRIBUTE_MAP.put("profession", "profession");
        GALFIND_ATTRIBUTE_MAP.put("title", "title");
        GALFIND_ATTRIBUTE_MAP.put("department", "department");
        GALFIND_ATTRIBUTE_MAP.put("officeLocation", "location");

        GALFIND_ATTRIBUTE_MAP.put("birthday", "birthday"); // TODO may have to convert value

        GALFIND_ATTRIBUTE_MAP.put("yomiCompany", "yomicompany");

        GALFIND_ATTRIBUTE_MAP.put("mailboxType", "mailboxtype");
        GALFIND_ATTRIBUTE_MAP.put("personType", "persontype");
        GALFIND_ATTRIBUTE_MAP.put("userPrincipalName", "userprincipalname"); // for Active Directory / EntraID entries
        GALFIND_ATTRIBUTE_MAP.put("isFavorite", "isfavorite");
    }


    protected GraphExchangeSession.Contact buildGalfindContact(JSONObject response) {
        GraphExchangeSession.Contact contact = new GraphExchangeSession.Contact();
        contact.setName(response.optString("id"));
        contact.put("imapUid", response.optString("id"));
        contact.put("uid", response.optString("id"));
        Iterator keysIterator = response.keys();
        while (keysIterator.hasNext()) {
            String key = (String) keysIterator.next();
            String attributeName = key;
            // special handling for email addresses
            if ("emailAddresses".equals(key)) {
                JSONArray emailAddresses = response.optJSONArray("emailAddresses");
                if (emailAddresses != null) {
                    for (int i = 0; i < 3; i++) {
                        if (emailAddresses.length() > i) {
                            contact.put("smtpemail" + (i + 1), emailAddresses.optJSONObject(i).optString("address"));
                        }
                    }
                }
                // map phone numbers
            } else if ("phones".equals(key)) {
                JSONArray phones = response.optJSONArray("phones");
                if (phones != null) {
                    for (int i = 0; i < phones.length(); i++) {
                        String phoneType = phones.optJSONObject(i).optString("type");
                        String phoneNumber = phones.optJSONObject(i).optString("number");
                        if ("business".equals(phoneType)) {
                            contact.put("telephoneNumber", phoneNumber);
                        } else if ("mobile".equals(phoneType)) {
                            contact.put("mobile", phoneNumber);
                        } else if ("home".equals(phoneType)) {
                            contact.put("homePhone", phoneNumber);
                        } else {
                            LOGGER.debug("Unknown phoneType " + phoneType);
                            contact.put(phoneType + "Phone", phoneNumber);
                        }
                    }
                }
            } else if ("sources".equals(key)) {
                JSONArray sources = response.optJSONArray("sources");
                if (sources != null && sources.length() > 0) {
                    String sourceType = sources.optJSONObject(0).optString("type");
                    contact.put("sourceType", sourceType);
                }
            } else {
                if (GALFIND_ATTRIBUTE_MAP.get(key) != null) {
                    attributeName = GALFIND_ATTRIBUTE_MAP.get(key);
                } else {
                    LOGGER.debug("Unknown attribute " + attributeName);
                }

                String attributeValue = response.optString(key);
                if (attributeValue != null) {
                    contact.put(attributeName, attributeValue);
                }
            }
        }
        return contact;
    }

    @Override
    public Map<String, ExchangeSession.Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        Map<String, ExchangeSession.Contact> contacts = new HashMap<>();

        // poor implementation of search filter based on people endpoint limitations
        String search = null;
        String id = null;
        if (condition instanceof AttributeCondition) {
            if ("imapUid".equals(((AttributeCondition) condition).getAttributeName())) {
                id = ((AttributeCondition) condition).getValue();
            } else {
                search = ((AttributeCondition) condition).getValue();
            }
        }

        if (id != null) {

            // lookup by id only if this is actually an id
            if (id.length() == 36) {
                GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .addHeader("X-PeopleQuery-QuerySources", "Mailbox,Directory")
                        .setObjectType("people")
                        .setObjectId(id);
                JSONObject peopleObject = null;

                try {
                    peopleObject = executeJsonRequest(httpRequestBuilder);
                } catch (HttpNotFoundException e) {
                    LOGGER.warn("No person found for id " + id);
                }

                if (peopleObject != null) {
                    Contact contact = buildGalfindContact(peopleObject);

                    contacts.put(contact.getName().toLowerCase(), contact);
                    LOGGER.debug("found user " + contact.getName());
                }
            }

        } else {
            GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder()
                    .setMethod(HttpGet.METHOD_NAME)
                    .addHeader("X-PeopleQuery-QuerySources", "Mailbox,Directory")
                    .setObjectType("people")
                    .setSearch(search);
            LOGGER.debug("search users");
            GraphIterator graphIterator = executeSearchRequest(httpRequestBuilder);

            while (graphIterator.hasNext() && contacts.size() < sizeLimit) {
                Contact contact = buildGalfindContact(graphIterator.next());
                contacts.put(contact.getName().toLowerCase(), contact);
                LOGGER.debug("found user " + contact.getName());
            }
        }

        return contacts;
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException {
        // https://learn.microsoft.com/en-us/graph/outlook-get-free-busy-schedule
        // POST /me/calendar/getschedule
        String fbdata = null;
        JSONObject jsonBody = new JSONObject();
        try {
            String timeZone = getVTimezone().getPropertyValue("TZID");
            jsonBody.put("Schedules", new JSONArray().put(attendee));
            jsonBody.put("StartTime", new JSONObject().put("dateTime", start).put("timeZone", timeZone));
            jsonBody.put("EndTime", new JSONObject().put("dateTime", end).put("timeZone", timeZone));
            jsonBody.put("availabilityViewInterval", interval);

            GraphObject graphResponse = executeGraphRequest(new GraphRequestBuilder()
                    .setMethod(HttpPost.METHOD_NAME)
                    .setObjectType("calendar")
                    .setAction("getschedule")
                    .setJsonBody(jsonBody));
            JSONArray value = graphResponse.optJSONArray("value");
            if (value != null && value.length() > 0) {
                fbdata = value.getJSONObject(0).optString("availabilityView", null);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
        return fbdata;
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
            this.vTimezone = getVTimezone(timezoneId);

        } catch (IOException | MissingResourceException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        }
    }

    private VObject getVTimezone(String timezoneId) {
        if (!"tzone://Microsoft/Custom".equals(timezoneId)) {
            try {
                return new VObject(ResourceBundle.getBundle("vtimezones").getString(timezoneId));
            } catch (IOException | MissingResourceException e) {
                LOGGER.warn("Unable to get VTIMEZONE: " + e, e);
            }
        }
        // unsupported timezone, return default
        return getVTimezone();
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
            values = jsonObject.optJSONArray("value");
        }

        public boolean hasNext() throws IOException {
            if (values != null && index < values.length()) {
                return true;
            } else if (nextLink != null) {
                fetchNextPage();
                return values != null && values.length() > 0;
            } else {
                return false;
            }
        }

        public JSONObject next() throws IOException {
            if (values == null || !hasNext()) {
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
                // workaround for people search bug
                if (nextLink != null && nextLink.endsWith("skip=0")) {
                    nextLink = null;
                }
                values = jsonObject.optJSONArray("value");
                index = 0;
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
        JSONObject jsonResponse = null;
        boolean isThrottled;
        do {
            HttpRequestBase request = httpRequestBuilder
                    .setAccessToken(token.getAccessToken())
                    .build();

            // DEBUG only, disable gzip encoding
            //request.setHeader("Accept-Encoding", "");
            try (
                    CloseableHttpResponse response = httpClient.execute(request)
            ) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    LOGGER.warn(response.getStatusLine());
                }
                isThrottled = handleThrottling(response);
                if (!isThrottled) {
                    jsonResponse = new JsonResponseHandler().handleResponse(response);
                }
            }
        } while (isThrottled);
        return jsonResponse;
    }


    /**
     * Execute graph request and wrap response in a graph object
     * @param httpRequestBuilder request builder
     * @return returned graph object
     * @throws IOException on error
     */
    private GraphObject executeGraphRequest(GraphRequestBuilder httpRequestBuilder) throws IOException {
        HttpRequestBase request = httpRequestBuilder
                .setAccessToken(token.getAccessToken())
                .build();

        GraphObject graphObject = null;
        boolean isThrottled;
        do {
            // DEBUG only, disable gzip encoding
            //request.setHeader("Accept-Encoding", "");
            try (
                    CloseableHttpResponse response = httpClient.execute(request)
            ) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    LOGGER.warn("Request returned " + response.getStatusLine());
                }
                isThrottled = handleThrottling(response);
                if (!isThrottled) {
                    graphObject = new GraphObject(new JsonResponseHandler().handleResponse(response));
                    graphObject.statusCode = response.getStatusLine().getStatusCode();
                }
            }
        } while (isThrottled);
        return graphObject;
    }

    /**
     * Detect throttling and wait according to Retry-After header.
     * See <a href="https://learn.microsoft.com/en-us/graph/throttling">https://learn.microsoft.com/en-us/graph/throttling</a>
     * @param response HTTP response
     * @return true if throttled, false otherwise
     */
    private boolean handleThrottling(CloseableHttpResponse response) {
        long retryDelay = 0;
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_TOO_MANY_REQUESTS) {
            LOGGER.info("Detected throttling " + response.getStatusLine());
            Header retryAfter = response.getFirstHeader("Retry-After");
            if (retryAfter != null) {
                retryDelay = Long.parseLong(retryAfter.getValue()) + 1;
                waitRetryDelay(retryDelay);
            }
        } else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE) {
            LOGGER.info("Detected graph request error, waiting to retry " + response.getStatusLine());
            retryDelay = 5;
            waitRetryDelay(retryDelay);
        }
        return retryDelay > 0;
    }

    private void waitRetryDelay(long retryDelay) {
        LOGGER.debug("Waiting " + retryDelay + " seconds to retry request");
        try {
            Thread.sleep(retryDelay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if itemName is long and base64 encoded.
     * User-generated item names are usually short
     *
     * @param itemName item name
     * @return true if itemName is an EWS item id
     */
    protected static boolean isItemId(String itemName) {
        // Length 72 for immutableId, 140 for classic id
        return (itemName.length() >= 140 || itemName.length() == 72)
                // the item name is base64url
                && itemName.matches("^([A-Za-z0-9-_]{4})*([A-Za-z0-9-_]{4}|[A-Za-z0-9-_]{3}=|[A-Za-z0-9-_]{2}==)\\.EML$")
                && itemName.indexOf(' ') < 0;
    }

}

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
package davmail.exchange;

import davmail.Settings;
import davmail.util.StringUtil;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * VCalendar object.
 */
public class VCalendar extends VObject {
    protected static final Logger LOGGER = Logger.getLogger(VCalendar.class);
    protected VObject firstVevent;
    protected VObject vTimezone;
    protected String email;

    /**
     * Create VCalendar object from reader;
     *
     * @param reader    stream reader
     * @param email     current user email
     * @param vTimezone user OWA timezone
     * @throws IOException on error
     */
    public VCalendar(BufferedReader reader, String email, VObject vTimezone) throws IOException {
        super(reader);
        if (!"VCALENDAR".equals(type)) {
            throw new IOException("Invalid type: " + type);
        }
        this.email = email;
        // set OWA timezone information
        if (this.vTimezone == null && vTimezone != null) {
            setTimezone(vTimezone);
        }
    }

    /**
     * Create VCalendar object from string;
     *
     * @param vCalendarBody item body
     * @param email         current user email
     * @param vTimezone     user OWA timezone
     * @throws IOException on error
     */
    public VCalendar(String vCalendarBody, String email, VObject vTimezone) throws IOException {
        this(new ICSBufferedReader(new StringReader(vCalendarBody)), email, vTimezone);
    }

    /**
     * Create VCalendar object from string;
     *
     * @param vCalendarContent item content
     * @param email            current user email
     * @param vTimezone        user OWA timezone
     * @throws IOException on error
     */
    public VCalendar(byte[] vCalendarContent, String email, VObject vTimezone) throws IOException {
        this(new ICSBufferedReader(new InputStreamReader(new ByteArrayInputStream(vCalendarContent), "UTF-8")), email, vTimezone);
    }

    /**
     * Empty constructor
     */
    public VCalendar() {
        type = "VCALENDAR";
    }

    /**
     * Set timezone on vObject
     *
     * @param vTimezone timezone object
     */
    public void setTimezone(VObject vTimezone) {
        if (vObjects == null) {
            addVObject(vTimezone);
        } else {
            vObjects.add(0, vTimezone);
        }
        this.vTimezone = vTimezone;
    }

    @Override
    public void addVObject(VObject vObject) {
        super.addVObject(vObject);
        if (firstVevent == null && ("VEVENT".equals(vObject.type) || "VTODO".equals(vObject.type))) {
            firstVevent = vObject;
        }
        if ("VTIMEZONE".equals(vObject.type)) {
            vTimezone = vObject;
        }
    }

    protected boolean isAllDay(VObject vObject) {
        VProperty dtstart = vObject.getProperty("DTSTART");
        return dtstart != null && dtstart.hasParam("VALUE", "DATE");
    }

    protected boolean isCdoAllDay(VObject vObject) {
        return "TRUE".equals(vObject.getPropertyValue("X-MICROSOFT-CDO-ALLDAYEVENT"));
    }

    /**
     * Check if vCalendar is CDO allday.
     *
     * @return true if vCalendar has X-MICROSOFT-CDO-ALLDAYEVENT property set to TRUE
     */
    public boolean isCdoAllDay() {
        return firstVevent != null && isCdoAllDay(firstVevent);
    }

    /**
     * Get email from property value.
     *
     * @param property property
     * @return email value
     */
    public String getEmailValue(VProperty property) {
        if (property == null) {
            return null;
        }
        String propertyValue = property.getValue();
        if (propertyValue != null && (propertyValue.startsWith("MAILTO:") || propertyValue.startsWith("mailto:"))) {
            return propertyValue.substring(7);
        } else {
            return propertyValue;
        }
    }

    protected String getMethod() {
        return getPropertyValue("METHOD");
    }

    protected void fixVCalendar(boolean fromServer) {
        // set iCal 4 global X-CALENDARSERVER-ACCESS from CLASS
        if (fromServer) {
            setPropertyValue("X-CALENDARSERVER-ACCESS", getCalendarServerAccess());
        }

        // iCal 4 global X-CALENDARSERVER-ACCESS
        String calendarServerAccess = getPropertyValue("X-CALENDARSERVER-ACCESS");
        String now = ExchangeSession.getZuluDateFormat().format(new Date());

        // fix method from iPhone
        if (!fromServer && getPropertyValue("METHOD") == null) {
            setPropertyValue("METHOD", "PUBLISH");
        }

        // rename TZID for maximum iCal/iPhone compatibility
        String tzid = null;
        if (fromServer) {
            // get current tzid
            VObject vObject = vTimezone;
            if (vObject != null) {
                String currentTzid = vObject.getPropertyValue("TZID");
                // fix TZID with \n (Exchange 2010 bug)
                if (currentTzid != null && currentTzid.endsWith("\n")) {
                    currentTzid = currentTzid.substring(0, currentTzid.length() - 1);
                    vObject.setPropertyValue("TZID", currentTzid);
                }
                if (currentTzid != null && currentTzid.indexOf(' ') >= 0) {
                    try {
                        tzid = ResourceBundle.getBundle("timezones").getString(currentTzid);
                        vObject.setPropertyValue("TZID", tzid);
                    } catch (MissingResourceException e) {
                        LOGGER.debug("Timezone " + currentTzid + " not found in rename table");
                    }
                }
            }
        }

        if (!fromServer) {
            fixTimezone();
        }

        // iterate over vObjects
        for (VObject vObject : vObjects) {
            if ("VEVENT".equals(vObject.type)) {
                if (calendarServerAccess != null) {
                    vObject.setPropertyValue("CLASS", getEventClass(calendarServerAccess));
                    // iCal 3, get X-CALENDARSERVER-ACCESS from local VEVENT
                } else if (vObject.getPropertyValue("X-CALENDARSERVER-ACCESS") != null) {
                    vObject.setPropertyValue("CLASS", getEventClass(vObject.getPropertyValue("X-CALENDARSERVER-ACCESS")));
                }
                if (fromServer) {
                    // remove organizer line for event without attendees for iPhone
                    if (vObject.getProperty("ATTENDEE") == null) {
                        vObject.setPropertyValue("ORGANIZER", null);
                    }
                    // detect allday and update date properties
                    if (isCdoAllDay(vObject)) {
                        setClientAllday(vObject.getProperty("DTSTART"));
                        setClientAllday(vObject.getProperty("DTEND"));
                        setClientAllday(vObject.getProperty("RECURRENCE-ID"));
                    }
                    String cdoBusyStatus = vObject.getPropertyValue("X-MICROSOFT-CDO-BUSYSTATUS");
                    if (cdoBusyStatus != null) {
                        vObject.setPropertyValue("TRANSP",
                                !"FREE".equals(cdoBusyStatus) ? "OPAQUE" : "TRANSPARENT");
                    }

                    // Apple iCal doesn't understand this key, and it's entourage
                    // specific (i.e. not needed by any caldav client): strip it out
                    vObject.removeProperty("X-ENTOURAGE_UUID");

                    splitExDate(vObject);

                    // remove empty properties
                    if ("".equals(vObject.getPropertyValue("LOCATION"))) {
                        vObject.removeProperty("LOCATION");
                    }
                    if ("".equals(vObject.getPropertyValue("DESCRIPTION"))) {
                        vObject.removeProperty("DESCRIPTION");
                    }
                    if ("".equals(vObject.getPropertyValue("CLASS"))) {
                        vObject.removeProperty("CLASS");
                    }
                    // rename TZID
                    if (tzid != null) {
                        VProperty dtStart = vObject.getProperty("DTSTART");
                        if (dtStart != null && dtStart.getParam("TZID") != null) {
                            dtStart.setParam("TZID", tzid);
                        }
                        VProperty dtEnd = vObject.getProperty("DTEND");
                        if (dtEnd != null && dtStart.getParam("TZID") != null) {
                            dtEnd.setParam("TZID", tzid);
                        }
                        VProperty reccurrenceId = vObject.getProperty("RECURRENCE-ID");
                        if (reccurrenceId != null && reccurrenceId.getParam("TZID") != null) {
                            reccurrenceId.setParam("TZID", tzid);
                        }
                    }
                    // remove unsupported attachment reference
                    if (vObject.getProperty("ATTACH") != null) {
                        List<String> toRemoveValues = null;
                        List<String> values = vObject.getProperty("ATTACH").getValues();
                        for (String value : values) {
                            if (value.indexOf("CID:") >= 0) {
                                if (toRemoveValues == null) {
                                    toRemoveValues = new ArrayList<String>();
                                }
                                toRemoveValues.add(value);
                            }
                        }
                        if (toRemoveValues != null) {
                            values.removeAll(toRemoveValues);
                            if (values.size() == 0) {
                                vObject.removeProperty("ATTACH");
                            }
                        }
                    }
                } else {
                    // add organizer line to all events created in Exchange for active sync
                    String organizer = getEmailValue(vObject.getProperty("ORGANIZER"));
                    if (organizer == null) {
                        vObject.setPropertyValue("ORGANIZER", "MAILTO:" + email);
                    } else if (!email.equalsIgnoreCase(organizer) && vObject.getProperty("X-MICROSOFT-CDO-REPLYTIME") == null) {
                        vObject.setPropertyValue("X-MICROSOFT-CDO-REPLYTIME", now);
                    }
                    // set OWA allday flag
                    vObject.setPropertyValue("X-MICROSOFT-CDO-ALLDAYEVENT", isAllDay(vObject) ? "TRUE" : "FALSE");
                    vObject.setPropertyValue("X-MICROSOFT-CDO-BUSYSTATUS",
                            !"TRANSPARENT".equals(vObject.getPropertyValue("TRANSP")) ? "BUSY" : "FREE");

                    if (isAllDay(vObject)) {
                        // convert date values to outlook compatible values
                        setServerAllday(vObject.getProperty("DTSTART"));
                        setServerAllday(vObject.getProperty("DTEND"));
                    } else {
                        fixTzid(vObject.getProperty("DTSTART"));
                        fixTzid(vObject.getProperty("DTEND"));
                    }
                }

                fixAttendees(vObject, fromServer);

                fixAlarm(vObject, fromServer);
            }
        }

    }

    private void fixTimezone() {
        if (vTimezone != null && vTimezone.vObjects != null && vTimezone.vObjects.size() > 2) {
            VObject standard = null;
            VObject daylight = null;
            for (VObject vObject : vTimezone.vObjects) {
                if ("STANDARD".equals(vObject.type)) {
                    if (standard == null ||
                            (vObject.getPropertyValue("DTSTART").compareTo(standard.getPropertyValue("DTSTART")) > 0)) {
                        standard = vObject;
                    }
                }
                if ("DAYLIGHT".equals(vObject.type)) {
                    if (daylight == null ||
                            (vObject.getPropertyValue("DTSTART").compareTo(daylight.getPropertyValue("DTSTART")) > 0)) {
                        daylight = vObject;
                    }
                }
            }
            vTimezone.vObjects.clear();
            vTimezone.vObjects.add(standard);
            vTimezone.vObjects.add(daylight);
        }
    }

    private void fixTzid(VProperty property) {
        if (property != null && !property.hasParam("TZID")) {
            property.addParam("TZID", vTimezone.getPropertyValue("TZID"));
        }
    }

    protected void splitExDate(VObject vObject) {
        List<VProperty> exDateProperties = vObject.getProperties("EXDATE");
        if (exDateProperties != null) {
            for (VProperty property : exDateProperties) {
                String value = property.getValue();
                if (value.indexOf(',') >= 0) {
                    // split property
                    vObject.removeProperty(property);
                    for (String singleValue : value.split(",")) {
                        VProperty singleProperty = new VProperty("EXDATE", singleValue);
                        singleProperty.setParams(property.getParams());
                        vObject.addProperty(singleProperty);
                    }
                }
            }
        }
    }

    protected void setServerAllday(VProperty property) {
        if (vTimezone != null) {
            // set TZID param
            if (!property.hasParam("TZID")) {
                property.addParam("TZID", vTimezone.getPropertyValue("TZID"));
            }
            // remove VALUE
            property.removeParam("VALUE");
            String value = property.getValue();
            if (value.length() != 8) {
                LOGGER.warn("Invalid date value in allday event: " + value);
            }
            property.setValue(property.getValue() + "T000000");
        }
    }

    protected void setClientAllday(VProperty property) {
        if (property != null) {
            // set VALUE=DATE param
            if (!property.hasParam("VALUE")) {
                property.addParam("VALUE", "DATE");
            }
            // remove TZID
            property.removeParam("TZID");
            String value = property.getValue();
            if (value.length() != 8) {
                // try to convert datetime value to date value
                try {
                    Calendar calendar = Calendar.getInstance();
                    SimpleDateFormat dateParser = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                    calendar.setTime(dateParser.parse(value));
                    calendar.add(Calendar.HOUR_OF_DAY, 12);
                    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
                    value = dateFormatter.format(calendar.getTime());
                } catch (ParseException e) {
                    LOGGER.warn("Invalid date value in allday event: " + value);
                }
            }
            property.setValue(value);
        }
    }

    protected void fixAlarm(VObject vObject, boolean fromServer) {
        if (vObject.vObjects != null) {
            if (Settings.getBooleanProperty("davmail.caldavDisableReminders", false)) {
                ArrayList<VObject> vAlarms = null;
                for (VObject vAlarm : vObject.vObjects) {
                    if ("VALARM".equals(vAlarm.type)) {
                        if (vAlarms == null) {
                            vAlarms = new ArrayList<VObject>();
                        }
                        vAlarms.add(vAlarm);
                    }
                }
                // remove all vAlarms
                if (vAlarms != null) {
                    for (VObject vAlarm : vAlarms) {
                        vObject.vObjects.remove(vAlarm);
                    }
                }

            } else {
                for (VObject vAlarm : vObject.vObjects) {
                    if ("VALARM".equals(vAlarm.type)) {
                        String action = vAlarm.getPropertyValue("ACTION");
                        if (fromServer && "DISPLAY".equals(action)
                                // convert DISPLAY to AUDIO only if user defined an alarm sound
                                && Settings.getProperty("davmail.caldavAlarmSound") != null) {
                            // Convert alarm to audio for iCal
                            vAlarm.setPropertyValue("ACTION", "AUDIO");

                            if (vAlarm.getPropertyValue("ATTACH") == null) {
                                // Add defined sound into the audio alarm
                                VProperty vProperty = new VProperty("ATTACH", Settings.getProperty("davmail.caldavAlarmSound"));
                                vProperty.addParam("VALUE", "URI");
                                vAlarm.addProperty(vProperty);
                            }

                        } else if (!fromServer && "AUDIO".equals(action)) {
                            // Use the alarm action that exchange (and blackberry) understand
                            // (exchange and blackberry don't understand audio actions)
                            vAlarm.setPropertyValue("ACTION", "DISPLAY");
                        }
                    }
                }
            }
        }
    }

    /**
     * Replace iCal4 (Snow Leopard) principal paths with mailto expression
     *
     * @param value attendee value or ics line
     * @return fixed value
     */
    protected String replaceIcal4Principal(String value) {
        if (value.contains("/principals/__uuids__/")) {
            return value.replaceAll("/principals/__uuids__/([^/]*)__AT__([^/]*)/", "mailto:$1@$2");
        } else {
            return value;
        }
    }

    private void fixAttendees(VObject vObject, boolean fromServer) {
        if (vObject.properties != null) {
            for (VProperty property : vObject.properties) {
                if ("ATTENDEE".equalsIgnoreCase(property.getKey())) {
                    if (fromServer) {
                        // If this is coming from the server, strip out RSVP for this
                        // user as an attendee where the partstat is something other
                        // than PARTSTAT=NEEDS-ACTION since the RSVP confuses iCal4 into
                        // thinking the attendee has not replied
                        if (isCurrentUser(property) && property.hasParam("RSVP", "TRUE")) {
                            VProperty.Param partstat = property.getParam("PARTSTAT");
                            if (partstat == null || !"NEEDS-ACTION".equals(partstat.getValue())) {
                                property.removeParam("RSVP");
                            }
                        }
                    } else {
                        property.setValue(replaceIcal4Principal(property.getValue()));
                    }
                }

            }
        }

    }

    private boolean isCurrentUser(VProperty property) {
        return property.getValue().equalsIgnoreCase("mailto:" + email);
    }

    /**
     * Return VTimezone object
     *
     * @return VTimezone
     */
    public VObject getVTimezone() {
        return vTimezone;
    }

    /**
     * Convert X-CALENDARSERVER-ACCESS to CLASS.
     * see http://svn.calendarserver.org/repository/calendarserver/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt
     *
     * @param calendarServerAccess X-CALENDARSERVER-ACCESS value
     * @return CLASS value
     */
    protected String getEventClass(String calendarServerAccess) {
        if ("PRIVATE".equalsIgnoreCase(calendarServerAccess)) {
            return "CONFIDENTIAL";
        } else if ("CONFIDENTIAL".equalsIgnoreCase(calendarServerAccess) || "RESTRICTED".equalsIgnoreCase(calendarServerAccess)) {
            return "PRIVATE";
        } else {
            return null;
        }
    }

    /**
     * Convert CLASS to X-CALENDARSERVER-ACCESS.
     * see http://svn.calendarserver.org/repository/calendarserver/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt     *
     *
     * @return X-CALENDARSERVER-ACCESS value
     */
    protected String getCalendarServerAccess() {
        String eventClass = getFirstVeventPropertyValue("CLASS");
        if ("PRIVATE".equalsIgnoreCase(eventClass)) {
            return "CONFIDENTIAL";
        } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
            return "PRIVATE";
        } else {
            return null;
        }
    }

    /**
     * Get property value from first VEVENT in VCALENDAR.
     *
     * @param name property name
     * @return property value
     */
    public String getFirstVeventPropertyValue(String name) {
        if (firstVevent == null) {
            return null;
        } else {
            return firstVevent.getPropertyValue(name);
        }
    }

    protected VProperty getFirstVeventProperty(String name) {
        if (firstVevent == null) {
            return null;
        } else {
            return firstVevent.getProperty(name);
        }
    }


    /**
     * Get properties by name from first VEVENT.
     *
     * @param name property name
     * @return properties
     */
    public List<VProperty> getFirstVeventProperties(String name) {
        if (firstVevent == null) {
            return null;
        } else {
            return firstVevent.getProperties(name);
        }
    }

    /**
     * Remove VAlarm from VCalendar.
     */
    public void removeVAlarm() {
        if (vObjects != null) {
            for (VObject vObject : vObjects) {
                if ("VEVENT".equals(vObject.type)) {
                    // As VALARM is the only possible inner object, just drop all objects
                    if (vObject.vObjects != null) {
                        vObject.vObjects = null;
                    }
                }
            }
        }
    }

    /**
     * Check if VCalendar has a VALARM item.
     *
     * @return true if VCalendar has a VALARM
     */
    public boolean hasVAlarm() {
        if (vObjects != null) {
            for (VObject vObject : vObjects) {
                if ("VEVENT".equals(vObject.type)) {
                    if (vObject.vObjects != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if this VCalendar is a meeting.
     *
     * @return true if this VCalendar has attendees
     */
    public boolean isMeeting() {
        return getFirstVeventProperty("ATTENDEE") != null;
    }

    /**
     * Check if current user is meeting organizer.
     *
     * @return true it user email matched organizer email
     */
    public boolean isMeetingOrganizer() {
        return email.equalsIgnoreCase(getEmailValue(getFirstVeventProperty("ORGANIZER")));
    }

    /**
     * Set property value on first VEVENT.
     *
     * @param propertyName  property name
     * @param propertyValue property value
     */
    public void setFirstVeventPropertyValue(String propertyName, String propertyValue) {
        firstVevent.setPropertyValue(propertyName, propertyValue);
    }

    /**
     * Add property on first VEVENT.
     *
     * @param vProperty property object
     */
    public void addFirstVeventProperty(VProperty vProperty) {
        firstVevent.addProperty(vProperty);
    }

    /**
     * Check if this item is a VTODO item
     *
     * @return true with VTODO items
     */
    public boolean isTodo() {
        return "VTODO".equals(firstVevent.type);
    }

    /**
     * VCalendar recipients for notifications
     */
    public static class Recipients {
        /**
         * attendee list
         */
        public String attendees;

        /**
         * optional attendee list
         */
        public String optionalAttendees;

        /**
         * vCalendar organizer
         */
        public String organizer;
    }

    /**
     * Build recipients value for VCalendar.
     *
     * @param isNotification if true, filter recipients that should receive meeting notifications
     * @return notification/event recipients
     */
    public Recipients getRecipients(boolean isNotification) {

        HashSet<String> attendees = new HashSet<String>();
        HashSet<String> optionalAttendees = new HashSet<String>();

        // get recipients from first VEVENT
        List<VProperty> attendeeProperties = getFirstVeventProperties("ATTENDEE");
        if (attendeeProperties != null) {
            for (VProperty property : attendeeProperties) {
                // exclude current user and invalid values from recipients
                // also exclude no action attendees
                String attendeeEmail = getEmailValue(property);
                if (!email.equalsIgnoreCase(attendeeEmail) && attendeeEmail != null && attendeeEmail.indexOf('@') >= 0
                        // return all attendees for user calendar folder, filter for notifications
                        && (!isNotification
                        // notify attendee if reply explicitly requested
                        || (property.hasParam("RSVP", "TRUE"))
                        || (
                        // workaround for iCal bug: do not notify if reply explicitly not requested
                        !(property.hasParam("RSVP", "FALSE")) &&
                                ((property.hasParam("PARTSTAT", "NEEDS-ACTION")
                                        // need to include other PARTSTATs participants for CANCEL notifications
                                        || property.hasParam("PARTSTAT", "ACCEPTED")
                                        || property.hasParam("PARTSTAT", "DECLINED")
                                        || property.hasParam("PARTSTAT", "TENTATIVE")))
                ))) {
                    if (property.hasParam("ROLE", "OPT-PARTICIPANT")) {
                        optionalAttendees.add(attendeeEmail);
                    } else {
                        attendees.add(attendeeEmail);
                    }
                }
            }
        }
        Recipients recipients = new Recipients();
        recipients.organizer = getEmailValue(getFirstVeventProperty("ORGANIZER"));
        recipients.attendees = StringUtil.join(attendees, ", ");
        recipients.optionalAttendees = StringUtil.join(optionalAttendees, ", ");
        return recipients;
    }

    protected String getAttendeeStatus() {
        String status = null;
        List<VProperty> attendeeProperties = getFirstVeventProperties("ATTENDEE");
        if (attendeeProperties != null) {
            for (VProperty property : attendeeProperties) {
                String attendeeEmail = getEmailValue(property);
                if (email.equalsIgnoreCase(attendeeEmail) && property.hasParam("PARTSTAT")) {
                    // found current user attendee line
                    status = property.getParam("PARTSTAT").getValue();
                    break;
                }
            }
        }
        return status;
    }

    /**
     * Get first VEvent
     * @return first VEvent
     */
    public VObject getFirstVevent() {
        return firstVevent;
    }

    /**
     * Get recurring VCalendar occurence exceptions.
     *
     * @return event occurences
     */
    public List<VObject> getModifiedOccurrences() {
        boolean first = true;
        ArrayList<VObject> results = new ArrayList<VObject>();
        for (VObject vObject : vObjects) {
            if ("VEVENT".equals(vObject.type)) {
                if (first) {
                    first = false;
                } else {
                    results.add(vObject);
                }
            }
        }
        return results;
    }
}

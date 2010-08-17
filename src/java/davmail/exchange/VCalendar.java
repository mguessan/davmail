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
import java.util.Date;
import java.util.HashSet;
import java.util.List;

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
        if (this.vTimezone == null) {
            this.vObjects.add(0, vTimezone);
            this.vTimezone = vTimezone;
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

    @Override
    protected void addVObject(VObject vObject) {
        super.addVObject(vObject);
        if (firstVevent == null && "VEVENT".equals(vObject.type)) {
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

    protected String getEmailValue(VProperty property) {
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
        // iCal 4 global private flag
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

    protected void setClientAllday(VProperty property) {
        // set VALUE=DATE param
        if (!property.hasParam("VALUE")) {
            property.addParam("VALUE", "DATE");
        }
        // remove TZID
        property.removeParam("TZID");
        String value = property.getValue();
        int tIndex = value.indexOf('T');
        if (tIndex >= 0) {
            value = value.substring(0, tIndex);
        } else {
            LOGGER.warn("Invalid date value in allday event: " + value);
        }
        property.setValue(value);
    }

    protected void fixAlarm(VObject vObject, boolean fromServer) {
        if (vObject.vObjects != null) {
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
            return calendarServerAccess;
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
            return eventClass;
        }
    }

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


    protected List<VProperty> getFirstVeventProperties(String name) {
        if (firstVevent == null) {
            return null;
        } else {
            return firstVevent.getProperties(name);
        }
    }

    class Recipients {
        String attendees;
        String optionalAttendees;
        String organizer;
    }

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

}

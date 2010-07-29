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
import davmail.exception.DavMailException;
import davmail.util.StringUtil;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Test ExchangeSession event conversion.
 */
public class TestExchangeSessionEvent extends TestCase {
    String email = "user@company.com";

    /*
        X-CALENDARSERVER-ACCESS conversion
        see http://svn.calendarserver.org/repository/calendarserver/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt

        X-CALENDARSERVER-ACCESS -> CLASS
        NONE -> NONE
        PUBLIC -> PUBLIC
        PRIVATE -> CONFIDENTIAL
        CONFIDENTIAL -> PRIVATE
        RESTRICTED -> PRIVATE

        CLASS -> X-CALENDARSERVER-ACCESS
        NONE -> NONE
        PUBLIC -> PUBLIC
        PRIVATE -> CONFIDENTIAL
        CONFIDENTIAL -> PRIVATE

        iCal 3 sends X-CALENDARSERVER-ACCESS inside VEVENT, iCal 4 uses global X-CALENDARSERVER-ACCESS
     */

    protected String getAllDayLine(String line) throws IOException {
        int valueIndex = line.lastIndexOf(':');
        int valueEndIndex = line.lastIndexOf('T');
        if (valueIndex < 0 || valueEndIndex < 0) {
            throw new DavMailException("EXCEPTION_INVALID_ICS_LINE", line);
        }
        int keyIndex = line.indexOf(';');
        if (keyIndex == -1) {
            keyIndex = valueIndex;
        }
        String dateValue = line.substring(valueIndex + 1, valueEndIndex);
        String key = line.substring(0, Math.min(keyIndex, valueIndex));
        return key + ";VALUE=DATE:" + dateValue;
    }

    protected String fixTimezoneId(String line, String validTimezoneId) {
        return StringUtil.replaceToken(line, "TZID=", ":", validTimezoneId);
    }

    protected String replaceIcal4Principal(String value) {
        if (value.contains("/principals/__uuids__/")) {
            return value.replaceAll("/principals/__uuids__/([^/]*)__AT__([^/]*)/", "mailto:$1@$2");
        } else {
            return value;
        }
    }

    protected void splitExDate(ICSBufferedWriter result, String line) {
        int cur = line.lastIndexOf(':') + 1;
        String start = line.substring(0, cur);

        for (int next = line.indexOf(',', cur); next != -1; next = line.indexOf(',', cur)) {
            String val = line.substring(cur, next);
            result.writeLine(start + val);

            cur = next + 1;
        }

        result.writeLine(start + line.substring(cur));
    }

    protected String oldfixICS(String icsBody, boolean fromServer) throws IOException {
        // first pass : detect
        class AllDayState {
            boolean isAllDay;
            boolean hasCdoAllDay;
            boolean isCdoAllDay;
        }

        // Convert event class from and to iCal
        // See https://trac.calendarserver.org/browser/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt
        boolean isAppleiCal = false;
        boolean hasAttendee = false;
        boolean hasCdoBusyStatus = false;
        // detect ics event with empty timezone (all day from Lightning)
        boolean hasTimezone = false;
        String transp = null;
        String validTimezoneId = null;
        String eventClass = null;
        String organizer = null;
        String action = null;
        String method = null;
        boolean sound = false;

        List<AllDayState> allDayStates = new ArrayList<AllDayState>();
        AllDayState currentAllDayState = new AllDayState();
        BufferedReader reader = null;
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(':');
                if (index >= 0) {
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    if ("DTSTART;VALUE=DATE".equals(key)) {
                        currentAllDayState.isAllDay = true;
                    } else if ("X-MICROSOFT-CDO-ALLDAYEVENT".equals(key)) {
                        currentAllDayState.hasCdoAllDay = true;
                        currentAllDayState.isCdoAllDay = "TRUE".equals(value);
                    } else if ("END:VEVENT".equals(line)) {
                        allDayStates.add(currentAllDayState);
                        currentAllDayState = new AllDayState();
                    } else if ("PRODID".equals(key) && line.contains("iCal")) {
                        // detect iCal created events
                        isAppleiCal = true;
                    } else if (isAppleiCal && "X-CALENDARSERVER-ACCESS".equals(key)) {
                        eventClass = value;
                    } else if (!isAppleiCal && "CLASS".equals(key)) {
                        eventClass = value;
                    } else if ("ACTION".equals(key)) {
                        action = value;
                    } else if ("ATTACH;VALUES=URI".equals(key)) {
                        // This is a marker that this event has an alarm with sound
                        sound = true;
                    } else if (key.startsWith("ORGANIZER")) {
                        if (value.startsWith("MAILTO:")) {
                            organizer = value.substring(7);
                        } else {
                            organizer = value;
                        }
                    } else if (key.startsWith("ATTENDEE")) {
                        hasAttendee = true;
                    } else if ("TRANSP".equals(key)) {
                        transp = value;
                    } else if (line.startsWith("TZID:(GMT") ||
                            // additional test for Outlook created recurring events
                            line.startsWith("TZID:GMT ")) {
                        try {
                            validTimezoneId = ResourceBundle.getBundle("timezones").getString(value);
                        } catch (MissingResourceException mre) {
                            //LOGGER.warn(new BundleMessage("LOG_INVALID_TIMEZONE", value));
                        }
                    } else if ("X-MICROSOFT-CDO-BUSYSTATUS".equals(key)) {
                        hasCdoBusyStatus = true;
                    } else if ("BEGIN:VTIMEZONE".equals(line)) {
                        hasTimezone = true;
                    } else if ("METHOD".equals(key)) {
                        method = value;
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        // second pass : fix
        int count = 0;
        ICSBufferedWriter result = new ICSBufferedWriter();
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;

            while ((line = reader.readLine()) != null) {
                // remove empty properties
                if ("CLASS:".equals(line) || "LOCATION:".equals(line)) {
                    continue;
                }
                // fix invalid exchange timezoneid
                if (validTimezoneId != null && line.indexOf(";TZID=") >= 0) {
                    line = fixTimezoneId(line, validTimezoneId);
                }
                if (!fromServer && "BEGIN:VCALENDAR".equals(line) && method == null) {
                    result.writeLine(line);
                    // append missing method
                    if (method == null) {
                        result.writeLine("METHOD:PUBLISH");
                    }
                    continue;
                }
                if (fromServer && line.startsWith("PRODID:") && eventClass != null) {
                    result.writeLine(line);
                    // set global calendarserver access for iCal 4
                    if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                        result.writeLine("X-CALENDARSERVER-ACCESS:CONFIDENTIAL");
                    } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                        result.writeLine("X-CALENDARSERVER-ACCESS:PRIVATE");
                    } else if (eventClass != null) {
                        result.writeLine("X-CALENDARSERVER-ACCESS:" + eventClass);
                    }
                    continue;
                }
                if (!fromServer && "BEGIN:VEVENT".equals(line) && !hasTimezone) {
                    result.write("BEGIN:VTIMEZONE\nFAKE:FAKE\nEND:VTIMEZONE\n");
                    hasTimezone = true;
                }
                if (!fromServer && currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE";
                } else if (!fromServer && "END:VEVENT".equals(line)) {
                    if (!hasCdoBusyStatus) {
                        result.writeLine("X-MICROSOFT-CDO-BUSYSTATUS:" + (!"TRANSPARENT".equals(transp) ? "BUSY" : "FREE"));
                    }
                    if (currentAllDayState.isAllDay && !currentAllDayState.hasCdoAllDay) {
                        result.writeLine("X-MICROSOFT-CDO-ALLDAYEVENT:TRUE");
                    }
                    // add organizer line to all events created in Exchange for active sync
                    if (organizer == null) {
                        result.writeLine("ORGANIZER:MAILTO:" + email);
                    }
                    if (isAppleiCal) {
                        if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                            result.writeLine("CLASS:PRIVATE");
                        } else if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                            result.writeLine("CLASS:CONFIDENTIAL");
                        } else if (eventClass != null) {
                            result.writeLine("CLASS:" + eventClass);
                        }
                    }
                } else if (!fromServer && line.startsWith("X-MICROSOFT-CDO-BUSYSTATUS:")) {
                    line = "X-MICROSOFT-CDO-BUSYSTATUS:" + (!"TRANSPARENT".equals(transp) ? "BUSY" : "FREE");
                } else if (!fromServer && !currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE";
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTSTART") && !line.startsWith("DTSTART;VALUE=DATE")) {
                    line = getAllDayLine(line);
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTEND") && !line.startsWith("DTEND;VALUE=DATE")) {
                    line = getAllDayLine(line);
                } else if (!fromServer && currentAllDayState.isAllDay && line.startsWith("DTSTART") && line.startsWith("DTSTART;VALUE=DATE")) {
                    line = "DTSTART;TZID=\"" + "OWATZID" + "\":" + line.substring(19) + "T000000";
                } else if (!fromServer && currentAllDayState.isAllDay && line.startsWith("DTEND") && line.startsWith("DTEND;VALUE=DATE")) {
                    line = "DTEND;TZID=\"" + "OWATZID" + "\":" + line.substring(17) + "T000000";
                } else if (line.startsWith("TZID:") && validTimezoneId != null) {
                    line = "TZID:" + validTimezoneId;
                } else if ("BEGIN:VEVENT".equals(line)) {
                    currentAllDayState = allDayStates.get(count++);
                    // remove calendarserver access
                } else if (line.startsWith("X-CALENDARSERVER-ACCESS:")) {
                    continue;
                } else if (line.startsWith("EXDATE;TZID=") || line.startsWith("EXDATE:")) {
                    // Apple iCal doesn't support EXDATE with multiple exceptions
                    // on one line.  Split into multiple EXDATE entries (which is
                    // also legal according to the caldav standard).
                    splitExDate(result, line);
                    continue;
                } else if (line.startsWith("X-ENTOURAGE_UUID:")) {
                    // Apple iCal doesn't understand this key, and it's entourage
                    // specific (i.e. not needed by any caldav client): strip it out
                    continue;
                } else if (fromServer && line.startsWith("ATTENDEE;")
                        && (line.indexOf(email) >= 0)) {
                    // If this is coming from the server, strip out RSVP for this
                    // user as an attendee where the partstat is something other
                    // than PARTSTAT=NEEDS-ACTION since the RSVP confuses iCal4 into
                    // thinking the attendee has not replied

                    int rsvpSuffix = line.indexOf("RSVP=TRUE;");
                    int rsvpPrefix = line.indexOf(";RSVP=TRUE");

                    if (((rsvpSuffix >= 0) || (rsvpPrefix >= 0))
                            && (line.indexOf("PARTSTAT=") >= 0)
                            && (line.indexOf("PARTSTAT=NEEDS-ACTION") < 0)) {

                        // Strip out the "RSVP" line from the calendar entry
                        if (rsvpSuffix >= 0) {
                            line = line.substring(0, rsvpSuffix) + line.substring(rsvpSuffix + 10);
                        } else {
                            line = line.substring(0, rsvpPrefix) + line.substring(rsvpPrefix + 10);
                        }

                    }
                } else if (line.startsWith("ACTION:")) {
                    if (fromServer && "DISPLAY".equals(action)
                            // convert DISPLAY to AUDIO only if user defined an alarm sound
                            && Settings.getProperty("davmail.caldavAlarmSound") != null) {
                        // Convert alarm to audio for iCal
                        result.writeLine("ACTION:AUDIO");

                        if (!sound) {
                            // Add defined sound into the audio alarm
                            result.writeLine("ATTACH;VALUE=URI:" + Settings.getProperty("davmail.caldavAlarmSound"));
                        }

                        continue;
                    } else if (!fromServer && "AUDIO".equals(action)) {
                        // Use the alarm action that exchange (and blackberry) understand
                        // (exchange and blackberry don't understand audio actions)

                        result.writeLine("ACTION:DISPLAY");
                        continue;
                    }

                    // Don't recognize this type of action: pass it through

                } else if (line.startsWith("CLASS:")) {
                    if (!fromServer && isAppleiCal) {
                        continue;
                    } else {
                        // still set calendarserver access inside event for iCal 3
                        if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:CONFIDENTIAL");
                        } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:PRIVATE");
                        } else {
                            result.writeLine("X-CALENDARSERVER-ACCESS:" + eventClass);
                        }
                    }
                    // remove organizer line if user is organizer for iPhone
                } else if (fromServer && line.startsWith("ORGANIZER") && !hasAttendee) {
                    continue;
                } else if (organizer != null && line.startsWith("ATTENDEE") && line.contains(organizer)) {
                    // Ignore organizer as attendee
                    continue;
                } else if (!fromServer && line.startsWith("ATTENDEE")) {
                    line = replaceIcal4Principal(line);
                }

                result.writeLine(line);
            }
        } finally {
            reader.close();
        }

        String resultString = result.toString();

        return resultString;
    }

    protected String fixICS(String icsBody, boolean fromServer) throws IOException {
        VCalendar vCalendar = new VCalendar(icsBody, email);
        vCalendar.fixVCalendar(fromServer);
        return vCalendar.toString();
    }

    public void testNoClass() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        String toClient = fixICS(itemBody, true);
        System.out.println(toServer);
        System.out.println(toClient);
        assertTrue(toServer.indexOf("CLASS") < 0);
        assertTrue(toClient.indexOf("CLASS") < 0);
        assertTrue(toClient.indexOf("X-CALENDARSERVER-ACCESS") < 0);
    }

    public void testPublicClass() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "CLASS:PUBLIC\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        String toClient = fixICS(itemBody, true);
        System.out.println(toServer);
        System.out.println(toClient);
        assertTrue(toServer.indexOf("CLASS:PUBLIC") >= 0);
        assertTrue(toClient.indexOf("CLASS:PUBLIC") >= 0);
        assertTrue(toClient.indexOf("X-CALENDARSERVER-ACCESS:PUBLIC") >= 0);
    }

    public void testPrivateClass() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "CLASS:PRIVATE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        String toClient = fixICS(itemBody, true);
        System.out.println(toServer);
        System.out.println(toClient);
        assertTrue(toServer.indexOf("CLASS:PRIVATE") >= 0);
        assertTrue(toClient.indexOf("CLASS:PRIVATE") >= 0);
        assertTrue(toClient.indexOf("X-CALENDARSERVER-ACCESS:CONFIDENTIAL") >= 0);
    }

    public void testConfidentialClass() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "CLASS:CONFIDENTIAL\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        String toClient = fixICS(itemBody, true);
        System.out.println(toServer);
        System.out.println(toClient);
        assertTrue(toServer.indexOf("CLASS:CONFIDENTIAL") >= 0);
        assertTrue(toClient.indexOf("CLASS:CONFIDENTIAL") >= 0);
        assertTrue(toClient.indexOf("X-CALENDARSERVER-ACCESS:PRIVATE") >= 0);
    }

    public void testCalendarServerAccessPrivate() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Apple Inc.//iCal 4.0.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "X-CALENDARSERVER-ACCESS:PRIVATE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.indexOf("CLASS:CONFIDENTIAL") >= 0);
    }

    public void testCalendarServerAccessConfidential() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Apple Inc.//iCal 4.0.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "X-CALENDARSERVER-ACCESS:CONFIDENTIAL\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.indexOf("CLASS:PRIVATE") >= 0);
    }

    public void testCalendarServerAccessPublic() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Apple Inc.//iCal 4.0.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "X-CALENDARSERVER-ACCESS:PUBLIC\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.indexOf("CLASS:PUBLIC") >= 0);
    }

    public void testCalendarServerAccessNone() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Apple Inc.//iCal 4.0.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertFalse(toServer.contains("CLASS"));
    }

    public void testNoOrganizer() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.contains("ORGANIZER"));
    }

}

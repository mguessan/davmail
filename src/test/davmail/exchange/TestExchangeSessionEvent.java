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

import davmail.BundleMessage;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;

/**
 * Test ExchangeSession event conversion.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionEvent extends TestCase {
    static String email = "user@company.com";
    static VObject vTimeZone;

    static {
        try {
            vTimeZone = new VObject(new ICSBufferedReader(new StringReader("BEGIN:VTIMEZONE\n" +
                    "TZID:(GMT+01.00) Paris/Madrid/Brussels/Copenhagen\n" +
                    "X-MICROSOFT-CDO-TZID:3\n" +
                    "BEGIN:STANDARD\n" +
                    "DTSTART:16010101T030000\n" +
                    "TZOFFSETFROM:+0200\n" +
                    "TZOFFSETTO:+0100\n" +
                    "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=10;BYDAY=-1SU\n" +
                    "END:STANDARD\n" +
                    "BEGIN:DAYLIGHT\n" +
                    "DTSTART:16010101T020000\n" +
                    "TZOFFSETFROM:+0100\n" +
                    "TZOFFSETTO:+0200\n" +
                    "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=3;BYDAY=-1SU\n" +
                    "END:DAYLIGHT\n" +
                    "END:VTIMEZONE")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    protected String fixICS(String icsBody, boolean fromServer) throws IOException {
        VCalendar vCalendar = new VCalendar(icsBody, email, vTimeZone);
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


    public void testValarm() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-PT15M\n" +
                "ATTACH;VALUE=URI:Basso\n" +
                "ACTION:AUDIO\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.contains("ACTION:DISPLAY"));
    }

    public void testReceiveAllDay() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                vTimeZone +
                "BEGIN:VEVENT\n" +
                "DTSTART;TZID=\"(GMT+01.00) Paris/Madrid/Brussels/Copenhagen\":20100615T000000\n" +
                "DTEND;TZID=\"(GMT+01.00) Paris/Madrid/Brussels/Copenhagen\":20100616T000000\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(itemBody, true);
        System.out.println(toClient);
        // OWA created allday events have the X-MICROSOFT-CDO-ALLDAYEVENT set to true and always 000000 in event time
        // just remove the TZID, add VALUE=DATE param and set a date only value 
        assertTrue(toClient.contains("DTSTART;VALUE=DATE:20100615"));
        assertTrue(toClient.contains("DTEND;VALUE=DATE:20100616"));
    }

    public void testSendAllDay() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART;VALUE=DATE:20100615\n" +
                "DTEND;VALUE=DATE:20100616\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        // Client created allday event have no timezone and no time information in date values
        // first set the X-MICROSOFT-CDO-ALLDAYEVENT flag for OWA
        assertTrue(toServer.contains("X-MICROSOFT-CDO-ALLDAYEVENT:TRUE"));
        // then patch TZID for Outlook (need to retrieve OWA TZID
        assertTrue(toServer.contains("BEGIN:VTIMEZONE"));
        assertTrue(toServer.contains("TZID:" + vTimeZone.getPropertyValue("TZID")));
        assertTrue(toServer.contains("DTSTART;TZID=\"" + vTimeZone.getPropertyValue("TZID") + "\":20100615T000000"));
        assertTrue(toServer.contains("DTEND;TZID=\"" + vTimeZone.getPropertyValue("TZID") + "\":20100616T000000"));
    }

    public void testRsvp() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE:MAILTO:" + email + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(itemBody, true);
        System.out.println(toClient);
        assertTrue(toClient.contains("ATTENDEE;PARTSTAT=ACCEPTED:MAILTO:" + email));
    }

    public void testExdate() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "EXDATE;TZID=\"Europe/Paris\":20100809T150000,20100823T150000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(itemBody, true);
        System.out.println(toClient);
        assertTrue(toClient.contains("EXDATE;TZID=\"Europe/Paris\":20100823T150000"));

    }

    public void testEmptyLine() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(itemBody, true);
        System.out.println(toClient);
    }

    public void testAttendeeStatus() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE:MAILTO:" + email + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        VCalendar vCalendar = new VCalendar(itemBody, email, vTimeZone);
        vCalendar.fixVCalendar(false);
        String status = vCalendar.getAttendeeStatus();
        assertEquals("ACCEPTED", status);
        System.out.println("'" + BundleMessage.format(status) + "'");
    }

    public void testMissingTzid() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:20100101T000000\n" +
                "DTEND:20100102T000000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, false);
        System.out.println(toServer);
        assertTrue(toServer.contains("DTSTART;TZID="));
        assertTrue(toServer.contains("DTEND;TZID="));
    }

    public void testBroken() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20100916T115132Z\n" +
                "LAST-MODIFIED:20100916T115138Z\n" +
                "DTSTAMP:20100916T115138Z\n" +
                "UID:d72ff8cc-f3ee-4fbc-b44d-1aaf78d92847\n" +
                "SUMMARY:New Event\n" +
                "DTSTART;VALUE=DATE:20100929\n" +
                "DTEND;VALUE=DATE:20100930\n" +
                "TRANSP:TRANSPARENT\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, true);
        System.out.println(toServer);
    }

    public void testFloatingTimezone() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:Microsoft CDO for Microsoft Exchange\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Pacific Time (US & Canada)\\; Tijuana\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T030000\n" +
                "TZOFFSETFROM:-0700\n" +
                "TZOFFSETTO:-0800\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=11;BYDAY=1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T010000\n" +
                "TZOFFSETFROM:-0800\n" +
                "TZOFFSETTO:-0700\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=3;BYDAY=2SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE" +
                "BEGIN:VEVENT\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(itemBody, true);
        System.out.println(toServer);
    }

    public void testAnotherBroken() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\n" +
                "METHOD:PUBLISH\n" +
                "PRODID:Microsoft Exchange Server 2010\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:GMT -0800 (Standard) / GMT -0700 (Daylight)\\n\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0700\n" +
                "TZOFFSETTO:-0800\n" +
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=1SU;BYMONTH=11\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0800\n" +
                "TZOFFSETTO:-0700\n" +
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=2SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "ORGANIZER;CN=John Doe:MAILTO:aTargetAddress@dummy.com\n" +
                "DESCRIPTION;LANGUAGE=en-US:Look over broken timezone.\\n\n" +
                "SUMMARY;LANGUAGE=en-US:meeting\n" +
                "DTSTART;TZID=GMT -0800 (Standard) / GMT -0700 (Daylight)\n" +
                ":20060210T130000\n" +
                "DTEND;TZID=GMT -0800 (Standard) / GMT -0700 (Daylight)\n" +
                ":20060210T143000\n" +
                "UID:040000008200E00074C5B7101A82E00800000000D01FF309972CC601000000000000000\n" +
                " 010000000B389A3C5092D7640A06D2EF5A2125577\n" +
                "CLASS:PUBLIC\n" +
                "PRIORITY:5\n" +
                "DTSTAMP:20060208T180425Z\n" +
                "TRANSP:OPAQUE\n" +
                "STATUS:CONFIRMED\n" +
                "SEQUENCE:0\n" +
                "LOCATION;LANGUAGE=en-US:not sure\n" +
                "X-MICROSOFT-CDO-APPT-SEQUENCE:0\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:1602758614\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "X-MICROSOFT-CDO-INSTTYPE:0\n" +
                "X-MICROSOFT-DISALLOW-COUNTER:FALSE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(icsBody, true);
        System.out.println(toClient);
    }

    public void testInvalidTimezone() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//K Desktop Environment//NONSGML libkcal 4.3//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Europe/Amsterdam\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:NST\n" +
                "TZOFFSETFROM:+001932\n" +
                "TZOFFSETTO:+011932\n" +
                "DTSTART:19160501T234028\n" +
                "RDATE;VALUE=DATE-TIME:19160501T234028\n" +
                "RDATE;VALUE=DATE-TIME:19170417T014028\n" +
                "RDATE;VALUE=DATE-TIME:19180402T014028\n" +
                "RDATE;VALUE=DATE-TIME:19190408T014028\n" +
                "RDATE;VALUE=DATE-TIME:19200406T014028\n" +
                "RDATE;VALUE=DATE-TIME:19210405T014028\n" +
                "RDATE;VALUE=DATE-TIME:19220327T014028\n" +
                "RDATE;VALUE=DATE-TIME:19230602T014028\n" +
                "RDATE;VALUE=DATE-TIME:19240331T014028\n" +
                "RDATE;VALUE=DATE-TIME:19250606T014028\n" +
                "RDATE;VALUE=DATE-TIME:19260516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19270516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19280516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19290516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19300516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19310516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19320523T014028\n" +
                "RDATE;VALUE=DATE-TIME:19330516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19340516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19350516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19360516T014028\n" +
                "RDATE;VALUE=DATE-TIME:19370523T014028\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:AMT\n" +
                "TZOFFSETFROM:+011932\n" +
                "TZOFFSETTO:+001932\n" +
                "DTSTART:19161001T224028\n" +
                "RDATE;VALUE=DATE-TIME:19161001T224028\n" +
                "RDATE;VALUE=DATE-TIME:19170918T024028\n" +
                "RDATE;VALUE=DATE-TIME:19181001T024028\n" +
                "RDATE;VALUE=DATE-TIME:19190930T024028\n" +
                "RDATE;VALUE=DATE-TIME:19200928T024028\n" +
                "RDATE;VALUE=DATE-TIME:19210927T024028\n" +
                "RDATE;VALUE=DATE-TIME:19221009T024028\n" +
                "RDATE;VALUE=DATE-TIME:19231008T024028\n" +
                "RDATE;VALUE=DATE-TIME:19241006T024028\n" +
                "RDATE;VALUE=DATE-TIME:19251005T024028\n" +
                "RDATE;VALUE=DATE-TIME:19261004T024028\n" +
                "RDATE;VALUE=DATE-TIME:19271003T024028\n" +
                "RDATE;VALUE=DATE-TIME:19281008T024028\n" +
                "RDATE;VALUE=DATE-TIME:19291007T024028\n" +
                "RDATE;VALUE=DATE-TIME:19301006T024028\n" +
                "RDATE;VALUE=DATE-TIME:19311005T024028\n" +
                "RDATE;VALUE=DATE-TIME:19321003T024028\n" +
                "RDATE;VALUE=DATE-TIME:19331009T024028\n" +
                "RDATE;VALUE=DATE-TIME:19341008T024028\n" +
                "RDATE;VALUE=DATE-TIME:19351007T024028\n" +
                "RDATE;VALUE=DATE-TIME:19361005T024028\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:NEST\n" +
                "TZOFFSETFROM:+011932\n" +
                "TZOFFSETTO:+0120\n" +
                "DTSTART:19370701T224028\n" +
                "RDATE;VALUE=DATE-TIME:19370701T224028\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:NET\n" +
                "TZOFFSETFROM:+0120\n" +
                "TZOFFSETTO:+0020\n" +
                "DTSTART:19371004T024028\n" +
                "RDATE;VALUE=DATE-TIME:19371004T024028\n" +
                "RDATE;VALUE=DATE-TIME:19381003T024000\n" +
                "RDATE;VALUE=DATE-TIME:19391009T024000\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:NEST\n" +
                "TZOFFSETFROM:+0020\n" +
                "TZOFFSETTO:+0120\n" +
                "DTSTART:19380516T014000\n" +
                "RDATE;VALUE=DATE-TIME:19380516T014000\n" +
                "RDATE;VALUE=DATE-TIME:19390516T014000\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0020\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19400516T234000\n" +
                "RDATE;VALUE=DATE-TIME:19400516T234000\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19790930T030000\n" +
                "RRULE:FREQ=YEARLY;COUNT=17;BYDAY=-1SU;BYMONTH=9\n" +
                "END:STANDARD\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19961027T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19421103T024000\n" +
                "RDATE;VALUE=DATE-TIME:19421103T024000\n" +
                "RDATE;VALUE=DATE-TIME:19431004T020000\n" +
                "RDATE;VALUE=DATE-TIME:19441002T020000\n" +
                "RDATE;VALUE=DATE-TIME:19450916T020000\n" +
                "RDATE;VALUE=DATE-TIME:19770925T030000\n" +
                "RDATE;VALUE=DATE-TIME:19781001T030000\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19810329T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19430329T010000\n" +
                "RDATE;VALUE=DATE-TIME:19430329T010000\n" +
                "RDATE;VALUE=DATE-TIME:19440403T010000\n" +
                "RDATE;VALUE=DATE-TIME:19450402T010000\n" +
                "RDATE;VALUE=DATE-TIME:19770403T020000\n" +
                "RDATE;VALUE=DATE-TIME:19780402T020000\n" +
                "RDATE;VALUE=DATE-TIME:19790401T020000\n" +
                "RDATE;VALUE=DATE-TIME:19800406T020000\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20111022T175835Z\n" +
                "CREATED:20111022T175832Z\n" +
                "UID:libkcal-797112054.882\n" +
                "LAST-MODIFIED:20111022T175832Z\n" +
                "SUMMARY:Test Event 000\n" +
                "DTSTART;TZID=\"Europe/Amsterdam\":20111027T120000\n" +
                "DTEND;TZID=\"Europe/Amsterdam\":20111027T174500\n" +
                "TRANSP:OPAQUE\n" +
                "X-MICROSOFT-CDO-REPLYTIME:20111022T175835Z\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toServer = fixICS(icsBody, false);
        System.out.println(toServer);
    }

    public void testResourceComma() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Microsoft Corporation//Outlook 14.0 MIMEDIR//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:REQUEST\n" +
                "X-MS-OLK-FORCEINSPECTOROPEN:TRUE\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Eastern Standard Time\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16011104T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=11\n" +
                "TZOFFSETFROM:-0400\n" +
                "TZOFFSETTO:-0500\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010311T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=2SU;BYMONTH=3\n" +
                "TZOFFSETFROM:-0500\n" +
                "TZOFFSETTO:-0400\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "ATTENDEE;CN=Robert.P.Lindman@delphi.com;RSVP=TRUE:mailto:Robert.P.Lindman@d\n" +
                "\telphi.com\n" +
                "ATTENDEE;CN=\"CRUSINKOK, CTC4B\";CUTYPE=RESOURCE;ROLE=NON-PARTICIPANT;RSVP=TR\n" +
                "\tUE:mailto:ctc4b.crusinkok@delphi.com\n" +
                "CLASS:PUBLIC\n" +
                "CREATED:20111020T134050Z\n" +
                "DESCRIPTION:Sample meeting with a conference room added\\n\n" +
                "DTEND;TZID=\"Eastern Standard Time\":20111021T060000\n" +
                "DTSTAMP:20111020T134035Z\n" +
                "DTSTART;TZID=\"Eastern Standard Time\":20111021T053000\n" +
                "LAST-MODIFIED:20111020T134050Z\n" +
                "LOCATION:CRUSINKOK\\, CTC4B\n" +
                "ORGANIZER;CN=\"Lindman, Robert P\":mailto:Robert.P.Lindman@delphi.com\n" +
                "PRIORITY:5\n" +
                "RESOURCES:CRUSINKOK\\, CTC4A,CRUSINKOK\\, CTC4C,CRUSINKOK\\, CTC4D,CRUSINKOK\\,\n" +
                "\t CTC4E,CRUSINKOK\\, CTC3A,CRUSINKOK\\, CTC3B,CRUSINKOK\\, CTC3C,CRUSINKOK\\, C\n" +
                "\tTC3D,CRUSINKOK\\, CTC2A,CRUSINKOK\\, CTC2B,CRUSINKOK\\, CTC2C,CRUSINKOK\\, CTC\n" +
                "\t2D,CRUSINKOK\\, CTC1A,CRUSINKOK\\, CTC1B,CRUSINKOK\\, CTC1C,CRUSINKOK\\, CTC1D\n" +
                "\t,CRUSINKOK\\, CTC1E1\n" +
                "SEQUENCE:1\n" +
                "SUMMARY;LANGUAGE=en-us:Sample Meeting\n" +
                "TRANSP:OPAQUE\n" +
                "UID:040000008200E00074C5B7101A82E0080000000090B4D422078FCC01000000000000000\n" +
                "\t0100000000AFB9CCA2DE4D54794C2D688292D570B\n" +
                "X-ALT-DESC;FMTTYPE=text/html:<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2//E\n" +
                "\tN\">\\n<HTML>\\n<HEAD>\\n<META NAME=\"Generator\" CONTENT=\"MS Exchange Server ve\n" +
                "\trsion 08.01.0240.003\">\\n<TITLE></TITLE>\\n</HEAD>\\n<BODY>\\n<!-- Converted f\n" +
                "\trom text/rtf format -->\\n\\n<P DIR=LTR><SPAN LANG=\"en-us\"><FONT FACE=\"Calib\n" +
                "\tri\">Sample meeting with a conference room added</FONT></SPAN><SPAN LANG=\"e\n" +
                "\tn-us\"></SPAN></P>\\n\\n</BODY>\\n</HTML>\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "X-MICROSOFT-DISALLOW-COUNTER:FALSE\n" +
                "X-MS-OLK-APPTLASTSEQUENCE:1\n" +
                "X-MS-OLK-APPTSEQTIME:20111020T134035Z\n" +
                "X-MS-OLK-AUTOFILLLOCATION:TRUE\n" +
                "X-MS-OLK-CONFTYPE:0\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(icsBody, true);
        System.out.println(toClient);
    }
}

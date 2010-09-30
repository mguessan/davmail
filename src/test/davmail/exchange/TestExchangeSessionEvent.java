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
                "METHOD:REQUEST\n" +
                "PRODID:Microsoft CDO for Microsoft Exchange\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:GMT -0500 (Standard) / GMT -0400 (Daylight)\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0400\n" +
                "TZOFFSETTO:-0500\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=11;BYDAY=1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0500\n" +
                "TZOFFSETTO:-0400\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=3;BYDAY=2SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20091109T160328Z\n" +
                "DTSTART;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20090831T140000\n" +
                "SUMMARY:Canceled: LIMS on Demand Check In\n" +
                "UID:040000008200E00074C5B7101A82E0080000000040FBD1416E21CA01000000000000000\n" +
                " 010000000E6ECF22DE22C3141B0F14F2A61B150AD\n" +
                "ORGANIZER;CN=\"Najjar, Susan M.\":MAILTO:susan.najjar@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Shah, Kim\n" +
                " \":MAILTO:kim.shah@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Mac Conao\n" +
                " naigh, Seamus\":MAILTO:seamus.macconaonaigh@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Holbrook,\n" +
                "  Doug\":MAILTO:doug.holbrook@thermo.com\n" +
                "LOCATION:USBIL-Water\n" +
                "DTEND;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20090831T150000\n" +
                "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;WKST=SU\n" +
                "EXDATE;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20091026T140000\n" +
                "DESCRIPTION:When: Occurs every 2 weeks on Monday effective 8/31/2009 from 2\n" +
                " :00 PM to 3:00 PM (GMT-05:00) Eastern Time (US & Canada).\\NWhere: USBIL-Wa\n" +
                " ter\\N\\NNote: The GMT offset above does not reflect daylight saving time ad\n" +
                " justments.\\N\\N*~*~*~*~*~*~*~*~*~*\\N\\NAll\\,\\N\\NLet’s do a check in every \n" +
                " two weeks to talk about LIMS on Demand.  Please feel free to add to the ag\n" +
                " enda.  I scheduled an hour but don’t anticipate it to go that long unles\n" +
                " s we have something to review in detail.\\N\\NProposed Agenda\\N-\tBeta update\n" +
                " \\N-\tAutodemo review\\N-\tIT whitepaper update\\N-\tCloud picture update\\N\t\t\\N\t\n" +
                " \t   1 205 263 0808\\N\t\t   1 877 897 0005\\N\t\t   \\N\t\t   \\N2.\tIf prompted\\, en\n" +
                " ter the Meeting Number: * 3843152 *  \\N\t\t(Be sure to enter the * star key \n" +
                " before and after the Meeting Number)\\N\\N3.\tIf you are the Moderator\\, ente\n" +
                " r your *PIN*  \\N\t\t(Be sure to enter the * star key before and after your P\n" +
                " IN)\\N\\N24x7 Technical Support <http://www.genesys.com/support/index.html> \n" +
                " \\N\\N\\N\\N\\N\\N\\N\\N\n" +
                "SEQUENCE:2\n" +
                "PRIORITY:1\n" +
                "CLASS:\n" +
                "CREATED:20090820T121655Z\n" +
                "LAST-MODIFIED:20100701T143032Z\n" +
                "STATUS:CANCELLED\n" +
                "TRANSP:OPAQUE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:FREE\n" +
                "X-MICROSOFT-CDO-INSTTYPE:1\n" +
                "X-MICROSOFT-CDO-REPLYTIME:16010101T000000Z\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:2\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:-1508603943\n" +
                "X-MICROSOFT-CDO-APPT-SEQUENCE:2\n" +
                "X-MICROSOFT-CDO-ATTENDEE-CRITICAL-CHANGE:20091109T160328Z\n" +
                "X-MICROSOFT-CDO-OWNER-CRITICAL-CHANGE:20091109T160328Z\n" +
                "BEGIN:VALARM\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:REMINDER\n" +
                "TRIGGER;RELATED=START:-PT00H15M00S\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "X-MICROSOFT-CDO-MODPROPS:attendee,BEGIN,class,description,dtend,dtstamp,dts\n" +
                " tart,END,organizer,priority,recurrence-id,status,x-microsoft-cdo-attendee-\n" +
                " critical-change,x-microsoft-cdo-busystatus,x-microsoft-cdo-importance,x-mi\n" +
                " crosoft-cdo-insttype,x-microsoft-cdo-owner-critical-change,x-microsoft-cdo\n" +
                " -ownerapptid\n" +
                "DTSTAMP:20091109T160337Z\n" +
                "DTSTART;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20091109T140000\n" +
                "SUMMARY:Canceled: LIMS on Demand Check In\n" +
                "UID:040000008200E00074C5B7101A82E0080000000040FBD1416E21CA01000000000000000\n" +
                " 010000000E6ECF22DE22C3141B0F14F2A61B150AD\n" +
                "ORGANIZER;CN=\"Najjar, Susan M.\":MAILTO:susan.najjar@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Shah, Kim\n" +
                " \":MAILTO:kim.shah@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Mac Conao\n" +
                " naigh, Seamus\":MAILTO:seamus.macconaonaigh@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Holbrook,\n" +
                "  Doug\":MAILTO:doug.holbrook@thermo.com\n" +
                "LOCATION:USBIL-Water\n" +
                "DTEND;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20091109T150000\n" +
                "DESCRIPTION:When: Monday\\, November 09\\, 2009 2:00 PM-3:00 PM (GMT-05:00) E\n" +
                " astern Time (US & Canada).\\NWhere: USBIL-Water\\N\\NNote: The GMT offset abo\n" +
                " ve does not reflect daylight saving time adjustments.\\N\\N*~*~*~*~*~*~*~*~*\n" +
                " ~*\\N\\NAll\\,\\N\\NLet’s do a check in every two weeks to talk about LIMS on\n" +
                "  Demand.  Please feel free to add to the agenda.  I scheduled an hour but \n" +
                " don’t anticipate it to go that long unless we have something to review i\n" +
                " n detail.\\N\\NProposed Agenda\\N-\tBeta update\\N-\tAutodemo review\\N-\tIT white\n" +
                " paper update\\N-\tCloud picture update\\N\t\t\\N\t\t   1 205 263 0808\\N\t\t   1 877 \n" +
                " 897 0005\\N\t\t   \\N\t\t   \\N2.\tIf prompted\\, enter the Meeting Number: * 38431\n" +
                " 52 *  \\N\t\t(Be sure to enter the * star key before and after the Meeting Nu\n" +
                " mber)\\N\\N3.\tIf you are the Moderator\\, enter your *PIN*  \\N\t\t(Be sure to e\n" +
                " nter the * star key before and after your PIN)\\N\\N24x7 Technical Support <\n" +
                " http://www.genesys.com/support/index.html> \\N\\N\\N\\N\\N\\N\\N\\N\n" +
                "RECURRENCE-ID;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20091109T1\n" +
                " 40000\n" +
                "SEQUENCE:2\n" +
                "PRIORITY:1\n" +
                "CLASS:\n" +
                "CREATED:20090820T121655Z\n" +
                "LAST-MODIFIED:20100701T143032Z\n" +
                "STATUS:CANCELLED\n" +
                "TRANSP:OPAQUE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:FREE\n" +
                "X-MICROSOFT-CDO-INSTTYPE:3\n" +
                "X-MICROSOFT-CDO-REPLYTIME:16010101T000000Z\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:2\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:-1508603943\n" +
                "X-MICROSOFT-CDO-APPT-SEQUENCE:2\n" +
                "X-MICROSOFT-CDO-ATTENDEE-CRITICAL-CHANGE:20091109T160336Z\n" +
                "X-MICROSOFT-CDO-OWNER-CRITICAL-CHANGE:20091109T160337Z\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "X-MICROSOFT-CDO-MODPROPS:BEGIN,dtend,dtstamp,dtstart,END,recurrence-id,x-mi\n" +
                " crosoft-cdo-insttype\n" +
                "DTSTAMP:20091109T160328Z\n" +
                "DTSTART;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20090831T140000\n" +
                "SUMMARY:Canceled: LIMS on Demand Check In\n" +
                "UID:040000008200E00074C5B7101A82E0080000000040FBD1416E21CA01000000000000000\n" +
                " 010000000E6ECF22DE22C3141B0F14F2A61B150AD\n" +
                "ORGANIZER;CN=\"Najjar, Susan M.\":MAILTO:susan.najjar@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Shah, Kim\n" +
                " \":MAILTO:kim.shah@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Mac Conao\n" +
                " naigh, Seamus\":MAILTO:seamus.macconaonaigh@thermo.com\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"Holbrook,\n" +
                "  Doug\":MAILTO:doug.holbrook@thermo.com\n" +
                "LOCATION:USBIL-Water\n" +
                "DTEND;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20090831T150000\n" +
                "DESCRIPTION:When: Occurs every 2 weeks on Monday effective 8/31/2009 from 2\n" +
                " :00 PM to 3:00 PM (GMT-05:00) Eastern Time (US & Canada).\\NWhere: USBIL-Wa\n" +
                " ter\\N\\NNote: The GMT offset above does not reflect daylight saving time ad\n" +
                " justments.\\N\\N*~*~*~*~*~*~*~*~*~*\\N\\NAll\\,\\N\\NLet’s do a check in every \n" +
                " two weeks to talk about LIMS on Demand.  Please feel free to add to the ag\n" +
                " enda.  I scheduled an hour but don’t anticipate it to go that long unles\n" +
                " s we have something to review in detail.\\N\\NProposed Agenda\\N-\tBeta update\n" +
                " \\N-\tAutodemo review\\N-\tIT whitepaper update\\N-\tCloud picture update\\N\t\t\\N\t\n" +
                " \t   1 205 263 0808\\N\t\t   1 877 897 0005\\N\t\t   \\N\t\t   \\N2.\tIf prompted\\, en\n" +
                " ter the Meeting Number: * 3843152 *  \\N\t\t(Be sure to enter the * star key \n" +
                " before and after the Meeting Number)\\N\\N3.\tIf you are the Moderator\\, ente\n" +
                " r your *PIN*  \\N\t\t(Be sure to enter the * star key before and after your P\n" +
                " IN)\\N\\N24x7 Technical Support <http://www.genesys.com/support/index.html> \n" +
                " \\N\\N\\N\\N\\N\\N\\N\\N\n" +
                "RECURRENCE-ID;TZID=\"GMT -0500 (Standard) / GMT -0400 (Daylight)\":20090831T1\n" +
                " 40000\n" +
                "SEQUENCE:2\n" +
                "PRIORITY:1\n" +
                "CLASS:\n" +
                "CREATED:20090820T121655Z\n" +
                "LAST-MODIFIED:20100701T143032Z\n" +
                "STATUS:CANCELLED\n" +
                "TRANSP:OPAQUE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:FREE\n" +
                "X-MICROSOFT-CDO-INSTTYPE:3\n" +
                "X-MICROSOFT-CDO-REPLYTIME:16010101T000000Z\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:2\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:-1508603943\n" +
                "X-MICROSOFT-CDO-APPT-SEQUENCE:2\n" +
                "X-MICROSOFT-CDO-ATTENDEE-CRITICAL-CHANGE:20091109T160328Z\n" +
                "X-MICROSOFT-CDO-OWNER-CRITICAL-CHANGE:20091109T160328Z\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String toClient = fixICS(itemBody, true);
        System.out.println(toClient);
        assertTrue(toClient.contains("DTSTART;TZID="));
        assertTrue(toClient.contains("DTEND;TZID="));
    }

    public void testAnotherBroken() throws IOException {
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

}

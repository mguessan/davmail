package davmail.exchange;

import junit.framework.TestCase;

import java.io.StringReader;
import java.io.IOException;

/**
 * Test ICSBufferedReader
 */
public class TestICSBufferedReader extends TestCase {
    public void testSimpleRead() throws IOException {
        String value = "test\nmultiline\nstring";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        assertEquals("test", reader.readLine());
        assertEquals("multiline", reader.readLine());
        assertEquals("string", reader.readLine());
        assertNull(reader.readLine());
    }

    public void testContinuationRead() throws IOException {
        String value = "test\nmultiline\n string";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        assertEquals("test", reader.readLine());
        assertEquals("multilinestring", reader.readLine());
        assertNull(reader.readLine());
    }

    public void testEventWithEmptyLine() throws IOException {
        String value = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "METHOD:REQUEST\n" +
                "PRODID:Microsoft CDO for Microsoft Exchange\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Africa/Lagos\n" +
                "X-MICROSOFT-CDO-TZID:69\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T000000\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0100\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T000000\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0100\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART;TZID=\"Africa/Lagos\":20070326T070000\n" +
                "DTEND;TZID=\"Africa/Lagos\":20070326T083000\n" +
                "DTSTAMP:20070217T231150Z\n" +
                "SUMMARY:My meeting\n" +
                "CATEGORIES:Groupcal,iCal:user\n" +
                "UID:com.apple.syncservices:5C1BCD60-8C8E-4FCE-B2CA-C99DE0BE81EB\n" +
                "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,FR\n" +
                "ORGANIZER:MAILTO:user@domain\n" +
                "\n" +
                "X-GROUPCAL-ALARM:PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPCFET0\n" +
                "NUWVBFIHBsaXN0IFBVQkxJQyAiLS8vQXBwbGUgQ29tcHV0ZXIvL0RURCBQTElTVCAxLjAvL0VOI\n" +
                "iAiaHR0cDovL3d3dy5hcHBsZS5jb20vRFREcy9Qcm9wZXJ0eUxpc3QtMS4wLmR0ZCI+CjxwbGlz\n" +
                "dCB2ZXJzaW9uPSIxLjAiPgo8YXJyYXkvPgo8L3BsaXN0Pgo=\n" +
                "CLASS:\n" +
                "STATUS:TENTATIVE\n" +
                "TRANSP:OPAQUE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:OOF\n" +
                "X-MICROSOFT-CDO-INSTTYPE:1\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:-1\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        String line;
        String lastLine = null;
        while ((line =reader.readLine())!= null) {
            lastLine = line;
        }
        assertEquals("END:VCALENDAR", lastLine);
    }

    public void testBrokenAttendee() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "ATTENDEE;CN=\"Daniel " +
                "William Doe\";PARTSTAT=ACCEPTED;RSVP=TRUE:MAILTO:email@company.com\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        VObject vcalendar = new VCalendar(new ICSBufferedReader(new StringReader(itemBody)), "email@company.com", null);
        System.out.println(vcalendar);
    }

    public void testBrokenTask() throws IOException {
        String value = "BEGIN:VCALENDAR\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Central Standard Time\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0500\n" +
                "TZOFFSETTO:-0600\n" +
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=1SU;BYMONTH=11\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0600\n" +
                "TZOFFSETTO:-0500\n" +
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=2SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VTODO\n" +
                "LAST-MODIFIED:20110606T080802Z\n" +
                "CREATED:20110527T085302Z\n" +
                "UID:AAMkADQwOGRjMjIyLTQwNDUtNDE5OS05YWExLWZlOTM5Yjc2NTg0YgBGAAAAAAAi3Ph1JgynT\n" +
                " ILoGH8BTtfjBwAzPlOmuBONTIJTcNQH4CUkAAAAAACEAABeoDOEjEPERLNIwtCsV4KdAAABv75hA\n" +
                " AA=\n" +
                "SUMMARY:Get everyone view the videos\n" +
                "DESCRIPTION:They are on local dev server. Update on-boarding correspondingly\n" +
                " \\n\n" +
                "PERCENT-COMPLETE:100\n" +
                "STATUS:COMPLETED\n" +
                "DUE;VALUE=DATE:20110527\n" +
                "DTSTART;VALUE=DATE:20110527\n" +
                "COMPLETED;VALUE=DATE:20110605\n" +
                "END:VTODO\n" +
                "END:VCALENDAR";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        String line;
        String lastLine = null;
        while ((line =reader.readLine())!= null) {
            System.out.println(line);
            lastLine = line;
        }
        assertEquals("END:VCALENDAR", lastLine);
        new VCalendar(value, null, null);
    }

    public void testVCard() throws IOException {
        String itemBody = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "PRODID:-//Inverse inc.//SOGo Connector 1.0//EN\n" +
                "UID:C54E78FE-98B0-0001-2339-1D761540DA50\n" +
                "N:bb;aa\n" +
                "FN:aa bb\n" +
                "X-MOZILLA-HTML:FALSE\n" +
                "REV:20120713T130308Z\n" +
                "END:VCARD\n" +
                "\n";
        VObject vcard = new VObject(new ICSBufferedReader(new StringReader(itemBody)));
    }
}

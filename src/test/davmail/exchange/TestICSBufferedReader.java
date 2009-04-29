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
}

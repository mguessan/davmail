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
import davmail.exchange.ews.EwsExchangeSession;
import davmail.exchange.graph.GraphExchangeSession;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Test Exchange session calendar features.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionCalendar extends AbstractExchangeSessionTestCase {

    public void testGetVtimezone() {
        VObject timezone = session.getVTimezone();
        assertNotNull(timezone);
        assertNotNull(timezone.getPropertyValue("TZID"));
    }


    public void testSearchCalendar() throws IOException {
        String folderPath = "/users/" + session.getEmail() + "/calendar";
        Settings.setProperty("davmail.caldavPastDelay", "30");
        List<ExchangeSession.Event> events;
        try {
            events = session.getAllEvents(folderPath);
            assertNotNull(events);
            for (ExchangeSession.Event event : events) {
                // need per event request to retrieve full body
                ExchangeSession.Item item = session.getItem(folderPath, event.getName());
                System.out.println("retrieved "+event.getName());
                System.out.println(item.getBody());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public void testReportCalendar() throws IOException {
        List<ExchangeSession.Event> events;
        try {
            events = session.getAllEvents("/users/" + session.getEmail() + "/calendar");
            for (ExchangeSession.Event event : events) {
                System.out.println(event.subject);
                ExchangeSession.Item item = session.getItem("/users/" + session.getEmail() + "/calendar", event.itemName);
                System.out.println(item.getBody());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public void testGetFreeBusyData() throws IOException {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.MONTH, 7);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date endDate = cal.getTime();
        SimpleDateFormat formatter = ExchangeSession.getExchangeZuluDateFormat();
        // personal fbdata
        String fbdata = session.getFreeBusyData(session.getEmail(), formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNotNull(fbdata);
        // other user data
        fbdata = session.getFreeBusyData(Settings.getProperty("davmail.to"), formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNotNull(fbdata);
        // unknown user data
        fbdata = session.getFreeBusyData("unknown@company.org", formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNull(fbdata);
    }

    public void testCreateEvent() throws IOException {
        String iCaluid = UUID.randomUUID().toString();
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:W. Europe Standard Time\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T030000\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20120611T113748Z\n" +
                "LAST-MODIFIED:20120611T113823Z\n" +
                "DTSTAMP:20120611T113823Z\n" +
                "UID:"+iCaluid +"\n"+
                "DTSTART;TZID=W. Europe Standard Time:20120926T100000\n" +
                "DTEND;TZID=W. Europe Standard Time:20120926T120000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        String itemName = UUID.randomUUID() + ".EML";
        ExchangeSession.ItemResult item = session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
        assertNotNull(item);
        assertNotNull(item.itemName);

        ExchangeSession.Item createdItem = session.getItem("calendar", item.itemName);
        assertNotNull(createdItem);

        VCalendar vCalendar = new VCalendar(createdItem.getBody(), session.getEmail(), session.getVTimezone());

        assertEquals(iCaluid, vCalendar.getFirstVeventPropertyValue("UID"));

        session.deleteItem("calendar", itemName);
    }

    public void testGetInbox() throws IOException {
        List<ExchangeSession.Event> items = session.getEventMessages("INBOX");
        for (ExchangeSession.Item item : items) {
            System.out.println(item.getBody());
        }
    }

    public void testSearchEventCount() throws IOException {
        Set<String> properties = session.getItemProperties();
        properties.add("recurringappointment");
        properties.add("isrecurring");
        properties.add("recurrencestart");
        properties.add("recurrencetype");
        //Settings.setLoggingLevel("davmail", Level.WARN);
        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        /*System.out.println("Item count: " + session.searchEvents("calendar", properties, null).size());
        //Settings.setLoggingLevel("httpclient.wire", Level.INFO);
        System.out.println("InstanceType null: " + session.searchEvents("calendar", session.isNull("instancetype")).size());
        //System.out.println("InstanceType not null: " + session.searchEvents("calendar", session.not(session.isNull("instancetype"))).size());
        System.out.println("InstanceType 0: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 0)).size());
        System.out.println("InstanceType 1: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 1)).size());
        System.out.println("InstanceType 2: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 2)).size());
        System.out.println("InstanceType 3: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 3)).size());
*/
        if (session instanceof GraphExchangeSession) {
            System.out.println("Recurring: " + session.searchEvents("calendar", session.isTrue("isrecurring")).size());
            System.out.println("Non recurring: " + session.searchEvents("calendar", session.isFalse("isrecurring")).size());
            System.out.println("Null recurring: " + session.searchEvents("calendar", session.isNull("isrecurring")).size());

        }
        if (session instanceof EwsExchangeSession) {
            System.out.println("Recurring: " + session.searchEvents("calendar", session.isTrue("isrecurring")).size());
            System.out.println("Non recurring: " + session.searchEvents("calendar", session.isFalse("isrecurring")).size());
            System.out.println("Null recurring: " + session.searchEvents("calendar", session.isNull("isrecurring")).size());

            System.out.println("recurringappointment master: " + session.searchEvents("calendar", session.exists("recurringappointment")).size());
            System.out.println("recurrencestart master: " + session.searchEvents("calendar", session.exists("recurrencestart")).size());

            //System.out.println("recurring master: " + session.searchEvents("calendar", session.isTrue("recurring")).size());
            System.out.println("recurrencetype 2: " + session.searchEvents("calendar", session.isEqualTo("recurrencetype", 2)).size());
            System.out.println("recurrencetype 0: " + session.searchEvents("calendar", session.isEqualTo("recurrencetype", 0)).size());
        }

    }


    public void testCreateEventTZ() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//iCal4OL2.11.20\n" +
                "VERSION:2.0\n" +
                "X-WR-TIMEZONE:Europe/Berlin\n" +
                "CALSCALE:GREGORIAN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Europe/Berlin\n" +
                "X-LIC-LOCATION:Europe/Berlin\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:20100328T010000\n" +
                "TZOFFSETTO:+0200\n" +
                "TZOFFSETFROM:+0100\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:20101031T030000\n" +
                "TZOFFSETTO:+0100\n" +
                "TZOFFSETFROM:+0200\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20111205T102048Z\n" +
                "SUMMARY:Roland Test\n" +
                "DESCRIPTION:\n" +
                "CLASS:PUBLIC\n" +
                "DTSTART;TZID=Europe/Berlin:20120205T113000\n" +
                "DTEND;TZID=Europe/Berlin:20120205T120000\n" +
                "DTSTAMP:20111205T102305Z\n" +
                "TRANSP:OPAQUE\n" +
                "STATUS:CONFIRMED\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String itemName = UUID.randomUUID() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);

        session.deleteItem("calendar", itemName);
    }
    
    public void testCreateEventBrokenTZ() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "PRODID:-//Ximian//NONSGML Evolution Calendar//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Asia/Jerusalem\n" +
                "X-LIC-LOCATION:Asia/Jerusalem\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:19700923T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-2SU;BYMONTH=9\n" +
                "TZOFFSETFROM:+0300\n" +
                "TZOFFSETTO:+0200\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:19700330T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1FR;BYMONTH=3\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0300\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20120920T061713Z\n" +
                "DTSTART;TZID=\"Asia/Jerusalem\":2012092\n" +
                " 0T093000\n" +
                "DTEND;TZID=\"Asia/Jerusalem\":20120920T\n" +
                " 103000\n" +
                "TRANSP:OPAQUE\n" +
                "SEQUENCE:3\n" +
                "SUMMARY:test\n" +
                "CLASS:PUBLIC\n" +
                "DESCRIPTION:tEin Test!\n" +
                "CREATED:20120920T062017Z\n" +
                "LAST-MODIFIED:20120920T062017Z\n" +
                "ORGANIZER:MAILTO:shai.berger@healarium.com\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String itemName = UUID.randomUUID() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);

        session.deleteItem("calendar", itemName);
    }

    public void testCreateEventDuplicateTZ() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//K Desktop Environment//NONSGML libkcal 4.3//EN\n" +
                "VERSION:2.0\n" +
                "X-KDE-ICAL-IMPLEMENTATION-VERSION:1.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Central Europe Standard Time\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0000\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19800405T230000\n" +
                "RDATE:19800405T230000\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19810927T030000\n" +
                "RRULE:FREQ=YEARLY;UNTIL=19961027T030000;COUNT=15;BYDAY=-1SU;BYMONTH=9\n" +
                "END:STANDARD\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19971026T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19800928T000000\n" +
                "RDATE:19800928T000000\n" +
                "RDATE:19950924T030000\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19810329T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Central Europe Standard Time\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19971026T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19810329T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20180726T130457Z\n" +
                "CREATED:20180726T130457Z\n" +
                "LAST-MODIFIED:20180726T130457Z\n" +
                "SUMMARY:tesssssss\n" +
                "DTSTART;TZID=Central Europe Standard Time:20180726T161500\n" +
                "DTEND;TZID=Central Europe Standard Time:20180726T173000\n" +
                "TRANSP:OPAQUE\n" +
                "ORGANIZER:MAILTO:P20315@xxx.yyy.zz\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "BEGIN:VALARM\n" +
                "DESCRIPTION:\n" +
                "ACTION:DISPLAY\n" +
                "TRIGGER:-PT15M\n" +
                "X-KDE-KCALCORE-ENABLED:TRUE\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        String itemName = UUID.randomUUID() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
    }

    public void testCreateEventInvalidRRule() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//K Desktop Environment//NONSGML libkcal 4.3//EN\n" +
                "VERSION:2.0\n" +
                "X-KDE-ICAL-IMPLEMENTATION-VERSION:1.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Central Europe Standard Time\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZNAME:CEST\n" +
                "TZOFFSETFROM:+0000\n" +
                "TZOFFSETTO:+0200\n" +
                "DTSTART:19800405T230000\n" +
                "RDATE:19800405T230000\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:CET\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "DTSTART:19810927T030000\n" +
                "RRULE:FREQ=YEARLY;UNTIL=19961027T030000;COUNT=15;BYDAY=-1SU;BYMONTH=9\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20180726T130457Z\n" +
                "CREATED:20180726T130457Z\n" +
                "LAST-MODIFIED:20180726T130457Z\n" +
                "SUMMARY:tesssssss\n" +
                "DTSTART;TZID=Central Europe Standard Time:20180726T161500\n" +
                "DTEND;TZID=Central Europe Standard Time:20180726T173000\n" +
                "TRANSP:OPAQUE\n" +
                "ORGANIZER:MAILTO:P20315@xxx.yyy.zz\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "BEGIN:VALARM\n" +
                "DESCRIPTION:\n" +
                "ACTION:DISPLAY\n" +
                "TRIGGER:-PT15M\n" +
                "X-KDE-KCALCORE-ENABLED:TRUE\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        String itemName = UUID.randomUUID() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);

        session.deleteItem("calendar", itemName);
    }

    public void testMissingTimeZone() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Missing timezone id\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T030000\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20120611T113748Z\n" +
                "LAST-MODIFIED:20120611T113823Z\n" +
                "DTSTAMP:20120611T113823Z\n" +
                "UID:040000008200E00074C5B7101A82E0080000000020EA852CF458CC0100000000000000001\n" +
                " 000000011278A1693B8494C8592446E6E249BCF\n" +
                "DTSTART;TZID=Missing timezone id:20120926T100000\n" +
                "DTEND;TZID=Missing timezone id:20120926T120000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        VCalendar vCalendar = new VCalendar(itemBody, session.getEmail(), session.getVTimezone());
        vCalendar.convertCalendarDateToExchangeZulu("20120926T100000", "Missing timezone id");
    }


    public void testSearchTasks() throws IOException {
        List<ExchangeSession.Event> events;
        try {
            events = session.searchTasksOnly("/users/" + session.getEmail() + "/tasks");
            for (ExchangeSession.Event event : events) {
                System.out.println(event.getBody());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public void testInvalidRrule() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Romance Standard Time\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "TZNAME:CEST\n" +
                "DTSTART:19700329T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "TZNAME:CET\n" +
                "DTSTART:19701025T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20190109T121039Z\n" +
                "DTSTAMP:20190109T121039Z\n" +
                "SUMMARY:test rrule\n" +
                "PRIORITY:5\n" +
                "STATUS:CONFIRMED\n" +
                "DTSTART;TZID=Romance Standard Time:20190126T140000\n" +
                "DTEND;TZID=Romance Standard Time:20190126T150000\n" +
                "CLASS:PUBLIC\n" +
                "TRANSP:OPAQUE\n" +
                "SEQUENCE:1\n" +
                "X-MICROSOFT-CDO-APPT-SEQUENCE:0\n" +
                "X-MICROSOFT-CDO-OWNERAPPTID:2117160174\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "X-MICROSOFT-CDO-INSTTYPE:0\n" +
                "X-MICROSOFT-DISALLOW-COUNTER:FALSE\n" +
                "X-MOZ-GENERATION:1\n" +
                "ORGANIZER:MAILTO:"+session.getEmail()+"\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String itemName = UUID.randomUUID() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
        VCalendar vCalendar = new VCalendar(itemBody, session.getEmail(), session.getVTimezone());
        vCalendar.getFirstVevent().setPropertyValue("RRULE","FREQ=MONTHLY");
        session.createOrUpdateItem("calendar", itemName, vCalendar.toString(), null, null);

        session.deleteItem("calendar", itemName);
    }

    public void testCreateZuluEvent() throws IOException {
        String iCaluid = UUID.randomUUID().toString();
        String itemBody = "BEGIN:VCALENDAR\n" +
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20260721T100000Z\n" +
                "LAST-MODIFIED:20260721T100000Z\n" +
                "DTSTAMP:20260721T100000Z\n" +
                "UID:"+iCaluid +"\n"+
                "DTSTART:20260721T100000Z\n" +
                "DTEND:20260721T110000Z\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        String itemName = UUID.randomUUID() + ".EML";
        ExchangeSession.ItemResult item = session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
        assertNotNull(item);
        assertNotNull(item.itemName);

        ExchangeSession.Item createdItem = session.getItem("calendar", item.itemName);
        assertNotNull(createdItem);

        VCalendar vCalendar = new VCalendar(createdItem.getBody(), session.getEmail(), session.getVTimezone());

        assertEquals(iCaluid, vCalendar.getFirstVeventPropertyValue("UID"));

        // update with original content
        item = session.createOrUpdateItem("calendar", item.itemName, itemBody, null, null);
        System.out.println(item.status);

        session.deleteItem("calendar", itemName);
    }


}


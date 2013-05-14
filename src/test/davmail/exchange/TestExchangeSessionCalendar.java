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
import davmail.exchange.ews.FolderQueryTraversal;
import org.apache.log4j.Level;

import javax.mail.MessagingException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Test Exchange session calendar features .
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionCalendar extends AbstractExchangeSessionTestCase {

    public void testGetVtimezone() {
        VObject timezone = session.getVTimezone();
        assertNotNull(timezone);
        assertNotNull(timezone.getPropertyValue("TZID"));
    }

    public void testDumpVtimezones() throws IOException {
        Properties properties = new Properties() {
            @Override
            public synchronized Enumeration<Object> keys() {
                Enumeration keysEnumeration = super.keys();
                TreeSet<String> sortedKeySet = new TreeSet<String>();
                while (keysEnumeration.hasMoreElements()) {
                    sortedKeySet.add((String) keysEnumeration.nextElement());
                }
                final Iterator<String> sortedKeysIterator = sortedKeySet.iterator();
                return new Enumeration<Object>() {

                    public boolean hasMoreElements() {
                        return sortedKeysIterator.hasNext();
                    }

                    public Object nextElement() {
                        return sortedKeysIterator.next();
                    }
                };
            }

        };
        @SuppressWarnings("Since15") Set<String> tzReference = ResourceBundle.getBundle("tzreference").keySet();
        Set<String> timezoneids = ResourceBundle.getBundle("timezoneids").keySet();
        Map<String,String> timezoneIndexToIdMap = new HashMap<String,String>();
        for (String timezoneid:timezoneids) {
            timezoneIndexToIdMap.put(ResourceBundle.getBundle("timezoneids").getString(timezoneid), timezoneid);
        }
        for (int i = 1; i < 120; i++) {
            Settings.setProperty("davmail.timezoneId", String.valueOf(i));
            VObject timezone = session.getVTimezone();
            if (timezone != null && timezone.getProperty("TZID") != null) {
                String value = timezone.getPropertyValue("TZID").replaceAll("\\\\", "");
                properties.put(value, String.valueOf(i));
                if (timezoneIndexToIdMap.get(String.valueOf(i)) != null) {
                //properties.put(timezoneIndexToIdMap.get(String.valueOf(i)), ResourceBundle.getBundle("timezones").getString(value));
                    System.out.println(timezoneIndexToIdMap.get(String.valueOf(i)).replaceAll(" ", "\\\\ ") + '=' + ResourceBundle.getBundle("timezones").getString(value));
                } else {
                    System.out.println("Missing timezone id: "+i+" "+value);
                }
                //noinspection Since15
                if (!ResourceBundle.getBundle("timezones").keySet().contains(value)) {
                    System.out.println("Missing timezone: "+value.replaceAll(" ", "\\\\ "));
                }
            }
            session.vTimezone = null;
        }
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream("timezoneids.properties");
            properties.store(fileOutputStream, "Timezone ids");
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void testSearchCalendar() throws IOException {
        List<ExchangeSession.Event> events = null;
        try {
            events = session.getAllEvents("/users/" + session.getEmail() + "/calendar");
            for (ExchangeSession.Event event : events) {
                System.out.println(event.getBody());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public void testReportCalendar() throws IOException {
        List<ExchangeSession.Event> events = null;
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

    public void testGetFreeBusyData() throws IOException, MessagingException {
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
                "UID:040000008200E00074C5B7101A82E0080000000020EA852CF458CC0100000000000000001\n" +
                " 000000011278A1693B8494C8592446E6E249BCF\n" +
                "DTSTART;TZID=W. Europe Standard Time:20120926T100000\n" +
                "DTEND;TZID=W. Europe Standard Time:20120926T120000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n";
        String itemName = "test ok"/*UUID.randomUUID().toString()*/ + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
    }

    public void testGetEvent() throws IOException {
        ExchangeSession.Item item = session.getItem("calendar", "19083675-f8ce-4d81-8ac8-096fa0bd0e13.EML");
        item.getBody();
    }

    public void testGetInbox() throws IOException {
        List<ExchangeSession.Event> items = session.getEventMessages("INBOX");
        for (ExchangeSession.Item item : items) {
            System.out.println(item.getBody());
        }
    }

    public void testSearchEventCount() throws IOException {
        Settings.setLoggingLevel("davmail", Level.WARN);
        System.out.println("Item count: " + session.searchEvents("calendar", null).size());
        System.out.println("InstanceType null: " + session.searchEvents("calendar", session.isNull("instancetype")).size());
        System.out.println("InstanceType not null: " + session.searchEvents("calendar", session.not(session.isNull("instancetype"))).size());
        System.out.println("InstanceType 0: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 0)).size());
        System.out.println("InstanceType 1: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 1)).size());
        System.out.println("InstanceType 2: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 2)).size());
        System.out.println("InstanceType 3: " + session.searchEvents("calendar", session.isEqualTo("instancetype", 3)).size());

        if (session instanceof EwsExchangeSession) {
            System.out.println("Recurring: " + session.searchEvents("calendar", session.isTrue("isrecurring")).size());
            System.out.println("Non recurring: " + session.searchEvents("calendar", session.isFalse("isrecurring")).size());
            System.out.println("Null recurring: " + session.searchEvents("calendar", session.isNull("isrecurring")).size());
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
                "UID:1BDEA2053DF34221AAD74B15755B6B89\n" +
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
        String itemName = UUID.randomUUID().toString() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
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
                "UID:20120920T061713Z-6599-1001-1-2\n" +
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
        String itemName = UUID.randomUUID().toString() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
    }

}


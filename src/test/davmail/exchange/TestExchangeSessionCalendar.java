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
        for (int i = 1; i < 100; i++) {
            Settings.setProperty("davmail.timezoneId", String.valueOf(i));
            VObject timezone = session.getVTimezone();
            if (timezone.getProperty("TZID") != null) {
                properties.put(timezone.getPropertyValue("TZID").replaceAll("\\\\", ""), String.valueOf(i));
                System.out.println(timezone.getPropertyValue("TZID") + '=' + i);
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
                "BEGIN:VTIMEZONE\n" +
                "TZID:Pacific Time (US & Canada)\\; Tijuana\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0700\n" +
                "TZOFFSETTO:-0800\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=11;BYDAY=1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "TZOFFSETFROM:-0800\n" +
                "TZOFFSETTO:-0700\n" +
                "RRULE:FREQ=YEARLY;WKST=MO;INTERVAL=1;BYMONTH=3;BYDAY=2SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20100829T204658Z\n" +
                "LAST-MODIFIED:20100829T204829Z\n" +
                "DTSTAMP:20100829T204829Z\n" +
                "UID:701b9d8f-ab64-4a7c-a75d-251cc8687cd9\n" +
                "SUMMARY:testzz\n" +
                "DTSTART;TZID=\"Pacific Time (US & Canada); Tijuana\":20100830T230000\n" +
                "DTEND;TZID=\"Pacific Time (US & Canada); Tijuana\":20100831T000000\n" +
                "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE\n" +
                "X-MICROSOFT-CDO-BUSYSTATUS:BUSY\n" +
                "TRANSP:OPAQUE\n" +
                "X-MOZ-GENERATION:1\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String itemName = UUID.randomUUID().toString() + ".EML";
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
                "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:America/Bogota\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZOFFSETFROM:-0500\n" +
                "DTSTART:19920503T000000\n" +
                "TZNAME:COT\n" +
                "TZOFFSETTO:-0400\n" +
                "RDATE:19920503T000000\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:-0400\n" +
                "DTSTART:19930404T000000\n" +
                "TZNAME:COT\n" +
                "TZOFFSETTO:-0500\n" +
                "RDATE:19930404T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "CREATED:20110804T203742Z\n" +
                "UID:1E17151D-92DA-4D2E-9747-60B489DE56F4\n" +
                "DTEND;TZID=America/Bogota:20110805T090000\n" +
                "TRANSP:OPAQUE\n" +
                "SUMMARY:New Event 2\n" +
                "DTSTART;TZID=America/Bogota:20110805T080000\n" +
                "DTSTAMP:20110804T203742Z\n" +
                "SEQUENCE:0\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
        String itemName = UUID.randomUUID().toString() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
    }
    
    public void testCreateEventBrokenTZ() throws IOException {
        String itemBody = "BEGIN:VCALENDAR\n" +
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
        String itemName = UUID.randomUUID().toString() + ".EML";
        session.createOrUpdateItem("calendar", itemName, itemBody, null, null);
    }

}


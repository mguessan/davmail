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


}


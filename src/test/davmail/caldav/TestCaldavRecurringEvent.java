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

package davmail.caldav;

import davmail.AbstractDavMailTestCase;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.exchange.VCalendar;
import davmail.exchange.VObject;
import davmail.exchange.VProperty;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.UUID;

public class TestCaldavRecurringEvent extends AbstractDavMailTestCase {

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (session == null) {
            session = ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
        }
    }

    public String getFormattedDateTime(Date date, int hour) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        return formatter.format(date)+"T"+hour+"0000";
    }

    public String getZuluFormattedDateTime(Calendar cal) {

        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        return utcFormat.format(cal.getTime());
    }

    public VCalendar buildEvent(VObject timeZone, int startHour, int endHour) {

        VCalendar vCalendar = new VCalendar();
        vCalendar.setTimezone(timeZone);
        VObject vEvent = new VObject();
        vEvent.setType("VEVENT");
        vEvent.addPropertyValue("UID", UUID.randomUUID().toString());
        vEvent.addPropertyValue("SUMMARY", "Unit test event");
        vEvent.addPropertyValue("LOCATION", "Location");

        VProperty dtStart = new VProperty("DTSTART", getFormattedDateTime(new Date(), startHour));
        dtStart.addParam("TZID", timeZone.getPropertyValue("TZID"));
        vEvent.addProperty(dtStart);

        VProperty dtEnd = new VProperty("DTEND", getFormattedDateTime(new Date(), endHour));
        dtEnd.addParam("TZID", timeZone.getPropertyValue("TZID"));
        vEvent.addProperty(dtEnd);

        vCalendar.addVObject(vEvent);
        return vCalendar;
    }

    public void dumpEvent(VCalendar vCalendar) {
        VObject vEvent = vCalendar.getFirstVevent();
        System.out.println("**********");
        System.out.println("Summary: "+vEvent.getPropertyValue("SUMMARY"));
        String organizer = vEvent.getPropertyValue("ORGANIZER");
        if (organizer != null) {
            System.out.println("Organizer: " +organizer);
        }
        System.out.println("Start: "+vEvent.getPropertyValue("DTSTART"));
        System.out.println("End: "+vEvent.getPropertyValue("DTEND"));

        System.out.println("**********");
    }


    public void testCreateUpdateRecurringEvent() throws IOException {
        VCalendar vEvent = buildEvent(session.getVTimezone(), 10, 11);
        // set weekly recurrence
        vEvent.setFirstVeventPropertyValue("RRULE", "FREQ=WEEKLY");

        dumpEvent(vEvent);

        String itemName = vEvent.getFirstVeventPropertyValue("UID")+".EML";

        ExchangeSession.ItemResult itemResult = session.createOrUpdateItem("calendar",
                itemName,
                vEvent.toString(),null, null);

        assertEquals(201, itemResult.status);

        ExchangeSession.Item item = session.getItem("calendar", itemName);

        VCalendar createdEvent = new VCalendar(item.getBody(), session.getEmail(), session.getVTimezone());
        dumpEvent(createdEvent);

        assertEquals(
                vEvent.getFirstVeventPropertyValue("DTSTART"),
                createdEvent.getFirstVeventPropertyValue("DTSTART")
        );

        assertEquals(
                vEvent.getFirstVeventPropertyValue("DTEND"),
                createdEvent.getFirstVeventPropertyValue("DTEND")
        );

        assertEquals(
                vEvent.getFirstVeventPropertyValue("LOCATION"),
                createdEvent.getFirstVeventPropertyValue("LOCATION")
        );



        createdEvent.setFirstVeventPropertyValue("DTEND", getFormattedDateTime(new Date(), 12));
        createdEvent.setFirstVeventPropertyValue("LOCATION", "Location updated");

        session.createOrUpdateItem("calendar", itemName, createdEvent.toString(), null, null);

        ExchangeSession.Item updatedItem = session.getItem("calendar", itemName);
        VCalendar updatedEvent = new VCalendar(updatedItem.getBody(), session.getEmail(), session.getVTimezone());
        dumpEvent(updatedEvent);

        assertEquals(
                createdEvent.getFirstVeventPropertyValue("DTEND"),
                updatedEvent.getFirstVeventPropertyValue("DTEND")
        );

        assertEquals(
                createdEvent.getFirstVeventPropertyValue("LOCATION"),
                updatedEvent.getFirstVeventPropertyValue("LOCATION")
        );

        session.deleteItem("calendar", itemName);
    }

    public void testExclusion() throws IOException {

        VCalendar vEvent = buildEvent(session.getVTimezone(), 10, 11);
        // set dayly recurrence
        vEvent.setFirstVeventPropertyValue("RRULE", "FREQ=WEEKLY");

        dumpEvent(vEvent);

        String itemName = vEvent.getFirstVeventPropertyValue("UID")+".EML";

        ExchangeSession.ItemResult itemResult = session.createOrUpdateItem("calendar",
                itemName,
                vEvent.toString(),null, null);

        assertEquals(201, itemResult.status);

        ExchangeSession.Item item = session.getItem("calendar", itemName);

        VCalendar createdEvent = new VCalendar(item.getBody(), session.getEmail(), session.getVTimezone());
        dumpEvent(createdEvent);

        // need to find current session timezone
        String tzid = session.getVTimezone().getPropertyValue("TZID");
        System.out.println(tzid);
        // convert to standard timezone
        ResourceBundle tzBundle = ResourceBundle.getBundle("stdtimezones");
        String stdtzid = tzBundle.getString(tzid);
        TimeZone javaTimezone = TimeZone.getTimeZone(stdtzid);

        Calendar nextWeek = Calendar.getInstance();
        nextWeek.setTimeZone(javaTimezone);
        nextWeek.set(Calendar.HOUR, 10);
        nextWeek.set(Calendar.MINUTE, 0);
        nextWeek.set(Calendar.SECOND, 0);
        nextWeek.add(Calendar.DAY_OF_MONTH, 7);

        createdEvent.setFirstVeventPropertyValue("EXDATE", getZuluFormattedDateTime(nextWeek));

        session.createOrUpdateItem("calendar", itemName, createdEvent.toString(), null, null);

        ExchangeSession.Item updatedItem = session.getItem("calendar", itemName);
        VCalendar updatedEvent = new VCalendar(updatedItem.getBody(), session.getEmail(), session.getVTimezone());
        dumpEvent(updatedEvent);

        session.deleteItem("calendar", itemName);
    }
}

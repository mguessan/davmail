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
import davmail.exchange.*;
import davmail.exchange.ews.EwsExchangeSession;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class TestCaldavMeetings extends AbstractDavMailTestCase {
    ExchangeSession sessiona;
    ExchangeSession sessionb;

    String usera;
    String userb;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        // load testuser credentials
        Properties testProperties = new Properties();
        testProperties.load(new FileInputStream("test.properties"));
        if (testProperties.getProperty("davmail.url") != null) {
            Settings.setProperty("davmail.url", testProperties.getProperty("davmail.url"));
        }

        usera = testProperties.getProperty("davmail.usera");
        userb = testProperties.getProperty("davmail.userb");

        if (sessiona == null) {
            sessiona = ExchangeSessionFactory.getInstance(
                    usera,
                    testProperties.getProperty("davmail.passworda"));
        }

        if (sessionb == null) {
            sessionb = ExchangeSessionFactory.getInstance(
                    userb,
                    testProperties.getProperty("davmail.passwordb"));
        }
    }

    public void testOpenSession() {
        // just test settings
    }

    public void dumpCalendar(ExchangeSession session) throws IOException {
        List<ExchangeSession.Event> events = session.getAllEvents("/users/"+session.getEmail()+"/calendar");
        for (ExchangeSession.Event event: events) {
            System.out.println(event.getName());
            VCalendar vCalendar = new VCalendar(event.getBody(), null, null);
            dumpEvent(vCalendar);
        }
    }

    public void dumpEvent(VCalendar vCalendar) {
        VObject vEvent = vCalendar.getFirstVevent();
        System.out.println("**********");
        System.out.println("Summary: "+vEvent.getPropertyValue("SUMMARY"));
        System.out.println("Organizer: "+vEvent.getPropertyValue("ORGANIZER"));
        System.out.println("Start: "+vEvent.getPropertyValue("DTSTART"));
        System.out.println("End: "+vEvent.getPropertyValue("DTEND"));
        List<VProperty> attendees = vEvent.getProperties("ATTENDEE");
        if (attendees != null) {
            for (VProperty attendee: attendees) {
                System.out.println(attendee.getValue()
                        +" "+attendee.getParamValue("PARTSTAT")
                        +" "+attendee.getParamValue("ROLE"));
            }
        }
        System.out.println("**********");
    }

    public void dumpEvent(ExchangeSession session, String itemName) throws IOException {
        ExchangeSession.Item item = session.getItem("calendar", itemName);
        VCalendar event = new VCalendar(item.getBody(), session.getEmail(), session.getVTimezone());
        dumpEvent(event);
    }

    public void testGetCalendarA() throws IOException {
        dumpCalendar(sessiona);
    }

    public void testGetCalendarB() throws IOException {
        dumpCalendar(sessionb);
    }

    public String getFormattedDateTime(Date date, int hour) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        return formatter.format(date)+"T"+hour+"0000";
    }

    public VCalendar buildEvent(VObject timeZone, int startHour, int endHour) {

        VCalendar vCalendar = new VCalendar();
        vCalendar.setTimezone(timeZone);
        VObject vEvent = new VObject();
        vEvent.setType("VEVENT");
        vEvent.addPropertyValue("UID", UUID.randomUUID().toString());
        vEvent.addPropertyValue("SUMMARY", "Unit test event");
        vEvent.addPropertyValue("LOCATION", "Location1");
        Date now = new Date();

        VProperty dtStart = new VProperty("DTSTART", getFormattedDateTime(new Date(), startHour));
        dtStart.addParam("TZID", timeZone.getPropertyValue("TZID"));
        vEvent.addProperty(dtStart);

        VProperty dtEnd = new VProperty("DTEND", getFormattedDateTime(new Date(), endHour));
        dtEnd.addParam("TZID", timeZone.getPropertyValue("TZID"));
        vEvent.addProperty(dtEnd);

        vCalendar.addVObject(vEvent);
        return vCalendar;
    }

    public void testCreateUpdateEvent() throws IOException {
        VCalendar vEvent = buildEvent(sessiona.getVTimezone(), 10, 11);
        dumpEvent(vEvent);

        String itemName = vEvent.getFirstVeventPropertyValue("UID")+".EML";

        ExchangeSession.ItemResult itemResult = sessiona.createOrUpdateItem("calendar",
                itemName,
                vEvent.toString(),null, null);

        assertEquals(201, itemResult.status);

        ExchangeSession.Item item = sessiona.getItem("calendar", itemName);

        VCalendar createdEvent = new VCalendar(item.getBody(), sessiona.getEmail(), sessiona.getVTimezone());
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

        sessiona.createOrUpdateItem("calendar", itemName, createdEvent.toString(), null, null);

        ExchangeSession.Item updatedItem = sessiona.getItem("calendar", itemName);
        VCalendar updatedEvent = new VCalendar(updatedItem.getBody(), sessiona.getEmail(), sessiona.getVTimezone());
        dumpEvent(updatedEvent);

        assertEquals(
                createdEvent.getFirstVeventPropertyValue("DTEND"),
                updatedEvent.getFirstVeventPropertyValue("DTEND")
        );

        assertEquals(
                createdEvent.getFirstVeventPropertyValue("LOCATION"),
                updatedEvent.getFirstVeventPropertyValue("LOCATION")
        );

        sessiona.deleteItem("calendar", itemName);
    }

    public void testCreateMeeting() throws IOException {
        VCalendar vMeeting = buildEvent(sessiona.getVTimezone(), 10, 11);
        dumpEvent(vMeeting);

        String itemName = vMeeting.getFirstVeventPropertyValue("UID")+".EML";

        ExchangeSession.ItemResult itemResult = sessiona.createOrUpdateItem("calendar",
                itemName,
                vMeeting.toString(),null, null);

        assertEquals(201, itemResult.status);

        ExchangeSession.Item item = sessiona.getItem("calendar", itemName);

        VCalendar createdEvent = new VCalendar(item.getBody(), sessiona.getEmail(), sessiona.getVTimezone());
        dumpEvent(createdEvent);

        VProperty attendee = new VProperty("ATTENDEE", "mailto:"+sessionb.getEmail());
        attendee.addParam("PARTSTAT", "NEEDS-ACTION");
        attendee.addParam("ROLE", "REQ-PARTICIPANT");
        createdEvent.addFirstVeventProperty(attendee);

        sessiona.createOrUpdateItem("calendar", itemName, createdEvent.toString(), null, null);

        ExchangeSession.Item updatedItem = sessiona.getItem("calendar", itemName);
        VCalendar updatedEvent = new VCalendar(updatedItem.getBody(), sessiona.getEmail(), sessiona.getVTimezone());
        dumpEvent(updatedEvent);

        assertEquals(
                createdEvent.getFirstVeventPropertyValue("ATTENDEE"),
                updatedEvent.getFirstVeventPropertyValue("ATTENDEE")
        );


    }

}

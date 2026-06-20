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
package davmail.exchange.ews;

import davmail.exchange.VObject;
import davmail.exchange.VProperty;
import junit.framework.TestCase;

/**
 * EWS timezone unit tests.
 */
public class TestEwsExchangeSessionTimezone extends TestCase {
    public void testResolveUtcTimezoneFromZuluDateStart() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART:20240511T160000Z"));

        assertEquals("UTC", EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTSTART"));
    }

    public void testResolveUtcTimezoneFromZuluDateEnd() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTEND:20240511T200000Z"));

        assertEquals("UTC", EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTEND"));
    }

    public void testResolveUtcTimezoneFromZuluDateStartWithTzid() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART;TZID=Europe/Paris:20240511T160000Z"));

        assertEquals("UTC", EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTSTART"));
    }

    public void testResolveTimezoneKeepsTzid() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART;TZID=Europe/Paris:20240511T180000"));

        assertEquals("Europe/Paris", EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTSTART"));
    }

    public void testResolveTimezoneLeavesFloatingDateUnset() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART:20240511T180000"));

        assertNull(EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTSTART"));
    }

    public void testResolveTimezoneLeavesEmptyTzidUnset() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART;TZID=\"\":20240511T180000"));

        assertNull(EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTSTART"));
    }

    public void testResolveTimezoneHandlesMissingDateEnd() {
        VObject vEvent = new VObject();
        vEvent.type = "VEVENT";
        vEvent.addProperty(new VProperty("DTSTART:20240511T160000Z"));

        assertNull(EwsExchangeSession.resolveCalendarTimezone(vEvent, "DTEND"));
    }
}

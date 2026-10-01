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

package davmail.exchange.graph;

import davmail.exception.DavMailException;
import davmail.exchange.VProperty;
import junit.framework.TestCase;

/**
 * Test RRULE UNTIL built from graph recurrence range end date.
 */
public class TestGraphRecurrenceUntil extends TestCase {

    protected VProperty dtStart(String tzid, String value) {
        VProperty dtStart = new VProperty("DTSTART", value);
        dtStart.addParam("TZID", tzid);
        return dtStart;
    }

    /**
     * Series starts in winter (GMT) and ends in summer (BST): UNTIL is the local start time on the end date.
     */
    public void testWinterStartSummerEnd() throws DavMailException {
        assertEquals("20250906T110000Z", GraphExchangeSession.buildUntilDate("2025-09-06", dtStart("Europe/London", "20250306T120000")));
    }

    /**
     * Series starts in summer (BST) and ends in winter (GMT): UNTIL must not be before the last occurrence.
     */
    public void testSummerStartWinterEnd() throws DavMailException {
        assertEquals("20251106T120000Z", GraphExchangeSession.buildUntilDate("2025-11-06", dtStart("Europe/London", "20250703T120000")));
    }

    public void testExchangeTimeZoneId() throws DavMailException {
        assertEquals("20250906T110000Z", GraphExchangeSession.buildUntilDate("2025-09-06", dtStart("GMT Standard Time", "20250306T120000")));
    }

    public void testTaskDateOnly() throws DavMailException {
        assertEquals("20250906", GraphExchangeSession.buildUntilDate("2025-09-06", null));
    }
}

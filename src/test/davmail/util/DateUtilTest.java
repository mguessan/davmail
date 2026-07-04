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

package davmail.util;

import junit.framework.TestCase;

public class DateUtilTest extends TestCase {
    public void testGetTimeZone() {
        // standard to standard
        assertEquals("America/New_York", DateUtil.getTimeZone("America/New_York").getID());
        assertEquals("Europe/Paris", DateUtil.getTimeZone("Europe/Paris").getID());
        assertEquals("UTC", DateUtil.getTimeZone("UTC").getID());

        // Exchange to standard
        assertEquals("America/Los_Angeles", DateUtil.getTimeZone("Pacific Standard Time").getID());

        // failover to UTC
        assertEquals("UTC", DateUtil.getTimeZone("Invalid").getID());
        assertEquals("UTC", DateUtil.getTimeZone(null).getID());
    }
}

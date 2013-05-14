/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2011  Mickael Guessant
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

import junit.framework.TestCase;

/**
 * Test VProperty.
 */
public class TestVProperty  extends TestCase {
    public void testMultivaluedParam() {
        String line = "TEL;TYPE=home,voice:homePhone";
        VProperty vProperty = new VProperty(line);
        assertNotNull(vProperty);
        assertEquals(line, vProperty.toString());
    }

    public void testQuoteCn() {
        String line = "ATTENDEE;CN=\"test\":MAILTO:test@company.com";
        VProperty vProperty = new VProperty(line);
        assertNotNull(vProperty);
        assertEquals(line, vProperty.toString());
    }

    public void testBackslash() {
        String line = "SUMMARY:Electric Meter change 08:30\\";
        VProperty vProperty = new VProperty(line);
        assertNotNull(vProperty);
        assertEquals(line, vProperty.toString());
    }

}

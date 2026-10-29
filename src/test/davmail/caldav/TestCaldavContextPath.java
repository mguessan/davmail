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

import junit.framework.TestCase;

/**
 * Test caldav context path handling for reverse proxy support.
 */
public class TestCaldavContextPath extends TestCase {

    public void testEmptyContextPathKeepsPath() {
        assertEquals("/users/a@b.c/calendar/", CaldavConnection.stripContextPath("", "/users/a@b.c/calendar/"));
        assertEquals("/", CaldavConnection.stripContextPath("", "/"));
    }

    public void testStripPrefix() {
        assertEquals("/users/a@b.c/calendar/", CaldavConnection.stripContextPath("/caldav", "/caldav/users/a@b.c/calendar/"));
    }

    public void testContextPathItselfIsRoot() {
        assertEquals("/", CaldavConnection.stripContextPath("/caldav", "/caldav"));
        assertEquals("/", CaldavConnection.stripContextPath("/caldav", "/caldav/"));
    }

    public void testSimilarPrefixDoesNotMatch() {
        assertNull(CaldavConnection.stripContextPath("/caldav", "/caldavfoo/users"));
    }

    public void testOutsideContextPathDoesNotMatch() {
        assertNull(CaldavConnection.stripContextPath("/caldav", "/users/a@b.c/calendar/"));
        assertNull(CaldavConnection.stripContextPath("/caldav", "/"));
    }

    /**
     * Destination of a MOVE sent by a client contains the context path: it must be removed
     * to get the same kind of path as the request path.
     */
    public void testMoveDestinationIsRelativeToContextPath() {
        assertEquals("/users/a@b.c/calendar/event.ics",
                CaldavConnection.stripContextPath("/caldav", "/caldav/users/a@b.c/calendar/event.ics"));
    }
}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2015  Mickael Guessant
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
package davmail.imap;

import java.io.IOException;

/**
 * Test folders with quotes.
 */
public class TestImapQuotedFolder extends AbstractImapTestCase {
    public void testListFolders() throws IOException {
        writeLine(". LIST \"\" \"%/%\"");
        assertEquals(". OK LIST completed", readFullAnswer("."));
    }

    public void testCreateQuotedFolder() throws IOException {
        writeLine(". CREATE \"test \\\"quoted\\\" folder\"");
        assertEquals(". OK folder created", readFullAnswer("."));
    }

    public void testListQuotedFolder() throws IOException {
        writeLine(". LIST \"test \\\"quoted\\\" folder\" \"\"");
        assertEquals("* LIST (\\HasNoChildren) \"/\" \"test \\\"quoted\\\" folder\"", readLine());
        assertEquals(". OK LIST completed", readFullAnswer("."));
    }

    public void testDeleteQuotedFolder() throws IOException {
        writeLine(". DELETE \"test \\\"quoted\\\" folder\"");
        assertEquals(". OK folder deleted", readFullAnswer("."));
    }
}

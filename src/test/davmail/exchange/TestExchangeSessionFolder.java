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

import java.io.IOException;

/**
 * Test folder methods.
 */
public class TestExchangeSessionFolder extends AbstractExchangeSessionTestCase {
    public void testCreateFolder() throws IOException {
        session.createMessageFolder("test");
    }

    public void testGetFolder() throws IOException {
        ExchangeSession.Folder folder = session.getFolder("test");
        assertNotNull(folder);
        assertEquals("test", folder.folderPath);
        assertEquals("test", folder.displayName);
        assertEquals("IPF.Note", folder.folderClass);
        assertEquals(0, folder.unreadCount);
        assertFalse(folder.hasChildren);
        assertFalse(folder.noInferiors);
        assertNotNull(folder.ctag);
        assertNotNull(folder.etag);
    }

    public void testSubFolder() throws IOException {
        session.createMessageFolder("test/subfolder");
        ExchangeSession.Folder folder = session.getFolder("test/subfolder");
        assertNotNull(folder);
        assertEquals("test/subfolder", folder.folderPath);
        assertEquals("subfolder", folder.displayName);
        session.deleteFolder("test/subfolder");
    }

    public void testUpdateFolder() throws IOException {
        // TODO: implement
    }

    public void testMoveFolder() throws IOException {
        session.deleteFolder("target");
        session.deleteFolder("tomove");
        session.createMessageFolder("tomove");
        session.createMessageFolder("target");
        session.moveFolder("tomove", "target/moved");
        session.deleteFolder("target");
    }

    public void testDeleteFolder() throws IOException {
        session.deleteFolder("test");
    }

    public void testCalendarFolder() throws IOException {
        String folderName = "testcalendar";
        session.createCalendarFolder(folderName);
        ExchangeSession.Folder folder = session.getFolder(folderName);
        assertNotNull(folder);
        assertEquals("IPF.Appointment", folder.folderClass);
        session.deleteFolder(folderName);
    }

    public void testContactFolder() throws IOException {
        String folderName = "testcontact";
        session.createContactFolder(folderName);
        ExchangeSession.Folder folder = session.getFolder(folderName);
        assertNotNull(folder);
        assertEquals("IPF.Contact", folder.folderClass);
        session.deleteFolder(folderName);
    }


    public void testFolderAccent() throws IOException {
        String folderName = "testé";
        session.createMessageFolder(folderName);
        ExchangeSession.Folder folder = session.getFolder(folderName);
        assertNotNull(folder);
        assertEquals(folderName, folder.displayName);
        assertEquals(folderName, folder.folderPath);
        session.deleteFolder(folderName);
    }

    public void testFolderSpace() throws IOException {
        String folderName = "test space";
        session.createMessageFolder(folderName);
        ExchangeSession.Folder folder = session.getFolder(folderName);
        assertNotNull(folder);
        assertEquals(folderName, folder.displayName);
        assertEquals(folderName, folder.folderPath);
        session.deleteFolder(folderName);
    }
}

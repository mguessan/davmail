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
package davmail.imap;

import davmail.Settings;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Test folder requests.
 */
public class TestImapFolders extends AbstractImapTestCase {
    public void testListFolders() throws IOException {
        writeLine(". LSUB \"\" \"*\"");
        assertEquals(". OK LSUB completed", readFullAnswer("."));
    }

    public void testListAllSubFolders() throws IOException {
        writeLine(". LIST \"\" \"%/%\"");
        assertEquals(". OK LIST completed", readFullAnswer("."));
    }

    public void testListSubFolders() throws IOException {
        writeLine(". LIST \"\" \"INBOX*\"");
        assertEquals(". OK LIST completed", readFullAnswer("."));
    }

    public void testSelectInbox() throws IOException {
        writeLine(". SELECT INBOX");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testSelectRoot() throws IOException {
        writeLine(". SELECT \"\"");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testEwsPaging() throws IOException, InterruptedException, MessagingException {
        resetTestFolder();
        appendHundredMessages();

        writeLine(". SELECT testfolder");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
        writeLine(". UID FETCH 1:* (BODY[HEADER.FIELDS (DATE SUBJECT MESSAGE-ID )])");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

}

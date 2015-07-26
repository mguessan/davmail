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
import java.util.Random;

/**
 * IMAP error handling tests.
 */
public class TestImapErrorHandling extends AbstractImapTestCase {
    public void testSelectInbox() throws IOException {
        writeLine(". SELECT INBOX");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }
    public void testCreateFolder() throws IOException {
        writeLine(". DELETE testfolder");
        readFullAnswer(".");
        writeLine(". CREATE testfolder");
        assertEquals(". OK folder created", readFullAnswer("."));
        writeLine(". SELECT testfolder");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testBrokenPipe() throws IOException, InterruptedException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (RFC822.SIZE BODY.TEXT)");
        socketReader.readLine();
        // force close connection
        clientSocket.close();
        Thread.sleep(5000);
    }

    public void testFetchBigMessage() throws IOException, MessagingException {
        testCreateFolder();
        // create 10MB message
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("to", Settings.getProperty("davmail.to"));
        mimeMessage.addHeader("bcc", Settings.getProperty("davmail.bcc"));
        Random random = new Random();
        StringBuilder randomText = new StringBuilder();
        for (int i = 0; i < 10 * 1024 * 1024; i++) {
            randomText.append((char) ('a' + random.nextInt(26)));
        }
        mimeMessage.setText(randomText.toString());
        mimeMessage.setSubject("Big subject");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        byte[] content = baos.toByteArray();
        long start = System.currentTimeMillis();
        writeLine(". APPEND testfolder (\\Seen \\Draft) {" + content.length + '}');
        assertEquals("+ send literal data", readLine());
        writeLine(new String(content));
        assertEquals(". OK APPEND completed", readFullAnswer("."));
        System.out.println("Create time: " + (System.currentTimeMillis() - start) + " ms");
        writeLine(". NOOP");
        assertEquals(". OK NOOP completed", readFullAnswer("."));
        start = System.currentTimeMillis();
        writeLine(". UID FETCH 1:* (RFC822.SIZE BODY.TEXT)");
        readFullAnswer(".");
        System.out.println("Fetch time: " + (System.currentTimeMillis() - start) + " ms");

    }

    public void testSelectInboxTimeout() throws IOException {
        writeLine(". SELECT INBOX");
        // simulate client timeout
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // ignore
        }
        socketWriter.close();
        System.in.read();
    }

    public void testLogout() throws IOException {
        writeLine(". LOGOUT");
        assertEquals("* BYE Closing connection", socketReader.readLine());
        assertEquals(". OK LOGOUT completed", socketReader.readLine());
        clientSocket = null;
    }

}

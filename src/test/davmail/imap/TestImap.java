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

import davmail.AbstractDavMailTestCase;
import davmail.DavGateway;
import davmail.Settings;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * IMAP tests, an instance of DavMail Gateway must be available
 */
@SuppressWarnings({"JavaDoc", "UseOfSystemOutOrSystemErr"})
public class TestImap extends AbstractDavMailTestCase {
    static Socket clientSocket;
    static BufferedWriter socketWriter;
    static BufferedReader socketReader;

    static String messageUid;

    protected void write(String line) throws IOException {
        socketWriter.write(line);
        socketWriter.flush();
    }

    protected void writeLine(String line) throws IOException {
        socketWriter.write(line);
        socketWriter.newLine();
        socketWriter.flush();
    }

    protected String readLine() throws IOException {
        return socketReader.readLine();
    }

    protected String readFullAnswer(String prefix) throws IOException {
        String line = socketReader.readLine();
        while (!line.startsWith(prefix)) {
            line = socketReader.readLine();
        }
        return line;
    }

    @Override
    public void setUp() throws IOException {
        boolean needStart = !loaded;
        super.setUp();
        if (needStart) {
            // start gateway
            DavGateway.start();
        }
        if (clientSocket == null) {
            clientSocket = new Socket("localhost", Settings.getIntProperty("davmail.imapPort"));
            socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String banner = socketReader.readLine();
            assertNotNull(banner);

            writeLine(". LOGIN " + Settings.getProperty("davmail.username").replaceAll("\\\\", "\\\\\\\\") + ' ' + Settings.getProperty("davmail.password"));
            assertEquals(". OK Authenticated", socketReader.readLine());
        }
    }

    public void testListFolders() throws IOException {
        writeLine(". LSUB \"\" \"*\"");
        assertEquals(". OK LSUB completed", readFullAnswer("."));
    }

    public void testSelectInbox() throws IOException {
        writeLine(". SELECT INBOX");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testFetchFlags() throws IOException {
        writeLine(". UID FETCH 1:* (FLAGS)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testUidSearchUnDeleted() throws IOException {
        writeLine(". UID SEARCH UNDELETED");
        assertEquals(". OK SEARCH completed", readFullAnswer("."));
        writeLine(". UID SEARCH NOT DELETED");
        assertEquals(". OK SEARCH completed", readFullAnswer("."));
    }

    public void testUidSearchdeleted() throws IOException {
        writeLine(". UID SEARCH DELETED");
        assertEquals(". OK SEARCH completed", readFullAnswer("."));
    }

    public void testStoreUndelete() throws IOException {
        writeLine(". UID STORE 10 -FLAGS (\\Deleted)");
        readFullAnswer(".");
    }

    public void testCreateFolder() throws IOException {
        writeLine(". DELETE testfolder");
        readFullAnswer(".");
        writeLine(". CREATE testfolder");
        assertEquals(". OK folder created", readFullAnswer("."));
        writeLine(". SELECT testfolder");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testFetchBigMessage() throws IOException, MessagingException {
        testCreateFolder();
        // create 10MB message
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("to", Settings.getProperty("davmail.to"));
        mimeMessage.addHeader("bcc", Settings.getProperty("davmail.bcc"));
        Random random = new Random();
        StringBuilder randomText = new StringBuilder();
        for (int i=0;i<10*1024*1024;i++) {
           randomText.append((char)('a'+random.nextInt(26))); 
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
        System.out.println("Create time: "+(System.currentTimeMillis()-start)+" ms");
        writeLine(". NOOP");
        assertEquals(". OK NOOP completed", readFullAnswer("."));
        start = System.currentTimeMillis();
        writeLine(". UID FETCH 1:* (RFC822.SIZE BODY.TEXT)");
        readFullAnswer(".");
        System.out.println("Fetch time: "+(System.currentTimeMillis()-start)+" ms");

    }


    public void testSelectFolder() throws IOException {
        writeLine(". SELECT testfolder");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testCreateMessage() throws IOException, MessagingException {

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("to", Settings.getProperty("davmail.to"));
        mimeMessage.addHeader("bcc", Settings.getProperty("davmail.bcc"));
        mimeMessage.setText("Test message");
        mimeMessage.setSubject("Test subject");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        byte[] content = baos.toByteArray();
        writeLine(". APPEND testfolder (\\Seen \\Draft) {" + content.length + '}');
        assertEquals("+ send literal data", readLine());
        writeLine(new String(content));
        assertEquals(". OK APPEND completed", readFullAnswer("."));
        writeLine(". NOOP");
        assertEquals(". OK NOOP completed", readFullAnswer("."));

        // fetch message uid
        writeLine(". UID FETCH 1:* (FLAGS)");
        String messageLine = readLine();
        int uidIndex = messageLine.indexOf("UID ") + 4;
        messageUid = messageLine.substring(uidIndex, messageLine.indexOf(' ', uidIndex));
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
        assertNotNull(messageUid);
    }

    public void testUidStoreDeletedFlag() throws IOException {

        // test deleted flag
        writeLine(". UID STORE " + messageUid + " +FLAGS (\\Deleted)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Seen \\Deleted \\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        // remove deleted flag
        writeLine(". UID STORE " + messageUid + " -FLAGS (\\Deleted)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Seen \\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

    }

    public void testUidRemoveSeenFlag() throws IOException {
        // remove seen flag
        writeLine(". UID STORE " + messageUid + " FLAGS (\\Draft)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testUidStoreForwardedFlag() throws IOException {
        // add forwarded flag
        writeLine(". UID STORE " + messageUid + " +FLAGS ($Forwarded)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft $Forwarded))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        // remove forwarded flag
        writeLine(". UID STORE " + messageUid + " -FLAGS ($Forwarded)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testUidStoreAnsweredFlag() throws IOException {
        // add answered flag
        writeLine(". UID STORE " + messageUid + " +FLAGS (\\Answered)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft \\Answered))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        // remove answered flag
        writeLine(". UID STORE " + messageUid + " -FLAGS (\\Answered)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testUidStoreJunkFlag() throws IOException {
        // add Junk flag
        writeLine(". UID STORE " + messageUid + " +FLAGS (Junk)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (Junk \\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        // remove Junk flag
        writeLine(". UID STORE " + messageUid + " -FLAGS (Junk)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testUidStoreSeenFlag() throws IOException {
        // add Junk flag
        writeLine(". UID STORE " + messageUid + " +FLAGS (\\Seen)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Seen \\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        // remove Junk flag
        writeLine(". UID STORE " + messageUid + " -FLAGS (\\Seen)");
        assertEquals(". OK STORE completed", readFullAnswer("."));
        writeLine(". UID FETCH " + messageUid + " (FLAGS)");
        assertEquals("* 1 FETCH (UID " + messageUid + " FLAGS (\\Draft))", readLine());
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testPartialFetch() throws IOException {
        writeLine(". UID FETCH " + messageUid + " (BODY.PEEK[1.MIME])");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testHeaderFetch() throws IOException {
        writeLine(". UID FETCH " + messageUid + " (BODY[HEADER.FIELDS (DATE SUBJECT FROM CONTENT-TYPE TO CC BCC MESSAGE-ID IN-REPLY-TO REFERENCES)])");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testHeaderBodyFetch() throws IOException {
        writeLine(". UID FETCH " + messageUid + " (UID BODY.PEEK[HEADER.FIELDS (Content-Type Content-Transfer-Encoding)] BODY.PEEK[TEXT]<0.2048>)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testFetchInternalDate() throws IOException {
        writeLine(". UID FETCH " + messageUid + " (INTERNALDATE)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }


    public void testDeleteFolder() throws IOException {
        writeLine(". DELETE testfolder");
        assertEquals(". OK folder deleted", readFullAnswer("."));
    }

    public void testLogout() throws IOException {
        writeLine(". LOGOUT");
        assertEquals("* BYE Closing connection", socketReader.readLine());
        clientSocket = null;
    }

    public void testCopyMessage() throws IOException, InterruptedException, MessagingException {
        testCreateFolder();
        testCreateMessage();
        writeLine(". UID FETCH 1:* (FLAGS)");
        String messageLine = readLine();
        int uidIndex = messageLine.indexOf("UID ") + 4;
        messageUid = messageLine.substring(uidIndex, messageLine.indexOf(' ', uidIndex));
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));

        writeLine(". UID COPY "+messageUid+" Trash");
        assertEquals(". OK copy completed", readFullAnswer("."));

        writeLine(". COPY 1 Trash");
        assertEquals(". OK copy completed", readFullAnswer("."));

        testDeleteFolder();
    }

    public void testFetchInboxEnvelope() throws IOException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (ENVELOPE)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testFetchInboxBodyStructure() throws IOException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (BODYSTRUCTURE)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testBrokenPipe() throws IOException, InterruptedException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (RFC822.SIZE BODY.TEXT)");
        socketReader.readLine();
        // force close connection
        clientSocket.close();
        Thread.sleep(5000);
    }

    public void testFetchRfc822Header() throws IOException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (UID RFC822.HEADER RFC822.SIZE FLAGS)");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }

    public void testThunderbirdHeaderFetch() throws IOException {
        testSelectInbox();
        writeLine(". UID FETCH 1:* (UID RFC822.SIZE FLAGS BODY.PEEK[HEADER.FIELDS (From To Cc Bcc Subject Date Message-ID Priority X-Priority References Newsgroups In-Reply-To Content-Type)])");
        assertEquals(". OK UID FETCH completed", readFullAnswer("."));
    }
}

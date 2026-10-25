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

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * Test message handling features.
 */
public class TestExchangeSessionMessage extends AbstractExchangeSessionTestCase {
    static ExchangeSession.Message message;
    static String messageName;

    public void testCreateMessage() throws IOException, MessagingException {
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");
        MimeMessage mimeMessage = createMimeMessage();
        messageName = UUID.randomUUID().toString()+".EML";
        HashMap<String, String> properties = new HashMap<>();
        properties.put("draft", "0");
        message = session.createMessage("testfolder", messageName, properties, mimeMessage);
    }

    public void testSearchInbox() throws IOException, MessagingException {
        ExchangeSession.MessageList messageList = session.searchMessages("INBOX");
        assertNotNull(messageList);
    }

    public void testSearchTrash() throws IOException, MessagingException {
        ExchangeSession.MessageList messageList = session.searchMessages("Trash");
        assertNotNull(messageList);
    }

    public void testSearchMessage() throws IOException, MessagingException {
        testCreateMessage();

        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        message = messageList.get(0);
        assertFalse(message.answered);
        assertFalse(message.forwarded);
        assertFalse(message.flagged);
        assertFalse(message.draft);
        assertTrue(message.size > 0);
        assertFalse(message.deleted);
        assertFalse(message.read);
        assertNotNull(message.date);
    }

    public void testFlagMessage() throws IOException, MessagingException {
        testCreateMessage();

        ExchangeSession.Folder testFolder = session.getFolder("testfolder");
        testFolder.loadMessages();
        // use the message from the folder so we have the uid assigned by the server
        message = testFolder.get(0);
        long uidBeforeFlag = message.getImapUid();
        HashMap<String,String> properties = new HashMap<String,String>();
        properties.put("flagged", "2");
        session.updateMessage(message, properties);

        // refresh folder
        testFolder.loadMessages();
        assertNotNull(testFolder.get(0));
        assertTrue(testFolder.get(0).flagged);
        assertEquals(uidBeforeFlag, testFolder.get(0).getImapUid());
    }

    public void testGetContent() throws IOException, MessagingException {
        testCreateMessage();
        byte[] content = session.getContent(message);
        assertNotNull(content);
        MimeMessage mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(content));
        assertTrue(mimeMessage.getHeader("To")[0].indexOf("test@test.local") >= 0);
        assertEquals("Test subject", mimeMessage.getSubject());
        assertEquals("Test message\n", mimeMessage.getContent());
    }

    public void testProcessMessage() throws IOException, MessagingException {
        session.processItem("testfolder", messageName);
    }

    public void testFolderUidNext() throws IOException, MessagingException {
        ExchangeSession.Folder folder = session.getFolder("testfolder");
        assertTrue(folder.uidNext > 0);
    }

    public void testMarkMessageDeleted() throws IOException, MessagingException {
        testCreateMessage();
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());

        message = messageList.get(0);

        HashMap<String,String> properties = new HashMap<String,String>();
        properties.put("deleted", "1");
        properties.put("read", "1");
        session.updateMessage(message, properties);

        messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        message = messageList.get(0);
        assertTrue(message.deleted);
    }

    public void testDeleteMessage() throws IOException, MessagingException {
        testCreateMessage();

        // fetch message
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        message = messageList.get(0);

        session.deleteMessage(message);

        // try to delete already deleted message — should not throw (issue #558)
        session.deleteMessage(message);

        messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(0, messageList.size());

    }

    /**
     * Test that deleting an already-deleted message does not throw.
     * Regression test for <a href="https://github.com/mguessan/davmail/issues/558">#558</a>:
     * Graph API returns 403 ErrorCannotDeleteObject or 404 when the item is already gone.
     */
    public void testDeleteAlreadyDeletedMessage() throws IOException, MessagingException {
        testCreateMessage();

        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        message = messageList.get(0);

        // first delete should succeed
        session.deleteMessage(message);

        // second delete on same message should not throw
        try {
            session.deleteMessage(message);
        } catch (IOException e) {
            fail("Deleting an already-deleted message should not throw, but got: " + e.getMessage());
        }
    }

    public void testSpecialMessageCharacter() throws IOException, MessagingException {
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");
        MimeMessage mimeMessage = createMimeMessage();
        messageName = "Special & accenté.EML";
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "0");
        session.createMessage("testfolder", messageName, properties, mimeMessage);
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder", session.isEqualTo("urlcompname", messageName));
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
    }

    public void testSlashMessageName() throws IOException, MessagingException {
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");
        MimeMessage mimeMessage = createMimeMessage();
        messageName = "test _xF8FF_ slash.EML";
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "0");
        session.createMessage("testfolder", messageName, properties, mimeMessage);
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder", session.isEqualTo("urlcompname", messageName));
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
    }

    public void testPlusMessageName() throws IOException, MessagingException {
        // fails on Exchange 2003
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");
        MimeMessage mimeMessage = createMimeMessage();
        messageName = "test + plus.EML";
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "0");
        session.createMessage("testfolder", messageName, properties, mimeMessage);
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder", session.isEqualTo("urlcompname", messageName));
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
    }

    /**
     * Test creating a non-draft message with a calendar invite (text/calendar method=REQUEST).
     * Regression test for <a href="https://github.com/mguessan/davmail/issues/572">#572</a>:
     * Graph APPEND fails with ErrorInvalidRequest for messages containing calendar invites.
     */
    public void testCreateMessageWithCalendarInvite() throws IOException, MessagingException {
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");

        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("To", "test@test.local");
        mimeMessage.setSubject("Meeting invite test");

        // build multipart with text + calendar invite
        javax.mail.internet.MimeMultipart multipart = new javax.mail.internet.MimeMultipart("alternative");
        javax.mail.internet.MimeBodyPart textPart = new javax.mail.internet.MimeBodyPart();
        textPart.setText("Please join the meeting", "UTF-8");
        multipart.addBodyPart(textPart);

        javax.mail.internet.MimeBodyPart calendarPart = new javax.mail.internet.MimeBodyPart();
        String ics = "BEGIN:VCALENDAR\r\n" +
                "METHOD:REQUEST\r\n" +
                "VERSION:2.0\r\n" +
                "BEGIN:VEVENT\r\n" +
                "DTSTART:20261022T100000Z\r\n" +
                "DTEND:20261022T110000Z\r\n" +
                "SUMMARY:Test meeting\r\n" +
                "UID:" + UUID.randomUUID() + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
        calendarPart.setContent(ics, "text/calendar; method=REQUEST; charset=UTF-8");
        multipart.addBodyPart(calendarPart);

        mimeMessage.setContent(multipart);
        mimeMessage.saveChanges();

        messageName = UUID.randomUUID() + ".EML";
        HashMap<String, String> properties = new HashMap<>();
        properties.put("draft", "0");
        session.createMessage("testfolder", messageName, properties, mimeMessage);

        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertFalse(messageList.get(0).draft);
    }

    /**
     * Test creating a non-draft message with an embedded message/rfc822 attachment.
     * Regression test for <a href="https://github.com/mguessan/davmail/issues/572">#572</a>:
     * Graph APPEND fails with ErrorInvalidProperty for messages containing embedded messages.
     */
    public void testCreateMessageWithEmbeddedMessage() throws IOException, MessagingException {
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");

        // create the inner message
        MimeMessage innerMessage = new MimeMessage((Session) null);
        innerMessage.addHeader("To", "inner@test.local");
        innerMessage.setSubject("Inner forwarded message");
        innerMessage.setText("This is the forwarded content\n");
        innerMessage.saveChanges();

        // create the outer message with the inner message attached
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("To", "test@test.local");
        mimeMessage.setSubject("Message with embedded rfc822");

        javax.mail.internet.MimeMultipart multipart = new javax.mail.internet.MimeMultipart("mixed");
        javax.mail.internet.MimeBodyPart textPart = new javax.mail.internet.MimeBodyPart();
        textPart.setText("See forwarded message below", "UTF-8");
        multipart.addBodyPart(textPart);

        javax.mail.internet.MimeBodyPart attachedMessage = new javax.mail.internet.MimeBodyPart();
        attachedMessage.setContent(innerMessage, "message/rfc822");
        attachedMessage.setDisposition(javax.mail.Part.ATTACHMENT);
        multipart.addBodyPart(attachedMessage);

        mimeMessage.setContent(multipart);
        mimeMessage.saveChanges();

        messageName = UUID.randomUUID() + ".EML";
        HashMap<String, String> properties = new HashMap<>();
        properties.put("draft", "0");
        session.createMessage("testfolder", messageName, properties, mimeMessage);

        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertFalse(messageList.get(0).draft);
    }

    /**
     * Cleanup
     */
    public void testDeleteFolder() throws IOException {
        session.deleteFolder("testfolder");
    }

}

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

import davmail.Settings;
import org.apache.log4j.Level;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;

/**
 * Test message flag update
 */
public class TestExchangeSessionMessageFlags extends AbstractExchangeSessionTestCase {

    @Override
    public void setUp() throws IOException {
        super.setUp();
        // recreate empty folder
        session.deleteFolder("testfolder");
        session.createMessageFolder("testfolder");
    }

    public void testCreateDraftMessage() throws MessagingException, IOException {
        MimeMessage mimeMessage = createMimeMessage();
        String messageName = UUID.randomUUID().toString()+".EML";
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "9");
        session.createMessage("testfolder", messageName, properties, getMimeBody(mimeMessage));
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertTrue(messageList.get(0).draft);
    }

    public void testCreateDraftReadMessage() throws MessagingException, IOException {
        MimeMessage mimeMessage = createMimeMessage();
        String messageName = UUID.randomUUID().toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "9");
        session.createMessage("testfolder", messageName, properties, getMimeBody(mimeMessage));
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertTrue(messageList.get(0).draft);
        assertTrue(messageList.get(0).read);
    }

    public void testCreateReadMessage() throws MessagingException, IOException {
        MimeMessage mimeMessage = createMimeMessage();
        String messageName = UUID.randomUUID().toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "1");
        session.createMessage("testfolder", messageName, properties, getMimeBody(mimeMessage));
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertFalse(messageList.get(0).draft);
        assertTrue(messageList.get(0).read);
    }

    public void testCreateBccMessage() throws MessagingException, IOException {
        Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        MimeMessage mimeMessage = createMimeMessage();
        String messageName = UUID.randomUUID().toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("draft", "8");
        properties.put("bcc", "testbcc@test.local");
        session.createMessage("testfolder", messageName, properties, getMimeBody(mimeMessage));
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
    }

    public void testCreateDateReceivedMessage() throws MessagingException, IOException {
        MimeMessage mimeMessage = createMimeMessage();
        String messageName = UUID.randomUUID().toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        SimpleDateFormat dateFormatter = ExchangeSession.getExchangeZuluDateFormat();
        dateFormatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        properties.put("datereceived", dateFormatter.format(cal.getTime()));
        session.createMessage("testfolder", messageName, properties, getMimeBody(mimeMessage));
        ExchangeSession.MessageList messageList = session.searchMessages("testfolder");
        assertNotNull(messageList);
        assertEquals(1, messageList.size());
        assertNotNull(messageList);
        // TODO: use same format for date read/write
        assertEquals(ExchangeSession.getZuluDateFormat().format(cal.getTime()), messageList.get(0).date);
    }


}

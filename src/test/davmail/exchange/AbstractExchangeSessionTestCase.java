/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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

import davmail.AbstractDavMailTestCase;
import davmail.Settings;
import davmail.exchange.dav.DavExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Exchange session test case.
 * Open a session to default DavMail server as found in user davmail.properties,
 * except if url is not null
 */
public class AbstractExchangeSessionTestCase extends AbstractDavMailTestCase {

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (session == null) {
            // open session, get username and password from davmail.properties
            // Note: those properties should *not* exist in normal production mode,
            // they are not used by DavMail, just by this test case
            if (Settings.getBooleanProperty("davmail.enableEws")) {
                session = new EwsExchangeSession(Settings.getProperty("davmail.url"), Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
            } else {
                session = new DavExchangeSession(Settings.getProperty("davmail.url"), Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
            }
        }
    }

    protected MimeMessage createMimeMessage() throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("To", "test@test.local");
        mimeMessage.setText("Test message");
        mimeMessage.setSubject("Test subject");
        return mimeMessage;
    }

    protected String getMimeBody(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        byte[] content = baos.toByteArray();
        return new String(content);
    }

}

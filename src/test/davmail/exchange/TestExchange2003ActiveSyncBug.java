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
import junit.framework.TestCase;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Test Exchange 2003 ActiveSync bug.
 */
public class TestExchange2003ActiveSyncBug extends TestCase {

    public byte[] buildContent() throws MessagingException, IOException {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("to", "testto <" + Settings.getProperty("davmail.to") + ">");
        mimeMessage.addHeader("cc", "testcc <" + Settings.getProperty("davmail.to") + ">");
        mimeMessage.setText("Test message ");
        mimeMessage.setSubject("Test subject ");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        byte[] content = baos.toByteArray();
        return content;

    }
    public void testMailFrom() throws IOException, MessagingException {
        byte[] mimeBody = buildContent();
        mimeBody = ("MAIL FROM: test \r\n\r\n"+new String(mimeBody)).getBytes("UTF-8");
        System.out.println(new String(mimeBody));
        System.out.println("******************************");

        System.out.println("length: " + mimeBody.length);
        MimeMessage mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(mimeBody));
        // workaround for Exchange 2003 ActiveSync bug
        if (mimeMessage.getHeader("MAIL FROM") != null) {
            byte[] mimeBodyCopy = new byte[((SharedByteArrayInputStream) mimeMessage.getRawInputStream()).available()];
            int offset = mimeBody.length - mimeBodyCopy.length;
            System.arraycopy(mimeBody, offset, mimeBodyCopy, 0, mimeBodyCopy.length);
            mimeBody = mimeBodyCopy;
            mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(mimeBody));
            System.out.println(new String(mimeBody));
        }
    }
}

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
package davmail;

import davmail.exchange.ExchangeSession;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import junit.framework.TestCase;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * DavMail generic test case.
 * Loads DavMail settings
 */
public class AbstractDavMailTestCase extends TestCase {
    protected static boolean loaded;
    protected static String url;
    protected static String certificateHash;
    protected static String username;
    protected static String password;
    protected static ExchangeSession session;

    @Override
    public void setUp() throws IOException {
        if (!loaded) {
            loaded = true;

            if (url == null) {
                // load test settings from a separate file
                File testFile = new File("test.properties");
                if (!testFile.exists()) {
                    throw new IOException("Please create a test.properties file with davmail.username and davmail.password");
                }
                Settings.setDefaultSettings();
                Settings.load(new FileInputStream(testFile));
                if (Settings.getProperty("davmail.username") != null) {
                    username = Settings.getProperty("davmail.username");
                }
                if (Settings.getProperty("davmail.password") != null) {
                    password = Settings.getProperty("davmail.password");
                }

            } else {
                Settings.setDefaultSettings();
                Settings.setProperty("davmail.url", url);
                Settings.setProperty("davmail.server.certificate.hash", certificateHash);
                Settings.setProperty("davmail.username", username);
                Settings.setProperty("davmail.password", password);
            }

            if (Settings.getBooleanProperty("davmail.enableKerberos", false)) {
                System.setProperty("java.security.krb5.realm", "CORP.COMPANY.COM");
                System.setProperty("java.security.krb5.kdc", "192.168.184.129");
            }


            DavGatewaySSLProtocolSocketFactory.register();
            // force server mode
            Settings.setProperty("davmail.server", "true");

            // enable WIRE debug log
            //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
            // enable EWS support
            //Settings.setProperty("davmail.enableEws", "false");

        }
    }

    protected MimeMessage createMimeMessage() throws MessagingException {
        return createMimeMessage("test@test.local");
    }

    protected MimeMessage createMimeMessage(String recipient) throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        mimeMessage.addHeader("To", recipient);
        mimeMessage.setText("Test message\n");
        mimeMessage.setSubject("Test subject");
        return mimeMessage;
    }

    protected byte[] getMimeBody(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(baos);
        return baos.toByteArray();
    }
}

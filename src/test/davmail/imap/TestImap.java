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
import davmail.exchange.AbstractExchangeSessionTestCase;

import java.io.*;
import java.net.Socket;

/**
 * IMAP tests, an instance of DavMail Gateway must be available
 */
public class TestImap extends AbstractExchangeSessionTestCase {
    static Socket clientSocket;
    static BufferedWriter socketWriter;
    static BufferedReader socketReader;

    protected void writeLine(String line) throws IOException {
        socketWriter.write(line);
        socketWriter.newLine();
        socketWriter.flush();
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
        super.setUp();
        if (clientSocket == null) {
            clientSocket = new Socket("localhost", Settings.getIntProperty("davmail.imapPort"));
            socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }
    }

    public void testBanner() throws IOException {
        String banner = socketReader.readLine();
        assertNotNull(banner);
    }

    public void testLogin() throws IOException {
        writeLine(". LOGIN " + Settings.getProperty("davmail.username").replaceAll("\\\\", "\\\\\\\\") + ' ' + Settings.getProperty("davmail.password"));
        assertEquals(". OK Authenticated", socketReader.readLine());
    }

    public void testSelectInbox() throws IOException {
        writeLine(". SELECT INBOX");
        assertEquals(". OK [READ-WRITE] SELECT completed", readFullAnswer("."));
    }

    public void testUidSearchDeleted() throws IOException {
        writeLine(". UID SEARCH UNDELETED");
        assertEquals(". OK SEARCH completed", readFullAnswer("."));
    }

    public void testUidSearchUndeleted() throws IOException {
        writeLine(". UID SEARCH DELETED");
        assertEquals(". OK SEARCH completed", readFullAnswer("."));
    }

    public void testLogout() throws IOException {
        writeLine(". LOGOUT");
        assertEquals("* BYE Closing connection", socketReader.readLine());
    }
}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2011  Mickael Guessant
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

import java.io.*;
import java.net.Socket;

/**
 * Abstract IMAP test case.
 */
public class AbstractImapTestCase extends AbstractDavMailTestCase {
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

}

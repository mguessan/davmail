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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * ICS Buffered Reader.
 * Read events by line, handle multiple line elements
 */
public class ICSBufferedReader extends BufferedReader {
    protected String nextLine;
    protected final StringBuilder currentLine = new StringBuilder(75);

    /**
     * Create an ICS reader on the provided reader
     *
     * @param in input reader
     * @throws IOException on error
     */
    public ICSBufferedReader(Reader in) throws IOException {
        super(in);
        nextLine = super.readLine();
    }

    /**
     * Read a line from input reader, unwrap long lines.
     */
    @Override
    public String readLine() throws IOException {
        if (nextLine == null) {
            return null;
        } else {
            currentLine.setLength(0);
            currentLine.append(nextLine);
            nextLine = super.readLine();
            while (nextLine != null && !(nextLine.length() == 0) &&
                    (nextLine.charAt(0) == ' ' || nextLine.charAt(0) == '\t'
                            // workaround for broken items with \n as first line character
                            || nextLine.charAt(0) == '\\'
                            // workaround for Exchange 2010 bug
                            || nextLine.charAt(0) == ':')) {
                // Timezone ends with \n => next line starts with :
                if (nextLine.charAt(0) == ':') {
                    currentLine.append(nextLine);
                } else {
                    currentLine.append(nextLine.substring(1));
                }
                nextLine = super.readLine();
            }
            return currentLine.toString();
        }
    }
}

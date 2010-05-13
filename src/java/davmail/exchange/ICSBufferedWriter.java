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

/**
 * ICS String writer.
 * split lines longer than 75 characters
 */
public class ICSBufferedWriter {
    final StringBuilder buffer = new StringBuilder();

    /**
     * Write content to buffer, do not split lines.
     *
     * @param content ics content
     */
    public void write(String content) {
        if (content != null) {
            buffer.append(content);
        }
    }

    /**
     * Write line to buffer, split lines at 75 characters.
     *
     * @param line ics event line
     */
    public void writeLine(String line) {
        if (line.length() > 75) {
            buffer.append(line.substring(0, 75));
            newLine();
            buffer.append(' ');
            writeLine(line.substring(75));
        } else {
            buffer.append(line);
            newLine();
        }
    }

    protected void newLine() {
        buffer.append((char) 13).append((char) 10);
    }

    /**
     * Get buffer as String
     *
     * @return ICS content as String
     */
    @Override
    public String toString() {
        return buffer.toString();
    }

}

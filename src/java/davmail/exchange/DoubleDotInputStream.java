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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Replace double dot lines with single dot in input stream.
 * A line with a single dot means end of stream
 */
public class DoubleDotInputStream extends PushbackInputStream {
    int[] buffer = new int[4];
    int index = -1;

    /**
     * @inheritDoc
     */
    public DoubleDotInputStream(InputStream in) {
        super(in, 3);
    }

    /**
     * Push current byte to buffer and read next byte.
     *
     * @param currentByte current byte
     * @return next byte
     * @throws IOException on error
     */
    protected int readNextByte() throws IOException {
        int b = super.read();
        buffer[++index] = b;
        return b;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == '\r') {
            // \r\n
            if (readNextByte() == '\n') {
                // \r\n.
                if (readNextByte() == '.') {
                    // \r\n.\r
                    if (readNextByte() == '\r') {
                        // \r\n.\r\n
                        if (readNextByte() == '\n') {
                            // end of stream
                            index = -1;
                            b = -1;
                        }
                        // \r\n..
                    } else if (buffer[index] == '.') {
                        // \r\n..\r
                        if ((readNextByte()) == '\r') {
                            // replace double dot
                            buffer[--index] = '\r';
                        }
                    }
                }
            }
            // push back characters
            if (index >= 0) {
                while(index >= 0) {
                    unread(buffer[index--]);
                }
            }
        }
        return b;
    }

}

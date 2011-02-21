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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * RFC 1939: 3 Basic Operations
 * [...]
 * If any line begins with the termination octet, the line is "byte-stuffed" by
 * pre-pending the termination octet to that line of the response.
 */
public class DoubleDotOutputStream extends FilterOutputStream {

    // remember last 2 bytes written
    int[] buf = {0, 0};

    /**
     * @inheritDoc
     */
    public DoubleDotOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '.' && (buf[0] == '\r' || buf[0] == '\n' || buf[0] == 0)) {
            // line starts with '.', prepend it with an additional '.'
            out.write('.');
        }
        out.write(b);

        buf[1] = buf[0];
        buf[0] = b;
    }

    /**
     * RFC 1939: 3 Basic Operations
     * [...]
     * Hence a multi-line response is terminated with the five octets
     * "CRLF.CRLF"
     * <p/>
     * Do not close actual outputstream
     *
     * @throws IOException on error
     */
    @Override
    public void close() throws IOException {
        if (buf[1] != '\r' || buf[0] != '\n') {
            out.write('\r');
            out.write('\n');
        }
        out.write('.');
        out.write('\r');
        out.write('\n');
    }

}

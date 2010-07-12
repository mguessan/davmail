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
 * Replace single dot lines with double dot.
 */
public class DoubleDotOutputStream extends FilterOutputStream {
    enum State {
        CR, CRLF, CRLFDOT
    }

    State currentState;

    /**
     * @inheritDoc
     */
    public DoubleDotOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        if (currentState == null && b == '\r') {
            currentState = State.CR;
        } else if (currentState == State.CR && b == '\n') {
            currentState = State.CRLF;
        } else if (currentState == State.CRLF && b == '.') {
            currentState = State.CRLFDOT;
        } else if (currentState == State.CRLFDOT && b == '\r') {
            out.write('.');
            currentState = null;
        } else {
            currentState = null;
        }
        out.write(b);
    }

}

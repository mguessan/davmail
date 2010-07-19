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
package davmail.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Input output functions.
 */
public class IOUtil {
    private IOUtil() {
    }

    /**
     * Write all inputstream content to outputstream.
     *
     * @param inputStream  input stream
     * @param outputStream output stream
     * @throws IOException on error
     */
    public static void write(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bytes = new byte[8192];
        int length;
        while ((length = inputStream.read(bytes)) > 0) {
            outputStream.write(bytes, 0, length);
        }
    }
}

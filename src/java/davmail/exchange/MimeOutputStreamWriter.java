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

import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mime OutputStreamWriter to build in memory Mime message.
 */
public class MimeOutputStreamWriter extends OutputStreamWriter {
    /**
     * Build MIME outputStreamWriter
     *
     * @param out outputstream
     * @throws UnsupportedEncodingException on error
     */
    public MimeOutputStreamWriter(OutputStream out) throws UnsupportedEncodingException {
        super(out, "ASCII");
    }

    /**
     * Write MIME header
     *
     * @param header header name
     * @param value  header value
     * @throws IOException on error
     */
    public void writeHeader(String header, String value) throws IOException {
        // do not write empty headers
        if (value != null && value.length() > 0) {
            write(header);
            write(": ");
            write(MimeUtility.encodeText(value, "UTF-8", null));
            writeLn();
        }
    }

    /**
     * Write MIME header
     *
     * @param header header name
     * @param value  header value
     * @throws IOException on error
     */
    public void writeHeader(String header, Date value) throws IOException {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss Z", Locale.ENGLISH);
        writeHeader(header, formatter.format(value));
    }

    /**
     * Write line.
     *
     * @param line line content
     * @throws IOException on error
     */
    public void writeLn(String line) throws IOException {
        write(line);
        write("\r\n");
    }

    /**
     * Write CRLF.
     *
     * @throws IOException on error
     */
    public void writeLn() throws IOException {
        write("\r\n");
    }

}

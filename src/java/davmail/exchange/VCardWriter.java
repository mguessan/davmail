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

/**
 * VCard Writer
 */
public class VCardWriter extends ICSBufferedWriter {
    public void startCard() {
        writeLine("BEGIN:VCARD");
        writeLine("VERSION:3.0");
    }

    public void appendProperty(String propertyName, String propertyValue) {
        if (propertyValue != null && propertyValue.length() > 0) {
            write(propertyName);
            write(":");
            if (propertyValue.indexOf('\n') >= 0) {
                writeLine(propertyValue.replaceAll("\\n", "\\\\n"));
            }
        }
    }

    public void appendProperty(String propertyName, String... propertyValue) {
        boolean hasValue = false;
        for (String value : propertyValue) {
            if ((value != null) && (value.length() > 0)) {
                hasValue = true;
                break;
            }
        }
        if (hasValue) {
            write(propertyName);
            write(":");
            boolean first = true;
            StringBuilder valueBuffer = new StringBuilder();
            for (String value : propertyValue) {
                if (first) {
                    first = false;
                } else {
                    valueBuffer.append(';');
                }
                if (value != null) {
                    valueBuffer.append(value);
                }
            }
            writeLine(valueBuffer.toString());
        }
    }

    public void endCard() {
        writeLine("END:VCARD");
    }
}

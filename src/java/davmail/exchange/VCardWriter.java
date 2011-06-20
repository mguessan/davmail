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
    /**
     * Begin VCard and version
     */
    public void startCard() {
        writeLine("BEGIN:VCARD");
        writeLine("VERSION:3.0");
    }

    /**
     * Append compound value
     *
     * @param propertyName  property name
     * @param propertyValue property values
     */
    public void appendProperty(String propertyName, String... propertyValue) {
        boolean hasValue = false;
        for (String value : propertyValue) {
            if ((value != null) && (value.length() > 0)) {
                hasValue = true;
                break;
            }
        }
        if (hasValue) {
            boolean first = true;
            StringBuilder lineBuffer = new StringBuilder();
            lineBuffer.append(propertyName);
            lineBuffer.append(':');
            for (String value : propertyValue) {
                if (first) {
                    first = false;
                } else {
                    lineBuffer.append(';');
                }
                appendEncodedValue(lineBuffer, value);
            }
            writeLine(lineBuffer.toString());
        }
    }

    /**
     * Encode and append value to buffer
     *
     * @param buffer current buffer
     * @param value  property value
     */
    private void appendEncodedValue(StringBuilder buffer, String value) {
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == ',' || c == ';') {
                    buffer.append('\\');
                }
                if (c == '\n') {
                    buffer.append("\\n");
                } else {
                    buffer.append(value.charAt(i));
                }
            }
        }
    }

    /**
     * End VCard
     */
    public void endCard() {
        writeLine("END:VCARD");
    }
}

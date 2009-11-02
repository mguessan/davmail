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
package davmail.util;

/**
 * Various string handling methods
 */
public class StringUtil {
    private StringUtil() {
    }

    /**
     * Return the characters between startDelimiter and endDelimiter or null.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @return token value
     */
    public static String getToken(String value, String startDelimiter, String endDelimiter) {
        String token = null;
        if (value != null) {
            int startIndex = value.indexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    token = value.substring(startIndex, endIndex);
                }
            }
        }
        return token;
    }
}

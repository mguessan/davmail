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

import davmail.exception.DavMailException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Date related conversion methods
 */
public class DateUtil {
    public static final String GRAPH_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String CALDAV_DATE_TIME = "yyyyMMdd'T'HHmmss";

    public static String convertDateFormat(String sourceDate, String sourceFormat, String targetFormat) throws DavMailException {
        String targetDate = null;
        if (sourceDate != null) {
            try {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(sourceFormat);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(targetFormat);
                LocalDateTime localDateTime = LocalDateTime.parse(sourceDate, parser);

                targetDate = localDateTime.format(formatter);
            } catch (DateTimeParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", sourceDate);
            }
        }
        return targetDate;
    }

    public static String convertToUTCDateFormat(String sourceDate, String sourceTimezone, String sourceFormat, String targetFormat) throws DavMailException {
        String targetDate = null;
        if (sourceDate != null && sourceTimezone != null) {
            try {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(sourceFormat);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(targetFormat);
                LocalDateTime localDateTime = LocalDateTime.parse(sourceDate, parser);

                // Convert to UTC
                ZonedDateTime zonedStart = localDateTime.atZone(ZoneId.of(sourceTimezone));
                ZonedDateTime utcStartDate = zonedStart.withZoneSameInstant(ZoneId.of("UTC"));
                targetDate = utcStartDate.format(formatter);
            } catch (DateTimeParseException e) {
                throw new DavMailException("EXCEPTION_INVALID_DATE", sourceDate);
            }
        }
        return targetDate;
    }
}

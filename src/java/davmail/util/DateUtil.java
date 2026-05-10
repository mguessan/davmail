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
import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;

/**
 * Date conversion methods
 */
public class DateUtil {

    protected static final Logger LOGGER = Logger.getLogger("davmail.util.DateUtil");

    public static final String GRAPH_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String CALDAV_DATE_TIME = "yyyyMMdd'T'HHmmss";

    /**
     * Standard to Exchange timezone mapping
     */
    public static final ResourceBundle STD_TO_EXCHANGE_TZ = ResourceBundle.getBundle("exchtimezones");
    /**
     * Standard to Exchange timezone mapping
     */
    public static final ResourceBundle EXCHANGE_TO_STD_TZ = ResourceBundle.getBundle("stdtimezones");

    public static final ResourceBundle VTIMEZONES = ResourceBundle.getBundle("vtimezones");



    public static String getExchangeTimeZone(String timezoneId) {
        if (EXCHANGE_TO_STD_TZ.containsKey(timezoneId)) {
            return timezoneId;
        } else if (STD_TO_EXCHANGE_TZ.containsKey(timezoneId)) {
            return STD_TO_EXCHANGE_TZ.getString(timezoneId);
        } else {
            LOGGER.warn("Unknown timezone: " + timezoneId);
            // fallback to UTC / Zulu
            return "UTC";
        }
    }

    public static String getStandardTimeZone(String timezoneId) {
        if (STD_TO_EXCHANGE_TZ.containsKey(timezoneId)) {
            return timezoneId;
        } else if (EXCHANGE_TO_STD_TZ.containsKey(timezoneId)) {
            return EXCHANGE_TO_STD_TZ.getString(timezoneId);
        } else {
            LOGGER.warn("Unknown timezone: " + timezoneId);
            // fallback to UTC / Zulu
            return "UTC";
        }
    }

    public static String getVTimeZone(String timezoneId) {
        String exchangeTimeZone = getExchangeTimeZone(timezoneId);
        if (VTIMEZONES.containsKey(exchangeTimeZone)) {
            return VTIMEZONES.getString(exchangeTimeZone);
        } else {
            LOGGER.warn("VTimezone not available for timezone: " + exchangeTimeZone);
            return VTIMEZONES.getString("UTC");
        }
    }

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

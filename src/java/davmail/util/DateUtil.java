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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;
import java.util.TimeZone;

/**
 * Date conversion methods
 */
public class DateUtil {

    protected static final Logger LOGGER = Logger.getLogger(DateUtil.class);

    public static final String GRAPH_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String CALDAV_DATE_TIME = "yyyyMMdd'T'HHmmss";

    public static final String YYYY_MM_DDTHH_MM_SS_SSSSSSS = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS";


    /**
     * Standard to Exchange timezone mapping
     */
    public static final ResourceBundle STD_TO_EXCHANGE_TZ = ResourceBundle.getBundle("exchtimezones");
    /**
     * Standard to Exchange timezone mapping
     */
    public static final ResourceBundle EXCHANGE_TO_STD_TZ = ResourceBundle.getBundle("stdtimezones");

    public static final ResourceBundle VTIMEZONES = ResourceBundle.getBundle("vtimezones");


    /**
     * Convert timezone to Exchange timezone.
     * @param timezoneId exchange or standard timezone id
     * @return exchange timezone or null if unknown
     */
    public static String getExchangeTimeZone(String timezoneId) {
        if (timezoneId == null) {
            return null;
        } else if (EXCHANGE_TO_STD_TZ.containsKey(timezoneId)) {
            return timezoneId;
        } else if (STD_TO_EXCHANGE_TZ.containsKey(timezoneId)) {
            return STD_TO_EXCHANGE_TZ.getString(timezoneId);
        } else if ("tzone://Microsoft/Utc".equals(timezoneId)) {
            return "UTC";
        } else if ("tzone://Microsoft/Custom".equals(timezoneId)) {
            return null;
        } else {
            LOGGER.warn("Unknown timezone: " + timezoneId);
            return null;
        }
    }

    /**
     * Convert timezone id to standard timezone id.
     * @param timezoneId exchange or standard timezone id
     * @return standard timezone or null if unknown
     */
    public static String getStandardTimeZone(String timezoneId) {
        if (timezoneId == null) {
            return null;
        } else if (STD_TO_EXCHANGE_TZ.containsKey(timezoneId)) {
            return timezoneId;
        } else if (EXCHANGE_TO_STD_TZ.containsKey(timezoneId)) {
            return EXCHANGE_TO_STD_TZ.getString(timezoneId);
        } else {
            LOGGER.warn("Unknown timezone: " + timezoneId);
            return null;
        }
    }

    public static TimeZone getTimeZone(String timezoneId) {
        String standardTimeZoneId = getStandardTimeZone(timezoneId);
        if (standardTimeZoneId != null) {
            return TimeZone.getTimeZone(standardTimeZoneId);
        } else {
            LOGGER.warn("Unknown timezone: " + timezoneId+", using UTC");
            return TimeZone.getTimeZone("UTC");
        }
    }

    public static String getVTimeZone(String timezoneId) {
        String exchangeTimeZone = getExchangeTimeZone(timezoneId);
        if (exchangeTimeZone != null && VTIMEZONES.containsKey(exchangeTimeZone)) {
            return VTIMEZONES.getString(exchangeTimeZone);
        } else {
            LOGGER.warn("VTimezone not available for timezone: " + timezoneId);
            return null;
        }
    }

    public static String convertDate(String sourceDate, String sourceFormat, String sourceTimezone,
                                           String targetFormat, String targetTimezone) throws DavMailException {
        return convertDate(sourceDate, sourceFormat, getTimeZone(sourceTimezone), targetFormat, getTimeZone(targetTimezone));
    }

    public static String convertDate(String sourceDate, String sourceFormat, TimeZone sourceTimezone,
                                     String targetFormat, TimeZone targetTimezone) throws DavMailException {
        String targetDate = null;
        SimpleDateFormat parser = new SimpleDateFormat(sourceFormat);
        parser.setTimeZone(sourceTimezone);
        SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
        formatter.setTimeZone(targetTimezone);
        try {
            targetDate = formatter.format(parser.parse(sourceDate));
        } catch (ParseException e) {
            LOGGER.error("Error converting date: " + sourceDate + " from " + sourceFormat + " to " + targetFormat, e);
            throw new DavMailException("EXCEPTION_INVALID_DATE", sourceDate);
        }
        return targetDate;
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

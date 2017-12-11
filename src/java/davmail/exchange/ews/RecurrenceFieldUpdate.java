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

package davmail.exchange.ews;

import davmail.exchange.ExchangeSession;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Handle calendar item recurrence update
 */
public class RecurrenceFieldUpdate extends FieldUpdate {
    private Date startDate;

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public enum RecurrencePattern {WeeklyRecurrence, DailyRecurrence, AbsoluteYearly, AbsoluteMonthly};
    RecurrencePattern recurrencePattern;
    int recurrenceInterval = 1;

    /**
     * Create recurrence field update.
     */
    public RecurrenceFieldUpdate() {
    }

    public void setRecurrencePattern(RecurrencePattern recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }

    public void setRecurrenceInterval(RecurrencePattern recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }

    /**
     * Write field to request writer.
     *
     * @param itemType item type
     * @param writer   request writer
     * @throws IOException on error
     */
    public void write(String itemType, Writer writer) throws IOException {
        if (itemType != null) {
            writer.write("<t:Set");
            writer.write(itemType);
            writer.write("Field>");
        }

        // do not try to set empty value on create
        if (itemType != null) {
            writer.write("<t:FieldURI FieldURI=\"calendar:Recurrence\"/>");
            writer.write("<t:CalendarItem>");

            writer.write("<t:Recurrence>");
            writer.write("<t:");
            writer.write(recurrencePattern.toString());
            writer.write(">");
            writer.write("<t:Interval>");
            writer.write(String.valueOf(recurrenceInterval));
            writer.write("</t:Interval>");
            writer.write("<t:DaysOfWeek>"+getDayOfWeek()+"</t:DaysOfWeek>");
            writer.write("</t:");
            writer.write(recurrencePattern.toString());
            writer.write(">");
            writer.write("<t:NoEndRecurrence><t:StartDate>"+getFormattedStartDate()+"</t:StartDate></t:NoEndRecurrence>");
            writer.write("</t:Recurrence>");

            writer.write("</t:CalendarItem>");
        }

        if (itemType != null) {
            writer.write("</t:Set");
            writer.write(itemType);
            writer.write("Field>");
        }
    }

    private String getDayOfWeek() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(startDate);
    }

    private String getFormattedStartDate() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(startDate);
    }

}

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
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * Handle calendar item recurrence update
 * See <a href="https://learn.microsoft.com/en-us/exchange/client-developer/exchange-web-services/recurrence-patterns-and-ews">...</a>
 */
public class RecurrenceFieldUpdate extends FieldUpdate {
    public static final Logger LOGGER = Logger.getLogger(RecurrenceFieldUpdate.class);
    static final HashMap<String, String> calDayToDayOfWeek = new HashMap<>();
    static {
        calDayToDayOfWeek.put("SU", "Sunday");
        calDayToDayOfWeek.put("MO", "Monday");
        calDayToDayOfWeek.put("TU", "Tuesday");
        calDayToDayOfWeek.put("WE", "Wednesday");
        calDayToDayOfWeek.put("TH", "Thursday");
        calDayToDayOfWeek.put("FR", "Friday");
        calDayToDayOfWeek.put("SA", "Saturday");
    }
    protected Date startDate;
    protected Date endDate;
    protected String count;
    protected HashSet<String> byDays = null;
    protected String dayOfMonth;
    protected String month;
    protected String dayOfWeekIndex;
    protected String firstDayOfWeek;

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public void setByDay(String[] days) {
        byDays = new HashSet<>();
        for (String day: days) {
            // strip leading positional index (e.g. "2MO" -> "MO", "-1FR" -> "FR")
            String dayCode = day.replaceAll("^-?\\d+", "");
            String value = calDayToDayOfWeek.get(dayCode);
            if (value == null) {
                LOGGER.warn("Invalid day value: "+day);
            } else {
                byDays.add(value);
            }
        }
        // fix recurrence for Thunderbird weekday pattern
        if (recurrencePattern == RecurrencePattern.DailyRecurrence) {
            recurrencePattern = RecurrencePattern.WeeklyRecurrence;
        }
    }

    public void setDayOfMonth(String dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public void setMonthFromByMonth(String byMonth) {
        this.month = convertByMonthToName(byMonth);
    }

    public void setDayOfWeekIndexFromByDay(String byDay) {
        this.dayOfWeekIndex = extractDayOfWeekIndex(byDay);
    }

    public void setFirstDayOfWeek(String firstDayOfWeek) {
        this.firstDayOfWeek = firstDayOfWeek;
    }

    public void setCount(String count) {
        this.count = count;
    }

    public enum RecurrencePattern {DailyRecurrence, WeeklyRecurrence, AbsoluteMonthlyRecurrence, AbsoluteYearlyRecurrence, RelativeMonthlyRecurrence, RelativeYearlyRecurrence}

    RecurrencePattern recurrencePattern;
    int recurrenceInterval = 1;

    /**
     * Create a recurrence field update.
     */
    public RecurrenceFieldUpdate() {
    }

    public void setRecurrencePattern(RecurrencePattern recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }

    public void setRecurrencePattern(String value) {
        if ("DAILY".equals(value)) {
            setRecurrencePattern(RecurrenceFieldUpdate.RecurrencePattern.DailyRecurrence);
        } else if ("WEEKLY".equals(value)) {
            setRecurrencePattern(RecurrencePattern.WeeklyRecurrence);
        } else if ("MONTHLY".equals(value)) {
            setRecurrencePattern(RecurrencePattern.AbsoluteMonthlyRecurrence);
        } else if ("YEARLY".equals(value)) {
            setRecurrencePattern(RecurrencePattern.AbsoluteYearlyRecurrence);
        } else if ("RELATIVEMONTHLY".equals(value)) {
            setRecurrencePattern(RecurrencePattern.RelativeMonthlyRecurrence);
        } else if ("RELATIVEYEARLY".equals(value)) {
            setRecurrencePattern(RecurrencePattern.RelativeYearlyRecurrence);
        }
    }

    public void setRecurrenceInterval(String interval) {
        this.recurrenceInterval = Integer.parseInt(interval);
    }

    /**
     * Write this field to request writer.
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
            if (recurrencePattern == RecurrencePattern.AbsoluteYearlyRecurrence) {
                writeDayOfMonth(writer);
                writeMonth(writer);
            } else if (recurrencePattern == RecurrencePattern.AbsoluteMonthlyRecurrence) {
                writeInterval(writer);
                writeDayOfMonth(writer);
            } else if (recurrencePattern == RecurrencePattern.RelativeYearlyRecurrence) {
                writeDaysOfWeek(writer);
                writeDayOfWeekIndex(writer);
                writeMonth(writer);
            } else if (recurrencePattern == RecurrencePattern.RelativeMonthlyRecurrence) {
                writeInterval(writer);
                writeDaysOfWeek(writer);
                writeDayOfWeekIndex(writer);
            } else if (recurrencePattern == RecurrencePattern.WeeklyRecurrence) {
                writeInterval(writer);
                writeDaysOfWeek(writer);
                if (firstDayOfWeek != null) {
                    writeFirstDayOfWeek(writer);
                }
            } else if (recurrencePattern == RecurrencePattern.DailyRecurrence) {
                writeInterval(writer);
            }
            writer.write("</t:");
            writer.write(recurrencePattern.toString());
            writer.write(">");
            writeStartEnd(writer);
            writer.write("</t:Recurrence>");

            writer.write("</t:CalendarItem>");
        }

        if (itemType != null) {
            writer.write("</t:Set");
            writer.write(itemType);
            writer.write("Field>");
        }
    }

    private void writeInterval(Writer writer) throws IOException {
        writer.write("<t:Interval>");
        writer.write(String.valueOf(recurrenceInterval));
        writer.write("</t:Interval>");
    }

    private void writeStartEnd(Writer writer) throws IOException {
        if (count != null) {
            writer.write("<t:NumberedRecurrence>");
            writer.write("<t:StartDate>");
            writer.write(getFormattedDate(startDate));
            writer.write("</t:StartDate>");
            writer.write("<t:NumberOfOccurrences>");
            writer.write(count);
            writer.write("</t:NumberOfOccurrences>");
            writer.write("</t:NumberedRecurrence>");
        } else if (endDate == null) {
            writer.write("<t:NoEndRecurrence><t:StartDate>");
            writer.write(getFormattedDate(startDate));
            writer.write("</t:StartDate></t:NoEndRecurrence>");
        } else {
            writer.write("<t:EndDateRecurrence>");
            writer.write("<t:StartDate>");
            writer.write(getFormattedDate(startDate));
            writer.write("</t:StartDate>");
            writer.write("<t:EndDate>");
            writer.write(getFormattedDate(endDate));
            writer.write("</t:EndDate>");
            writer.write("</t:EndDateRecurrence>");

        }
    }

    private void writeDaysOfWeek(Writer writer) throws IOException {
        writer.write("<t:DaysOfWeek>");
        if (byDays != null) {
            boolean first = true;
            for (String dayOfeek:byDays) {
                if (first) {
                    first = false;
                } else {
                    writer.write(' ');
                }
                writer.write(dayOfeek);
            }
        } else {
            writer.write(getDayOfWeek());
        }
        writer.write("</t:DaysOfWeek>");
    }

    private void writeDayOfMonth(Writer writer) throws IOException {
        writer.write("<t:DayOfMonth>");
        writer.write(dayOfMonth != null ? dayOfMonth : getDayOfMonth());
        writer.write("</t:DayOfMonth>");
    }

    private void writeMonth(Writer writer) throws IOException {
        writer.write("<t:Month>");
        writer.write(month != null ? month : getMonth());
        writer.write("</t:Month>");
    }

    private void writeDayOfWeekIndex(Writer writer) throws IOException {
        writer.write("<t:DayOfWeekIndex>");
        writer.write(dayOfWeekIndex != null ? dayOfWeekIndex : "First");
        writer.write("</t:DayOfWeekIndex>");
    }

    private void writeFirstDayOfWeek(Writer writer) throws IOException {
        writer.write("<t:FirstDayOfWeek>");
        writer.write(firstDayOfWeek);
        writer.write("</t:FirstDayOfWeek>");
    }

    private String getDayOfWeek() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(startDate);
    }

    private String getMonth() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(startDate);
    }

    private String getDayOfMonth() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("d", Locale.ENGLISH);
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(startDate);
    }

    private String getFormattedDate(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(ExchangeSession.GMT_TIMEZONE);
        return simpleDateFormat.format(date);
    }

    private String extractDayOfWeekIndex(String byDay) {
        // extract numeric prefix from first entry, e.g. "2MO" -> "Second", "-1FR" -> "Last"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)").matcher(byDay);
        if (m.find()) {
            switch (m.group(1)) {
                case "2":  return "Second";
                case "3":  return "Third";
                case "4":  return "Fourth";
                case "-1": return "Last";
                default:   return "First";
            }
        }
        return "First";
    }

    private static final String[] MONTH_NAMES = {
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
    };

    private String convertByMonthToName(String byMonth) {
        try {
            int m = Integer.parseInt(byMonth);
            if (m >= 1 && m <= 12) return MONTH_NAMES[m - 1];
        } catch (NumberFormatException e) {
            // ignore
        }
        return byMonth;
    }


}

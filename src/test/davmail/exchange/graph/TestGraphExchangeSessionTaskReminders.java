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

package davmail.exchange.graph;

import davmail.exchange.AbstractExchangeSessionTestCase;
import davmail.exchange.ExchangeSession;
import davmail.exchange.VCalendar;
import davmail.exchange.VObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * To Do task reminders over CalDAV: VALARM on VTODO maps to reminderDateTime.
 */
public class TestGraphExchangeSessionTaskReminders extends AbstractExchangeSessionTestCase {

    protected String getFolderPath() {
        return "/users/" + session.getEmail() + "/calendar";
    }

    protected String buildVTodo(String summary, String due, String alarm) {
        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//davmail//test//EN\r\n" +
                "BEGIN:VTODO\r\n" +
                "UID:" + UUID.randomUUID() + "\r\n" +
                "DTSTAMP:20261015T120000Z\r\n" +
                "SUMMARY:" + summary + "\r\n" +
                // due is a value, or parameters and value starting with ';'
                (due != null ? "DUE" + (due.startsWith(";") ? due : ":" + due) + "\r\n" : "") +
                (alarm != null ? "BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Reminder\r\n" + alarm + "\r\nEND:VALARM\r\n" : "") +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n";
    }

    /**
     * Find a task by summary, task item names are Graph ids.
     */
    protected ExchangeSession.Event findTask(String summary) throws IOException {
        for (ExchangeSession.Event event : session.searchTasksOnly(getFolderPath())) {
            if (event.getBody().contains("SUMMARY:" + summary)) {
                return event;
            }
        }
        return null;
    }

    protected String createAndGetBody(String summary, String due, String alarm) throws IOException {
        session.createOrUpdateItem(getFolderPath(), UUID.randomUUID() + ".EML", buildVTodo(summary, due, alarm), null, "*");
        ExchangeSession.Event task = findTask(summary);
        assertNotNull(task);
        return task.getBody();
    }

    protected void deleteTask(String summary) throws IOException {
        ExchangeSession.Event task = findTask(summary);
        if (task != null) {
            session.deleteItem(getFolderPath(), task.getName());
        }
    }

    public void testAbsoluteReminder() throws IOException {
        String summary = "davmail reminder test " + UUID.randomUUID();
        try {
            String body = createAndGetBody(summary, null, "TRIGGER;VALUE=DATE-TIME:20261230T140000Z");
            assertTrue(body, body.contains("TRIGGER;VALUE=DATE-TIME:20261230T140000Z"));
        } finally {
            deleteTask(summary);
        }
    }

    public void testRelativeReminder() throws IOException {
        String summary = "davmail reminder test " + UUID.randomUUID();
        try {
            // 15 minutes before due
            String body = createAndGetBody(summary, "20261231T120000Z", "TRIGGER;RELATED=END:-PT15M");
            assertTrue(body, body.contains("TRIGGER;VALUE=DATE-TIME:20261231T114500Z"));
        } finally {
            deleteTask(summary);
        }
    }

    public void testRelativeReminderOnDateOnlyDue() throws IOException {
        String summary = "davmail reminder test " + UUID.randomUUID();
        try {
            // date only due starts at local midnight in the mailbox timezone
            VObject vTimezone = session.getVTimezone();
            TimeZone timeZone = new VCalendar(buildVTodo(summary, null, null).getBytes(StandardCharsets.UTF_8), session.getEmail(), vTimezone)
                    .getStandardTimezoneId(vTimezone.getPropertyValue("TZID"));
            Calendar expected = Calendar.getInstance(timeZone);
            expected.clear();
            expected.set(2026, Calendar.DECEMBER, 31, 0, 0, 0);
            expected.add(Calendar.HOUR_OF_DAY, -6);
            SimpleDateFormat zuluFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
            zuluFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            String body = createAndGetBody(summary, ";VALUE=DATE:20261231", "TRIGGER;RELATED=END:-PT6H");
            assertTrue(body, body.contains("TRIGGER;VALUE=DATE-TIME:" + zuluFormat.format(expected.getTime())));
        } finally {
            deleteTask(summary);
        }
    }

    public void testUpdateWithoutAlarmKeepsReminder() throws IOException {
        String summary = "davmail reminder test " + UUID.randomUUID();
        try {
            createAndGetBody(summary, null, "TRIGGER;VALUE=DATE-TIME:20261230T140000Z");
            // clients without task alarms, e.g. org-caldav, send updates without VALARM
            ExchangeSession.Event task = findTask(summary);
            assertNotNull(task);
            session.createOrUpdateItem(getFolderPath(), task.getName(), buildVTodo(summary, null, null), null, null);
            String body = findTask(summary).getBody();
            assertTrue(body, body.contains("TRIGGER;VALUE=DATE-TIME:20261230T140000Z"));
        } finally {
            deleteTask(summary);
        }
    }

    public void testNoReminder() throws IOException {
        String summary = "davmail reminder test " + UUID.randomUUID();
        try {
            String body = createAndGetBody(summary, null, null);
            assertFalse(body, body.contains("BEGIN:VALARM"));
        } finally {
            deleteTask(summary);
        }
    }
}

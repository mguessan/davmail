/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2026  Franklin Tranié
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

import davmail.Settings;
import junit.framework.TestCase;

public class TestSentCopies extends TestCase {
    private static final String USER = "user@example.com";

    @Override
    protected void setUp() {
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.imapDropSentCopy", "true");
        SentCopies.clear();
    }

    public void testDropsOneAppendToSentAfterSend() {
        SentCopies.record(USER, "<ABC@example.com>");
        assertFalse(SentCopies.consume(USER, "INBOX", "<abc@example.com>"));
        assertFalse(SentCopies.consume(USER, "Drafts", "<abc@example.com>"));
        assertTrue(SentCopies.consume(USER, "Sent", " <abc@example.com> "));
        assertFalse(SentCopies.consume(USER, "Sent", "<abc@example.com>"));
    }

    public void testOtherUserOrMailbox() {
        SentCopies.record(USER, "<x@example.com>");
        assertFalse(SentCopies.consume("other@example.com", "Sent", "<x@example.com>"));
        assertFalse(SentCopies.consume(USER, "/users/shared@example.com/Sent", "<x@example.com>"));
        assertFalse(SentCopies.consume(USER, "Sent/Archive", "<x@example.com>"));
        assertTrue(SentCopies.consume(USER.toUpperCase(), "SENT", "<x@example.com>"));
    }

    public void testUnknownOrMissingMessageId() {
        assertFalse(SentCopies.consume(USER, "Sent", "<never@example.com>"));
        assertFalse(SentCopies.consume(USER, "Sent", null));
        SentCopies.record(USER, null);
        SentCopies.record(USER, "  ");
        SentCopies.record(null, "<y@example.com>");
        assertFalse(SentCopies.consume(USER, "Sent", "<y@example.com>"));
    }

    public void testDisabledByDefault() {
        Settings.setDefaultSettings();
        SentCopies.record(USER, "<z@example.com>");
        assertFalse(SentCopies.consume(USER, "Sent", "<z@example.com>"));
    }

    public void testBounded() {
        for (int i = 0; i < SentCopies.MAX_ENTRIES + 10; i++) {
            SentCopies.record(USER, "<" + i + "@example.com>");
        }
        assertFalse(SentCopies.consume(USER, "Sent", "<0@example.com>"));
        assertTrue(SentCopies.consume(USER, "Sent", "<" + (SentCopies.MAX_ENTRIES + 9) + "@example.com>"));
    }
}

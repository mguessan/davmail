/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2015  Mickael Guessant
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
package davmail.imap;

import junit.framework.TestCase;

/**
 * Test IMAPTokenizer
 */
public class TestImapTokenizer extends TestCase {
    public void testCreateFolder() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer(". CREATE testfolder");
        assertEquals(".", imapTokenizer.nextQuotedToken());
        assertEquals("CREATE", imapTokenizer.nextQuotedToken());
        assertEquals("testfolder", imapTokenizer.nextQuotedToken());
        assertEquals(false, imapTokenizer.hasMoreTokens());
    }

    public void testStoreFlags() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer(". UID STORE 10 -FLAGS (\\Deleted)");
        assertEquals(".", imapTokenizer.nextQuotedToken());
        assertEquals("UID", imapTokenizer.nextQuotedToken());
        assertEquals("STORE", imapTokenizer.nextQuotedToken());
        assertEquals("10", imapTokenizer.nextQuotedToken());
        assertEquals("-FLAGS", imapTokenizer.nextQuotedToken());
        assertEquals("(\\Deleted)", imapTokenizer.nextQuotedToken());
        assertEquals(false, imapTokenizer.hasMoreTokens());
    }

    public void testSearchHeader() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer(". UID SEARCH HEADER X-OfflineIMAP \"testvalue\"");
        assertEquals(".", imapTokenizer.nextQuotedToken());
        assertEquals("UID", imapTokenizer.nextQuotedToken());
        assertEquals("SEARCH", imapTokenizer.nextQuotedToken());
        assertEquals("HEADER", imapTokenizer.nextQuotedToken());
        assertEquals("X-OfflineIMAP", imapTokenizer.nextQuotedToken());
        assertEquals("\"testvalue\"", imapTokenizer.nextQuotedToken());
        assertEquals(false, imapTokenizer.hasMoreTokens());
    }

    public void testCreateQuotedFolder() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer(". CREATE \"test \\\"quoted\\\" folder\"");
        assertEquals(".", imapTokenizer.nextQuotedToken());
        assertEquals("CREATE", imapTokenizer.nextQuotedToken());
        assertEquals("\"test \\\"quoted\\\" folder\"", imapTokenizer.nextQuotedToken());
        assertEquals(false, imapTokenizer.hasMoreTokens());
    }



    public void testComplexSearch() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer(". UID SEARCH UNDELETED (OR (OR (OR FROM \"test\" OR TO \"test\" HEADER CC \"test\") SUBJECT test) BODY \"test\")");
        assertEquals(".", imapTokenizer.nextQuotedToken());
        assertEquals("UID", imapTokenizer.nextQuotedToken());
        assertEquals("SEARCH", imapTokenizer.nextQuotedToken());
        assertEquals("UNDELETED", imapTokenizer.nextQuotedToken());
        assertEquals("(OR (OR (OR FROM \"test\" OR TO \"test\" HEADER CC \"test\") SUBJECT test) BODY \"test\")", imapTokenizer.nextQuotedToken());

        assertEquals(false, imapTokenizer.hasMoreTokens());
    }

    public void testAppend() {
        ImapConnection.ImapTokenizer imapTokenizer = new ImapConnection.ImapTokenizer("2 append \"INBOX\" (\\Seen) \"01-Mar-2015 20:43:04 +0100\" {4608}");
        assertEquals("2", imapTokenizer.nextQuotedToken());
        assertEquals("append", imapTokenizer.nextQuotedToken());
        assertEquals("\"INBOX\"", imapTokenizer.nextQuotedToken());
        assertEquals("(\\Seen)", imapTokenizer.nextQuotedToken());
        assertEquals("\"01-Mar-2015 20:43:04 +0100\"", imapTokenizer.nextQuotedToken());
        assertEquals("{4608}", imapTokenizer.nextQuotedToken());

        assertEquals(false, imapTokenizer.hasMoreTokens());
    }


}

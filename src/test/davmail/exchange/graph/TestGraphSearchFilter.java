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

import davmail.exchange.ExchangeSession;
import junit.framework.TestCase;

public class TestGraphSearchFilter extends TestCase {

    private GraphExchangeSession session;

    @Override
    protected void setUp() {
        session = new GraphExchangeSession();
    }

    public void testFromFilterContains() {
        ExchangeSession.Condition condition = session.contains("from", "sender@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(contains(from/emailAddress/address,'sender@example.com') or contains(from/emailAddress/name,'sender@example.com'))", buffer.toString());
    }

    public void testFromFilterStartsWith() {
        ExchangeSession.Condition condition = session.startsWith("from", "sender");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(startswith(from/emailAddress/address,'sender') or startswith(from/emailAddress/name,'sender'))", buffer.toString());
    }

    public void testFromFilterIsEqualTo() {
        ExchangeSession.Condition condition = session.isEqualTo("from", "sender@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(from/emailAddress/address eq 'sender@example.com' or from/emailAddress/name eq 'sender@example.com')", buffer.toString());
    }

    public void testToFilterContains() {
        ExchangeSession.Condition condition = session.contains("to", "recipient@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E04' and contains(ep/value,'recipient@example.com'))", buffer.toString());
    }

    public void testCcFilterContains() {
        ExchangeSession.Condition condition = session.contains("cc", "cc@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E03' and contains(ep/value,'cc@example.com'))", buffer.toString());
    }

    public void testBccFilterContains() {
        ExchangeSession.Condition condition = session.contains("bcc", "bcc@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E02' and contains(ep/value,'bcc@example.com'))", buffer.toString());
    }

    public void testDisplayToFilter() {
        ExchangeSession.Condition condition = session.contains("displayto", "recipient@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E04' and contains(ep/value,'recipient@example.com'))", buffer.toString());
    }

    public void testHeaderIsEqualToFrom() {
        ExchangeSession.Condition condition = session.headerIsEqualTo("from", "sender@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(contains(from/emailAddress/address,'sender@example.com') or contains(from/emailAddress/name,'sender@example.com'))", buffer.toString());
    }

    public void testHeaderIsEqualToTo() {
        ExchangeSession.Condition condition = session.headerIsEqualTo("to", "recipient@example.com");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E04' and contains(ep/value,'recipient@example.com'))", buffer.toString());
    }

    public void testHeaderIsEqualToMessageId() {
        ExchangeSession.Condition condition = session.headerIsEqualTo("message-id", "<test-id@example.com>");
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("internetMessageId eq '<test-id@example.com>'", buffer.toString());
    }

    public void testCombinedAndFilter() {
        ExchangeSession.Condition condition = session.and(
                session.contains("from", "sender@example.com"),
                session.isFalse("read")
        );
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(contains(from/emailAddress/address,'sender@example.com') or contains(from/emailAddress/name,'sender@example.com')) And isRead eq false", buffer.toString());
    }

    public void testCombinedOrFilter() {
        ExchangeSession.Condition condition = session.or(
                session.contains("from", "sender@example.com"),
                session.contains("to", "recipient@example.com")
        );
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        assertEquals("(contains(from/emailAddress/address,'sender@example.com') or contains(from/emailAddress/name,'sender@example.com')) Or singleValueExtendedProperties/Any(ep: ep/id eq 'String 0x0E04' and contains(ep/value,'recipient@example.com'))", buffer.toString());
    }
}

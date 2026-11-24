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

import davmail.Settings;
import davmail.exchange.ExchangeSession;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the OData filter generated for internet header search
 * conditions (IMAP FROM/TO/CC search over Graph), in both the legacy form
 * and the opt-in improved form (davmail.improvedHeaderSearch).
 */
public class TestGraphSearchCondition {

    private String buildFilter(String attributeName, String value) {
        GraphExchangeSession.AttributeCondition condition =
                new GraphExchangeSession.AttributeCondition(attributeName, ExchangeSession.Operator.Contains, value);
        StringBuilder buffer = new StringBuilder();
        condition.appendTo(buffer);
        return buffer.toString();
    }

    private void enableImprovedHeaderSearch(boolean value) {
        Settings.setProperty("davmail.improvedHeaderSearch", String.valueOf(value));
    }

    @After
    public void resetFlag() {
        enableImprovedHeaderSearch(false);
    }

    @Test
    public void testDefaultOffKeepsLegacyFilter() {
        enableImprovedHeaderSearch(false);
        assertEquals(GraphExchangeSession.buildInternetHeadersContains("from", "Alice"),
                buildFilter("from", "Alice"));
    }

    @Test
    public void testImprovedFromNameUsesTwoLambdas() {
        enableImprovedHeaderSearch(true);
        assertEquals("(singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, 'from: Alice'))"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, ' Alice <')))",
                buildFilter("from", "Alice"));
    }

    @Test
    public void testImprovedFromSurnameMatchesMidLineForm() {
        // header line is "From: Alice Doe <alice@example.com>": legacy 'from: Doe' alone would not match
        enableImprovedHeaderSearch(true);
        assertEquals("(singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, 'from: Doe'))"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, ' Doe <')))",
                buildFilter("from", "Doe"));
    }

    @Test
    public void testImprovedFromAddressUsesStructuredFilterPlusBracketedContains() {
        enableImprovedHeaderSearch(true);
        assertEquals("(from/emailAddress/address eq 'alice@example.com'"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, '<alice@example.com>')))",
                buildFilter("from", "alice@example.com"));
    }

    @Test
    public void testImprovedToAddressUsesToRecipientsFilter() {
        enableImprovedHeaderSearch(true);
        assertEquals("(toRecipients/any(r:r/emailAddress/address eq 'bob@example.com')"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, '<bob@example.com>')))",
                buildFilter("to", "bob@example.com"));
    }

    @Test
    public void testImprovedCcAddressUsesCcRecipientsFilter() {
        enableImprovedHeaderSearch(true);
        assertEquals("(ccRecipients/any(r:r/emailAddress/address eq 'carol@example.com')"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, '<carol@example.com>')))",
                buildFilter("cc", "carol@example.com"));
    }

    @Test
    public void testFullHeaderValueFallsBackToHeaderContains() {
        // not a bare address: must keep the header contains path, not a never-matching eq
        enableImprovedHeaderSearch(true);
        assertEquals("(singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, 'from: Alice Doe <alice@example.com>'))"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, ' Alice Doe <alice@example.com> <')))",
                buildFilter("from", "Alice Doe <alice@example.com>"));
    }

    @Test
    public void testImprovedConditionInsideMultiConditionStaysParenthesized() {
        enableImprovedHeaderSearch(true);
        GraphExchangeSession.AttributeCondition fromCondition =
                new GraphExchangeSession.AttributeCondition("from", ExchangeSession.Operator.Contains, "alice@example.com");
        GraphExchangeSession.AttributeCondition subjectCondition =
                new GraphExchangeSession.AttributeCondition("subject", ExchangeSession.Operator.Contains, "report");
        GraphExchangeSession.MultiCondition andCondition =
                new GraphExchangeSession.MultiCondition(ExchangeSession.Operator.And, fromCondition, subjectCondition);
        StringBuilder buffer = new StringBuilder();
        andCondition.appendTo(buffer);
        assertEquals(buildFilter("from", "alice@example.com") + " And contains(subject,'report')",
                buffer.toString());
    }

    @Test
    public void testQuoteInValueIsEscaped() {
        enableImprovedHeaderSearch(true);
        assertEquals("(singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, 'from: O''Brien'))"
                        + " or singleValueExtendedProperties/any(ep:ep/id eq 'String 0x007D' and contains(ep/value, ' O''Brien <')))",
                buildFilter("from", "O'Brien"));
    }
}

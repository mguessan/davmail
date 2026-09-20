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
import davmail.util.DateUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.ResourceBundle;

public class TestGraphConvertDate extends junit.framework.TestCase {
    public void testConvertDate() throws IOException {
        Calendar date = Calendar.getInstance();
        String inputDateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date.getTime());
        String outputDateString = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(date.getTime());

        // yyyy-MM-dd'T'HH:mm:ss'Z' to yyyyMMdd'T'HHmmss'Z'
        assertEquals(outputDateString, GraphExchangeSession.convertDateFromExchange(inputDateString));

        // test with nanosecs
        inputDateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSSSSSS'Z'").format(date.getTime());
        outputDateString = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(date.getTime());

        assertEquals(outputDateString, GraphExchangeSession.convertDateFromExchange(inputDateString));

        // test with short nanosecs
        inputDateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS'Z'").format(date.getTime());
        outputDateString = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(date.getTime());

        assertEquals(outputDateString, GraphExchangeSession.convertDateFromExchange(inputDateString));

        // test without zulu tag
        inputDateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSSSSSS").format(date.getTime());
        outputDateString = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(date.getTime());

        assertEquals(outputDateString, GraphExchangeSession.convertDateFromExchange(inputDateString));

        // test without zulu tag, short nanosecs
        inputDateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS").format(date.getTime());
        outputDateString = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(date.getTime());

        assertEquals(outputDateString, GraphExchangeSession.convertDateFromExchange(inputDateString));
    }

    public void testTimeZoneMapping() throws IOException {
        ResourceBundle vtimezones = ResourceBundle.getBundle("vtimezones");
        Enumeration<String> tzKeys = vtimezones.getKeys();
        while (tzKeys.hasMoreElements()) {
            String tzKey = tzKeys.nextElement();
            ArrayList<String> missing = new ArrayList<>();
            if (!(DateUtil.EXCHANGE_TO_STD_TZ.containsKey(tzKey))) {
                System.out.println("Missing from EXCHANGE_TO_STD_TZ: " + tzKey);
                missing.add(tzKey);
            }
            assertEquals(0, missing.size());
        }

        assertNotNull(DateUtil.getVTimeZone("UTC"));
        assertNotNull(DateUtil.getVTimeZone("tzone://Microsoft/Utc"));

    }

    public void testKeywordAndUnkeywordSearchExpression() {
        ExchangeSession.Condition keywordCondition = new GraphExchangeSession.AttributeCondition("keywords", ExchangeSession.Operator.IsEqualTo, "Paperless");
        StringBuilder buffer = new StringBuilder();
        keywordCondition.appendTo(buffer);
        assertEquals("categories/any(a:a eq 'Paperless')", buffer.toString());

        ExchangeSession.Condition unkeywordCondition = new GraphExchangeSession.NotCondition(keywordCondition);
        buffer.setLength(0);
        unkeywordCondition.appendTo(buffer);
        assertEquals("not (categories/any(a:a eq 'Paperless'))", buffer.toString());
    }

    public void testSinceDateSearchExpression() {
        ExchangeSession.Condition sinceCondition = new GraphExchangeSession.AttributeCondition("date", ExchangeSession.Operator.IsGreaterThanOrEqualTo, "2026-08-21T00:00:00Z");
        StringBuilder buffer = new StringBuilder();
        sinceCondition.appendTo(buffer);
        assertEquals("receivedDateTime ge 2026-08-21T00:00:00Z", buffer.toString());
    }

}

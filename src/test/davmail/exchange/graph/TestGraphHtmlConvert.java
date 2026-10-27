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

import junit.framework.TestCase;

public class TestGraphHtmlConvert extends TestCase {

    public void testSimpleText() {
        assertEquals("hello", GraphExchangeSession.convertHtmlToText("<p>hello</p>"));
    }

    public void testNestedNoDuplication() {
        // nested elements must not duplicate text
        String result = GraphExchangeSession.convertHtmlToText("<div><p>word</p></div>");
        assertEquals("word", result);
    }

    public void testDeepNesting() {
        String result = GraphExchangeSession.convertHtmlToText("<div><span><b>abc</b></span></div>");
        assertEquals("abc", result);
    }

    public void testMultipleParagraphs() {
        String result = GraphExchangeSession.convertHtmlToText("<p>first</p><p>second</p>");
        assertEquals("firstsecond", result);
    }

    public void testEntitiesDecoded() {
        String result = GraphExchangeSession.convertHtmlToText("<p>a&amp;b</p>");
        assertEquals("a&b", result);
    }
}

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

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

/**
 * Test null character reference filtering in EWS SOAP responses.
 * Exchange may return &#x0; or &#0; in property values (e.g. null-padded WCHAR timezone names
 * from ActiveSync/TbSync clients) which are illegal in XML 1.0 and 1.1.
 */
public class TestEWSNullCharacterFilter extends TestCase {

    public void testNoNullReference() {
        byte[] input = "<t:StartTimeZone Id=\"W. Europe Standard Time\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertSame("Clean input should return same array", input, result);
    }

    public void testHexNullReference() {
        byte[] input = "<t:StartTimeZone Id=\"W. Europe Standard Time&#x0;\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<t:StartTimeZone Id=\"W. Europe Standard Time\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }

    public void testDecimalNullReference() {
        byte[] input = "<t:StartTimeZone Id=\"W. Europe Standard Time&#0;\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<t:StartTimeZone Id=\"W. Europe Standard Time\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }

    public void testMultipleNullReferences() {
        byte[] input = "<t:StartTimeZone Id=\"TZ&#x0;&#x0;&#x0;Name\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<t:StartTimeZone Id=\"TZName\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }

    public void testPaddedHexNullReference() {
        // &#x00; and &#x000; are also null character references
        byte[] input = "<Value>text&#x00;&#x000;end</Value>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<Value>textend</Value>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }

    public void testPaddedDecimalNullReference() {
        // &#00; and &#000; are also null character references
        byte[] input = "<Value>text&#00;&#000;end</Value>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<Value>textend</Value>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }

    public void testNonNullEntityPreserved() {
        // &#x1; &#x20; &#65; &amp; should not be stripped
        byte[] input = "<Value>&#x1;&#x20;&#65;&amp;</Value>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertSame("Non-null entities should not trigger filtering", input, result);
    }

    public void testNullInput() {
        assertNull(EWSMethod.filterNullCharacterReferences(null));
    }

    public void testEmptyInput() {
        byte[] input = new byte[0];
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertSame(input, result);
    }

    public void testHasNullCharacterReferenceHex() {
        assertTrue(EWSMethod.hasNullCharacterReference("abc&#x0;def".getBytes(StandardCharsets.UTF_8)));
    }

    public void testHasNullCharacterReferenceDecimal() {
        assertTrue(EWSMethod.hasNullCharacterReference("abc&#0;def".getBytes(StandardCharsets.UTF_8)));
    }

    public void testHasNullCharacterReferenceClean() {
        assertFalse(EWSMethod.hasNullCharacterReference("abc&#x1;def".getBytes(StandardCharsets.UTF_8)));
    }

    public void testHasNullCharacterReferenceShortInput() {
        assertFalse(EWSMethod.hasNullCharacterReference("&#".getBytes(StandardCharsets.UTF_8)));
    }

    public void testMixedNullAndNonNullEntities() {
        byte[] input = "<V>&#x41;&#x0;&#x42;</V>".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "<V>&#x41;&#x42;</V>".getBytes(StandardCharsets.UTF_8);
        byte[] result = EWSMethod.filterNullCharacterReferences(input);
        assertEquals(new String(expected, StandardCharsets.UTF_8), new String(result, StandardCharsets.UTF_8));
    }
}

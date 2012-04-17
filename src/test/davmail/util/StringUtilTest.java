/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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
package davmail.util;

import junit.framework.TestCase;

/**
 * Test StringUtil.
 */
public class StringUtilTest extends TestCase {
    /**
     * Test get token
     */
    public void testGetToken() {
        assertNull(StringUtil.getToken(null, null, null));
        assertNull(StringUtil.getToken(null, "\'", "\'"));
        assertNull(StringUtil.getToken("'test", "\'", "\'"));
        assertEquals("test", StringUtil.getToken("'test'", "'", "'"));
        assertEquals("test", StringUtil.getToken("value=\"test\"", "value=\"", "\""));
    }

    /**
     * Test replace token
     */
    public void testReplaceToken() {
        assertNull(StringUtil.replaceToken(null, null, null, null));
        assertNull(StringUtil.replaceToken(null, null, null, "new"));
        assertEquals("'new'", StringUtil.replaceToken("'old'", "'", "'", "new"));
        assertEquals("value=\"new\"", StringUtil.replaceToken("value=\"old\"", "value=\"", "\"", "new"));
    }

    /**
     * Test Xml Encode
     */
    public void testXmlEncode() {
        assertEquals("&amp;", StringUtil.xmlEncode("&"));
        assertEquals("&lt;", StringUtil.xmlEncode("<"));
        assertEquals("&gt;", StringUtil.xmlEncode(">"));
        assertEquals("&", StringUtil.xmlDecode("&amp;"));
        assertEquals("<", StringUtil.xmlDecode("&lt;"));
        assertEquals(">", StringUtil.xmlDecode("&gt;"));
        assertEquals("&lt;test&gt;", StringUtil.xmlEncode("<test>"));
    }

    public void testUrlEncodeAmpersand() {
        assertEquals("%26", StringUtil.urlEncodeAmpersand("&"));
        assertEquals("&", StringUtil.urlDecodeAmpersand("%26"));
    }

    public void testPerf() {
        String value = "dqsdqs+dsqds+dsqdqs";
        for (int j = 0; j < 5; j++) {
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000000; i++) {
                //String result = StringUtil.encodePlusSign(value);
                //String result = StringUtil.replaceAll(value, '+', "%2B");
                                                 
                /*int length = value.length();
                StringBuilder buffer = new StringBuilder(length);
                int startIndex = 0;
                int endIndex = value.indexOf('+');
                while (endIndex >= 0) {
                    buffer.append(value.substring(startIndex, endIndex));
                    buffer.append("%2B");
                    startIndex = endIndex + 1;
                    endIndex = value.indexOf('+', startIndex);
                }
                buffer.append(value.substring(startIndex)); */
                /*
                for (int k = 0; k < length; k++) {
                    char c = value.charAt(k);
                    if (c == '+') {
                        buffer.append("%2B");
                    } else {
                        buffer.append(c);
                    }
                }*/
                //String result = buffer.toString();
                //String result = value.replaceAll("\\+", "%2B");
            }
            System.out.println("Elapsed: " + (System.currentTimeMillis() - startTime) + " ms");
        }
    }

    public void testRemoveQuotes() {
        assertEquals("test", StringUtil.removeQuotes("test"));
        assertEquals("test", StringUtil.removeQuotes("\"test\""));
    }

    public void testEncodePipe() {
        assertEquals("test %7C", StringUtil.encodeUrlcompname("test |"));
        assertEquals("test |", StringUtil.decodeUrlcompname("test %7C"));
    }
}

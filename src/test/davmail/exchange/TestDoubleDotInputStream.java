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
package davmail.exchange;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Test double dot input stream.
 */
public class TestDoubleDotInputStream extends TestCase {
    static final String END_OF_STREAM = "\r\n.\r\n";

    protected String doubleDotRead(String value) throws IOException {
        DoubleDotInputStream doubleDotInputStream = new DoubleDotInputStream(new ByteArrayInputStream(value.getBytes()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = doubleDotInputStream.read()) != -1) {
            baos.write(b);
        }
        return new String(baos.toByteArray());
    }
    
    protected String doubleDotWrite(String value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DoubleDotOutputStream doubleDotOutputStream = new DoubleDotOutputStream(baos);
        doubleDotOutputStream.write(value.getBytes());
        doubleDotOutputStream.close();
        return new String(baos.toByteArray());
    }

    public void testSimple() throws IOException {
        String value = "simple test";
        assertEquals(value, doubleDotRead(value + END_OF_STREAM));
    }

    public void testNoEof() throws IOException {
        String value = "simple test";
        assertEquals(value, doubleDotRead(value));
    }

    public void testMultiLine() throws IOException {
        String value = "simple test\r\nsecond line";
        assertEquals(value, doubleDotRead(value+END_OF_STREAM));
    }

    public void testDoubleDot() throws IOException {
        String value = "simple test\r\n..\r\nsecond line";
        assertEquals(value.replaceAll("\\.\\.", "."), doubleDotRead(value+END_OF_STREAM));
    }

    public void testDoubleDotEnd() throws IOException {
        String value = "simple test\r\n..";
        assertEquals(value.replaceAll("\\.\\.", "."), doubleDotRead(value+END_OF_STREAM));
        assertEquals("..", doubleDotRead(".."+END_OF_STREAM));
    }

    public void testWriteCRLF() throws IOException {
        String value = "simple test\r\n.\r\nsecond line";
        assertEquals(value.replaceAll("\\.", "..")+END_OF_STREAM, doubleDotWrite(value));
    }

    public void testEndsWithCRLF() throws IOException {
        String value = "simple test\r\n";
        assertEquals("simple test"+END_OF_STREAM, doubleDotWrite(value));
    }

    public void testEndsWithLF() throws IOException { 
        String value = "simple test\n";
        assertEquals("simple test\n"+END_OF_STREAM, doubleDotWrite(value));
    }

    public void testWriteOSXCR() throws IOException {
        String value = "simple test\r.\rsecond line";
        assertEquals(value.replaceAll("\\.", "..")+END_OF_STREAM, doubleDotWrite(value));
    }

    public void testWriteUnixLF() throws IOException {
        String value = "simple test\n.\nsecond line";
        assertEquals(value.replaceAll("\\.", "..")+END_OF_STREAM, doubleDotWrite(value));
    }

    public void testAnotherTest() throws IOException {
        String value = "foo\r\n..bar";
        assertEquals(value.replaceAll("\\.\\.", "."), doubleDotRead(value));
    }

}

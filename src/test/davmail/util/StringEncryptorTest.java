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

package davmail.util;

import junit.framework.TestCase;

import java.io.IOException;

public class StringEncryptorTest extends TestCase {
    public void testEncrypt() throws IOException {
        String password = "P@ssw0rd";
        String value = "MyVeryLongToken";
        StringEncryptor encryptor = new StringEncryptor(password);
        String encrypted = encryptor.encryptString(value);

        encryptor = new StringEncryptor(password);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(value, decrypted);
    }

    public void testDecrypt() {
        String password = "P@ssw0rd";
        StringEncryptor encryptor = new StringEncryptor(password);

        try {
            encryptor.decryptString("{AES}invalid");
            fail("Expected IOException");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testNullInput() throws IOException {
        String password = "P@ssw0rd";
        StringEncryptor encryptor = new StringEncryptor(password);
        assertNull(encryptor.encryptString(null));
        assertNull(encryptor.decryptString(null));
    }

    public void testEmptyInput() throws IOException {
        String password = "P@ssw0rd";
        StringEncryptor encryptor = new StringEncryptor(password);
        assertEquals("", encryptor.encryptString(""));
        assertEquals("", encryptor.decryptString(""));
    }

    public void testPassthrough() throws IOException {
        String password = "P@ssw0rd";
        StringEncryptor encryptor = new StringEncryptor(password);
        assertEquals("plaintext", encryptor.decryptString("plaintext"));
    }

    public void testInvalidPassword() throws IOException {
        String password = "P@ssw0rd";
        StringEncryptor encryptor = new StringEncryptor(password);
        String encrypted = encryptor.encryptString("plaintext");

        try {
            new StringEncryptor("invalid").decryptString(encrypted);
            fail("Expected IOException");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testEncryptSpecialCharacters() throws IOException {
        String password = "P@ssw0rd";
        String value = "Just a ' test with spécial ch@racters !:/`$£ \"";
        StringEncryptor encryptor = new StringEncryptor(password);
        String encrypted = encryptor.encryptString(value);

        encryptor = new StringEncryptor(password);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(value, decrypted);
    }

    public void testCustom() throws IOException {
        String password = "CxRixKrdKxHWjhO!";
        String encrypted = "{AES}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";
        StringEncryptor encryptor = new StringEncryptor(password);
        //String encrypted = encryptor.encryptString(value);

        encryptor = new StringEncryptor(password);
        String decrypted = encryptor.decryptString(encrypted);
        //assertEquals(value, decrypted);
    }

}

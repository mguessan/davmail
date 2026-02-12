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
            // Expected
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
    }
}

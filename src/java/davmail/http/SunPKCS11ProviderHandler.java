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
package davmail.http;

import sun.security.pkcs11.SunPKCS11;

import java.security.Provider;
import java.security.Security;
import java.io.ByteArrayInputStream;

/**
 * Add the SunPKCS11 Provider.
 */
public final class SunPKCS11ProviderHandler {

    private SunPKCS11ProviderHandler() {
    }

    /**
     * Register PKCS11 provider.
     *
     * @param pkcs11Config PKCS11 config string
     */
    public static void registerProvider(String pkcs11Config) {
        Provider p = new SunPKCS11(new ByteArrayInputStream(pkcs11Config.getBytes()));
        Security.addProvider(p);
    }

}

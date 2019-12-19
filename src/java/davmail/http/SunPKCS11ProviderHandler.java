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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;

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
        Provider p;

        try {
            Class sunPkcs11Class = Class.forName("sun.security.pkcs11.SunPKCS11");
            @SuppressWarnings("unchecked") Constructor sunPkcs11Constructor = sunPkcs11Class.getDeclaredConstructor(InputStream.class);
            p = (Provider) sunPkcs11Constructor.newInstance(new ByteArrayInputStream(pkcs11Config.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchMethodException e) {
            // try java 9 configuration
            p = configurePkcs11Provider(pkcs11Config);
        } catch (Exception e) {
            StringBuilder errorMessage = new StringBuilder("Unable to configure SunPKCS11 provider");
            Throwable cause = e.getCause();
            while (cause != null) {
                errorMessage.append(" ").append(cause.getMessage());
                cause = cause.getCause();
            }
            throw new RuntimeException(errorMessage.toString());
        }

        Security.addProvider(p);
    }

    private static Provider configurePkcs11Provider(String pkcs11Config) {
        Provider p;
        try {
            p = Security.getProvider("SunPKCS11");
            //p.configure("--"+pkcs11Config);
            //noinspection JavaReflectionMemberAccess new Java 9 configure method
            Method configureMethod = Provider.class.getDeclaredMethod("configure", String.class);
            configureMethod.invoke(p, "--"+pkcs11Config);
        } catch (Exception e) {
            StringBuilder errorMessage = new StringBuilder("Unable to configure SunPKCS11 provider");
            Throwable cause = e.getCause();
            while (cause != null) {
                errorMessage.append(" ").append(cause.getMessage());
                cause = cause.getCause();
            }
            throw new RuntimeException(errorMessage.toString());
        }
        return p;
    }

}

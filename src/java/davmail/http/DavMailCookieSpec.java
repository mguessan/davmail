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
package davmail.http;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.cookie.MalformedCookieException;
import org.apache.commons.httpclient.cookie.RFC2109Spec;

/**
 * Custom CookieSpec to allow extended domain names.
 */
public class DavMailCookieSpec extends RFC2109Spec {
    @Override
    public void validate(String host, int port, String path,
                         boolean secure, final Cookie cookie) throws MalformedCookieException {
        // workaround for space in cookie name
        String cookieName = cookie.getName();
        if (cookieName != null && cookieName.indexOf(' ') >= 0) {
            cookie.setName(cookieName.replaceAll(" ", ""));
        } else {
            cookieName = null;
        }
        // workaround for invalid cookie path
        String cookiePath = cookie.getPath();
        if (cookiePath != null && !path.startsWith(cookiePath)) {
            cookie.setPath(path);
        } else {
            cookiePath = null;
        }
        String hostWithoutDomain = host.substring(0, host.length()
                - cookie.getDomain().length());
        int dotIndex = hostWithoutDomain.indexOf('.');
        if (dotIndex != -1) {
            // discard additional host name part
            super.validate(host.substring(dotIndex + 1), port, path, secure, cookie);
        } else {
            super.validate(host, port, path, secure, cookie);
        }
        if (cookieName != null) {
            cookie.setName(cookieName);
        }
        if (cookiePath != null) {
            cookie.setPath(cookiePath);
        }
    }
}

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

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * Override native SPNegoSchemeFactory to load DavMail specific Kerberos settings.
 */
public class DavMailSPNegoSchemeFactory implements AuthSchemeFactory, AuthSchemeProvider {

    private final boolean stripPort;
    private final boolean useCanonicalHostname;

    /**
     * @since 4.4
     */
    public DavMailSPNegoSchemeFactory(final boolean stripPort, final boolean useCanonicalHostname) {
        super();
        this.stripPort = stripPort;
        this.useCanonicalHostname = useCanonicalHostname;
    }

    public DavMailSPNegoSchemeFactory(final boolean stripPort) {
        super();
        this.stripPort = stripPort;
        this.useCanonicalHostname = true;
    }

    public DavMailSPNegoSchemeFactory() {
        this(true, true);
    }

    public boolean isStripPort() {
        return stripPort;
    }

    public boolean isUseCanonicalHostname() {
        return useCanonicalHostname;
    }

    @Override
    public AuthScheme newInstance(final HttpParams params) {
        return new DavMailSPNegoScheme(this.stripPort, this.useCanonicalHostname);
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new DavMailSPNegoScheme(this.stripPort, this.useCanonicalHostname);
    }

}

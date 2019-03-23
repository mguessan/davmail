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

package davmail;

import java.io.IOException;

public class AbstractExchange2007TestCase extends AbstractDavMailTestCase {
    protected String domain;
    protected String email;
    protected String server;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        // set Exchange 2007 server info from test.properties
        Settings.setProperty("davmail.server.certificate.hash",
                Settings.getProperty("davmail.exchange2007.server.certificate.hash"));
        server = Settings.getProperty("davmail.exchange2007.server");
        domain = Settings.getProperty("davmail.exchange2007.domain");
        username = Settings.getProperty("davmail.exchange2007.username");
        email = Settings.getProperty("davmail.exchange2007.email");
        password = Settings.getProperty("davmail.exchange2007.password");
    }

}

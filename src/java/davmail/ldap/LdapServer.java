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
package davmail.ldap;

import davmail.AbstractConnection;
import davmail.AbstractServer;
import davmail.Settings;

import java.net.Socket;

/**
 * LDAP server, handle LDAP directory requests.
 */
public class LdapServer extends AbstractServer {
    /**
     * Default LDAP port
     */
    public static final int DEFAULT_PORT = 389;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     *
     * @param port pop listen port, 389 if not defined (0)
     */
    public LdapServer(int port)  {
        super(LdapServer.class.getName(), port, LdapServer.DEFAULT_PORT);
		nosslFlag = Settings.getBooleanProperty("davmail.ssl.nosecureldap");
    }

    @Override
    public String getProtocolName() {
        return "LDAP";
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new LdapConnection(clientSocket);
    }
}
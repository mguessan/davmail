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
package davmail.imap;


import davmail.AbstractConnection;
import davmail.AbstractServer;

import java.net.Socket;

/**
 * Pop3 server
 */
public class ImapServer extends AbstractServer {
    public static final int DEFAULT_PORT = 143;

    /**
     * Create a ServerSocket to listen for connections.
     * Start the thread.
     *
     * @param port imap listen port, 143 if not defined (0)
     */
    public ImapServer(int port) {
        super(ImapServer.class.getName(), port, ImapServer.DEFAULT_PORT);
    }

    @Override
    public String getProtocolName() {
        return "IMAP";
    }

    @Override
    public AbstractConnection createConnectionHandler(Socket clientSocket) {
        return new ImapConnection(clientSocket);
    }

}

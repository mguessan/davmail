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

import org.apache.log4j.Logger;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * SSLSocketFactory implementation.
 * Wrapper for DavGatewaySSLProtocolSocketFactory used by HttpClient 4
 */
public class DavGatewaySSLSocketFactory extends SSLSocketFactory {
    static final Logger LOGGER = Logger.getLogger(DavGatewaySSLSocketFactory.class);

    private DavGatewaySSLProtocolSocketFactory socketFactory;

    public DavGatewaySSLSocketFactory() {
        socketFactory = new DavGatewaySSLProtocolSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return socketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return socketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        return socketFactory.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        return socketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port + " " + clientHost + " " + clientPort);
        return socketFactory.createSocket(host, port, clientHost, clientPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port);
        return socketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress clientHost, int clientPort) throws IOException {
        LOGGER.debug("createSocket " + host + " " + port + " " + clientHost + " " + clientPort);
        return socketFactory.createSocket(host, port, clientHost, clientPort);
    }
}

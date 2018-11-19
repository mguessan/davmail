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

import davmail.Settings;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class DavGatewayProxySelector extends ProxySelector {
    static final Logger LOGGER = Logger.getLogger(DavGatewayProxySelector.class);

    static final ArrayList<Proxy> DIRECT = new ArrayList<Proxy>();
    static {
        DIRECT.add(Proxy.NO_PROXY);
    }

    ProxySelector proxySelector;

    public DavGatewayProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    @Override
    public List<Proxy> select(URI uri) {
        boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE);
        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        String proxyHost = Settings.getProperty("davmail.proxyHost");
        int proxyPort = Settings.getIntProperty("davmail.proxyPort");
        String scheme = uri.getScheme();
        if (useSystemProxies) {
            List<Proxy> proxyes = proxySelector.select(uri);
            LOGGER.debug("Selected " + proxyes + " proxy for " + uri);
            return proxyes;
        } else if (enableProxy
                && proxyHost != null && proxyHost.length() > 0 && proxyPort > 0
                && !DavGatewayHttpClientFacade.isNoProxyFor(uri)
                && ("http".equals(scheme) || "https".equals(scheme)) && enableProxy) {
            // DavMail defined proxies
            ArrayList<Proxy> proxies = new ArrayList<Proxy>();
            proxies.add(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyHost, proxyPort)));
            return proxies;
        } else {
            return DIRECT;
        }
    }

    @Override
    public void connectFailed(java.net.URI uri, SocketAddress sa, IOException ioe) {
        LOGGER.debug("Connection to "+uri+" failed, socket address "+sa+" "+ioe);
        proxySelector.connectFailed(uri, sa, ioe);
    }
}

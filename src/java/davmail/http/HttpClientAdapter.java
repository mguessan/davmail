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
import davmail.exception.DavMailException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.security.Security;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpClientAdapter {
    static final Logger LOGGER = Logger.getLogger("davmail.http.DavGatewayHttpClientFacade");

    static final Registry<ConnectionSocketFactory> SCHEME_REGISTRY;
    static final RequestConfig DEFAULT_REQUEST_CONFIG;
    static String WORKSTATION_NAME = "UNKNOWN";
    static final int MAX_REDIRECTS = 10;

    static {
        // disable Client-initiated TLS renegotiation
        System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true");
        // force strong ephemeral Diffie-Hellman parameter
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");

        Security.setProperty("ssl.SocketFactory.provider", "davmail.http.DavGatewaySSLSocketFactory");

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();
        schemeRegistry.register("http", new PlainConnectionSocketFactory());
        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        SCHEME_REGISTRY = schemeRegistry.build();

        try {
            WORKSTATION_NAME = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            // ignore
        }

        DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
                // socket connect timeout
                .setConnectTimeout(Settings.getIntProperty("davmail.exchange.connectionTimeout", 10) * 1000)
                // inactivity timeout
                .setSocketTimeout(Settings.getIntProperty("davmail.exchange.soTimeout", 120) * 1000)
                // max redirect
                .setMaxRedirects(Settings.getIntProperty("davmail.httpMaxRedirects", MAX_REDIRECTS))
                .build();

        // set system property *before* calling ProxySelector.getDefault()
        if (Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE)) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
        ProxySelector.setDefault(new DavGatewayProxySelector(ProxySelector.getDefault()));
    }

    PoolingHttpClientConnectionManager connectionManager;
    CloseableHttpClient httpClient;
    IdleConnectionEvictor idleConnectionEvictor;


    public HttpClientAdapter(String url) throws DavMailException {
        this(url, null, null);
    }

    public HttpClientAdapter(String url, String username, String password) throws DavMailException {
        connectionManager = new PoolingHttpClientConnectionManager(SCHEME_REGISTRY);
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG)
                .setUserAgent(DavGatewayHttpClientFacade.IE_USER_AGENT)
                .setConnectionManager(connectionManager);

        SystemDefaultRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        clientBuilder.setRoutePlanner(routePlanner);
        CredentialsProvider provider = null;
        if (username != null && password != null) {
            provider = new BasicCredentialsProvider();
            NTCredentials credentials;
            int backSlashIndex = username.indexOf('\\');
            if (backSlashIndex >= 0) {
                // separate domain from username in credentials
                String domain = username.substring(0, backSlashIndex);
                username = username.substring(backSlashIndex + 1);
                credentials = new NTCredentials(username, password, WORKSTATION_NAME, domain);
            } else {
                credentials = new NTCredentials(username, password, WORKSTATION_NAME, "");
            }
            provider.setCredentials(AuthScope.ANY, credentials);
        }

        try {
            boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
            boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE);
            String proxyHost = null;
            int proxyPort = 0;
            String proxyUser = null;
            String proxyPassword = null;

            java.net.URI uri = new java.net.URI(url);
            if (useSystemProxies) {
                // get proxy for url from system settings
                System.setProperty("java.net.useSystemProxies", "true");
                List<Proxy> proxyList = getProxyForURI(uri);
                if (!proxyList.isEmpty() && proxyList.get(0).address() != null) {
                    InetSocketAddress inetSocketAddress = (InetSocketAddress) proxyList.get(0).address();
                    proxyHost = inetSocketAddress.getHostName();
                    proxyPort = inetSocketAddress.getPort();

                    // we may still need authentication credentials
                    proxyUser = Settings.getProperty("davmail.proxyUser");
                    proxyPassword = Settings.getProperty("davmail.proxyPassword");
                }
            } else if (isNoProxyFor(uri)) {
                LOGGER.debug("no proxy for " + uri.getHost());
            } else if (enableProxy) {
                proxyHost = Settings.getProperty("davmail.proxyHost");
                proxyPort = Settings.getIntProperty("davmail.proxyPort");
                proxyUser = Settings.getProperty("davmail.proxyUser");
                proxyPassword = Settings.getProperty("davmail.proxyPassword");
            }

            if (proxyHost != null && proxyHost.length() > 0) {
                if (proxyUser != null && proxyUser.length() > 0) {

                    AuthScope authScope = new AuthScope(proxyHost, proxyPort, AuthScope.ANY_REALM);
                    if (provider == null) {
                        provider = new BasicCredentialsProvider();
                    }

                    // detect ntlm authentication (windows domain name in user name)
                    int backslashindex = proxyUser.indexOf('\\');
                    if (backslashindex > 0) {
                        provider.setCredentials(authScope, new NTCredentials(proxyUser.substring(backslashindex + 1),
                                proxyPassword, WORKSTATION_NAME,
                                proxyUser.substring(0, backslashindex)));
                    } else {
                        provider.setCredentials(authScope, new NTCredentials(proxyUser, proxyPassword, WORKSTATION_NAME, ""));
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new DavMailException("LOG_INVALID_URL", url);
        }

        clientBuilder.setDefaultCredentialsProvider(provider);

        httpClient = clientBuilder.build();

    }

    /**
     * Retrieve Proxy Selector
     *
     * @param uri target uri
     * @return proxy selector
     */
    private static List<Proxy> getProxyForURI(java.net.URI uri) {
        LOGGER.debug("get Default proxy selector");
        ProxySelector proxySelector = ProxySelector.getDefault();
        LOGGER.debug("getProxyForURI(" + uri + ')');
        List<Proxy> proxies = proxySelector.select(uri);
        LOGGER.debug("got system proxies:" + proxies);
        return proxies;
    }

    protected static boolean isNoProxyFor(java.net.URI uri) {
        final String noProxyFor = Settings.getProperty("davmail.noProxyFor");
        if (noProxyFor != null) {
            final String urihost = uri.getHost().toLowerCase();
            final String[] domains = noProxyFor.toLowerCase().split(",\\s*");
            for (String domain : domains) {
                if (urihost.endsWith(domain)) {
                    return true; //break;
                }
            }
        }
        return false;
    }

    public void startEvictorThread() {
        idleConnectionEvictor = new IdleConnectionEvictor(connectionManager, 1, TimeUnit.MINUTES);
        idleConnectionEvictor.start();
    }

    public void close() throws IOException {
        if (idleConnectionEvictor != null) {
            idleConnectionEvictor.shutdown();
        }
        httpClient.close();
    }

    public CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
        return httpClient.execute(request);
    }
}

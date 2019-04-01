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
import davmail.exception.HttpForbiddenException;
import davmail.exception.HttpNotFoundException;
import davmail.exception.HttpPreconditionFailedException;
import davmail.exception.HttpServerErrorException;
import davmail.exception.LoginTimeoutException;
import org.apache.commons.httpclient.HttpException;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.client.methods.BaseDavRequest;
import org.apache.jackrabbit.webdav.client.methods.HttpCopy;
import org.apache.jackrabbit.webdav.client.methods.HttpMove;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.security.Security;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpClientAdapter {
    static final Logger LOGGER = Logger.getLogger("davmail.http.DavGatewayHttpClientFacade");

    static final Registry<ConnectionSocketFactory> SCHEME_REGISTRY;
    static final Registry<AuthSchemeProvider> AUTH_SCHEME_REGISTRY;
    static final RequestConfig DEFAULT_REQUEST_CONFIG;
    static String WORKSTATION_NAME = "UNKNOWN";
    static final int MAX_REDIRECTS = 10;

    static {
        // disable Client-initiated TLS renegotiation
        System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true");
        // force strong ephemeral Diffie-Hellman parameter
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");

        Security.setProperty("ssl.SocketFactory.provider", "davmail.http.DavGatewaySSLSocketFactory");

        // reenable basic proxy authentication on Java >= 1.8.111
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

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

        AUTH_SCHEME_REGISTRY = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.NTLM, new JCIFSNTLMSchemeFactory())
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
                .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
                .build();

        DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
                // socket connect timeout
                .setConnectTimeout(Settings.getIntProperty("davmail.exchange.connectionTimeout", 10) * 1000)
                // inactivity timeout
                .setSocketTimeout(Settings.getIntProperty("davmail.exchange.soTimeout", 120) * 1000)
                .build();

        // set system property *before* calling ProxySelector.getDefault()
        if (Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE)) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
        ProxySelector.setDefault(new DavGatewayProxySelector(ProxySelector.getDefault()));
    }

    HttpClientConnectionManager connectionManager;
    CloseableHttpClient httpClient;
    CredentialsProvider provider = new BasicCredentialsProvider();
    BasicCookieStore cookieStore = new BasicCookieStore();
    IdleConnectionEvictor idleConnectionEvictor;
    // current URI
    URI uri;
    String domain;
    String userid;
    String userEmail;

    public HttpClientAdapter(String url) {
        this(URI.create(url));
    }

    public HttpClientAdapter(String url, String username, String password) {
        this(URI.create(url), username, password, false);
    }

    public HttpClientAdapter(String url, boolean enablePool) {
        this(URI.create(url), null, null, enablePool);
    }

    public HttpClientAdapter(URI uri) {
        this(uri, null, null, false);
    }

    public HttpClientAdapter(URI uri, boolean enablePool) {
        this(uri, null, null, enablePool);
    }

    public HttpClientAdapter(URI uri, String username, String password) {
        this(uri, username, password, false);
    }

    public HttpClientAdapter(URI uri, String username, String password, boolean enablePool) {
        if (enablePool) {
            connectionManager = new PoolingHttpClientConnectionManager(SCHEME_REGISTRY);
        } else {
            connectionManager = new BasicHttpClientConnectionManager(SCHEME_REGISTRY);
        }
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG)
                .setUserAgent(DavGatewayHttpClientFacade.IE_USER_AGENT)
                .setDefaultAuthSchemeRegistry(AUTH_SCHEME_REGISTRY)
                .setConnectionManager(connectionManager);

        SystemDefaultRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        clientBuilder.setRoutePlanner(routePlanner);

        clientBuilder.setDefaultCookieStore(cookieStore);

        setCredentials(username, password);

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE);
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPassword = null;

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

        clientBuilder.setDefaultCredentialsProvider(provider);

        httpClient = clientBuilder.build();
    }

    private void parseUserName(String username) {
        if (username != null) {
            int pipeIndex = username.indexOf("|");
            if (pipeIndex >= 0) {
                userid = username.substring(0, pipeIndex);
                userEmail = username.substring(pipeIndex + 1);
            } else {
                userid = username;
                userEmail = username;
            }
            // separate domain name
            int backSlashIndex = userid.indexOf('\\');
            if (backSlashIndex >= 0) {
                // separate domain from username in credentials
                domain = userid.substring(0, backSlashIndex);
                userid = userid.substring(backSlashIndex + 1);
            } else {
                domain = Settings.getProperty("davmail.defaultDomain", "");
            }
        }
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
        // TODO: create a common connection evictor for all connection managers
        idleConnectionEvictor = new IdleConnectionEvictor(connectionManager, 1, TimeUnit.MINUTES);
        idleConnectionEvictor.start();
    }

    public void close() {
        if (idleConnectionEvictor != null) {
            idleConnectionEvictor.shutdown();
        }
        try {
            httpClient.close();
        } catch (IOException e) {
            LOGGER.warn("Exception closing http client", e);
        }
    }

    public static void close(HttpClientAdapter httpClientAdapter) {
        if (httpClientAdapter != null) {
            httpClientAdapter.close();
        }
    }

    /**
     * Execute request, do not follow redirects.
     * if request is an instance of ResponseHandler, process and close response
     *
     * @param request Http request
     * @return Http response
     * @throws IOException on error
     */
    public CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
        return execute(request, null);
    }

    /**
     * Execute request, do not follow redirects.
     * if request is an instance of ResponseHandler, process and close response
     *
     * @param request Http request
     * @param context Http request context
     * @return Http response
     * @throws IOException on error
     */
    public CloseableHttpResponse execute(HttpRequestBase request, HttpClientContext context) throws IOException {
        URI requestURI = request.getURI();
        if (!requestURI.isAbsolute()) {
            request.setURI(URIUtils.resolve(uri, requestURI));
        }
        uri = request.getURI();
        CloseableHttpResponse response = httpClient.execute(request, context);
        if (request instanceof ResponseHandler) {
            try {
                ((ResponseHandler) request).handleResponse(response);
            } finally {
                response.close();
            }
        }
        return response;
    }

    /**
     * Execute request, manually follow redirects
     *
     * @param request Http request
     * @return Http response
     * @throws IOException on error
     */
    public CloseableHttpResponse executeFollowRedirects(HttpRequestBase request) throws IOException {
        CloseableHttpResponse httpResponse;
        int count = 0;
        int maxRedirect = Settings.getIntProperty("davmail.httpMaxRedirects", MAX_REDIRECTS);
        httpResponse = execute(request);
        while (count++ < maxRedirect
                && isRedirect(httpResponse.getStatusLine().getStatusCode())
                && httpResponse.getFirstHeader("Location") != null) {
            // close previous response
            httpResponse.close();
            String location = httpResponse.getFirstHeader("Location").getValue();
            LOGGER.debug("Redirect " + request.getURI() + " to " + location);
            // replace uri with target location
            request.setURI(URI.create(location));
            httpResponse = execute(request);
        }

        return httpResponse;
    }

    public MultiStatus executeDavRequest(BaseDavRequest request) throws IOException, DavException {
        MultiStatus multiStatus = null;
        CloseableHttpResponse response = execute(request);
        try {
            request.checkSuccess(response);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
                multiStatus = request.getResponseBodyAsMultiStatus(response);
            }
        } finally {
            response.close();
        }
        return multiStatus;
    }

    public static boolean isRedirect(HttpResponse response) {
        return isRedirect(response.getStatusLine().getStatusCode());
    }

    /**
     * Check if status is a redirect (various 30x values).
     *
     * @param status Http status
     * @return true if status is a redirect
     */
    public static boolean isRedirect(int status) {
        return status == HttpStatus.SC_MOVED_PERMANENTLY
                || status == HttpStatus.SC_MOVED_TEMPORARILY
                || status == HttpStatus.SC_SEE_OTHER
                || status == HttpStatus.SC_TEMPORARY_REDIRECT;
    }

    /**
     * Check if status is a redirect (various 30x values).
     *
     * @param response Http response
     * @return URI target location
     */
    public static URI getRedirectLocation(HttpResponse response) {
        Header location = response.getFirstHeader("Location");
        if (isRedirect(response.getStatusLine().getStatusCode()) && location != null) {
            return URI.create(location.getValue());
        }
        return null;
    }

    public void setCredentials(String username, String password) {
        parseUserName(username);
        if (userid != null && password != null) {
            NTCredentials credentials = new NTCredentials(userid, password, WORKSTATION_NAME, domain);
            provider.setCredentials(AuthScope.ANY, credentials);
        }
    }

    public List<Cookie> getCookies() {
        return cookieStore.getCookies();
    }

    public void addCookie(Cookie cookie) {
        cookieStore.addCookie(cookie);
    }

    /**
     * Build Http Exception from method status
     *
     * @param method Http Method
     * @return Http Exception
     */
    public static HttpException buildHttpException(HttpRequestBase method, HttpResponse response) {
        int status = response.getStatusLine().getStatusCode();
        StringBuilder message = new StringBuilder();
        message.append(status).append(' ').append(response.getStatusLine().getReasonPhrase());
        message.append(" at ").append(method.getURI());
        if (method instanceof HttpCopy || method instanceof HttpMove) {
            message.append(" to ").append(method.getFirstHeader("Destination"));
        }
        // 440 means forbidden on Exchange
        if (status == 440) {
            return new LoginTimeoutException(message.toString());
        } else if (status == org.apache.commons.httpclient.HttpStatus.SC_FORBIDDEN) {
            return new HttpForbiddenException(message.toString());
        } else if (status == org.apache.commons.httpclient.HttpStatus.SC_NOT_FOUND) {
            return new HttpNotFoundException(message.toString());
        } else if (status == org.apache.commons.httpclient.HttpStatus.SC_PRECONDITION_FAILED) {
            return new HttpPreconditionFailedException(message.toString());
        } else if (status == org.apache.commons.httpclient.HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            return new HttpServerErrorException(message.toString());
        } else {
            return new HttpException(message.toString());
        }
    }

    public String getHost() {
        return uri.getHost();
    }
}

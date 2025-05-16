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
import davmail.exception.*;
import davmail.http.request.*;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
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
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.BaseDavRequest;
import org.apache.jackrabbit.webdav.client.methods.HttpCopy;
import org.apache.jackrabbit.webdav.client.methods.HttpMove;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.security.Security;
import java.util.HashSet;
import java.util.List;

public class HttpClientAdapter implements Closeable {
    static final Logger LOGGER = Logger.getLogger("davmail.http.HttpClientAdapter");

    static final String[] SUPPORTED_PROTOCOLS = new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
    static final Registry<ConnectionSocketFactory> SCHEME_REGISTRY;
    static String WORKSTATION_NAME = "UNKNOWN";
    static final int MAX_REDIRECTS = 10;

    static {
        // disable Client-initiated TLS renegotiation
        System.setProperty("jdk.tls.rejectClientInitiatedRenegotiation", "true");
        // force strong ephemeral Diffie-Hellman parameter
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");

        Security.setProperty("ssl.SocketFactory.provider", "davmail.http.DavGatewaySSLSocketFactory");

        // DavMail is Kerberos configuration provider
        Security.setProperty("login.configuration.provider", "davmail.http.KerberosLoginConfiguration");

        // reenable basic proxy authentication on Java >= 1.8.111
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();
        schemeRegistry.register("http", new PlainConnectionSocketFactory());
        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SUPPORTED_PROTOCOLS, null,
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        SCHEME_REGISTRY = schemeRegistry.build();

        try {
            WORKSTATION_NAME = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // ignore
        }

        // set system property *before* calling ProxySelector.getDefault()
        if (Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE)) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
        ProxySelector.setDefault(new DavGatewayProxySelector(ProxySelector.getDefault()));
    }

    /**
     * Test if the response is gzip encoded
     *
     * @param response http response
     * @return true if response is gzip encoded
     */
    public static boolean isGzipEncoded(HttpResponse response) {
        Header header = response.getFirstHeader("Content-Encoding");
        return header != null && "gzip".equals(header.getValue());
    }

    HttpClientConnectionManager connectionManager;
    CloseableHttpClient httpClient;
    CredentialsProvider provider = new BasicCredentialsProvider();
    BasicCookieStore cookieStore = new BasicCookieStore() {
        @Override
        public void addCookie(final Cookie cookie) {
            //LOGGER.debug("Add cookie " + cookie);
            super.addCookie(cookie);
        }
    };
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

    public HttpClientAdapter(String url, String username, String password, boolean enablePool) {
        this(URI.create(url), username, password, enablePool);
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
        // init current uri
        this.uri = uri;

        if (enablePool) {
            connectionManager = new PoolingHttpClientConnectionManager(SCHEME_REGISTRY);
            ((PoolingHttpClientConnectionManager) connectionManager).setDefaultMaxPerRoute(5);
            startEvictorThread();
        } else {
            connectionManager = new BasicHttpClientConnectionManager(SCHEME_REGISTRY);
        }
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(getRequestConfig())
                .setUserAgent(getUserAgent())
                .setDefaultAuthSchemeRegistry(getAuthSchemeRegistry())
                // httpClient is not shared between clients, do not track connection state
                .disableConnectionState()
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

        if (proxyHost != null && !proxyHost.isEmpty() && (proxyUser != null && !proxyUser.isEmpty())) {

                AuthScope authScope = new AuthScope(proxyHost, proxyPort, AuthScope.ANY_REALM);
                if (provider == null) {
                    provider = new BasicCredentialsProvider();
                }

                // detect ntlm authentication (windows domain name in username)
                int backslashIndex = proxyUser.indexOf('\\');
                if (backslashIndex > 0) {
                    provider.setCredentials(authScope, new NTCredentials(proxyUser.substring(backslashIndex + 1),
                            proxyPassword, WORKSTATION_NAME,
                            proxyUser.substring(0, backslashIndex)));
                } else {
                    provider.setCredentials(authScope, new NTCredentials(proxyUser, proxyPassword, WORKSTATION_NAME, ""));
                }

        }

        clientBuilder.setDefaultCredentialsProvider(provider);

        httpClient = clientBuilder.build();
    }

    /**
     * Get current uri host
     *
     * @return current host
     */
    public String getHost() {
        return uri.getHost();
    }

    /**
     * Force current uri.
     *
     * @param uri new uri
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    /**
     * Current uri.
     *
     * @return current uri
     */
    public URI getUri() {
        return uri;
    }

    private Registry<AuthSchemeProvider> getAuthSchemeRegistry() {
        final RegistryBuilder<AuthSchemeProvider> registryBuilder = RegistryBuilder.create();
        AuthSchemeProvider ntlmSchemeProvider;
        if (Settings.getBooleanProperty("davmail.enableJcifs", false)) {
            ntlmSchemeProvider = new JCIFSNTLMSchemeFactory();
        } else {
            ntlmSchemeProvider = new DavMailNTLMSchemeFactory();
        }
        registryBuilder.register(AuthSchemes.NTLM, ntlmSchemeProvider)
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory());
        if (Settings.getBooleanProperty("davmail.enableKerberos")) {
            registryBuilder.register(AuthSchemes.SPNEGO, new DavMailSPNegoSchemeFactory());
        }

        return registryBuilder.build();
    }

    private RequestConfig getRequestConfig() {
        HashSet<String> authSchemes = new HashSet<>();
        if (Settings.getBooleanProperty("davmail.enableKerberos")) {
            authSchemes.add(AuthSchemes.SPNEGO);
            authSchemes.add(AuthSchemes.KERBEROS);
        } else {
            authSchemes.add(AuthSchemes.NTLM);
            authSchemes.add(AuthSchemes.BASIC);
            authSchemes.add(AuthSchemes.DIGEST);
        }
        return RequestConfig.custom()
                // fixes Invalid 'expires' attribute
                .setCookieSpec(CookieSpecs.STANDARD)
                // socket connect timeout
                .setConnectTimeout(Settings.getIntProperty("davmail.exchange.connectionTimeout", 10) * 1000)
                // inactivity timeout
                .setSocketTimeout(Settings.getIntProperty("davmail.exchange.soTimeout", 120) * 1000)
                .setTargetPreferredAuthSchemes(authSchemes)
                .build();
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
            } else if (userid.contains("@")) {
                // no need for domain name with userPrincipalName
                domain = "";
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
            final String uriHost = uri.getHost().toLowerCase();
            final String[] domains = noProxyFor.toLowerCase().split(",\\s*");
            for (String domain : domains) {
                if (uriHost.endsWith(domain)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void startEvictorThread() {
        DavMailIdleConnectionEvictor.addConnectionManager(connectionManager);
    }

    @Override
    public void close() {
        DavMailIdleConnectionEvictor.removeConnectionManager(connectionManager);
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
        // make sure request path is absolute
        handleURI(request);
        // execute request and return response
        return httpClient.execute(request, context);
    }

    /**
     * fix relative uri and update current uri.
     *
     * @param request http request
     */
    private void handleURI(HttpRequestBase request) {
        URI requestURI = request.getURI();
        if (!requestURI.isAbsolute()) {
            request.setURI(URIUtils.resolve(uri, requestURI));
        }
        uri = request.getURI();
    }

    public ResponseWrapper executeFollowRedirect(PostRequest request) throws IOException {
        ResponseWrapper responseWrapper = request;
        LOGGER.debug(request.getMethod() + " " + request.getURI().toString());
        LOGGER.debug(request.getParameters());

        int count = 0;
        int maxRedirect = Settings.getIntProperty("davmail.httpMaxRedirects", MAX_REDIRECTS);

        executePostRequest(request);
        URI redirectLocation = request.getRedirectLocation();

        while (count++ < maxRedirect && redirectLocation != null) {
            LOGGER.debug("Redirect " + request.getURI() + " to " + redirectLocation);
            // replace uri with target location
            responseWrapper = new GetRequest(redirectLocation);
            executeGetRequest((GetRequest) responseWrapper);
            redirectLocation = ((GetRequest) responseWrapper).getRedirectLocation();
        }

        return responseWrapper;
    }

    public GetRequest executeFollowRedirect(GetRequest request) throws IOException {
        GetRequest result = request;
        LOGGER.debug(request.getMethod() + " " + request.getURI().toString());

        int count = 0;
        int maxRedirect = Settings.getIntProperty("davmail.httpMaxRedirects", MAX_REDIRECTS);

        executeGetRequest(request);
        URI redirectLocation = request.getRedirectLocation();

        while (count++ < maxRedirect && redirectLocation != null) {
            LOGGER.debug("Redirect " + request.getURI() + " to " + redirectLocation);
            // replace uri with target location
            result = new GetRequest(redirectLocation);
            executeGetRequest(result);
            redirectLocation = result.getRedirectLocation();
        }

        return result;
    }

    /**
     * Execute get request and return response body as string.
     *
     * @param getRequest get request
     * @return response body
     * @throws IOException on error
     */
    public String executeGetRequest(GetRequest getRequest) throws IOException {
        handleURI(getRequest);
        String responseBodyAsString;
        try (CloseableHttpResponse response = execute(getRequest)) {
            responseBodyAsString = getRequest.handleResponse(response);
        }
        return responseBodyAsString;
    }

    /**
     * Execute post request and return response body as string.
     *
     * @param postRequest post request
     * @return response body
     * @throws IOException on error
     */
    public String executePostRequest(PostRequest postRequest) throws IOException {
        handleURI(postRequest);
        String responseBodyAsString;
        try (CloseableHttpResponse response = execute(postRequest)) {
            responseBodyAsString = postRequest.handleResponse(response);
        }
        return responseBodyAsString;
    }

    public JSONObject executeRestRequest(RestRequest restRequest) throws IOException {
        handleURI(restRequest);
        JSONObject responseBody;
        try (CloseableHttpResponse response = execute(restRequest)) {
            responseBody = restRequest.handleResponse(response);
        }
        return responseBody;
    }

    /**
     * Execute WebDav request
     *
     * @param request WebDav request
     * @return multistatus response
     * @throws IOException on error
     */
    public MultiStatus executeDavRequest(BaseDavRequest request) throws IOException {
        handleURI(request);
        MultiStatus multiStatus = null;
        try (CloseableHttpResponse response = execute(request)) {
            request.checkSuccess(response);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
                multiStatus = request.getResponseBodyAsMultiStatus(response);
            }
        } catch (DavException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IOException(e.getErrorCode() + " " + e.getStatusPhrase(), e);
        }
        return multiStatus;
    }

    /**
     * Execute Exchange WebDav request
     *
     * @param request WebDav request
     * @return multistatus response
     * @throws IOException on error
     */
    public MultiStatusResponse[] executeDavRequest(ExchangeDavRequest request) throws IOException {
        handleURI(request);
        MultiStatusResponse[] responses;
        try (CloseableHttpResponse response = execute(request)) {
            List<MultiStatusResponse> responseList = request.handleResponse(response);
            // TODO check error handling
            //request.checkSuccess(response);
            responses = responseList.toArray(new MultiStatusResponse[0]);
        }
        return responses;
    }


    /**
     * Execute webdav search method.
     *
     * @param path            <i>encoded</i> searched folder path
     * @param searchStatement (SQL like) search statement
     * @param maxCount        max item count
     * @return Responses enumeration
     * @throws IOException on error
     */
    public MultiStatusResponse[] executeSearchRequest(String path, String searchStatement, int maxCount) throws IOException {
        ExchangeSearchRequest searchRequest = new ExchangeSearchRequest(path, searchStatement);
        if (maxCount > 0) {
            searchRequest.setHeader("Range", "rows=0-" + (maxCount - 1));
        }
        return executeDavRequest(searchRequest);
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
     * Get redirect location from header.
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
            LOGGER.debug("Creating NTCredentials for user " + userid + " workstation " + WORKSTATION_NAME + " domain " + domain);
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

    public String getUserAgent() {
        return Settings.getUserAgent();
    }

    public static HttpResponseException buildHttpResponseException(HttpRequestBase request, HttpResponse response) {
        return buildHttpResponseException(request, response.getStatusLine());
    }

    /**
     * Build Http Exception from method status
     *
     * @param method Http Method
     * @return Http Exception
     */
    public static HttpResponseException buildHttpResponseException(HttpRequestBase method, StatusLine statusLine) {
        int status = statusLine.getStatusCode();
        StringBuilder message = new StringBuilder();
        message.append(status).append(' ').append(statusLine.getReasonPhrase());
        message.append(" at ").append(method.getURI());
        if (method instanceof HttpCopy || method instanceof HttpMove) {
            message.append(" to ").append(method.getFirstHeader("Destination"));
        }
        // 440 means forbidden on Exchange
        if (status == 440) {
            return new LoginTimeoutException(message.toString());
        } else if (status == HttpStatus.SC_FORBIDDEN) {
            return new HttpForbiddenException(message.toString());
        } else if (status == HttpStatus.SC_NOT_FOUND) {
            return new HttpNotFoundException(message.toString());
        } else if (status == HttpStatus.SC_PRECONDITION_FAILED) {
            return new HttpPreconditionFailedException(message.toString());
        } else if (status == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            return new HttpServerErrorException(message.toString());
        } else {
            return new HttpResponseException(status, message.toString());
        }
    }

}

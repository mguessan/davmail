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
package davmail.http;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.*;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.util.IdleConnectionTimeoutThread;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Create HttpClient instance according to DavGateway Settings
 */
public final class DavGatewayHttpClientFacade {
    static final Logger LOGGER = Logger.getLogger("davmail.http.DavGatewayHttpClientFacade");

    static final String IE_USER_AGENT = "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0)";
    static final int MAX_REDIRECTS = 10;
    static final Object LOCK = new Object();
    private static MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager;
    private static boolean needNTLM;

    static final long ONE_MINUTE = 60000;

    private static IdleConnectionTimeoutThread httpConnectionManagerThread;

    static {
        DavGatewayHttpClientFacade.start();
    }

    private DavGatewayHttpClientFacade() {
    }

    /**
     * Create a configured HttpClient instance.
     *
     * @param url target url
     * @return httpClient
     * @throws DavMailException on error
     */
    public static HttpClient getInstance(String url) throws DavMailException {
        // create an HttpClient instance
        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, IE_USER_AGENT);
        httpClient.getParams().setParameter(HttpClientParams.MAX_REDIRECTS, MAX_REDIRECTS);
        configureClient(httpClient, url);
        return httpClient;
    }

    /**
     * Build an HttpClient instance for the provided url and credentials.
     *
     * @param url      http(s) url
     * @param userName user name
     * @param password user password
     * @return HttpClient instance
     * @throws DavMailException on error
     */
    public static HttpClient getInstance(String url, String userName, String password) throws DavMailException {
        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, IE_USER_AGENT);
        httpClient.getParams().setParameter(HttpClientParams.MAX_REDIRECTS, MAX_REDIRECTS);
        configureClient(httpClient, url);
        // some Exchange servers redirect to a different host for freebusy, use wide auth scope
        AuthScope authScope = new AuthScope(null, -1);
        httpClient.getState().setCredentials(authScope, new NTCredentials(userName, password, "", ""));
        return httpClient;
    }

    /**
     * Update http client configuration (proxy)
     *
     * @param httpClient current Http client
     * @param url        target url
     * @throws DavMailException on error
     */
    public static void configureClient(HttpClient httpClient, String url) throws DavMailException {
        try {
            HostConfiguration hostConfig = httpClient.getHostConfiguration();
            URI httpURI = new URI(url, true);
            hostConfig.setHost(httpURI);
        } catch (URIException e) {
            throw new DavMailException("LOG_INVALID_URL", url);
        }

        synchronized (LOCK) {
            httpClient.setHttpConnectionManager(multiThreadedHttpConnectionManager);
        }

        if (!needNTLM) {
            ArrayList<String> authPrefs = new ArrayList<String>();
            authPrefs.add(AuthPolicy.DIGEST);
            authPrefs.add(AuthPolicy.BASIC);
            // exclude NTLM authentication scheme
            httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
        }

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies");
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPassword = null;

        if (useSystemProxies) {
            // get proxy for url from system settings
            System.setProperty("java.net.useSystemProxies", "true");
            try {
                List<Proxy> proxyList = ProxySelector.getDefault().select(new java.net.URI(url));
                if (!proxyList.isEmpty() && proxyList.get(0).address() != null) {
                    InetSocketAddress inetSocketAddress = (InetSocketAddress) proxyList.get(0).address();
                    proxyHost = inetSocketAddress.getHostName();
                    proxyPort = inetSocketAddress.getPort();

                    // we may still need authentication credentials
                    proxyUser = Settings.getProperty("davmail.proxyUser");
                    proxyPassword = Settings.getProperty("davmail.proxyPassword");
                }
            } catch (URISyntaxException e) {
                throw new DavMailException("LOG_INVALID_URL", url);
            }


        } else if (enableProxy) {
            proxyHost = Settings.getProperty("davmail.proxyHost");
            proxyPort = Settings.getIntProperty("davmail.proxyPort");
            proxyUser = Settings.getProperty("davmail.proxyUser");
            proxyPassword = Settings.getProperty("davmail.proxyPassword");
        }

        // configure proxy
        if (proxyHost != null && proxyHost.length() > 0) {
            httpClient.getHostConfiguration().setProxy(proxyHost, proxyPort);
            if (proxyUser != null && proxyUser.length() > 0) {

                AuthScope authScope = new AuthScope(proxyHost, proxyPort, AuthScope.ANY_REALM);

                // detect ntlm authentication (windows domain name in user name)
                int backslashindex = proxyUser.indexOf('\\');
                if (backslashindex > 0) {
                    httpClient.getState().setProxyCredentials(authScope,
                            new NTCredentials(proxyUser.substring(backslashindex + 1),
                                    proxyPassword, "",
                                    proxyUser.substring(0, backslashindex)));
                } else {
                    httpClient.getState().setProxyCredentials(authScope,
                            new NTCredentials(proxyUser, proxyPassword, "", ""));
                }
            }
        }

    }


    /**
     * Get Http Status code for the given URL
     *
     * @param url url string
     * @return HttpStatus code
     * @throws IOException on error
     */
    public static int getHttpStatus(String url) throws IOException {
        int status = 0;
        HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(url);
        HttpMethod testMethod = new GetMethod(url);
        testMethod.setDoAuthentication(false);
        try {
            status = httpClient.executeMethod(testMethod);
        } finally {
            testMethod.releaseConnection();
        }
        return status;
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
     * Execute given url, manually follow redirects.
     * Workaround for HttpClient bug (GET full URL over HTTPS and proxy)
     *
     * @param httpClient HttpClient instance
     * @param url        url string
     * @return executed method
     * @throws IOException on error
     */
    public static HttpMethod executeFollowRedirects(HttpClient httpClient, String url) throws IOException {
        HttpMethod method = new GetMethod(url);
        method.setFollowRedirects(false);
        return executeFollowRedirects(httpClient, method);
    }

    /**
     * Execute method with httpClient, follow 30x redirects.
     *
     * @param httpClient Http client instance
     * @param method     Http method
     * @return last http method after redirects
     * @throws IOException on error
     */
    public static HttpMethod executeFollowRedirects(HttpClient httpClient, HttpMethod method) throws IOException {
        HttpMethod currentMethod = method;
        try {
            DavGatewayTray.debug(new BundleMessage("LOG_EXECUTE_FOLLOW_REDIRECTS", currentMethod.getURI()));
            int status = httpClient.executeMethod(currentMethod);
            // check NTLM
            if ((status == HttpStatus.SC_UNAUTHORIZED || status == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED)
                    && acceptsNTLMOnly(method) && !hasNTLM(httpClient)) {
                LOGGER.debug("Received " + status + " unauthorized at " + currentMethod.getURI() + ", retrying with NTLM");
                resetMethod(currentMethod);
                addNTLM(httpClient);
                status = httpClient.executeMethod(currentMethod);
            }
            Header location = currentMethod.getResponseHeader("Location");
            int redirectCount = 0;
            while (redirectCount++ < 10
                    && location != null
                    && isRedirect(status)) {
                currentMethod.releaseConnection();
                currentMethod = new GetMethod(location.getValue());
                currentMethod.setFollowRedirects(false);
                DavGatewayTray.debug(new BundleMessage("LOG_EXECUTE_FOLLOW_REDIRECTS_COUNT", currentMethod.getURI(), redirectCount));
                httpClient.executeMethod(currentMethod);
                location = currentMethod.getResponseHeader("Location");
            }
            if (location != null && isRedirect(currentMethod.getStatusCode())) {
                currentMethod.releaseConnection();
                throw new HttpException("Maximum redirections reached");
            }
        } catch (IOException e) {
            currentMethod.releaseConnection();
            throw e;
        }
        // caller will need to release connection
        return currentMethod;
    }

    /**
     * Execute webdav search method.
     *
     * @param httpClient    http client instance
     * @param path          <i>encoded</i> searched folder path
     * @param searchRequest (SQL like) search request
     * @return Responses enumeration
     * @throws IOException on error
     */
    public static MultiStatusResponse[] executeSearchMethod(HttpClient httpClient, String path, String searchRequest) throws IOException {
        String searchBody = "<?xml version=\"1.0\"?>\n" +
                "<d:searchrequest xmlns:d=\"DAV:\">\n" +
                "        <d:sql>" + StringUtil.xmlEncode(searchRequest) + "</d:sql>\n" +
                "</d:searchrequest>";
        DavMethodBase searchMethod = new DavMethodBase(path) {

            @Override
            public String getName() {
                return "SEARCH";
            }

            @Override
            protected boolean isSuccess(int statusCode) {
                return statusCode == 207;
            }
        };
        searchMethod.setRequestEntity(new StringRequestEntity(searchBody, "text/xml", "UTF-8"));
        return executeMethod(httpClient, searchMethod);
    }

    /**
     * Execute webdav propfind method.
     *
     * @param httpClient http client instance
     * @param path       <i>encoded</i> searched folder path
     * @param depth      propfind request depth
     * @param properties propfind requested properties
     * @return Responses enumeration
     * @throws IOException on error
     */
    public static MultiStatusResponse[] executePropFindMethod(HttpClient httpClient, String path, int depth, DavPropertyNameSet properties) throws IOException {
        PropFindMethod propFindMethod = new PropFindMethod(path, properties, depth);
        return executeMethod(httpClient, propFindMethod);
    }

    /**
     * Execute a delete method on the given path with httpClient.
     *
     * @param httpClient Http client instance
     * @param path       Path to be deleted
     * @throws IOException on error
     */
    public static void executeDeleteMethod(HttpClient httpClient, String path) throws IOException {
        DeleteMethod deleteMethod = new DeleteMethod(path);
        deleteMethod.setFollowRedirects(false);

        int status = executeHttpMethod(httpClient, deleteMethod);
        // do not throw error if already deleted
        if (status != HttpStatus.SC_OK && status != HttpStatus.SC_NOT_FOUND) {
            throw DavGatewayHttpClientFacade.buildHttpException(deleteMethod);
        }
    }

    /**
     * Execute webdav request.
     *
     * @param httpClient http client instance
     * @param method     webdav method
     * @return Responses enumeration
     * @throws IOException on error
     */
    public static MultiStatusResponse[] executeMethod(HttpClient httpClient, DavMethodBase method) throws IOException {
        MultiStatusResponse[] responses = null;
        try {
            int status = httpClient.executeMethod(method);

            // need to follow redirects (once) on public folders
            if (isRedirect(status)) {
                method.releaseConnection();
                URI targetUri = new URI(method.getResponseHeader("Location").getValue(), true);
                checkExpiredSession(targetUri.getQuery());
                method.setURI(targetUri);
                status = httpClient.executeMethod(method);
            }

            if (status != HttpStatus.SC_MULTI_STATUS) {
                throw buildHttpException(method);
            }
            responses = method.getResponseBodyAsMultiStatus().getResponses();

        } catch (DavException e) {
            throw new IOException(e.getMessage());
        } finally {
            method.releaseConnection();
        }
        return responses;
    }

    /**
     * Execute method with httpClient.
     *
     * @param httpClient Http client instance
     * @param method     Http method
     * @return Http status
     * @throws IOException on error
     */
    public static int executeHttpMethod(HttpClient httpClient, HttpMethod method) throws IOException {
        int status = 0;
        try {
            status = httpClient.executeMethod(method);
        } finally {
            method.releaseConnection();
        }
        return status;
    }

    /**
     * Test if NTLM auth scheme is enabled.
     *
     * @param httpClient HttpClient instance
     * @return true if NTLM is enabled
     */
    public static boolean hasNTLM(HttpClient httpClient) {
        Object authPrefs = httpClient.getParams().getParameter(AuthPolicy.AUTH_SCHEME_PRIORITY);
        return authPrefs == null || (authPrefs instanceof List<?> && ((Collection) authPrefs).contains(AuthPolicy.NTLM));
    }

    /**
     * Enable NTLM authentication on http client
     *
     * @param httpClient HttpClient instance
     */
    public static void addNTLM(HttpClient httpClient) {
        // NTLM authentication uses persistent connections, use private connection manager
        httpClient.setHttpConnectionManager(createConnectionManager());
        // register the jcifs based NTLMv2 implementation
        AuthPolicy.registerAuthScheme(AuthPolicy.NTLM, NTLMv2Scheme.class);

        ArrayList<String> authPrefs = new ArrayList<String>();
        authPrefs.add(AuthPolicy.NTLM);
        authPrefs.add(AuthPolicy.DIGEST);
        authPrefs.add(AuthPolicy.BASIC);
        httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
        // make sure NTLM is always active
        needNTLM = true;
    }

    /**
     * Test method header for supported authentication mode,
     * return true if Basic authentication is not available
     *
     * @param getMethod http method
     * @return true if only NTLM is enabled
     */
    public static boolean acceptsNTLMOnly(HttpMethod getMethod) {
        Header authenticateHeader = null;
        if (getMethod.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            authenticateHeader = getMethod.getResponseHeader("WWW-Authenticate");
        } else if (getMethod.getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
            authenticateHeader = getMethod.getResponseHeader("Proxy-Authenticate");
        }
        if (authenticateHeader == null) {
            return false;
        } else {
            boolean acceptBasic = false;
            boolean acceptNTLM = false;
            HeaderElement[] headerElements = authenticateHeader.getElements();
            for (HeaderElement headerElement : headerElements) {
                if ("NTLM".equalsIgnoreCase(headerElement.getName())) {
                    acceptNTLM = true;
                }
                if ("Basic realm".equalsIgnoreCase(headerElement.getName())) {
                    acceptBasic = true;
                }
            }
            return acceptNTLM && !acceptBasic;

        }
    }

    /**
     * Execute test method from checkConfig, with proxy credentials, but without Exchange credentials.
     *
     * @param httpClient Http client instance
     * @param method     Http method
     * @return Http status
     * @throws IOException on error
     */
    public static int executeTestMethod(HttpClient httpClient, GetMethod method) throws IOException {
        // do not follow redirects in expired sessions
        method.setFollowRedirects(false);
        int status = httpClient.executeMethod(method);
        if (status == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED
                && acceptsNTLMOnly(method) && !hasNTLM(httpClient)) {
            resetMethod(method);
            LOGGER.debug("Received " + status + " unauthorized at " + method.getURI() + ", retrying with NTLM");
            addNTLM(httpClient);
            status = httpClient.executeMethod(method);
        }

        return status;
    }

    /**
     * Execute Get method, do not follow redirects.
     *
     * @param httpClient      Http client instance
     * @param method          Http method
     * @param followRedirects Follow redirects flag
     * @throws IOException on error
     */
    public static void executeGetMethod(HttpClient httpClient, GetMethod method, boolean followRedirects) throws IOException {
        // do not follow redirects in expired sessions
        method.setFollowRedirects(followRedirects);
        int status = httpClient.executeMethod(method);
        if ((status == HttpStatus.SC_UNAUTHORIZED || status == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED)
                && acceptsNTLMOnly(method) && !hasNTLM(httpClient)) {
            resetMethod(method);
            LOGGER.debug("Received " + status + " unauthorized at " + method.getURI() + ", retrying with NTLM");
            addNTLM(httpClient);
            status = httpClient.executeMethod(method);
        }
        if (status != HttpStatus.SC_OK && (followRedirects || !isRedirect(status))) {
            LOGGER.warn("GET failed with status " + status + " at " + method.getURI() + ": " + method.getResponseBodyAsString());
            throw DavGatewayHttpClientFacade.buildHttpException(method);
        }
        // check for expired session
        if (followRedirects) {
            String queryString = method.getQueryString();
            checkExpiredSession(queryString);
            if (queryString != null && queryString.contains("reason=2")) {
                LOGGER.warn("GET failed, session expired  at " + method.getURI() + ": " + method.getResponseBodyAsString());
                throw DavGatewayHttpClientFacade.buildHttpException(method);
            }
        }
    }

    private static void resetMethod(HttpMethod method) {
        // reset method state
        method.releaseConnection();
        method.getHostAuthState().invalidate();
        method.getProxyAuthState().invalidate();
    }

    private static void checkExpiredSession(String queryString) throws DavMailAuthenticationException {
        if (queryString != null && queryString.contains("reason=2")) {
            LOGGER.warn("Request failed, session expired  (reason=2) ");
            throw new DavMailAuthenticationException("EXCEPTION_SESSION_EXPIRED");
        }
    }

    /**
     * Build Http Exception from methode status
     *
     * @param method Http Method
     * @return Http Exception
     */
    public static HttpException buildHttpException(HttpMethod method) {
        int status = method.getStatusCode();
        // 440 means forbidden on Exchange
        if (status == 440) {
            status = HttpStatus.SC_FORBIDDEN;
        }
        StringBuilder message = new StringBuilder();
        message.append(status).append(' ').append(method.getStatusText());
        try {
            message.append(" at ").append(method.getURI().getURI());
        } catch (URIException e) {
            message.append(method.getPath());
        }
        if (status == HttpStatus.SC_FORBIDDEN) {
            return new HttpForbiddenException(message.toString());
        } else if (status == HttpStatus.SC_NOT_FOUND) {
            return new HttpNotFoundException(message.toString());
        } else if (status == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            return new HttpServerErrorException(message.toString());
        } else {
            return new HttpException(message.toString());
        }
    }

    /**
     * Stop HttpConnectionManager.
     */
    public static void stop() {
        synchronized (LOCK) {
            if (multiThreadedHttpConnectionManager != null) {
                if (httpConnectionManagerThread != null) {
                    httpConnectionManagerThread.interrupt();
                }
                MultiThreadedHttpConnectionManager.shutdownAll();
                multiThreadedHttpConnectionManager = null;
            }
        }
    }

    private static MultiThreadedHttpConnectionManager createConnectionManager() {
        MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(100);
        connectionManager.getParams().setConnectionTimeout(10000);
        synchronized (LOCK) {
            httpConnectionManagerThread.addConnectionManager(connectionManager);
        }
        return connectionManager;
    }

    /**
     * Create and start a new HttpConnectionManager, close idle connections every minute.
     */
    public static void start() {
        synchronized (LOCK) {
            if (multiThreadedHttpConnectionManager == null) {
                httpConnectionManagerThread = new IdleConnectionTimeoutThread();
                httpConnectionManagerThread.setName(IdleConnectionTimeoutThread.class.getSimpleName());
                httpConnectionManagerThread.setConnectionTimeout(ONE_MINUTE);
                httpConnectionManagerThread.setTimeoutInterval(ONE_MINUTE);
                httpConnectionManagerThread.start();

                multiThreadedHttpConnectionManager = createConnectionManager();
            }
        }
    }
}

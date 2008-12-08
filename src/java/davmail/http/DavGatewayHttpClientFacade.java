package davmail.http;

import davmail.Settings;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.*;
import java.net.URI;
import java.util.List;

/**
 * Create HttpClient instance according to DavGateway Settings
 */
public class DavGatewayHttpClientFacade {
    static MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager;

    static {
        DavGatewayHttpClientFacade.start();
        // force XML response with Internet Explorer header
        System.getProperties().setProperty("httpclient.useragent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1; .NET CLR 1.1.4322)");
    }

    private DavGatewayHttpClientFacade() {
    }

    /**
     * Create a configured HttpClient instance.
     *
     * @return httpClient
     */
    public static HttpClient getInstance() {
        // create an HttpClient instance
        HttpClient httpClient = new HttpClient();
        configureClient(httpClient);
        return httpClient;
    }

    /**
     * Update http client configuration (proxy)
     *
     * @param httpClient current Http client
     */
    public static void configureClient(HttpClient httpClient) {
        httpClient.setHttpConnectionManager(multiThreadedHttpConnectionManager);

        // do not send basic auth automatically
        httpClient.getState().setAuthenticationPreemptive(false);

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        boolean systemProxy = Settings.getBooleanProperty("davmail.systemProxy");
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPassword = null;

        if (systemProxy) {
            String url = Settings.getProperty("davmail.url");
            try {
                List<Proxy> proxyList = ProxySelector.getDefault().select(
                        new URI(url));
                // get first returned proxy
                if (proxyList.size() > 0) {
                    Proxy proxy = proxyList.get(0);
                    if (proxy.equals(Proxy.NO_PROXY)) {
                        DavGatewayTray.debug("System proxy : direct connection");
                    } else {
                        InetSocketAddress addr = (InetSocketAddress) proxy.address();
                        proxyHost = addr.getHostName();
                        proxyPort = addr.getPort();
                        // no way to get credentials from system proxy
                        proxyUser = Settings.getProperty("davmail.proxyUser");
                        proxyPassword = Settings.getProperty("davmail.proxyPassword");

                        DavGatewayTray.debug("System proxy : " + proxyHost + ":" + proxyPort);
                    }

                }
            } catch (URISyntaxException e) {
                DavGatewayTray.error(e);
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

/*              // Only available in newer HttpClient releases, not compatible with slide library
                List authPrefs = new ArrayList();
                authPrefs.add(AuthPolicy.BASIC);
                httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,authPrefs);
*/
                // instead detect ntlm authentication (windows domain name in user name)
                int backslashindex = proxyUser.indexOf("\\");
                if (backslashindex > 0) {
                    httpClient.getState().setProxyCredentials(null, proxyHost,
                            new NTCredentials(proxyUser.substring(backslashindex + 1),
                                    proxyPassword, null,
                                    proxyUser.substring(0, backslashindex)));
                } else {
                    httpClient.getState().setProxyCredentials(null, proxyHost,
                            new UsernamePasswordCredentials(proxyUser, proxyPassword));
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
        HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();
        HttpMethod testMethod = new GetMethod(url);
        testMethod.setDoAuthentication(false);
        try {
            status = httpClient.executeMethod(testMethod);
        } finally {
            testMethod.releaseConnection();
        }
        return status;
    }

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

    public static HttpMethod executeFollowRedirects(HttpClient httpClient, HttpMethod method) throws IOException {
        try {
            DavGatewayTray.debug("executeFollowRedirects: " + method.getURI());
            httpClient.executeMethod(method);
            Header location = method.getResponseHeader("Location");
            int redirectCount = 0;
            while (redirectCount++ < 10
                    && location != null
                    && isRedirect(method.getStatusCode())) {
                method.releaseConnection();
                method = new GetMethod(location.getValue());
                method.setFollowRedirects(false);
                DavGatewayTray.debug("executeFollowRedirects: " + method.getURI() + " redirectCount:" + redirectCount);
                httpClient.executeMethod(method);
                location = method.getResponseHeader("Location");
            }
            if (location != null && isRedirect(method.getStatusCode())) {
                method.releaseConnection();
                throw new HttpException("Maximum redirections reached");
            }
        } catch (IOException e) {
            method.releaseConnection();
            throw e;
        }
        // caller will need to release connection
        return method;
    }

    public static void stop() {
        if (multiThreadedHttpConnectionManager != null) {
            multiThreadedHttpConnectionManager.shutdown();
            multiThreadedHttpConnectionManager = null;
        }
    }

    public static void start() {
        if (multiThreadedHttpConnectionManager == null) {
            multiThreadedHttpConnectionManager = new MultiThreadedHttpConnectionManager();
            multiThreadedHttpConnectionManager.setMaxConnectionsPerHost(10);
        }
    }
}

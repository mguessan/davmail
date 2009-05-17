package davmail.http;

import davmail.Settings;
import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Create HttpClient instance according to DavGateway Settings
 */
public final class DavGatewayHttpClientFacade {
    static final String IE_USER_AGENT = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)";
    static final int MAX_REDIRECTS = 10;
    static MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager;

    static final long ONE_MINUTE = 60000;

    static Thread httpConnectionManagerThread ;

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
        httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, IE_USER_AGENT);
        httpClient.getParams().setParameter(HttpClientParams.MAX_REDIRECTS, MAX_REDIRECTS);
        configureClient(httpClient);
        return httpClient;
    }

    public static HttpClient getInstance(HttpURL httpURL) throws URIException {
        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, IE_USER_AGENT);
        httpClient.getParams().setParameter(HttpClientParams.MAX_REDIRECTS, MAX_REDIRECTS);
        HostConfiguration hostConfig = httpClient.getHostConfiguration();
        hostConfig.setHost(httpURL);
        UsernamePasswordCredentials hostCredentials =
                new UsernamePasswordCredentials(httpURL.getUser(),
                        httpURL.getPassword());
        httpClient.getState().setCredentials(new AuthScope(httpURL.getHost(), httpURL.getPort(), AuthScope.ANY_REALM),
                hostCredentials);
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

        ArrayList<String> authPrefs = new ArrayList<String>();
        authPrefs.add(AuthPolicy.DIGEST);
        authPrefs.add(AuthPolicy.BASIC);
        // exclude the NTLM authentication scheme
        httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPassword = null;

        if (enableProxy) {
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
                                    proxyPassword, null,
                                    proxyUser.substring(0, backslashindex)));
                } else {
                    httpClient.getState().setProxyCredentials(authScope,
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
        HttpMethod currentMethod = method;
        try {
            DavGatewayTray.debug(new BundleMessage("LOG_EXECUTE_FOLLOW_REDIRECTS", currentMethod.getURI()));
            httpClient.executeMethod(currentMethod);
            Header location = currentMethod.getResponseHeader("Location");
            int redirectCount = 0;
            while (redirectCount++ < 10
                    && location != null
                    && isRedirect(currentMethod.getStatusCode())) {
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
                "        <d:sql>" + searchRequest.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;") + "</d:sql>\n" +
                "</d:searchrequest>";
        DavMethodBase searchMethod = new DavMethodBase(path) {

            @Override public String getName() {
                return "SEARCH";
            }

            @Override protected boolean isSuccess(int statusCode) {
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

    public static int executeDeleteMethod(HttpClient httpClient, String path) throws IOException {
        DeleteMethod deleteMethod = new DeleteMethod(path);

        int status = executeHttpMethod(httpClient, deleteMethod);
        // do not throw error if already deleted
        if (status != HttpStatus.SC_OK && status != HttpStatus.SC_NOT_FOUND) {
            throw DavGatewayHttpClientFacade.buildHttpException(deleteMethod);
        }
        return HttpStatus.SC_OK;
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
     * Build Http Exception from methode status
     *
     * @param method Http Method
     * @return Http Exception
     */
    public static HttpException buildHttpException(HttpMethod method) {
        return new HttpException(method.getStatusCode() + " " + method.getStatusText());
    }

    public static void stop() {
        if (multiThreadedHttpConnectionManager != null) {
            if (httpConnectionManagerThread != null) {
                httpConnectionManagerThread.interrupt();
            }
            multiThreadedHttpConnectionManager.shutdown();
            multiThreadedHttpConnectionManager = null;
        }
    }

    public static void start() {
        if (multiThreadedHttpConnectionManager == null) {
            multiThreadedHttpConnectionManager = new MultiThreadedHttpConnectionManager();
            multiThreadedHttpConnectionManager.getParams().setDefaultMaxConnectionsPerHost(100);
            httpConnectionManagerThread = new Thread(HttpConnectionManager.class.getSimpleName()) {
                @Override public void run() {
                    boolean terminated = false;
                    while (!terminated) {
                        try {
                            sleep(ONE_MINUTE);
                        } catch (InterruptedException e) {
                            terminated = true;
                        }
                        if (multiThreadedHttpConnectionManager != null) {
                            multiThreadedHttpConnectionManager.closeIdleConnections(ONE_MINUTE);
                        }
                    }
                }
            };
            httpConnectionManagerThread.setDaemon(true);
            httpConnectionManagerThread.start();
        }
    }
}

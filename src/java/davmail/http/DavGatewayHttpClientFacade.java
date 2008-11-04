package davmail.http;

import davmail.Settings;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;

/**
 * Create HttpClient instance according to DavGateway Settings
 */
public class DavGatewayHttpClientFacade {
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
        // do not send basic auth automatically
        httpClient.getState().setAuthenticationPreemptive(false);

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
        try {
            method.setFollowRedirects(false);
            httpClient.executeMethod(method);
            Header location = method.getResponseHeader("Location");
            int redirectCount = 0;
            while (redirectCount++ < 10
                    && location != null
                    && isRedirect(method.getStatusCode())) {
                method.releaseConnection();
                method = new GetMethod(method.getResponseHeader("Location").getValue());
                method.setFollowRedirects(false);
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
}

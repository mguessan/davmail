package davmail.http;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import davmail.Settings;

/**
 * Create HttpClient instance according to DavGateway Settings
 */
public class DavGatewayHttpClientFactory {
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

}

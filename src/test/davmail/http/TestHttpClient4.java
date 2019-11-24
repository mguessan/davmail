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

import davmail.AbstractDavMailTestCase;
import davmail.Settings;
import davmail.exchange.ews.AutoDiscoverMethod;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.Level;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class TestHttpClient4 extends AbstractDavMailTestCase {
    public void testBasicGetRequest() throws IOException {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            }
        }
    }

    public void testConnectionPooling() throws IOException {
        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
        poolingHttpClientConnectionManager.setDefaultMaxPerRoute(5);
        poolingHttpClientConnectionManager.setMaxTotal(5);
        poolingHttpClientConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(1000 * 60).build());

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(poolingHttpClientConnectionManager);
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            for (int i = 0; i < 10; i++) {

                HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
                try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                    System.out.println("Pool stats after execute: " + poolingHttpClientConnectionManager.getTotalStats());
                    assertEquals(1, poolingHttpClientConnectionManager.getTotalStats().getLeased());
                    assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getAvailable());
                    String responseString = new BasicResponseHandler().handleResponse(response);
                    System.out.println(responseString);
                    System.out.println("Pool stats after response: " + poolingHttpClientConnectionManager.getTotalStats());
                }
                System.out.println("Pool stats after close response: " + poolingHttpClientConnectionManager.getTotalStats());
                assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getLeased());
                assertEquals(1, poolingHttpClientConnectionManager.getTotalStats().getAvailable());
            }
        }
        System.out.println("Pool stats after close httpClient: " + poolingHttpClientConnectionManager.getTotalStats());
        assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getLeased());
        assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getAvailable());
    }

    public void testSSL() throws IOException {

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();

        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(schemeRegistry.build());
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setConnectionManager(poolingHttpClientConnectionManager);
        try (CloseableHttpClient httpClient = clientBuilder.build()) {

            HttpGet httpget = new HttpGet("https://outlook.office365.com");
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
            }
        }
    }

    public void testBasicAuthentication() throws IOException {
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();
        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials
                = new UsernamePasswordCredentials(username, password);
        provider.setCredentials(AuthScope.ANY, credentials);

        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(schemeRegistry.build());
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultCredentialsProvider(provider)
                .setConnectionManager(poolingHttpClientConnectionManager);
        try (CloseableHttpClient httpClient = clientBuilder.build()) {

            HttpGet httpget = new HttpGet("https://outlook.office365.com/EWS/Exchange.asmx");
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            }
        }
    }

    public void testHttpProxy() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        String proxyHost = Settings.getProperty("davmail.proxyHost");
        int proxyPort = Settings.getIntProperty("davmail.proxyPort");
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setProxy(proxy).setUserAgent(DavGatewayHttpClientFacade.IE_USER_AGENT);

        clientBuilder.setDefaultCredentialsProvider(getProxyCredentialProvider());

        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            }
        }
    }

    private CredentialsProvider getProxyCredentialProvider() {
        String proxyHost = Settings.getProperty("davmail.proxyHost");
        int proxyPort = Settings.getIntProperty("davmail.proxyPort");

        // proxy authentication
        String proxyUser = Settings.getProperty("davmail.proxyUser");
        String proxyPassword = Settings.getProperty("davmail.proxyPassword");
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        AuthScope authScope = new AuthScope(proxyHost, proxyPort, AuthScope.ANY_REALM);
        credentialsProvider.setCredentials(authScope, new UsernamePasswordCredentials(proxyUser, proxyPassword));
        return credentialsProvider;
    }

    public void testGetPath() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            String url = "http://davmail.sourceforge.net";
            // get with host
            HttpGet httpget = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                new BasicResponseHandler().handleResponse(response);
            }

            // get with path only
            httpget = new HttpGet(URIUtils.resolve(httpget.getURI(), "/version.txt"));
            try (CloseableHttpResponse response2 = httpClient.execute(httpget)) {
                String responseString = new BasicResponseHandler().handleResponse(response2);
                System.out.println(responseString);
            }
        }
    }

    public void testFollowRedirects() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create().disableRedirectHandling();
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            HttpGet httpget = new HttpGet("https://outlook.office365.com/owa/");
            Header location;
            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
                location = response.getFirstHeader("Location");
            }
            assertNotNull(location);
            URI targetUri = URIUtils.resolve(httpget.getURI(), location.getValue());
            httpget = new HttpGet(targetUri);
            try (CloseableHttpResponse response2 = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response2);
                System.out.println(responseString);
            }
        }
    }

    public void testTimeouts() throws IOException, InterruptedException {
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http.impl.conn", Level.DEBUG);

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();
        schemeRegistry.register("http", new PlainConnectionSocketFactory());
        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        Registry<ConnectionSocketFactory> registry = schemeRegistry.build();

        RequestConfig config = RequestConfig.custom()
                // time to get request from the pool
                .setConnectionRequestTimeout(5000)
                // socket connect timeout
                .setConnectTimeout(5000)
                // inactivity timeout
                .setSocketTimeout(5000)
                // disable redirect
                .setRedirectsEnabled(false)
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(config)
                .setConnectionManager(connectionManager);

        IdleConnectionEvictor evictor = new IdleConnectionEvictor(connectionManager, 1, TimeUnit.MINUTES);
        evictor.start();

        try (CloseableHttpClient httpClient = clientBuilder.build()) {

            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");

            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            }
            while (connectionManager.getTotalStats().getAvailable() > 0) {
                Thread.sleep(5000);
                System.out.println("Pool: " + connectionManager.getTotalStats());
            }
        } finally {
            evictor.shutdown();
        }

    }

    public void testTimeoutsWithProxy() throws IOException, InterruptedException {
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http.impl.conn", Level.DEBUG);

        RegistryBuilder<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create();
        schemeRegistry.register("http", new PlainConnectionSocketFactory());
        schemeRegistry.register("https", new SSLConnectionSocketFactory(new DavGatewaySSLSocketFactory(),
                SSLConnectionSocketFactory.getDefaultHostnameVerifier()));

        Registry<ConnectionSocketFactory> registry = schemeRegistry.build();

        RequestConfig config = RequestConfig.custom()
                // time to get request from the pool
                .setConnectionRequestTimeout(5000)
                // socket connect timeout
                .setConnectTimeout(5000)
                // inactivity timeout
                .setSocketTimeout(5000)
                // disable redirect
                .setRedirectsEnabled(false)
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(config)
                .setConnectionManager(connectionManager);
        String proxyHost = Settings.getProperty("davmail.proxyHost");
        int proxyPort = Settings.getIntProperty("davmail.proxyPort");
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        clientBuilder.setProxy(proxy);

        clientBuilder.setDefaultCredentialsProvider(getProxyCredentialProvider());

        IdleConnectionEvictor evictor = new IdleConnectionEvictor(connectionManager, 1, TimeUnit.MINUTES);
        evictor.start();

        try (CloseableHttpClient httpClient = clientBuilder.build()) {

            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");

            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            }
            while (connectionManager.getTotalStats().getAvailable() > 0) {
                Thread.sleep(5000);
                System.out.println("Pool: " + connectionManager.getTotalStats());
            }
        } finally {
            evictor.shutdown();
        }

    }

    public void testAutoDiscover() throws IOException {
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);
        //Settings.setLoggingLevel("org.apache.http.impl.conn", Level.DEBUG);

        String userid;
        String userEmail;
        int pipeIndex = username.indexOf("|");
        if (pipeIndex >= 0) {
            userid = username.substring(0, pipeIndex);
            userEmail = username.substring(pipeIndex + 1);
        } else {
            userid = username;
            userEmail = username;
        }


        String suffix = userEmail.substring(userEmail.indexOf("@") + 1);
        String autodiscoverHost = "autodiscover." + suffix;
        url = "http://" + autodiscoverHost + "/autodiscover/autodiscover.xml";

        String ewsUrl;

        try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url, userid, password)) {
            AutoDiscoverMethod autoDiscoverRequest = new AutoDiscoverMethod(url, userEmail);
            try (CloseableHttpResponse httpResponse = httpClientAdapter.executeFollowRedirects(autoDiscoverRequest)) {
                ewsUrl = (String) autoDiscoverRequest.handleResponse(httpResponse);
            }
        }
        System.out.println(ewsUrl);
        assertNotNull(ewsUrl);
    }
}

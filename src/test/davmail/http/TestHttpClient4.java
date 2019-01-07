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
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.Level;

import java.io.IOException;
import java.net.URI;

public class TestHttpClient4 extends AbstractDavMailTestCase {
    public void testBasicGetRequest() throws IOException {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        CloseableHttpClient httpClient = clientBuilder.build();
        try {

            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
            CloseableHttpResponse response = httpClient.execute(httpget);
            try {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }

    public void testConnectionPooling() throws IOException {
        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
        poolingHttpClientConnectionManager.setDefaultMaxPerRoute(5);
        poolingHttpClientConnectionManager.setMaxTotal(5);
        poolingHttpClientConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(1000 * 60).build());

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(poolingHttpClientConnectionManager);
        CloseableHttpClient httpClient = clientBuilder.build();
        try {
            for (int i = 0; i < 10; i++) {

                HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
                CloseableHttpResponse response = httpClient.execute(httpget);
                System.out.println("Pool stats after execute: " + poolingHttpClientConnectionManager.getTotalStats());
                assertEquals(1, poolingHttpClientConnectionManager.getTotalStats().getLeased());
                assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getAvailable());
                try {
                    String responseString = new BasicResponseHandler().handleResponse(response);
                    System.out.println(responseString);
                    System.out.println("Pool stats after response: " + poolingHttpClientConnectionManager.getTotalStats());
                } finally {
                    response.close();
                }
                System.out.println("Pool stats after close response: " + poolingHttpClientConnectionManager.getTotalStats());
                assertEquals(0, poolingHttpClientConnectionManager.getTotalStats().getLeased());
                assertEquals(1, poolingHttpClientConnectionManager.getTotalStats().getAvailable());
            }
        } finally {
            httpClient.close();
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
        CloseableHttpClient httpClient = clientBuilder.build();
        try {

            HttpGet httpget = new HttpGet("https://outlook.office365.com");
            CloseableHttpResponse response = httpClient.execute(httpget);
            try {
                assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }

    public void testBasicAuthentication() throws IOException {

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
        CloseableHttpClient httpClient = clientBuilder.build();
        try {

            HttpGet httpget = new HttpGet("https://outlook.office365.com/EWS/Exchange.asmx");
            CloseableHttpResponse response = httpClient.execute(httpget);
            try {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
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
        CloseableHttpClient httpClient = clientBuilder.build();
        try {
            HttpGet httpget = new HttpGet("http://davmail.sourceforge.net/version.txt");
            CloseableHttpResponse response = httpClient.execute(httpget);
            try {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }

    public void testGetPath() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        CloseableHttpClient httpClient = clientBuilder.build();
        try {
            String url = "http://davmail.sourceforge.net";
            // get with host
            HttpGet httpget = new HttpGet(url);
            CloseableHttpResponse response = httpClient.execute(httpget);
            try {
                new BasicResponseHandler().handleResponse(response);
            } finally {
                response.close();
            }

            // get with path only
            httpget = new HttpGet(URIUtils.resolve(httpget.getURI(), "/version.txt"));
            response = httpClient.execute(httpget);
            try {
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }

    public void testFollowRedirects() throws IOException {
        Settings.setLoggingLevel("org.apache.http.wire", Level.DEBUG);
        Settings.setLoggingLevel("org.apache.http", Level.DEBUG);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create().disableRedirectHandling();
        CloseableHttpClient httpClient = clientBuilder.build();
        try {
            HttpGet httpget = new HttpGet("https://outlook.office365.com/owa/");
            CloseableHttpResponse response = httpClient.execute(httpget);
            Header location;
            try {
                assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getStatusLine().getStatusCode());
                location = response.getFirstHeader("Location");
            } finally {
                response.close();
            }
            assertNotNull(location);
            URI targetUri = URIUtils.resolve(httpget.getURI(), location.getValue());
            httpget = new HttpGet(targetUri);
            response = httpClient.execute(httpget);
            try {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseString = new BasicResponseHandler().handleResponse(response);
                System.out.println(responseString);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }
}

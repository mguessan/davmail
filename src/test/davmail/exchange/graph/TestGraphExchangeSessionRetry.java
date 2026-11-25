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

package davmail.exchange.graph;

import com.sun.net.httpserver.HttpServer;
import davmail.Settings;
import davmail.exception.HttpServerErrorException;
import davmail.exchange.auth.O365Token;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry rules of Graph requests: bounded retries on 502/503/504 for GET only, no retry on 500,
 * and a total wait budget per request.
 * Uses a local mock Graph server and a fake token, no network access.
 * Answers send Retry-After: 0 (1 second wait) to keep the test short.
 */
public class TestGraphExchangeSessionRetry extends TestCase {

    private HttpServer server;
    /**
     * Status codes to answer, then 200.
     */
    private final ConcurrentLinkedQueue<Integer> statusCodes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile String retryAfter = "0";
    private GraphExchangeSession session;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.oauth.persistToken", "false");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            try (InputStream is = exchange.getRequestBody()) {
                while (is.read() >= 0) {
                    // drain request
                }
            }
            Integer statusCode = statusCodes.poll();
            if (statusCode == null) {
                statusCode = 200;
            }
            byte[] body;
            if (statusCode == 200) {
                body = "{\"value\":[{\"id\":\"inbox\"}]}".getBytes(StandardCharsets.UTF_8);
            } else {
                body = "{\"error\":{\"code\":\"ServiceUnavailable\",\"message\":\"test\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Settings.setProperty("davmail.graphUrl", baseUrl);
        session = new GraphExchangeSession(new HttpClientAdapter(baseUrl, true), fakeToken(), "user@example.com");
        requestCount.set(0);
    }

    @Override
    protected void tearDown() throws Exception {
        session.close();
        server.stop(0);
        super.tearDown();
    }

    private static O365Token fakeToken() throws Exception {
        O365Token token = new O365Token("common", "client-id", "http://localhost", "password");
        token.setJsonToken(new JSONObject()
                .put("access_token", "fake-access-token")
                .put("refresh_token", "fake-refresh-token")
                .put("expires_in", 3600));
        return token;
    }

    private GraphObject get() throws IOException {
        return session.executeGraphRequest(new GraphRequestBuilder()
                .setMethod(HttpGet.METHOD_NAME)
                .setObjectType("mailFolders"));
    }

    private GraphObject post() throws IOException {
        return session.executeGraphRequest(new GraphRequestBuilder()
                .setMethod(HttpPost.METHOD_NAME)
                .setObjectType("mailFolders")
                .setJsonBody(new JSONObject()));
    }

    public void testGetRetriedAfterServerError() throws IOException {
        statusCodes.add(503);
        statusCodes.add(504);
        GraphObject graphObject = get();
        assertEquals(200, graphObject.statusCode);
        assertEquals(3, requestCount.get());
    }

    public void testGetServerErrorRetriesBounded() {
        for (int i = 0; i < 10; i++) {
            statusCodes.add(502);
        }
        try {
            get();
            fail("expected HttpServerErrorException");
        } catch (HttpServerErrorException e) {
            // expected
        } catch (IOException e) {
            fail("expected HttpServerErrorException, got " + e);
        }
        assertEquals(1 + GraphExchangeSession.MAX_SERVER_ERROR_RETRIES, requestCount.get());
    }

    public void testPostNotRetriedAfterServerError() {
        statusCodes.add(503);
        try {
            post();
            fail("expected IOException");
        } catch (IOException e) {
            // expected
        }
        assertEquals(1, requestCount.get());
    }

    public void testInternalServerErrorNotRetried() {
        statusCodes.add(500);
        try {
            get();
            fail("expected HttpServerErrorException");
        } catch (HttpServerErrorException e) {
            // expected
        } catch (IOException e) {
            fail("expected HttpServerErrorException, got " + e);
        }
        assertEquals(1, requestCount.get());
    }

    public void testRetryBudget() {
        // Retry-After longer than the budget: fail at once instead of waiting
        retryAfter = String.valueOf(GraphExchangeSession.RETRY_BUDGET);
        statusCodes.add(429);
        long start = System.currentTimeMillis();
        try {
            get();
            fail("expected HttpServerErrorException");
        } catch (HttpServerErrorException e) {
            // expected
        } catch (IOException e) {
            fail("expected HttpServerErrorException, got " + e);
        }
        assertEquals(1, requestCount.get());
        assertTrue(System.currentTimeMillis() - start < 5000);
    }

    public void testThrottledPostRetried() throws IOException {
        statusCodes.add(429);
        GraphObject graphObject = post();
        assertEquals(200, graphObject.statusCode);
        assertEquals(2, requestCount.get());
    }
}

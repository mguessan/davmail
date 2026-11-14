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
import davmail.exchange.auth.O365Token;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.codehaus.jettison.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Regression test for https://github.com/mguessan/davmail/issues/544:
 * after an Error inside HttpClient (e.g. OutOfMemoryError) the connection pool is shut down,
 * the cached Graph session must then be reported as expired instead of throwing
 * IllegalStateException "Connection pool shut down" forever.
 * Uses a local mock Graph server and a fake token, no network access.
 */
public class TestGraphExchangeSessionPoolShutdown extends TestCase {

    private HttpServer server;
    private String baseUrl;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.oauth.persistToken", "false");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = "{\"value\":[{\"id\":\"inbox\"}]}".getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                while (is.read() >= 0) {
                    // drain request
                }
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Settings.setProperty("davmail.graphUrl", baseUrl);
    }

    @Override
    protected void tearDown() throws Exception {
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

    /**
     * Request entity failing with an Error while being sent, MainClientExec then shuts down the connection manager.
     */
    private static class ErrorEntity extends AbstractHttpEntity {
        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(OutputStream outstream) {
            throw new OutOfMemoryError("simulated");
        }

        @Override
        public boolean isStreaming() {
            return false;
        }
    }

    public void testIsExpiredAfterConnectionPoolShutDown() throws Exception {
        HttpClientAdapter httpClient = new HttpClientAdapter(baseUrl, true);
        GraphExchangeSession session = new GraphExchangeSession(httpClient, fakeToken(), "user@example.com");
        assertFalse("fresh session must be valid", session.isExpired());

        HttpPost post = new HttpPost(baseUrl + "/boom");
        post.setEntity(new ErrorEntity());
        try {
            httpClient.execute(post).close();
            fail("expected simulated OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            // HttpClient shut down its connection pool
        }

        // before the fix: IllegalStateException "Connection pool shut down" on every call, session never replaced
        assertTrue("session with shut down connection pool must be expired", session.isExpired());
        session.close();
    }
}

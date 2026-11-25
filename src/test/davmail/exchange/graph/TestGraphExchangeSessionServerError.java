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
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.exchange.auth.O365Token;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;
import org.codehaus.jettison.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A Graph server error (5xx) during the session check must not expire the session:
 * closing it would close the HTTP client shared by all connections of the user,
 * see https://github.com/mguessan/davmail/issues/544.
 * Uses a local mock Graph server and a fake token, no network access.
 */
public class TestGraphExchangeSessionServerError extends TestCase {

    private HttpServer server;
    private volatile int statusCode = 200;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.oauth.persistToken", "false");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                while (is.read() >= 0) {
                    // drain request
                }
            }
            byte[] body;
            if (statusCode == 200) {
                body = "{\"value\":[{\"id\":\"inbox\"}]}".getBytes(StandardCharsets.UTF_8);
            } else {
                body = "{\"error\":{\"code\":\"InternalServerError\",\"message\":\"test\"}}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        Settings.setProperty("davmail.graphUrl", "http://127.0.0.1:" + server.getAddress().getPort());
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

    public void testServerErrorDoesNotExpireSession() throws Exception {
        HttpClientAdapter httpClient = new HttpClientAdapter(Settings.getGraphUrl(), true);
        GraphExchangeSession session = new GraphExchangeSession(httpClient, fakeToken(), "user@example.com");
        try {
            statusCode = 500;
            try {
                session.isExpired();
                fail("expected HttpServerErrorException");
            } catch (HttpServerErrorException e) {
                // expected: server unavailable, not an expired token
            }

            // the factory keeps the cached session instead of a network down error
            ExchangeSession checkedSession = ExchangeSessionFactory.getInstance(session, "user@example.com", "password");
            assertSame(session, checkedSession);
            assertFalse("shared http client must stay open", httpClient.isClosed());

            // Graph is back: the same session works
            statusCode = 200;
            assertFalse(session.isExpired());
        } finally {
            session.close();
        }
    }
}

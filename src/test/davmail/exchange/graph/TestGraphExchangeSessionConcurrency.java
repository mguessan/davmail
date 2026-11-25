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
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jettison.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * At most MAILBOX_CONCURRENCY Graph requests run at the same time on one mailbox.
 * Uses a local mock Graph server and a fake token, no network access.
 */
public class TestGraphExchangeSessionConcurrency extends TestCase {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private volatile long delayMillis;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.oauth.persistToken", "false");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(16);
        server.setExecutor(serverExecutor);
        byte[] body = "{\"value\":[{\"id\":\"inbox\"}]}".getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            int current = running.incrementAndGet();
            maxRunning.accumulateAndGet(current, Math::max);
            try (InputStream is = exchange.getRequestBody()) {
                while (is.read() >= 0) {
                    // drain request
                }
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
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
        serverExecutor.shutdownNow();
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

    public void testConcurrentRequestsPerMailbox() throws Exception {
        GraphExchangeSession session = new GraphExchangeSession(
                new HttpClientAdapter(Settings.getGraphUrl(), true), fakeToken(), "user@example.com");
        delayMillis = 300;
        maxRunning.set(0);
        ExecutorService clients = Executors.newFixedThreadPool(8);
        try {
            List<Future<GraphObject>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(clients.submit(() -> session.executeGraphRequest(new GraphRequestBuilder()
                        .setMethod(HttpGet.METHOD_NAME)
                        .setObjectType("mailFolders"))));
            }
            for (Future<GraphObject> result : results) {
                assertEquals(200, result.get(30, TimeUnit.SECONDS).statusCode);
            }
            assertEquals(GraphExchangeSession.MAILBOX_CONCURRENCY, maxRunning.get());
        } finally {
            clients.shutdownNow();
            session.close();
        }
    }
}

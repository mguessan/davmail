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
import org.codehaus.jettison.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A folder of a shared mailbox loaded with internalGetFolder must keep its mailbox,
 * else later requests on this folder (delta sync) go to /me with the shared folder id.
 * Uses a local mock Graph server and a fake token, no network access.
 */
public class TestGraphExchangeSessionSharedFolder extends TestCase {

    private HttpServer server;
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.oauth.persistToken", "false");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = ("{\"id\":\"sent-id\",\"parentFolderId\":\"root-id\",\"displayName\":\"Sent Items\","
                + "\"value\":[],\"@odata.deltaLink\":\"delta\"}").getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            requestPaths.add(exchange.getRequestURI().getPath());
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

    public void testSharedMailboxFolderKeepsMailbox() throws Exception {
        HttpClientAdapter httpClient = new HttpClientAdapter(Settings.getGraphUrl(), true);
        GraphExchangeSession session = new GraphExchangeSession(httpClient, fakeToken(), "user@example.com");
        try {
            GraphExchangeSession.Folder folder = session.internalGetFolder("/users/shared@example.com/Sent");
            assertEquals("shared@example.com", folder.folderId.mailbox);

            requestPaths.clear();
            session.getDeltaLink(folder.folderId);
            assertFalse(requestPaths.isEmpty());
            for (String path : requestPaths) {
                assertTrue("request must target the shared mailbox: " + path,
                        path.contains("/users/shared@example.com/"));
            }
        } finally {
            session.close();
        }
    }
}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2026  Arseniy Glushkov
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
package davmail.exchange.ews;

import com.sun.net.httpserver.HttpServer;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestEwsBatchGetItem extends TestCase {

    private static final String EMAIL = "user@example.com";

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger getItemCount = new AtomicInteger();
    private boolean failBatch;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Settings.setDefaultSettings();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String request = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
                if (request.contains("GetItem")) {
                    getItemCount.incrementAndGet();
                    List<String> ids = extractIds(request);
                    if (failBatch && ids.size() > 1) {
                        exchange.sendResponseHeaders(500, -1);
                    } else {
                        respond(exchange, buildGetItemResponse(ids));
                    }
                } else {
                    respond(exchange, buildGetItemResponse(new ArrayList<>()));
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/ews/exchange.asmx";
    }

    @Override
    protected void tearDown() throws Exception {
        server.stop(0);
        super.tearDown();
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) >= 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private static List<String> extractIds(String request) {
        List<String> ids = new ArrayList<>();
        int index = 0;
        while ((index = request.indexOf("ItemId Id=\"", index)) >= 0) {
            int start = index + "ItemId Id=\"".length();
            int end = request.indexOf('"', start);
            ids.add(request.substring(start, end));
            index = end;
        }
        return ids;
    }

    private static String buildGetItemResponse(List<String> ids) {
        StringBuilder items = new StringBuilder();
        for (String id : ids) {
            items.append("<m:GetItemResponseMessage ResponseClass=\"Success\">")
                    .append("<m:ResponseCode>NoError</m:ResponseCode><m:Items>")
                    .append("<t:CalendarItem><t:ItemId Id=\"").append(id).append("\" ChangeKey=\"ck\"/>")
                    .append("<t:MimeContent>").append(mimeContent(id)).append("</t:MimeContent>")
                    .append("</t:CalendarItem></m:Items></m:GetItemResponseMessage>");
        }
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:m=\"http://schemas.microsoft.com/exchange/services/2006/messages\" " +
                "xmlns:t=\"http://schemas.microsoft.com/exchange/services/2006/types\">" +
                "<soap:Body><m:GetItemResponse><m:ResponseMessages>" + items +
                "</m:ResponseMessages></m:GetItemResponse></soap:Body></soap:Envelope>";
    }

    private static String mimeContent(String uid) {
        String ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\n" +
                "UID:" + uid + "\r\nDTSTAMP:20260101T000000Z\r\n" +
                "DTSTART:20260102T000000Z\r\nDTEND:20260102T010000Z\r\n" +
                "SUMMARY:Event " + uid + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        return Base64.getEncoder().encodeToString(ics.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private EwsExchangeSession createSession() throws IOException {
        return new EwsExchangeSession(new HttpClientAdapter(baseUrl, "user", "pass", true), EMAIL) {
            @Override
            public void buildSessionInfo(URI uri) {
                email = EMAIL;
                alias = "user";
            }
        };
    }

    private List<ExchangeSession.Event> buildEvents(EwsExchangeSession session, int count) {
        List<ExchangeSession.Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EWSMethod.Item item = new EWSMethod.Item();
            item.type = "CalendarItem";
            item.put("ItemId", "id" + i);
            item.put("ChangeKey", "ck");
            events.add(session.new Event("/users/" + EMAIL + "/calendar", item));
        }
        return events;
    }

    public void testBatchPrefetchSingleRequest() throws IOException {
        Settings.setProperty("davmail.caldavBatchSize", "50");
        EwsExchangeSession session = createSession();
        List<ExchangeSession.Event> events = buildEvents(session, 3);
        session.prefetchEvents(events);
        assertEquals("3 events must be loaded in 1 batched GetItem", 1, getItemCount.get());
        for (ExchangeSession.Event event : events) {
            assertNotNull(event.getBody());
        }
        assertEquals("bodies must come from prefetch, no extra requests", 1, getItemCount.get());
        session.close();
    }

    public void testBatchFailureFallsBackToIndividual() throws IOException {
        failBatch = true;
        Settings.setProperty("davmail.caldavBatchSize", "50");
        EwsExchangeSession session = createSession();
        List<ExchangeSession.Event> events = buildEvents(session, 2);
        session.prefetchEvents(events);
        for (ExchangeSession.Event event : events) {
            assertNotNull("body must load individually after batch failure", event.getBody());
        }
        assertEquals("1 failed batch + 2 individual requests", 3, getItemCount.get());
        session.close();
    }

    public void testBatchPrefetchChunked() throws IOException {
        Settings.setProperty("davmail.caldavBatchSize", "2");
        EwsExchangeSession session = createSession();
        List<ExchangeSession.Event> events = buildEvents(session, 5);
        session.prefetchEvents(events);
        assertEquals("5 events with batchSize=2 must use 3 GetItem requests", 3, getItemCount.get());
        session.close();
    }
}

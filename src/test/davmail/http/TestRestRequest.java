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

import davmail.http.request.RestRequest;
import junit.framework.TestCase;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.codehaus.jettison.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class TestRestRequest extends TestCase {
    private static final String JSON_BODY = "{\"value\":\"пример\"}";

    public void testAcceptsJsonMediaTypes() throws Exception {
        for (String contentType : new String[]{"application/json", "application/json; charset=utf-8",
                "application/json;charset=UTF-8", "Application/JSON; charset=\"UTF-8\""}) {
            assertEquals(contentType, "пример", readResponse(contentType).getString("value"));
        }
    }

    public void testAcceptsGzipCompressedJson() throws Exception {
        ByteArrayOutputStream compressedBody = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedBody)) {
            gzip.write(JSON_BODY.getBytes(StandardCharsets.UTF_8));
        }

        BasicHttpResponse response = createResponse("application/json;charset=UTF-8");
        response.setHeader("Content-Encoding", "gzip");
        response.setEntity(new ByteArrayEntity(compressedBody.toByteArray()));
        assertEquals("пример", new RestRequest("https://localhost/").handleResponse(response).getString("value"));
    }

    public void testRejectsOtherMediaTypes() throws Exception {
        for (String contentType : new String[]{null, "", "text/html", "application/jsonp", "application/json, text/html"}) {
            try {
                readResponse(contentType);
                fail("Accepted Content-Type " + contentType);
            } catch (IOException e) {
                assertEquals("Invalid response content", e.getMessage());
            }
        }
    }

    private JSONObject readResponse(String contentType) throws IOException {
        BasicHttpResponse response = createResponse(contentType);
        response.setEntity(new StringEntity(JSON_BODY, StandardCharsets.UTF_8));
        return new RestRequest("https://localhost/").handleResponse(response);
    }

    private BasicHttpResponse createResponse(String contentType) {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        if (contentType != null) {
            response.setHeader("Content-Type", contentType);
        }
        return response;
    }
}

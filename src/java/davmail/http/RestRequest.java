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

import davmail.util.IOUtil;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

public class RestRequest extends HttpPost implements ResponseHandler {
    protected static final Logger LOGGER = Logger.getLogger(RestRequest.class);

    JSONObject jsonBody;
    JSONObject jsonResponse;

    public RestRequest(String uri) {
        super(uri);

        AbstractHttpEntity httpEntity = new AbstractHttpEntity() {
            byte[] content;

            @Override
            public boolean isRepeatable() {
                return true;
            }

            @Override
            public long getContentLength() {
                if (content == null) {
                    content = getJsonContent();
                }
                return content.length;
            }

            @Override
            public InputStream getContent() throws UnsupportedOperationException {
                if (content == null) {
                    content = getJsonContent();
                }
                return new ByteArrayInputStream(content);
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = getJsonContent();
                }
                outputStream.write(content);
            }

            @Override
            public boolean isStreaming() {
                return false;
            }
        };
        httpEntity.setContentType("application/json; charset=UTF-8");
        setEntity(httpEntity);

    }

    protected byte[] getJsonContent() {
        return jsonBody.toString().getBytes(Consts.UTF_8);
    }

    public void setJsonBody(JSONObject jsonBody) {
        this.jsonBody = jsonBody;
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        Header contentTypeHeader = response.getFirstHeader("Content-Type");
        if (contentTypeHeader != null && "application/json; charset=utf-8".equals(contentTypeHeader.getValue())) {
            try {
                InputStream inputStream = response.getEntity().getContent();
                if (isGzipEncoded(response)) {
                    inputStream = new GZIPInputStream(inputStream);
                }
                processResponseStream(inputStream);
            } catch (IOException e) {
                LOGGER.error("Error while parsing json response: " + e, e);
            } catch (JSONException e) {
                LOGGER.error("Error while parsing json response: " + e, e);
            }
        }
        return jsonResponse;
    }

    /**
     * Test if the response is gzip encoded
     *
     * @param response http response
     * @return true if response is gzip encoded
     */
    public boolean isGzipEncoded(HttpResponse response) {
        // TODO: move to HttpClientAdapter
        Header header = response.getFirstHeader("Content-Encoding");
        return header != null && "gzip".equals(header.getValue());
    }

    private void processResponseStream(InputStream responseBodyAsStream) throws IOException, JSONException {
        // quick non streaming implementation
        jsonResponse = new JSONObject(new String(IOUtil.readFully(responseBodyAsStream), "UTF-8"));
    }

    public JSONObject getJsonResponse() {
        return jsonResponse;
    }
}

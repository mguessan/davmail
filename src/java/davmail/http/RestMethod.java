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
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * REST/JSON method implementation
 */
public class RestMethod extends PostMethod {
    protected static final Logger LOGGER = Logger.getLogger(RestMethod.class);

    JSONObject jsonBody;
    JSONObject jsonResponse;

    public RestMethod(String uri) {
        super(uri);

        setRequestEntity(new RequestEntity() {
            byte[] content;

            public boolean isRepeatable() {
                return true;
            }

            public void writeRequest(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = getJsonContent();
                }
                outputStream.write(content);
            }

            public long getContentLength() {
                if (content == null) {
                    content = getJsonContent();
                }
                return content.length;
            }

            public String getContentType() {
                return "application/json; charset=UTF-8";
            }
        });
    }

    public void setJsonBody(JSONObject jsonBody) {
        this.jsonBody = jsonBody;
    }

    public JSONObject getJsonResponse() {
        return jsonResponse;
    }

    protected byte[] getJsonContent() {
        return jsonBody.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        Header contentTypeHeader = getResponseHeader("Content-Type");
        if (contentTypeHeader != null && "application/json; charset=utf-8".equals(contentTypeHeader.getValue())) {
            try {
                if (DavGatewayHttpClientFacade.isGzipEncoded(this)) {
                    processResponseStream(new GZIPInputStream(getResponseBodyAsStream()));
                } else {
                    processResponseStream(getResponseBodyAsStream());
                }
            } catch (IOException | JSONException e) {
                LOGGER.error("Error while parsing json response: " + e, e);
            }
        }
    }

    private void processResponseStream(InputStream responseBodyAsStream) throws IOException, JSONException {
        // quick non streaming implementation
        jsonResponse = new JSONObject(new String(IOUtil.readFully(responseBodyAsStream), StandardCharsets.UTF_8));
    }
}

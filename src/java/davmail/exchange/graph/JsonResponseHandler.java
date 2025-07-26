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

import davmail.exception.HttpForbiddenException;
import davmail.exception.HttpNotFoundException;
import davmail.http.HttpClientAdapter;
import davmail.util.IOUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Generic Json response handler for graph API calls
 */
public class JsonResponseHandler implements ResponseHandler<JSONObject> {
    @Override
    public JSONObject handleResponse(HttpResponse response) throws IOException {
        JSONObject jsonResponse = null;
        Header contentTypeHeader = response.getFirstHeader("Content-Type");
        if (contentTypeHeader != null && contentTypeHeader.getValue().startsWith("application/json")) {
            try {
                jsonResponse = new JSONObject(new String(readResponse(response), StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new IOException(e.getMessage(), e);
            }
        } else {
            HttpEntity httpEntity = response.getEntity();
            if (httpEntity != null) {
                try {
                    return new JSONObject().put("response", new String(readResponse(response), StandardCharsets.UTF_8));
                } catch (JSONException e) {
                    throw new IOException("Invalid response content");
                }
            }
        }
        // check http error code
        if (response.getStatusLine().getStatusCode() >= 400) {
            String errorMessage = null;
            if (jsonResponse != null && jsonResponse.optJSONObject("error") != null) {
                try {
                    JSONObject jsonError = jsonResponse.getJSONObject("error");
                    errorMessage = jsonError.optString("code") + " " + jsonError.optString("message");
                } catch (JSONException e) {
                    // ignore
                }
            }
            if (errorMessage == null) {
                errorMessage = response.getStatusLine().getReasonPhrase();
            }
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                throw new HttpForbiddenException(errorMessage);
            }
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new HttpNotFoundException(errorMessage);
            }
            throw new IOException(errorMessage);
        }
        return jsonResponse;
    }

    protected byte[] readResponse(HttpResponse response) throws IOException {
        byte[] content;
        try (InputStream inputStream = response.getEntity().getContent()) {
            if (HttpClientAdapter.isGzipEncoded(response)) {
                content = IOUtil.readFully(new GZIPInputStream(inputStream));
            } else {
                content = IOUtil.readFully(inputStream);
            }
        }
        return content;
    }
}
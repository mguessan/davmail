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

package davmail.http.request;

import davmail.http.HttpClientAdapter;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;
import java.net.URI;

/**
 * Http get request with a string response handler.
 */
public class GetRequest extends HttpGet implements ResponseHandler<String>, ResponseWrapper {
    private HttpResponse response;
    private String responseBodyAsString;

    public GetRequest(final URI uri) {
        super(uri);
    }

    /**
     * @throws IllegalArgumentException if the uri is invalid.
     */
    public GetRequest(final String uri) {
        super(uri);
    }


    /**
     * Handle request response and return response as string.
     * response body is null on redirect
     *
     * @param response response object
     * @return response body as string
     * @throws IOException on error
     */
    @Override
    public String handleResponse(HttpResponse response) throws IOException {
        this.response = response;
        if (HttpClientAdapter.isRedirect(response)) {
            return null;
        } else {
            responseBodyAsString = new BasicResponseHandler().handleResponse(response);
            return responseBodyAsString;
        }
    }

    public String getResponseBodyAsString() throws IOException {
        checkResponse();
        if (responseBodyAsString == null) {
            throw new IOException("No response body available");
        }
        return responseBodyAsString;
    }


    public Header getResponseHeader(String name) {
        checkResponse();
        return response.getFirstHeader(name);
    }

    /**
     * Get status code from response.
     * @return Http status code
     */
    public int getStatusCode() {
        checkResponse();
        return response.getStatusLine().getStatusCode();
    }

    /**
     * Get reason phrase from response.
     * @return reason phrase
     */
    public String getReasonPhrase() {
        checkResponse();
        return response.getStatusLine().getReasonPhrase();
    }

    public URI getRedirectLocation() {
        checkResponse();
        return HttpClientAdapter.getRedirectLocation(response);
    }

    public HttpResponse getHttpResponse() {
        return response;
    }

    /**
     * Check if response is available.
     */
    private void checkResponse() {
        if (response == null) {
            throw new IllegalStateException("Should execute request first");
        }
    }

}

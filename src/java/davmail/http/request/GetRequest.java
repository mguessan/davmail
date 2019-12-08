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
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Http get request to handle response transparently.
 */
public class GetRequest extends HttpGet implements ResponseHandler<String> {
    protected  HttpResponse response;
    protected String responseBodyAsString;

    public GetRequest(final URI uri) {
        super(uri);
    }

    public GetRequest(final String url) {
        super(URI.create(url));
    }

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

    public HttpResponse getResponse() {
        return response;
    }

    public String getResponseBodyAsString() {
        return responseBodyAsString;
    }

    public String getResponsePart(String pattern) throws IOException {
        if (responseBodyAsString == null) {
            throw new IOException("No response body");
        }
        String value;
        Matcher matcher = Pattern.compile(pattern).matcher(responseBodyAsString);
        if (matcher.find()) {
            value = matcher.group(1);
        } else {
            throw new IOException("pattern " + pattern + " not found in response body");
        }
        return value;
    }

    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }

    public String getPath() {
        return getURI().getPath();
    }
}

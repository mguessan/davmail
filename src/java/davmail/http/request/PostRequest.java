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
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

/**
 * Http post request with a string response handler.
 */
public class PostRequest extends HttpPost implements ResponseHandler<String> {
    private ArrayList<NameValuePair> parameters = new ArrayList<>();
    private String responseBodyAsString = null;
    private HttpResponse response;

    public PostRequest(final URI uri) {
        super(uri);
    }

    public PostRequest(final String url) {
        super(URI.create(url));
    }

    public void setRequestHeader(String name, String value) {
        setHeader(name, value);
    }

    @Override
    public HttpEntity getEntity() {
        return new UrlEncodedFormEntity(parameters, Consts.UTF_8);
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

    public void setParameter(final String name, final String value) {
        parameters.add(new BasicNameValuePair(name, value));
    }

    public void removeParameter(final String name) {
        ArrayList<NameValuePair> toDelete = new ArrayList<>();
        for (NameValuePair param: parameters) {
            if (param.getName().equals(name)) {
                toDelete.add(param);
            }
        }
        parameters.removeAll(toDelete);
    }


    public String getResponseBodyAsString() throws IOException {
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

    /**
     * Check if response is available.
     */
    private void checkResponse() {
        if (response == null) {
            throw new IllegalStateException("Should execute request first");
        }
    }

}

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

import org.apache.http.Consts;
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

public class PostRequest extends HttpPost implements ResponseHandler {
    ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
    String responseBodyAsString = null;

    public PostRequest(final URI uri) {
        super(uri);
    }

    public PostRequest(final String url) {
        super(URI.create(url));
    }

    @Override
    public HttpEntity getEntity() {
        return new UrlEncodedFormEntity(parameters, Consts.UTF_8);
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        responseBodyAsString = new BasicResponseHandler().handleResponse(response);
        return responseBodyAsString;
    }

    public void setParameter(final String name, final String value) {
        parameters.add(new BasicNameValuePair(name, value));
    }

    public String getResponseBodyAsString() {
        return responseBodyAsString;
    }

}

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

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetRequest extends HttpGet implements ResponseHandler {
    protected String responseBodyAsString;

    public GetRequest(final URI uri) {
        super(uri);
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        responseBodyAsString = new BasicResponseHandler().handleResponse(response);;
        return responseBodyAsString;
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
            throw new IOException("pattern "+pattern+" not found in response body");
        }
        return value;
    }
}

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

package davmail.exchange.ews;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AutoDiscoverMethod extends HttpPost implements ResponseHandler {

    protected static final Logger LOGGER = Logger.getLogger(AutoDiscoverMethod.class);

    public AutoDiscoverMethod(String url, String userEmail) {
        super(url);
        setRequestEntity(userEmail);
    }

    private void setRequestEntity(String userEmail) {
        String body = "<Autodiscover xmlns=\"http://schemas.microsoft.com/exchange/autodiscover/outlook/requestschema/2006\">" +
                "<Request>" +
                "<EMailAddress>" + userEmail + "</EMailAddress>" +
                "<AcceptableResponseSchema>http://schemas.microsoft.com/exchange/autodiscover/outlook/responseschema/2006a</AcceptableResponseSchema>" +
                "</Request>" +
                "</Autodiscover>";
        setEntity(new StringEntity(body, ContentType.create("text/xml", "UTF-8")));
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        String ewsUrl = null;
        try {
            Header contentTypeHeader = response.getFirstHeader("Content-Type");
            if (contentTypeHeader != null &&
                    ("text/xml; charset=utf-8".equals(contentTypeHeader.getValue())
                            || "text/html; charset=utf-8".equals(contentTypeHeader.getValue())
                    )) {
                BufferedReader autodiscoverReader = null;
                try {
                    autodiscoverReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                    String line;
                    // find ews url
                    //noinspection StatementWithEmptyBody
                    while ((line = autodiscoverReader.readLine()) != null
                            && (!line.contains("<EwsUrl>"))
                            && (!line.contains("</EwsUrl>"))) {
                    }
                    if (line != null) {
                        ewsUrl = line.substring(line.indexOf("<EwsUrl>") + 8, line.indexOf("</EwsUrl>"));
                    }
                } catch (IOException e) {
                    LOGGER.debug(e);
                } finally {
                    if (autodiscoverReader != null) {
                        try {
                            autodiscoverReader.close();
                        } catch (IOException e) {
                            LOGGER.debug(e);
                        }
                    }
                }
            }

        } finally {
            ((CloseableHttpResponse) response).close();
        }
        return ewsUrl;
    }
}

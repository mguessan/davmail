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

import davmail.util.StringUtil;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Custom Exchange SEARCH method.
 * Does not load full DOM in memory.
 */
public class ExchangeSearchRequest extends ExchangeDavRequest {
    protected static final Logger LOGGER = Logger.getLogger(ExchangeSearchRequest.class);

    protected final String searchRequest;

    /**
     * Create search method.
     *
     * @param uri           method uri
     * @param searchRequest Exchange search request
     */
    public ExchangeSearchRequest(String uri, String searchRequest) {
        super(uri);
        this.searchRequest = searchRequest;
    }

    protected byte[] generateRequestContent() {
        try {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                writer.write("<?xml version=\"1.0\"?>\n");
                writer.write("<d:searchrequest xmlns:d=\"DAV:\">\n");
                writer.write("        <d:sql>");
                writer.write(StringUtil.xmlEncode(searchRequest));
                writer.write("</d:sql>\n");
                writer.write("</d:searchrequest>");
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getMethod() {
        return "SEARCH";
    }

}
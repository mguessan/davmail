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

package davmail.exchange.auth;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for HttpURLConnection to fix missing content type and add logging.
 */
public class HttpURLConnectionWrapper extends HttpURLConnection {
    private static final Logger LOGGER = Logger.getLogger(HttpURLConnectionWrapper.class);
    HttpURLConnection httpURLConnection;

    HttpURLConnectionWrapper(HttpURLConnection httpURLConnection, URL url) {
        super(url);
        this.httpURLConnection = httpURLConnection;
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        httpURLConnection.setRequestMethod(method);
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        httpURLConnection.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return httpURLConnection.getInstanceFollowRedirects();
    }

    @Override
    public String getRequestMethod() {
        return httpURLConnection.getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
        return httpURLConnection.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return httpURLConnection.getResponseMessage();
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        LOGGER.debug(httpURLConnection.getHeaderFields());
        return httpURLConnection.getHeaderFields();
    }

    @Override
    public String getHeaderField(String name) {
        return httpURLConnection.getHeaderField(name);
    }

    @Override
    public String getHeaderField(int n) {
        return httpURLConnection.getHeaderField(n);
    }

    @Override
    public void disconnect() {
        httpURLConnection.disconnect();
    }

    @Override
    public void setDoOutput(boolean dooutput) {
        httpURLConnection.setDoOutput(dooutput);
    }

    @Override
    public boolean usingProxy() {
        return httpURLConnection.usingProxy();
    }

    @Override
    public void connect() throws IOException {
        try {
            httpURLConnection.connect();
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw e;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return httpURLConnection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return httpURLConnection.getOutputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return httpURLConnection.getErrorStream();
    }

    @Override
    public void setRequestProperty(String key, String value) {
        httpURLConnection.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        httpURLConnection.setRequestProperty(key, value);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return httpURLConnection.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return httpURLConnection.getRequestProperty(key);
    }

    /**
     * Fix missing content type
     * @return content type or text/html if mising
     */
    @Override
    public String getContentType() {
        final String contentType = httpURLConnection.getContentType();
        // workaround for missing content type
        if (contentType == null && getContentLength() > 0) {
            LOGGER.debug("Fix missing content-type at "+url.toString());
            return "text/html";
        }
        return contentType;
    }
}

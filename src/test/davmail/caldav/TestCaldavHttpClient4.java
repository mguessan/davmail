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

package davmail.caldav;

import davmail.AbstractDavMailTestCase;
import davmail.DavGateway;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.http.HttpClientAdapter;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.client.methods.BaseDavRequest;
import org.apache.jackrabbit.webdav.client.methods.HttpPropfind;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;

import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TestCaldavHttpClient4 extends AbstractDavMailTestCase {
    HttpClientAdapter httpClient;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (httpClient == null) {
            // start gateway
            DavGateway.start();
            httpClient = new HttpClientAdapter("http://localhost:" + Settings.getProperty("davmail.caldavPort"), username, password);
        }
        if (session == null) {
            session = ExchangeSessionFactory.getInstance(username, password);
        }
    }

    public void testGetRoot() throws IOException {
        HttpGet method = new HttpGet("/");
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testGetUserRoot() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + '/');
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testGetCalendar() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/calendar/");
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testGetInbox() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/inbox/");
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testGetContacts() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/contacts/");
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testPropfindCalendar() throws IOException {
        HttpPropfind method = new HttpPropfind("/users/" + session.getEmail() + "/calendar/", null, 1);
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testGetOtherUserCalendar() throws IOException {
        HttpPropfind method = new HttpPropfind("/principals/users/" + Settings.getProperty("davmail.usera"),
                DavConstants.PROPFIND_ALL_PROP, new DavPropertyNameSet(), DavConstants.DEPTH_INFINITY);
        CloseableHttpResponse response = httpClient.execute(method);
        try {
            assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatusLine().getStatusCode());
        } finally {
            response.close();
        }
    }

    public void testReportCalendar() throws IOException, DavException {
        SimpleDateFormat formatter = ExchangeSession.getZuluDateFormat();
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.MONTH, -1);
        Date start = cal.getTime();

        BaseDavRequest method = new BaseDavRequest(URI.create("/users/" + session.getEmail() + "/calendar/")) {
            @Override
            public String getMethod() {
                return DavMethods.METHOD_REPORT;
            }
        };
        String buffer = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<C:calendar-query xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:D=\"DAV:\">" +
                "<D:prop>" +
                "<C:calendar-data/>" +
                "</D:prop>" +
                "<C:comp-filter name=\"VCALENDAR\">" +
                "<C:comp-filter name=\"VEVENT\">" +
                "<C:time-range start=\"" + formatter.format(start) + "\" end=\"" + formatter.format(end) + "\"/>" +
                //"<C:time-range start=\"" + formatter.format(start) + "\"/>" +
                "</C:comp-filter>" +
                "</C:comp-filter>" +
                "<C:filter>" +
                "</C:filter>" +
                "</C:calendar-query>";
        method.setEntity(new StringEntity(buffer, ContentType.create("text/xml", "UTF-8")));

        MultiStatus multiStatus = httpClient.executeDavRequest(method);
        MultiStatusResponse[] responses = multiStatus.getResponses();
        List<ExchangeSession.Event> events = session.searchEvents("/users/" + session.getEmail() + "/calendar/",
                ExchangeSession.getZuluDateFormat().format(start),
                ExchangeSession.getZuluDateFormat().format(end)
        );

        assertEquals(events.size(), responses.length);
    }

}

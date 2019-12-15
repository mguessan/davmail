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
import davmail.util.StringUtil;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.BaseDavRequest;
import org.apache.jackrabbit.webdav.client.methods.HttpPropfind;
import org.apache.jackrabbit.webdav.client.methods.HttpProppatch;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    public void testGetUserRoot() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + '/');
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    public void testGetCalendar() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/calendar/");
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    public void testGetInbox() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/inbox/");
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    public void testGetContacts() throws IOException {
        HttpGet method = new HttpGet("/users/" + session.getEmail() + "/contacts/");
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    public void testPropfindCalendar() throws IOException {
        HttpPropfind method = new HttpPropfind("/users/" + session.getEmail() + "/calendar/", null, 1);
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatusLine().getStatusCode());
        }
    }

    public void testGetOtherUserCalendar() throws IOException {
        HttpPropfind method = new HttpPropfind("/principals/users/" + Settings.getProperty("davmail.usera"),
                DavConstants.PROPFIND_ALL_PROP, new DavPropertyNameSet(), DavConstants.DEPTH_INFINITY);
        try (CloseableHttpResponse response = httpClient.execute(method)) {
            assertEquals(HttpStatus.SC_MULTI_STATUS, response.getStatusLine().getStatusCode());
        }
    }

    public void testReportCalendar() throws IOException {
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

    public void testReportInbox() throws IOException {
        String buffer = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<C:calendar-query xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:D=\"DAV:\">" +
                "<D:prop>" +
                "<C:calendar-data/>" +
                "</D:prop>" +
                "<C:filter>" +
                "</C:filter>" +
                "</C:calendar-query>";
        BaseDavRequest method = new BaseDavRequest(URI.create("/users/" + session.getEmail() + "/inbox/")) {
            @Override
            public String getMethod() {
                return DavMethods.METHOD_REPORT;
            }
        };
        method.setEntity(new StringEntity(buffer, ContentType.create("text/xml", "UTF-8")));
        httpClient.executeDavRequest(method);
    }

    public void testReportTasks() throws IOException {
        String buffer = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<C:calendar-query xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:D=\"DAV:\">" +
                "<D:prop>" +
                "<C:calendar-data/>" +
                "</D:prop>" +
                "<C:comp-filter name=\"VCALENDAR\">" +
                "<C:comp-filter name=\"VTODO\"/>" +
                "</C:comp-filter>" +
                "<C:filter>" +
                "</C:filter>" +
                "</C:calendar-query>";
        BaseDavRequest method = new BaseDavRequest(URI.create("/users/" + session.getEmail() + "/calendar/")) {
            @Override
            public String getMethod() {
                return DavMethods.METHOD_REPORT;
            }
        };
        method.setEntity(new StringEntity(buffer, ContentType.create("text/xml", "UTF-8")));

        httpClient.executeDavRequest(method);
    }

    public void testReportEventsOnly() throws IOException {
        String buffer = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<C:calendar-query xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:D=\"DAV:\">" +
                "<D:prop>" +
                "<C:calendar-data/>" +
                "</D:prop>" +
                "<C:comp-filter name=\"VCALENDAR\">" +
                "<C:comp-filter name=\"VEVENT\"/>" +
                "</C:comp-filter>" +
                "<C:filter>" +
                "</C:filter>" +
                "</C:calendar-query>";
        BaseDavRequest method = new BaseDavRequest(URI.create("/users/" + session.getEmail() + "/calendar/")) {
            @Override
            public String getMethod() {
                return DavMethods.METHOD_REPORT;
            }
        };
        method.setEntity(new StringEntity(buffer, ContentType.create("text/xml", "UTF-8")));

        httpClient.executeDavRequest(method);
    }

    public void testCreateCalendar() throws IOException, URISyntaxException {
        String folderName = "test & accentué";
        //String folderName = "justatest";
        URI uri = new URIBuilder().setPath("/users/" + session.getEmail() + "/calendar/" + folderName + '/').build();
        // first delete calendar
        session.deleteFolder("calendar/" + folderName);
        String body =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <C:mkcalendar xmlns:D=\"DAV:\"\n" +
                        "                 xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                        "     <D:set>\n" +
                        "       <D:prop>\n" +
                        "         <D:displayname>" + StringUtil.xmlEncode(folderName) + "</D:displayname>\n" +
                        "         <C:calendar-description xml:lang=\"en\">Calendar description</C:calendar-description>\n" +
                        "         <C:supported-calendar-component-set>\n" +
                        "           <C:comp name=\"VEVENT\"/>\n" +
                        "         </C:supported-calendar-component-set>\n" +
                        "       </D:prop>\n" +
                        "     </D:set>\n" +
                        "   </C:mkcalendar>";
        BaseDavRequest method = new BaseDavRequest(uri) {
            @Override
            public String getMethod() {
                return "MKCALENDAR";
            }

            public boolean succeeded(HttpResponse response) {
                int status = response.getStatusLine().getStatusCode();
                return status == HttpStatus.SC_CREATED;
            }
        };
        method.setEntity(new StringEntity(body, ContentType.create("text/xml", "UTF-8")));

        httpClient.executeDavRequest(method);

        HttpGet getRequest = new HttpGet(uri);
        CloseableHttpResponse getResponse = httpClient.execute(getRequest);
        assertEquals(org.apache.commons.httpclient.HttpStatus.SC_OK, getResponse.getStatusLine().getStatusCode());
    }

    public void testPropfindPrincipal() throws IOException {
        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);

        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
        davPropertyNameSet.add(DavPropertyName.create("calendar-home-set", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("calendar-user-address-set", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("schedule-inbox-URL", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("schedule-outbox-URL", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        HttpPropfind method = new HttpPropfind("/principals/users/" + session.getEmail() + "/", davPropertyNameSet, 0);
        MultiStatus multiStatus = httpClient.executeDavRequest(method);
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertEquals(1, responses.length);
    }

    public void testRenameCalendar() throws IOException, URISyntaxException {
        String folderName = "testcalendarfolder";
        String renamedFolderName = "renamedcalendarfolder";
        URI uri = new URIBuilder().setPath("/users/" + session.getEmail() + "/calendar/" + folderName + '/').build();
        // first delete calendar
        session.deleteFolder("calendar/" + folderName);
        session.deleteFolder("calendar/" + renamedFolderName);

        session.createCalendarFolder("calendar/" + folderName, null);

        DavPropertySet davPropertySet = new DavPropertySet();
        davPropertySet.add(new DefaultDavProperty<>(DavPropertyName.create("displayname", Namespace.getNamespace("DAV:")), renamedFolderName));

        HttpProppatch propPatchMethod = new HttpProppatch(uri, davPropertySet, new DavPropertyNameSet());
        httpClient.executeDavRequest(propPatchMethod);

        ExchangeSession.Folder renamedFolder = session.getFolder("calendar/" + renamedFolderName);
        assertNotNull(renamedFolder);

    }

    public void testWellKnown() throws IOException {
        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
        davPropertyNameSet.add(DavPropertyName.create("current-user-principal", Namespace.getNamespace("DAV:")));
        davPropertyNameSet.add(DavPropertyName.create("principal-URL", Namespace.getNamespace("DAV:")));
        davPropertyNameSet.add(DavPropertyName.create("resourcetype", Namespace.getNamespace("DAV:")));
        HttpPropfind method = new HttpPropfind("/.well-known/caldav", davPropertyNameSet, 0) {
            @Override
            public boolean succeeded(HttpResponse response) {
                return response.getStatusLine().getStatusCode() == DavServletResponse.SC_MOVED_PERMANENTLY;
            }
        };
        httpClient.executeDavRequest(method);
    }

    public void testPrincipalUrl() throws IOException {
        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
        davPropertyNameSet.add(DavPropertyName.create("principal-URL", Namespace.getNamespace("DAV:")));
        HttpPropfind method = new HttpPropfind("/principals/users/" + session.getEmail(), davPropertyNameSet, 0);
        httpClient.executeDavRequest(method);
    }

    public void testPropfindRoot() throws IOException {
        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
        davPropertyNameSet.add(DavPropertyName.create("current-user-principal", Namespace.getNamespace("DAV:")));
        davPropertyNameSet.add(DavPropertyName.create("principal-URL", Namespace.getNamespace("DAV:")));
        davPropertyNameSet.add(DavPropertyName.create("resourcetype", Namespace.getNamespace("DAV:")));
        HttpPropfind method = new HttpPropfind("/", davPropertyNameSet, 0);
        httpClient.executeDavRequest(method);
    }

    public void testPropfindPublicPrincipal() throws IOException {
        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
        davPropertyNameSet.add(DavPropertyName.create("calendar-home-set", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("calendar-user-address-set", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("schedule-inbox-URL", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        davPropertyNameSet.add(DavPropertyName.create("schedule-outbox-URL", Namespace.getNamespace("urn:ietf:params:xml:ns:caldav")));
        HttpPropfind method = new HttpPropfind("/principals/public/testcalendar/", davPropertyNameSet, 0);
        MultiStatus multiStatus = httpClient.executeDavRequest(method);
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertEquals(1, responses.length);
    }

    public void testInvalidDavRequest() {
        BaseDavRequest method = new BaseDavRequest(URI.create("/users/" + session.getEmail() + "/calendar/")) {
            @Override
            public String getMethod() {
                return DavMethods.METHOD_REPORT;
            }
        };
        method.setEntity(new StringEntity("invalid", ContentType.create("text/xml", "UTF-8")));

        try {
            httpClient.executeDavRequest(method);
            fail("Should fail");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
            assertEquals(503, ((DavException)e.getCause()).getErrorCode());

        }

    }

}

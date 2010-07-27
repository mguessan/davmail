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
import davmail.util.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Test Caldav listener.
 */
public class TestCaldav extends AbstractDavMailTestCase {

    class SearchReportMethod extends DavMethodBase {
        SearchReportMethod(String path, String stringContent) throws UnsupportedEncodingException {
            this(path, stringContent.getBytes("UTF-8"));
        }

        SearchReportMethod(String path, final byte[] content) {
            super(path);
            setRequestEntity(new RequestEntity() {

                public boolean isRepeatable() {
                    return true;
                }

                public void writeRequest(OutputStream outputStream) throws IOException {
                    outputStream.write(content);
                }

                public long getContentLength() {
                    return content.length;
                }

                public String getContentType() {
                    return "text/xml;charset=UTF-8";
                }
            });
        }

        @Override
        public String getName() {
            return "REPORT";
        }

        @Override
        protected boolean isSuccess(int statusCode) {
            return statusCode == HttpStatus.SC_MULTI_STATUS;
        }
    }

    HttpClient httpClient;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (httpClient == null) {
            // start gateway
            DavGateway.start();
            httpClient = new HttpClient();
            HostConfiguration hostConfig = httpClient.getHostConfiguration();
            URI httpURI = new URI("http://localhost:" + Settings.getProperty("davmail.caldavPort"), true);
            hostConfig.setHost(httpURI);
            AuthScope authScope = new AuthScope(null, -1);
            httpClient.getState().setCredentials(authScope, new NTCredentials(Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"), "", ""));
        }
        if (session == null) {
            session = ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
        }
    }

    public void testGetRoot() throws IOException {
        GetMethod method = new GetMethod("/");
        httpClient.executeMethod(method);
        assertEquals(HttpStatus.SC_OK, method.getStatusCode());
    }

    public void testGetUserRoot() throws IOException {
        GetMethod method = new GetMethod("/users/" + session.getEmail() + '/');
        httpClient.executeMethod(method);
        assertEquals(HttpStatus.SC_OK, method.getStatusCode());
    }

    public void testGetCalendar() throws IOException {
        GetMethod method = new GetMethod("/users/" + session.getEmail() + "/calendar/");
        httpClient.executeMethod(method);
        assertEquals(HttpStatus.SC_OK, method.getStatusCode());
    }

    public void testReportCalendar() throws IOException, DavException {
        SimpleDateFormat formatter = ExchangeSession.getZuluDateFormat();
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.MONTH, -1);
        Date start = cal.getTime();

        StringBuilder buffer = new StringBuilder();
        buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        buffer.append("<C:calendar-query xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:D=\"DAV:\">");
        buffer.append("<D:prop>");
        buffer.append("<C:calendar-data/>");
        buffer.append("</D:prop>");
        buffer.append("<C:comp-filter name=\"VCALENDAR\">");
        buffer.append("<C:comp-filter name=\"VEVENT\">");
        buffer.append("<C:time-range start=\"").append(formatter.format(start)).append("\" end=\"").append(formatter.format(end)).append("\"/>");
        buffer.append("</C:comp-filter>");
        buffer.append("</C:comp-filter>");
        buffer.append("<C:filter>");
        buffer.append("</C:filter>");
        buffer.append("</C:calendar-query>");
        SearchReportMethod method = new SearchReportMethod("/users/" + session.getEmail() + "/calendar/",buffer.toString());
        httpClient.executeMethod(method);
        assertEquals(HttpStatus.SC_MULTI_STATUS, method.getStatusCode());
        MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses = multiStatus.getResponses();

        Set<String> ITEM_PROPERTIES = new HashSet<String>();
        ITEM_PROPERTIES.add("instancetype");
        List<ExchangeSession.Event> events = session.searchEvents("/users/" + session.getEmail() + "/calendar/", ITEM_PROPERTIES,
                session.and(
                        session.and(
                                session.gt("dtstart", session.formatSearchDate(start)),
                                session.lt("dtend", session.formatSearchDate(end))
                        )
                        , session.or(session.isEqualTo("instancetype", 1), session.isEqualTo("instancetype", 0))
                        )
        );

        assertEquals(events.size(), responses.length);
    }

    public void testCreateCalendar() throws IOException {
        String folderName = "test & accentué";
        String encodedFolderpath = URIUtil.encodePath("/users/" + session.getEmail() + "/calendar/"+folderName+ '/');
        // first delete calendar
        session.deleteFolder("calendar/"+folderName);
        String body =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "   <C:mkcalendar xmlns:D=\"DAV:\"\n" +
                "                 xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                "     <D:set>\n" +
                "       <D:prop>\n" +
                "         <D:displayname>"+ StringUtil.xmlEncode(folderName)+"</D:displayname>\n" +
                "         <C:calendar-description xml:lang=\"en\">Calendar description</C:calendar-description>\n" +
                "         <C:supported-calendar-component-set>\n" +
                "           <C:comp name=\"VEVENT\"/>\n" +
                "         </C:supported-calendar-component-set>\n" +
                "       </D:prop>\n" +
                "     </D:set>\n" +
                "   </C:mkcalendar>";

        SearchReportMethod method = new SearchReportMethod(encodedFolderpath, body) {
            @Override
            public String getName() {
                return "MKCALENDAR";
            }
        };
        httpClient.executeMethod(method);
        assertEquals(HttpStatus.SC_CREATED, method.getStatusCode());

        GetMethod getMethod = new GetMethod(encodedFolderpath);
        httpClient.executeMethod(getMethod);
        assertEquals(HttpStatus.SC_OK, getMethod.getStatusCode());
    }
}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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

import davmail.AbstractConnection;
import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.exchange.ICSBufferedReader;
import davmail.ui.tray.DavGatewayTray;
import davmail.util.StringUtil;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handle a caldav connection.
 */
public class CaldavConnection extends AbstractConnection {
    /**
     * Maximum keep alive time in seconds
     */
    protected static final int MAX_KEEP_ALIVE_TIME = 300;
    protected final Logger wireLogger = Logger.getLogger(this.getClass());

    protected boolean closed;

    /**
     * Initialize the streams and start the thread.
     *
     * @param clientSocket Caldav client socket
     */
    public CaldavConnection(Socket clientSocket) {
        super(CaldavConnection.class.getSimpleName(), clientSocket, "UTF-8");
        // set caldav logging to davmail logging level
        wireLogger.setLevel(Settings.getLoggingLevel("davmail"));
    }

    protected Map<String, String> parseHeaders() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        String line;
        while ((line = readClient()) != null && line.length() > 0) {
            int index = line.indexOf(':');
            if (index <= 0) {
                throw new DavMailException("EXCEPTION_INVALID_HEADER", line);
            }
            headers.put(line.substring(0, index).toLowerCase(), line.substring(index + 1).trim());
        }
        return headers;
    }

    protected String getContent(String contentLength) throws IOException {
        if (contentLength == null || contentLength.length() == 0) {
            return null;
        } else {
            int size;
            try {
                size = Integer.parseInt(contentLength);
            } catch (NumberFormatException e) {
                throw new DavMailException("EXCEPTION_INVALID_CONTENT_LENGTH", contentLength);
            }
            char[] buffer = new char[size];
            StringBuilder builder = new StringBuilder();
            int actualSize = in.read(buffer);
            builder.append(buffer, 0, actualSize);
            if (actualSize < 0) {
                throw new DavMailException("EXCEPTION_END_OF_STREAM");
            }
            // dirty hack to ensure full content read
            // TODO : replace with a dedicated reader
            while (builder.toString().getBytes("UTF-8").length < size) {
                actualSize = in.read(buffer);
                builder.append(buffer, 0, actualSize);
            }

            String content = builder.toString();
            if (wireLogger.isDebugEnabled()) {
                wireLogger.debug("< " + content);
            }
            return content;
        }
    }

    protected void setSocketTimeout(String keepAliveValue) throws IOException {
        if (keepAliveValue != null && keepAliveValue.length() > 0) {
            int keepAlive;
            try {
                keepAlive = Integer.parseInt(keepAliveValue);
            } catch (NumberFormatException e) {
                throw new DavMailException("EXCEPTION_INVALID_KEEPALIVE", keepAliveValue);
            }
            if (keepAlive > MAX_KEEP_ALIVE_TIME) {
                keepAlive = MAX_KEEP_ALIVE_TIME;
            }
            client.setSoTimeout(keepAlive * 1000);
            DavGatewayTray.debug(new BundleMessage("LOG_SET_SOCKET_TIMEOUT", keepAlive));
        }
    }

    @Override
    public void run() {
        String line;
        StringTokenizer tokens;

        try {
            while (!closed) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }
                tokens = new StringTokenizer(line);
                String command = tokens.nextToken();
                Map<String, String> headers = parseHeaders();
                String encodedPath = encodePlusSign(tokens.nextToken());
                String path = URIUtil.decode(encodedPath);
                String content = getContent(headers.get("content-length"));
                setSocketTimeout(headers.get("keep-alive"));
                // client requested connection close
                closed = "close".equals(headers.get("connection"));
                if ("OPTIONS".equals(command)) {
                    sendOptions();
                } else if (!headers.containsKey("authorization")) {
                    sendUnauthorized();
                } else {
                    decodeCredentials(headers.get("authorization"));
                    // need to check session on each request, credentials may have changed or session expired
                    try {
                        session = ExchangeSessionFactory.getInstance(userName, password);
                        handleRequest(command, path, headers, content);
                    } catch (DavMailAuthenticationException e) {
                        sendUnauthorized();
                    }
                }

                os.flush();
                DavGatewayTray.resetIcon();
            }
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_CLOSE_CONNECTION_ON_TIMEOUT"));
        } catch (SocketException e) {
            DavGatewayTray.debug(new BundleMessage("LOG_CONNECTION_CLOSED"));
        } catch (Exception e) {
            DavGatewayTray.log(e);
            try {
                sendErr(e);
            } catch (IOException e2) {
                DavGatewayTray.debug(new BundleMessage("LOG_EXCEPTION_SENDING_ERROR_TO_CLIENT"), e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    /**
     * Handle caldav request.
     *
     * @param command Http command
     * @param path    request path
     * @param headers Http headers map
     * @param body    request body
     * @throws IOException on error
     */
    public void handleRequest(String command, String path, Map<String, String> headers, String body) throws IOException {
        CaldavRequest request = new CaldavRequest(command, path, headers, body);
        if (request.isOptions()) {
            sendOptions();
        } else if (request.isPropFind() && request.isRoot()) {
            sendRoot(request);
        } else if (request.isGet() && request.isRoot()) {
            sendGetRoot();
        } else if (request.isPath(1, "principals")) {
            handlePrincipals(request);
        } else if (request.isPath(1, "users")) {
            if (request.isPropFind() && request.isPathLength(3)) {
                sendUserRoot(request);
            } else {
                handleFolder(request);
            }
        } else if (request.isPath(1, "public")) {
            handleFolder(request);
        } else if (request.isPath(1, "directory")) {
            sendDirectory(request);
        } else {
            sendUnsupported(request);
        }
    }

    protected void handlePrincipals(CaldavRequest request) throws IOException {
        if (request.isPath(2, "users")) {
            if (request.isPropFind() && request.isPathLength(4)) {
                sendPrincipal(request, "users", request.getPathElement(3));
                // send back principal on search
            } else if (request.isReport() && request.isPathLength(3)) {
                sendPrincipal(request, "users", session.getEmail());
                // iCal current-user-principal request
            } else if (request.isPropFind() && request.isPathLength(3)) {
                sendPrincipalsFolder(request);
            } else {
                sendUnsupported(request);
            }
        } else if (request.isPath(2, "public")) {
            StringBuilder prefixBuffer = new StringBuilder("public");
            for (int i = 3; i < request.getPathLength() - 1; i++) {
                prefixBuffer.append('/').append(request.getPathElement(i));
            }
            sendPrincipal(request, prefixBuffer.toString(), request.getLastPath());
        } else {
            sendUnsupported(request);
        }
    }

    protected void handleFolder(CaldavRequest request) throws IOException {
        String lastPath = StringUtil.xmlDecode(request.getLastPath());
        // folder requests
        if (request.isPropFind() && "inbox".equals(lastPath)) {
            sendInbox(request);
        } else if (request.isPropFind() && "outbox".equals(lastPath)) {
            sendOutbox(request);
        } else if (request.isPost() && "outbox".equals(lastPath)) {
            if (request.isFreeBusy()) {
                sendFreeBusy(request.getBody());
            } else {
                int status = session.sendEvent(request.getBody());
                // TODO: implement Itip response body
                sendHttpResponse(status);
            }
        } else if (request.isPropFind()) {
            sendFolder(request);
        } else if (request.isPropPatch()) {
            patchCalendar(request);
        } else if (request.isReport()) {
            reportEvents(request);
            // event requests
        } else if (request.isPut()) {
            String etag = request.getHeader("if-match");
            String noneMatch = request.getHeader("if-none-match");
            ExchangeSession.ItemResult itemResult = session.createOrUpdateItem(request.getFolderPath(), lastPath, request.getBody(), etag, noneMatch);
            sendHttpResponse(itemResult.status, buildEtagHeader(itemResult.etag), null, "", true);

        } else if (request.isDelete()) {
            int status;
            if (request.getFolderPath().endsWith("inbox")) {
                status = session.processItem(request.getFolderPath(), lastPath);
            } else {
                status = session.deleteItem(request.getFolderPath(), lastPath);
            }
            sendHttpResponse(status);
        } else if (request.isGet()) {
            if (request.path.endsWith("/")) {
                // GET request on a folder => build ics content of all folder events
                String folderPath = request.getFolderPath();
                ExchangeSession.Folder folder = session.getFolder(folderPath);
                if (folder.isContact()) {
                    sendHttpResponse(HttpStatus.SC_OK, buildEtagHeader(folder.etag), "text/vcard", (byte[]) null, true);
                } else {
                    List<ExchangeSession.Event> events = session.getAllEvents(folderPath);
                    ChunkedResponse response = new ChunkedResponse(HttpStatus.SC_OK, "text/calendar;charset=UTF-8");
                    response.append("BEGIN:VCALENDAR\r\n");
                    response.append("VERSION:2.0\r\n");
                    response.append("PRODID:-//davmail.sf.net/NONSGML DavMail Calendar V1.1//EN\r\n");
                    response.append("METHOD:PUBLISH\r\n");

                    for (ExchangeSession.Event event : events) {
                        String icsContent = StringUtil.getToken(event.getBody(), "BEGIN:VTIMEZONE", "END:VCALENDAR");
                        if (icsContent != null) {
                            response.append("BEGIN:VTIMEZONE");
                            response.append(icsContent);
                        } else {
                            icsContent = StringUtil.getToken(event.getBody(), "BEGIN:VEVENT", "END:VCALENDAR");
                            if (icsContent != null) {
                                response.append("BEGIN:VEVENT");
                                response.append(icsContent);
                            }
                        }
                    }
                    response.append("END:VCALENDAR");
                    response.close();
                }
            } else {
                ExchangeSession.Item item = session.getItem(request.getFolderPath(), lastPath);
                sendHttpResponse(HttpStatus.SC_OK, buildEtagHeader(item.getEtag()), item.getContentType(), item.getBody(), true);
            }
        } else if (request.isHead()) {
            // test event
            ExchangeSession.Item item = session.getItem(request.getFolderPath(), lastPath);
            sendHttpResponse(HttpStatus.SC_OK, buildEtagHeader(item.getEtag()), item.getContentType(), (byte[]) null, true);
        } else {
            sendUnsupported(request);
        }

    }

    protected HashMap<String, String> buildEtagHeader(String etag) {
        if (etag != null) {
            HashMap<String, String> etagHeader = new HashMap<String, String>();
            etagHeader.put("ETag", etag);
            return etagHeader;
        } else {
            return null;
        }
    }

    private void appendContactsResponses(CaldavResponse response, CaldavRequest request, List<ExchangeSession.Contact> contacts) throws IOException {
        int size = contacts.size();
        int count = 0;
        for (ExchangeSession.Contact contact : contacts) {
            DavGatewayTray.debug(new BundleMessage("LOG_LISTING_CONTACT", ++count, size));
            DavGatewayTray.switchIcon();
            appendItemResponse(response, request, contact);
        }
    }

    protected void appendEventsResponses(CaldavResponse response, CaldavRequest request, List<ExchangeSession.Event> events) throws IOException {
        int size = events.size();
        int count = 0;
        for (ExchangeSession.Event event : events) {
            DavGatewayTray.debug(new BundleMessage("LOG_LISTING_EVENT", ++count, size));
            DavGatewayTray.switchIcon();
            appendItemResponse(response, request, event);
        }
    }

    protected void appendItemResponse(CaldavResponse response, CaldavRequest request, ExchangeSession.Item item) throws IOException {
        StringBuilder eventPath = new StringBuilder();
        eventPath.append(URIUtil.encodePath(request.getPath()));
        if (!(eventPath.charAt(eventPath.length() - 1) == '/')) {
            eventPath.append('/');
        }
        String itemName = StringUtil.xmlEncode(item.getName());
        eventPath.append(URIUtil.encodeWithinQuery(itemName));
        response.startResponse(eventPath.toString());
        response.startPropstat();
        if (request.hasProperty("calendar-data") && item instanceof ExchangeSession.Event) {
            response.appendCalendarData(item.getBody());
        }
        if (request.hasProperty("address-data") && item instanceof ExchangeSession.Contact) {
            response.appendContactData(item.getBody());
        }
        if (request.hasProperty("getcontenttype")) {
            if (item instanceof ExchangeSession.Event) {
                response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
            } else if (item instanceof ExchangeSession.Contact) {
                response.appendProperty("D:getcontenttype", "text/vcard");
            }
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", item.getEtag());
        }
        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", itemName);
        }
        response.endPropStatOK();
        response.endResponse();
    }

    /**
     * Append folder object to Caldav response.
     *
     * @param response  Caldav response
     * @param request   Caldav request
     * @param subFolder calendar folder path relative to request path
     * @return Exchange folder object
     * @throws IOException on error
     */
    public ExchangeSession.Folder appendFolder(CaldavResponse response, CaldavRequest request, String subFolder) throws IOException {
        ExchangeSession.Folder folder = session.getFolder(request.getFolderPath(subFolder));

        response.startResponse(URIUtil.encodePath(request.getPath(subFolder)));
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            if (folder.isContact()) {
                response.appendProperty("D:resourcetype", "<D:collection/>" +
                        "<E:addressbook/>");
            } else if (folder.isCalendar()) {
                response.appendProperty("D:resourcetype", "<D:collection/>" + "<C:calendar/>");
            } else {
                response.appendProperty("D:resourcetype", "<D:collection/>");
            }

        }
        if (request.hasProperty("owner")) {
            if ("users".equals(request.getPathElement(1))) {
                response.appendHrefProperty("D:owner", "/principals/users/" + request.getPathElement(2));
            } else {
                response.appendHrefProperty("D:owner", "/principals" + request.getPath());
            }
        }
        if (request.hasProperty("getcontenttype")) {
            if (folder.isContact()) {
                response.appendProperty("D:getcontenttype", "text/x-vcard");
            } else if (folder.isCalendar()) {
                response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
            }
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", folder.etag);
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    base64Encode(folder.ctag));
        }
        if (request.hasProperty("displayname")) {
            if (subFolder == null || subFolder.length() == 0) {
                response.appendProperty("D:displayname", request.getLastPath());
            } else {
                response.appendProperty("D:displayname", subFolder);
            }
        }
        if (request.hasProperty("calendar-description")) {
            response.appendProperty("C:calendar-description", "");
        }
        if (request.hasProperty("supported-calendar-component-set") && folder.isCalendar()) {
            response.appendProperty("C:supported-calendar-component-set", "<C:comp name=\"VEVENT\"/><C:comp name=\"VTODO\"/>");
        }

        if (request.hasProperty("current-user-privilege-set")) {
            response.appendProperty("D:current-user-privilege-set", "<D:privilege><D:read/><D:write/></D:privilege>");
        }

        response.endPropStatOK();
        response.endResponse();
        return folder;
    }

    /**
     * Append calendar inbox object to Caldav response.
     *
     * @param response  Caldav response
     * @param request   Caldav request
     * @param subFolder inbox folder path relative to request path
     * @throws IOException on error
     */
    public void appendInbox(CaldavResponse response, CaldavRequest request, String subFolder) throws IOException {
        String ctag = "0";
        String etag = "0";
        String folderPath = request.getFolderPath(subFolder);
        // do not try to access inbox on shared calendar
        if (!session.isSharedFolder(folderPath)) {
            try {
                ExchangeSession.Folder folder = session.getFolder(folderPath);
                ctag = base64Encode(folder.ctag);
                etag = base64Encode(folder.etag);
            } catch (HttpException e) {
                // unauthorized access, probably an inbox on shared calendar
                DavGatewayTray.debug(new BundleMessage("LOG_ACCESS_FORBIDDEN", folderPath, e.getMessage()));
            }
        }
        response.startResponse(URIUtil.encodePath(request.getPath(subFolder)));
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:schedule-inbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("getcontenttype")) {
            response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"", ctag);
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", etag);
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "inbox");
        }
        response.endPropStatOK();
        response.endResponse();
    }

    /**
     * Append calendar outbox object to Caldav response.
     *
     * @param response  Caldav response
     * @param request   Caldav request
     * @param subFolder outbox folder path relative to request path
     * @throws IOException on error
     */
    public void appendOutbox(CaldavResponse response, CaldavRequest request, String subFolder) throws IOException {
        response.startResponse(URIUtil.encodePath(request.getPath(subFolder)));
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:schedule-outbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    "0");
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", "0");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "outbox");
        }
        response.endPropStatOK();
        response.endResponse();
    }

    /**
     * Send simple html response to GET /.
     *
     * @throws IOException on error
     */
    public void sendGetRoot() throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Connected to DavMail<br/>");
        buffer.append("UserName :").append(userName).append("<br/>");
        buffer.append("Email :").append(session.getEmail()).append("<br/>");
        sendHttpResponse(HttpStatus.SC_OK, null, "text/html;charset=UTF-8", buffer.toString(), true);
    }

    /**
     * Send inbox response for request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendInbox(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        appendInbox(response, request, null);
        // do not try to access inbox on shared calendar
        if (!session.isSharedFolder(request.getFolderPath(null)) && request.getDepth() == 1 && !Settings.getBooleanProperty("davmail.caldavDisableInbox")) {
            try {
                DavGatewayTray.debug(new BundleMessage("LOG_SEARCHING_CALENDAR_MESSAGES"));
                List<ExchangeSession.Event> events = session.getEventMessages(request.getFolderPath());
                DavGatewayTray.debug(new BundleMessage("LOG_FOUND_CALENDAR_MESSAGES", events.size()));
                appendEventsResponses(response, request, events);
            } catch (HttpException e) {
                // unauthorized access, probably an inbox on shared calendar
                DavGatewayTray.debug(new BundleMessage("LOG_ACCESS_FORBIDDEN", request.getFolderPath(), e.getMessage()));
            }
        }
        response.endMultistatus();
        response.close();
    }

    /**
     * Send outbox response for request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendOutbox(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        appendOutbox(response, request, null);
        response.endMultistatus();
        response.close();
    }

    /**
     * Send calendar response for request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendFolder(CaldavRequest request) throws IOException {
        String folderPath = request.getFolderPath();
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        ExchangeSession.Folder folder = appendFolder(response, request, null);
        if (request.getDepth() == 1) {
            if (folder.isContact()) {
                appendContactsResponses(response, request, session.getAllContacts(folderPath));
            } else {
                DavGatewayTray.debug(new BundleMessage("LOG_SEARCHING_CALENDAR_EVENTS", folderPath));
                List<ExchangeSession.Event> events = session.getAllEvents(folderPath);
                DavGatewayTray.debug(new BundleMessage("LOG_FOUND_CALENDAR_EVENTS", events.size()));
                appendEventsResponses(response, request, events);
                // Send sub folders for multi-calendar support under iCal, except for public folders
                if (!folderPath.startsWith("/public")) {
                    List<ExchangeSession.Folder> folderList = session.getSubCalendarFolders(folderPath, false);
                    for (ExchangeSession.Folder subFolder : folderList) {
                        appendFolder(response, request, subFolder.folderPath.substring(subFolder.folderPath.indexOf('/') + 1));
                    }
                }
            }
        }
        response.endMultistatus();
        response.close();
    }

    /**
     * Fake PROPPATCH response for request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void patchCalendar(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        // just ignore calendar folder proppatch (color not supported in Exchange)
        if (request.hasProperty("calendar-color")) {
            response.startPropstat();
            response.appendProperty("x1:calendar-color", "x1=\"http://apple.com/ns/ical/\"", null);
            response.endPropStatOK();
        }
        response.endMultistatus();
        response.close();
    }

    protected String getEventFileNameFromPath(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return null;
        } else {
            return StringUtil.xmlDecode(path.substring(index + 1));
        }
    }

    /**
     * Report events listed in request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void reportEvents(CaldavRequest request) throws IOException {
        String folderPath = request.getFolderPath();
        List<ExchangeSession.Event> events;
        List<String> notFound = new ArrayList<String>();

        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        if (request.isMultiGet()) {
            int count = 0;
            int total = request.getHrefs().size();
            for (String href : request.getHrefs()) {
                DavGatewayTray.debug(new BundleMessage("LOG_REPORT_EVENT", ++count, total));
                DavGatewayTray.switchIcon();
                String eventName = getEventFileNameFromPath(href);
                try {
                    // ignore cases for Sunbird
                    if (eventName != null && eventName.length() > 0
                            && !"inbox".equals(eventName) && !"calendar".equals(eventName)) {
                        ExchangeSession.Item item;
                        item = session.getItem(folderPath, eventName);
                        appendItemResponse(response, request, item);
                    }
                } catch (HttpException e) {
                    DavGatewayTray.warn(new BundleMessage("LOG_EVENT_NOT_AVAILABLE", eventName, href));
                    notFound.add(href);
                }
            }
        } else if (request.isPath(1, "users") && request.isPath(3, "inbox")) {
            events = session.getEventMessages(request.getFolderPath());
            appendEventsResponses(response, request, events);
        } else {
            // TODO: handle contacts ?
            events = session.getAllEvents(request.getFolderPath());
            appendEventsResponses(response, request, events);
        }

        // send not found events errors
        for (String href : notFound) {
            response.startResponse(URIUtil.encodePath(href));
            response.appendPropstatNotFound();
            response.endResponse();
        }
        response.endMultistatus();
        response.close();
    }

    /**
     * Send principals folder.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendPrincipalsFolder(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse(URIUtil.encodePath(request.getPath()));
        response.startPropstat();

        if (request.hasProperty("current-user-principal")) {
            response.appendHrefProperty("D:current-user-principal", "/principals/users/" + session.getEmail());
        }
        response.endPropStatOK();
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    /**
     * Send user response for request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendUserRoot(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse(URIUtil.encodePath(request.getPath()));
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", request.getLastPath());
        }
        if (request.hasProperty("getctag")) {
            ExchangeSession.Folder rootFolder = session.getFolder("");
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    base64Encode(rootFolder.ctag));
        }
        response.endPropStatOK();
        if (request.getDepth() == 1) {
            appendInbox(response, request, "inbox");
            appendOutbox(response, request, "outbox");
            appendFolder(response, request, "calendar");
            appendFolder(response, request, "contacts");
        }
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    /**
     * Send caldav response for / request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendRoot(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse("/");
        response.startPropstat();

        if (request.hasProperty("principal-collection-set")) {
            response.appendHrefProperty("D:principal-collection-set", "/principals/users/");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "ROOT");
        }
        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>");
        }
        if (request.hasProperty("current-user-principal")) {
            response.appendHrefProperty("D:current-user-principal", "/principals/users/" + session.getEmail());
        }
        response.endPropStatOK();
        response.endResponse();
        if (request.depth == 1) {
            // iPhone workaround: send calendar subfolder
            response.startResponse("/users/" + session.getEmail() + "/calendar");
            response.startPropstat();
            if (request.hasProperty("resourcetype")) {
                response.appendProperty("D:resourcetype", "<D:collection/>" +
                        "<C:calendar xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
            }
            if (request.hasProperty("displayname")) {
                response.appendProperty("D:displayname", session.getEmail());
            }
            if (request.hasProperty("supported-calendar-component-set")) {
                response.appendProperty("C:supported-calendar-component-set", "<C:comp name=\"VEVENT\"/>");
            }
            response.endPropStatOK();
            response.endResponse();

            response.startResponse("/users");
            response.startPropstat();
            if (request.hasProperty("displayname")) {
                response.appendProperty("D:displayname", "users");
            }
            if (request.hasProperty("resourcetype")) {
                response.appendProperty("D:resourcetype", "<D:collection/>");
            }
            response.endPropStatOK();
            response.endResponse();

            response.startResponse("/principals");
            response.startPropstat();
            if (request.hasProperty("displayname")) {
                response.appendProperty("D:displayname", "principals");
            }
            if (request.hasProperty("resourcetype")) {
                response.appendProperty("D:resourcetype", "<D:collection/>");
            }
            response.endPropStatOK();
            response.endResponse();
        }
        response.endMultistatus();
        response.close();
    }

    /**
     * Send caldav response for /directory/ request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendDirectory(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse("/directory/");
        response.startPropstat();
        if (request.hasProperty("current-user-privilege-set")) {
            response.appendProperty("D:current-user-privilege-set", "<D:privilege><D:read/></D:privilege>");
        }
        response.endPropStatOK();
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    /**
     * Send Caldav principal response.
     *
     * @param request   Caldav request
     * @param prefix    principal prefix (users or public)
     * @param principal principal name (email address for users)
     * @throws IOException on error
     */
    public void sendPrincipal(CaldavRequest request, String prefix, String principal) throws IOException {
        // actual principal is email address
        String actualPrincipal = principal;
        if ("users".equals(prefix) && session.getAlias().equalsIgnoreCase(principal)) {
            actualPrincipal = session.getEmail();
        }

        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse(URIUtil.encodePath("/principals/" + prefix + '/' + principal));
        response.startPropstat();

        if (request.hasProperty("calendar-home-set")) {
            if ("users".equals(prefix)) {
                response.appendHrefProperty("C:calendar-home-set", "/users/" + actualPrincipal + "/calendar/");
            } else {
                response.appendHrefProperty("C:calendar-home-set", '/' + prefix + '/' + actualPrincipal);
            }
        }

        if (request.hasProperty("calendar-user-address-set") && "users".equals(prefix)) {
            response.appendHrefProperty("C:calendar-user-address-set", "mailto:" + actualPrincipal);
        }

        if (request.hasProperty("addressbook-home-set") && "users".equals(prefix)) {
            response.appendHrefProperty("E:addressbook-home-set", "/users/" + actualPrincipal + '/');
        }

        if ("users".equals(prefix)) {
            if (request.hasProperty("schedule-inbox-URL")) {
                response.appendHrefProperty("C:schedule-inbox-URL", "/users/" + actualPrincipal + "/inbox/");
            }

            if (request.hasProperty("schedule-outbox-URL")) {
                response.appendHrefProperty("C:schedule-outbox-URL", "/users/" + actualPrincipal + "/outbox/");
            }
        } else {
            // public calendar, send root href as inbox url (always empty) for Lightning
            if (request.isLightning() && request.hasProperty("schedule-inbox-URL")) {
                response.appendHrefProperty("C:schedule-inbox-URL", "/");
            }
            // send user outbox
            if (request.hasProperty("schedule-outbox-URL")) {
                response.appendHrefProperty("C:schedule-outbox-URL", "/users/" + session.getEmail() + "/outbox/");
            }
        }

        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", actualPrincipal);
        }
        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/><D:principal/>");
        }
        response.endPropStatOK();
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    /**
     * Send free busy response for body request.
     *
     * @param body request body
     * @throws IOException on error
     */
    public void sendFreeBusy(String body) throws IOException {
        HashMap<String, String> valueMap = new HashMap<String, String>();
        ArrayList<String> attendees = new ArrayList<String>();
        HashMap<String, String> attendeeKeyMap = new HashMap<String, String>();
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(body));
        String line;
        String key;
        while ((line = reader.readLine()) != null) {
            int index = line.indexOf(':');
            if (index <= 0) {
                throw new DavMailException("EXCEPTION_INVALID_REQUEST", body);
            }
            String fullkey = line.substring(0, index);
            String value = line.substring(index + 1);
            int semicolonIndex = fullkey.indexOf(';');
            if (semicolonIndex > 0) {
                key = fullkey.substring(0, semicolonIndex);
            } else {
                key = fullkey;
            }
            if ("ATTENDEE".equals(key)) {
                attendees.add(value);
                attendeeKeyMap.put(value, fullkey);
            } else {
                valueMap.put(key, value);
            }
        }
        // get freebusy for each attendee
        HashMap<String, ExchangeSession.FreeBusy> freeBusyMap = new HashMap<String, ExchangeSession.FreeBusy>();
        for (String attendee : attendees) {
            ExchangeSession.FreeBusy freeBusy = session.getFreebusy(attendee, valueMap.get("DTSTART"), valueMap.get("DTEND"));
            if (freeBusy != null) {
                freeBusyMap.put(attendee, freeBusy);
            }
        }
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_OK);
        response.startScheduleResponse();

        for (Map.Entry<String, ExchangeSession.FreeBusy> entry : freeBusyMap.entrySet()) {
            String attendee = entry.getKey();
            response.startRecipientResponse(attendee);

            StringBuilder ics = new StringBuilder();
            ics.append("BEGIN:VCALENDAR").append((char) 13).append((char) 10)
                    .append("VERSION:2.0").append((char) 13).append((char) 10)
                    .append("PRODID:-//davmail.sf.net/NONSGML DavMail Calendar V1.1//EN").append((char) 13).append((char) 10)
                    .append("METHOD:REPLY").append((char) 13).append((char) 10)
                    .append("BEGIN:VFREEBUSY").append((char) 13).append((char) 10)
                    .append("DTSTAMP:").append(valueMap.get("DTSTAMP")).append("").append((char) 13).append((char) 10)
                    .append("ORGANIZER:").append(valueMap.get("ORGANIZER")).append("").append((char) 13).append((char) 10)
                    .append("DTSTART:").append(valueMap.get("DTSTART")).append("").append((char) 13).append((char) 10)
                    .append("DTEND:").append(valueMap.get("DTEND")).append("").append((char) 13).append((char) 10)
                    .append("UID:").append(valueMap.get("UID")).append("").append((char) 13).append((char) 10)
                    .append(attendeeKeyMap.get(attendee)).append(':').append(attendee).append("").append((char) 13).append((char) 10);
            entry.getValue().appendTo(ics);
            ics.append("END:VFREEBUSY").append((char) 13).append((char) 10)
                    .append("END:VCALENDAR");
            response.appendCalendarData(ics.toString());
            response.endRecipientResponse();

        }
        response.endScheduleResponse();
        response.close();

    }


    /**
     * Send Http error response for exception
     *
     * @param e exception
     * @throws IOException on error
     */
    public void sendErr(Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendErr(HttpStatus.SC_SERVICE_UNAVAILABLE, message);
    }

    /**
     * Send 400 bad response for unsupported request.
     *
     * @param request Caldav request
     * @throws IOException on error
     */
    public void sendUnsupported(CaldavRequest request) throws IOException {
        BundleMessage message = new BundleMessage("LOG_UNSUPPORTED_REQUEST", request);
        DavGatewayTray.error(message);
        sendErr(HttpStatus.SC_BAD_REQUEST, message.format());
    }

    /**
     * Send Http error status and message.
     *
     * @param status  Http status
     * @param message error messagee
     * @throws IOException on error
     */
    public void sendErr(int status, String message) throws IOException {
        sendHttpResponse(status, null, "text/plain;charset=UTF-8", message, false);
    }

    /**
     * Send OPTIONS response.
     *
     * @throws IOException on error
     */
    public void sendOptions() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Allow", "OPTIONS, PROPFIND, HEAD, GET, REPORT, PROPPATCH, PUT, DELETE, POST");
        sendHttpResponse(HttpStatus.SC_OK, headers);
    }

    /**
     * Send 401 Unauthorized response.
     *
     * @throws IOException on error
     */
    public void sendUnauthorized() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("WWW-Authenticate", "Basic realm=\"" + BundleMessage.format("UI_DAVMAIL_GATEWAY") + '\"');
        sendHttpResponse(HttpStatus.SC_UNAUTHORIZED, headers, null, (byte[]) null, true);
    }

    /**
     * Send Http response with given status.
     *
     * @param status Http status
     * @throws IOException on error
     */
    public void sendHttpResponse(int status) throws IOException {
        sendHttpResponse(status, null, null, (byte[]) null, true);
    }

    /**
     * Send Http response with given status and headers.
     *
     * @param status  Http status
     * @param headers Http headers
     * @throws IOException on error
     */
    public void sendHttpResponse(int status, Map<String, String> headers) throws IOException {
        sendHttpResponse(status, headers, null, (byte[]) null, true);
    }

    /**
     * Send Http response with given status in chunked mode.
     *
     * @param status      Http status
     * @param contentType MIME content type
     * @throws IOException on error
     */
    public void sendChunkedHttpResponse(int status, String contentType) throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Transfer-Encoding", "chunked");
        sendHttpResponse(status, headers, contentType, (byte[]) null, true);
    }

    /**
     * Send Http response with given status, headers, content type and content.
     * Close connection if keepAlive is false
     *
     * @param status      Http status
     * @param headers     Http headers
     * @param contentType MIME content type
     * @param content     response body as string
     * @param keepAlive   keep connection open
     * @throws IOException on error
     */
    public void sendHttpResponse(int status, Map<String, String> headers, String contentType, String content, boolean keepAlive) throws IOException {
        sendHttpResponse(status, headers, contentType, content.getBytes("UTF-8"), keepAlive);
    }

    /**
     * Send Http response with given status, headers, content type and content.
     * Close connection if keepAlive is false
     *
     * @param status      Http status
     * @param headers     Http headers
     * @param contentType MIME content type
     * @param content     response body as byte array
     * @param keepAlive   keep connection open
     * @throws IOException on error
     */
    public void sendHttpResponse(int status, Map<String, String> headers, String contentType, byte[] content, boolean keepAlive) throws IOException {
        sendClient("HTTP/1.1 " + status + ' ' + HttpStatus.getStatusText(status));
        if (status != HttpStatus.SC_UNAUTHORIZED) {
            String version = DavGateway.getCurrentVersion();
            sendClient("Server: DavMail Gateway " + (version == null ? "" : version));
            sendClient("DAV: 1, calendar-access, calendar-schedule, calendarserver-private-events, addressbook");
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            // force GMT timezone
            formatter.setTimeZone(ExchangeSession.GMT_TIMEZONE);
            String now = formatter.format(new Date());
            sendClient("Date: " + now);
            sendClient("Expires: " + now);
            sendClient("Cache-Control: private, max-age=0");
        }
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                sendClient(header.getKey() + ": " + header.getValue());
            }
        }
        if (contentType != null) {
            sendClient("Content-Type: " + contentType);
        }
        closed = closed || !keepAlive;
        sendClient("Connection: " + (closed ? "close" : "keep-alive"));
        if (content != null && content.length > 0) {
            sendClient("Content-Length: " + content.length);
        } else if (headers == null || !"chunked".equals(headers.get("Transfer-Encoding"))) {
            sendClient("Content-Length: 0");
        }
        sendClient("");
        if (content != null && content.length > 0) {
            // full debug trace
            if (wireLogger.isDebugEnabled()) {
                wireLogger.debug("> " + new String(content, "UTF-8"));
            }
            sendClient(content);
        }
    }

    /**
     * Decode HTTP credentials
     *
     * @param authorization http authorization header value
     * @throws IOException if invalid credentials
     */
    protected void decodeCredentials(String authorization) throws IOException {
        int index = authorization.indexOf(' ');
        if (index > 0) {
            String mode = authorization.substring(0, index).toLowerCase();
            if (!"basic".equals(mode)) {
                throw new DavMailException("EXCEPTION_UNSUPPORTED_AUTHORIZATION_MODE", mode);
            }
            String encodedCredentials = authorization.substring(index + 1);
            String decodedCredentials = base64Decode(encodedCredentials);
            index = decodedCredentials.indexOf(':');
            if (index > 0) {
                userName = decodedCredentials.substring(0, index);
                password = decodedCredentials.substring(index + 1);
            } else {
                throw new DavMailException("EXCEPTION_INVALID_CREDENTIALS");
            }
        } else {
            throw new DavMailException("EXCEPTION_INVALID_CREDENTIALS");
        }

    }

    /**
     * Make sure + sign in URL is encoded.
     *
     * @param path URL path
     * @return reencoded path
     */
    protected String encodePlusSign(String path) {
        // make sure + sign in URL is encoded
        if (path.indexOf('+') >= 0) {
            return path.replaceAll("\\+", "%2B");
        } else {
            return path;
        }
    }

    protected class CaldavRequest {
        protected final String command;
        protected final String path;
        protected final String[] pathElements;
        protected final Map<String, String> headers;
        protected int depth;
        protected final String body;
        protected final HashSet<String> properties = new HashSet<String>();
        protected HashSet<String> hrefs;
        protected boolean isMultiGet;

        protected CaldavRequest(String command, String path, Map<String, String> headers, String body) throws IOException {
            this.command = command;
            this.path = path.replaceAll("//", "/");
            pathElements = this.path.split("/");
            this.headers = headers;
            buildDepth();
            this.body = body;

            if (isPropFind() || isReport()) {
                parseXmlBody();
            }
        }

        public boolean isOptions() {
            return "OPTIONS".equals(command);
        }

        public boolean isPropFind() {
            return "PROPFIND".equals(command);
        }

        public boolean isPropPatch() {
            return "PROPPATCH".equals(command);
        }

        public boolean isReport() {
            return "REPORT".equals(command);
        }

        public boolean isGet() {
            return "GET".equals(command);
        }

        public boolean isHead() {
            return "HEAD".equals(command);
        }

        public boolean isPut() {
            return "PUT".equals(command);
        }

        public boolean isPost() {
            return "POST".equals(command);
        }

        public boolean isDelete() {
            return "DELETE".equals(command);
        }

        /**
         * Check if this request is a folder request.
         *
         * @return true if this is a folder (not event) request
         */
        public boolean isFolder() {
            return isPropFind() || isReport() || isPropPatch() || isOptions() || isPost();
        }

        public boolean isRoot() {
            return (pathElements.length == 0 || pathElements.length == 1);
        }

        public boolean isPathLength(int length) {
            return pathElements.length == length;
        }

        public int getPathLength() {
            return pathElements.length;
        }

        public String getPath() {
            return path;
        }

        public String getPath(String subFolder) {
            String folderPath;
            if (subFolder == null || subFolder.length() == 0) {
                folderPath = path;
            } else if (path.endsWith("/")) {
                folderPath = path + subFolder;
            } else {
                folderPath = path + '/' + subFolder;
            }
            if (folderPath.endsWith("/")) {
                return folderPath;
            } else {
                return folderPath + '/';
            }
        }

        /**
         * Check if path element at index is value
         *
         * @param index path element index
         * @param value path value
         * @return true if path element at index is value
         */
        public boolean isPath(int index, String value) {
            return value != null && value.equals(getPathElement(index));
        }

        protected String getPathElement(int index) {
            if (index < pathElements.length) {
                return pathElements[index];
            } else {
                return null;
            }
        }

        public String getLastPath() {
            return getPathElement(getPathLength() - 1);
        }

        protected boolean isBrokenHrefEncoding() {
            return isUserAgent("DAVKit/3") || isUserAgent("eM Client/");
        }

        protected boolean isLightning() {
            return isUserAgent("Lightning/");
        }

        protected boolean isUserAgent(String key) {
            String userAgent = headers.get("user-agent");
            return userAgent != null && userAgent.indexOf(key) >= 0;
        }

        public boolean isFreeBusy() {
            return body != null && body.indexOf("VFREEBUSY") >= 0;
        }

        protected void buildDepth() {
            String depthValue = headers.get("depth");
            if (depthValue != null) {
                try {
                    depth = Integer.valueOf(depthValue);
                } catch (NumberFormatException e) {
                    DavGatewayTray.warn(new BundleMessage("LOG_INVALID_DEPTH", depthValue));
                }
            }
        }

        public int getDepth() {
            return depth;
        }

        public String getBody() {
            return body;
        }

        public String getHeader(String headerName) {
            return headers.get(headerName);
        }

        protected void parseXmlBody() throws IOException {
            XMLStreamReader streamReader = null;
            try {
                XMLInputFactory inputFactory = XMLInputFactory.newInstance();
                inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
                inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);

                streamReader = inputFactory.createXMLStreamReader(new StringReader(body));
                boolean inElement = false;
                boolean inProperties = false;
                String currentElement = null;
                while (streamReader.hasNext()) {
                    int event = streamReader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        inElement = true;
                        currentElement = streamReader.getLocalName();
                        if ("prop".equals(currentElement)) {
                            inProperties = true;
                        } else if ("calendar-multiget".equals(currentElement)
                                || "addressbook-multiget".equals(currentElement)) {
                            isMultiGet = true;
                        } else if (inProperties) {
                            properties.add(currentElement);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if ("prop".equals(currentElement)) {
                            inProperties = false;
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS && inElement) {
                        if ("href".equals(currentElement)) {
                            if (hrefs == null) {
                                hrefs = new HashSet<String>();
                            }
                            if (isBrokenHrefEncoding()) {
                                hrefs.add(streamReader.getText());
                            } else {
                                hrefs.add(URIUtil.decode(encodePlusSign(streamReader.getText())));
                            }
                        }
                        inElement = false;
                    }
                }
            } catch (XMLStreamException e) {
                throw new DavMailException("EXCEPTION_INVALID_CALDAV_REQUEST", e.getMessage());
            } finally {
                try {
                    if (streamReader != null) {
                        streamReader.close();
                    }
                } catch (XMLStreamException e) {
                    DavGatewayTray.error(e);
                }
            }
        }

        public boolean hasProperty(String propertyName) {
            return properties.contains(propertyName);
        }

        public boolean isMultiGet() {
            return isMultiGet && hrefs != null;
        }

        public Set<String> getHrefs() {
            return hrefs;
        }

        @Override
        public String toString() {
            return command + ' ' + path + " Depth: " + depth + '\n' + body;
        }

        /**
         * Get request folder path.
         *
         * @return exchange folder path
         * @throws IOException on error
         */
        public String getFolderPath() throws IOException {
            return getFolderPath(null);
        }

        /**
         * Get request folder path with subFolder.
         *
         * @param subFolder sub folder path
         * @return folder path
         * @throws IOException on error
         */
        public String getFolderPath(String subFolder) throws IOException {
            int endIndex;
            if (isFolder()) {
                endIndex = getPathLength();
            } else {
                endIndex = getPathLength() - 1;
            }

            StringBuilder calendarPath = new StringBuilder();
            for (int i = 0; i < endIndex; i++) {
                if (getPathElement(i).length() > 0) {
                    calendarPath.append('/').append(getPathElement(i));
                }
            }
            if (subFolder != null && subFolder.length() > 0) {
                calendarPath.append('/').append(subFolder);
            }
            return calendarPath.toString();
        }
    }

    /**
     * Http chunked response.
     */
    protected class ChunkedResponse {
        Writer writer;

        protected ChunkedResponse(int status, String contentType) throws IOException {
            writer = new OutputStreamWriter(new BufferedOutputStream(new OutputStream() {
                @Override
                public void write(byte[] data, int offset, int length) throws IOException {
                    sendClient(Integer.toHexString(length));
                    sendClient(data, offset, length);
                    if (wireLogger.isDebugEnabled()) {
                        StringBuilder logBuffer = new StringBuilder("> ");
                        logBuffer.append(new String(data, offset, length));
                        wireLogger.debug(logBuffer.toString());
                    }
                    sendClient("");
                }

                @Override
                public void write(int b) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() throws IOException {
                    sendClient("0");
                    sendClient("");
                }
            }), "UTF-8");
            sendChunkedHttpResponse(status, contentType);
        }

        public void append(String data) throws IOException {
            writer.write(data);
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    /**
     * Caldav response wrapper, content sent in chunked mode to avoid timeout
     */
    protected class CaldavResponse extends ChunkedResponse {

        protected CaldavResponse(int status) throws IOException {
            super(status, "text/xml;charset=UTF-8");
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        }


        public void startMultistatus() throws IOException {
            writer.write("<D:multistatus xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:E=\"urn:ietf:params:xml:ns:carddav\">");
        }

        public void startResponse(String href) throws IOException {
            writer.write("<D:response>");
            writer.write("<D:href>");
            writer.write(StringUtil.xmlEncode(href));
            writer.write("</D:href>");
        }

        public void startPropstat() throws IOException {
            writer.write("<D:propstat>");
            writer.write("<D:prop>");
        }

        public void appendCalendarData(String ics) throws IOException {
            if (ics != null && ics.length() > 0) {
                writer.write("<C:calendar-data xmlns:C=\"urn:ietf:params:xml:ns:caldav\"");
                writer.write(" C:content-type=\"text/calendar\" C:version=\"2.0\">");
                writer.write(StringUtil.xmlEncode(ics));
                writer.write("</C:calendar-data>");
            }
        }

        public void appendContactData(String vcard) throws IOException {
            if (vcard != null && vcard.length() > 0) {
                writer.write("<E:address-data>");
                writer.write(StringUtil.xmlEncode(vcard));
                writer.write("</E:address-data>");
            }
        }


        public void appendHrefProperty(String propertyName, String propertyValue) throws IOException {
            appendProperty(propertyName, null, "<D:href>" + URIUtil.encodePath(StringUtil.xmlEncode(propertyValue)) + "</D:href>");
        }

        public void appendProperty(String propertyName) throws IOException {
            appendProperty(propertyName, null);
        }

        public void appendProperty(String propertyName, String propertyValue) throws IOException {
            appendProperty(propertyName, null, propertyValue);
        }

        public void appendProperty(String propertyName, String namespace, String propertyValue) throws IOException {
            if (propertyValue != null) {
                writer.write('<');
                writer.write(propertyName);
                if (namespace != null) {
                    writer.write("  xmlns:");
                    writer.write(namespace);
                }
                writer.write('>');
                writer.write(propertyValue);
                writer.write("</");
                writer.write(propertyName);
                writer.write('>');
            } else {
                writer.write('<');
                writer.write(propertyName);
                if (namespace != null) {
                    writer.write("  xmlns:");
                    writer.write(namespace);
                }
                writer.write("/>");
            }
        }

        public void endPropStatOK() throws IOException {
            writer.write("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>");
        }

        public void appendPropstatNotFound() throws IOException {
            writer.write("<D:propstat><D:status>HTTP/1.1 404 Not Found</D:status></D:propstat>");
        }

        public void endResponse() throws IOException {
            writer.write("</D:response>");
        }

        public void endMultistatus() throws IOException {
            writer.write("</D:multistatus>");
        }

        public void startScheduleResponse() throws IOException {
            writer.write("<C:schedule-response xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">");
        }

        public void startRecipientResponse(String recipient) throws IOException {
            writer.write("<C:response><C:recipient><D:href>");
            writer.write(recipient);
            writer.write("</D:href></C:recipient><C:request-status>2.0;Success</C:request-status>");
        }

        public void endRecipientResponse() throws IOException {
            writer.write("</C:response>");
        }

        public void endScheduleResponse() throws IOException {
            writer.write("</C:schedule-response>");
        }

    }
}


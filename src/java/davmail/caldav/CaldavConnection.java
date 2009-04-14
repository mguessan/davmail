package davmail.caldav;

import davmail.AbstractConnection;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.exchange.ICSBufferedReader;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.auth.AuthenticationException;
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
    protected final Logger wireLogger = Logger.getLogger(this.getClass());

    protected boolean closed = false;

    // Initialize the streams and start the thread
    public CaldavConnection(Socket clientSocket) {
        super("CaldavConnection", clientSocket, "UTF-8");
        wireLogger.setLevel(Settings.getLoggingLevel("httpclient.wire"));
    }

    protected Map<String, String> parseHeaders() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        String line;
        while ((line = readClient()) != null && line.length() > 0) {
            int index = line.indexOf(':');
            if (index <= 0) {
                throw new IOException("Invalid header: " + line);
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
                throw new IOException("Invalid content length: " + contentLength);
            }
            char[] buffer = new char[size];
            StringBuilder builder = new StringBuilder();
            int actualSize = in.read(buffer);
            builder.append(buffer, 0, actualSize);
            if (actualSize < 0) {
                throw new IOException("End of stream reached reading content");
            }
            // dirty hack to ensure full content read
            // TODO : replace with a dedicated reader
            while (builder.toString().getBytes("UTF-8").length < size) {
                actualSize = in.read(buffer);
                builder.append(buffer, 0, actualSize);
            }

            return builder.toString();
        }
    }

    protected void setSocketTimeout(String keepAliveValue) throws IOException {
        if (keepAliveValue != null && keepAliveValue.length() > 0) {
            int keepAlive;
            try {
                keepAlive = Integer.parseInt(keepAliveValue);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Keep-Alive: " + keepAliveValue);
            }
            if (keepAlive > 300) {
                keepAlive = 300;
            }
            client.setSoTimeout(keepAlive * 1000);
            DavGatewayTray.debug("Set socket timeout to " + keepAlive + " seconds");
        }
    }

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
                if (tokens.hasMoreTokens()) {
                    String command = tokens.nextToken();
                    Map<String, String> headers = parseHeaders();
                    if (tokens.hasMoreTokens()) {
                        String path = URIUtil.decode(tokens.nextToken());
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
                            // authenticate only once
                            if (session == null) {
                                // first check network connectivity
                                ExchangeSessionFactory.checkConfig();
                                try {
                                    session = ExchangeSessionFactory.getInstance(userName, password);
                                } catch (AuthenticationException e) {
                                    sendUnauthorized();
                                }
                            }
                            if (session != null) {
                                handleRequest(command, path, headers, content);
                            }
                        }
                    } else {
                        sendErr(HttpStatus.SC_NOT_IMPLEMENTED, "Invalid URI");
                    }
                }

                os.flush();
                DavGatewayTray.resetIcon();
            }
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug("Closing connection on timeout");
        } catch (SocketException e) {
            DavGatewayTray.debug("Connection closed");
        } catch (IOException e) {
            if (e.getMessage() != null) {
                DavGatewayTray.error(e.getMessage(), e);
            } else {
                DavGatewayTray.error(e);
            }
            try {
                sendErr(e);
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    public void handleRequest(String command, String path, Map<String, String> headers, String body) throws IOException {
        CaldavRequest request = new CaldavRequest(command, path, headers, body);
        // full debug trace
        if (wireLogger.isDebugEnabled()) {
            wireLogger.debug("Caldav command: " + request.toString());
        }
        if (request.isOptions()) {
            sendOptions();
        } else if (request.isPropFind() && request.isRoot()) {
            sendRoot(request);
        } else if (request.isGet() && request.isRoot()) {
            sendGetRoot();
            // deprecated url, will be removed
        } else if (request.isPath(1, "calendar")) {
            StringBuilder message = new StringBuilder();
            message.append("/calendar no longer supported, recreate calendar with /users/")
                    .append(session.getEmail()).append("/calendar");
            DavGatewayTray.error(message.toString());
            sendErr(HttpStatus.SC_BAD_REQUEST, message.toString());
            // /principals/users namespace
        } else if (request.isPath(1, "principals") && request.isPath(2, "users")) {
            handleUserPrincipals(request);
            // users root
        } else if (request.isPath(1, "users")) {
            // principal request
            if (request.isPropFind() && request.isPathLength(3)) {
                sendUserRoot(request);
            } else {
                handleCalendar(request, 3);
            }
        } else if (request.isPath(1, "public")) {
            handleCalendar(request, 2);
        } else {
            sendUnsupported(request);
        }
    }

    protected void handleUserPrincipals(CaldavRequest request) throws IOException {
        if (request.isPropFind() && request.isPathLength(4)) {
            sendPrincipal(request, request.getPathElement(3));
        } else if (request.isPropFind() && request.isPathLength(4)) {
            sendPrincipal(request, request.getPathElement(3));
            // send back principal on search
        } else if (request.isReport() && request.isPathLength(3)) {
            sendPrincipal(request, session.getEmail());
        } else {
            sendUnsupported(request);
        }
    }

    protected void handleCalendar(CaldavRequest request, int depth) throws IOException {
        String folderName = request.getPathElement(depth);
        // folder request
        if (request.isPathLength(depth + 1)) {
            if (request.isPropFind() && "inbox".equals(folderName)) {
                sendInbox(request);
            } else if (request.isPropFind() && "outbox".equals(folderName)) {
                sendOutbox(request);
            } else if (request.isPost() && "outbox".equals(folderName)) {
                if (request.isFreeBusy()) {
                    sendFreeBusy(request.getBody());
                } else {
                    int status = session.sendEvent(request.getBody());
                    sendHttpResponse(status);
                }
            } else if (request.isPropFind()) {
                sendCalendar(request);
            } else if (request.isPropPatch()) {
                patchCalendar();
            } else if (request.isReport()) {
                reportEvents(request);
            }
            // event request
        } else if (request.isPathLength(depth + 2)) {
            String eventName = xmlDecodeName(request.getPathElement(depth + 1));
            if (request.isPut()) {
                String etag = request.getHeader("if-match");
                String noneMatch = request.getHeader("if-none-match");
                ExchangeSession.EventResult eventResult = session.createOrUpdateEvent(request.getFolderPath(), eventName, request.getBody(), etag, noneMatch);
                if (eventResult.etag != null) {
                    HashMap<String, String> responseHeaders = new HashMap<String, String>();
                    responseHeaders.put("ETag", eventResult.etag);
                    sendHttpResponse(eventResult.status, responseHeaders, null, "", true);
                } else {
                    sendHttpResponse(eventResult.status);
                }

            } else if (request.isDelete()) {
                int status = session.deleteEvent(request.getFolderPath(), eventName);
                sendHttpResponse(status);
            } else if (request.isGet()) {
                ExchangeSession.Event event = session.getEvent(request.getFolderPath(), eventName);
                sendHttpResponse(HttpStatus.SC_OK, null, "text/calendar;charset=UTF-8", event.getICS(), true);
            } else {
                sendUnsupported(request);
            }

        } else {
            sendUnsupported(request);
        }
    }

    protected void appendEventsResponses(CaldavResponse response, CaldavRequest request, List<ExchangeSession.Event> events) throws IOException {
        int size = events.size();
        int count = 0;
        for (ExchangeSession.Event event : events) {
            DavGatewayTray.debug("Listing event " + (++count) + "/" + size);
            DavGatewayTray.switchIcon();
            appendEventResponse(response, request, event);
        }
    }

    protected void appendEventResponse(CaldavResponse response, CaldavRequest request, ExchangeSession.Event event) throws IOException {
        String eventPath = xmlEncodeName(event.getPath());
        response.startResponse(URIUtil.encodePath(request.getPath()) + "/" + URIUtil.encodeWithinQuery(eventPath));
        response.startPropstat();
        if (request.hasProperty("calendar-data")) {
            response.appendCalendarData(event.getICS());
        }
        if (request.hasProperty("getcontenttype")) {
            response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", event.getEtag());
        }
        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", eventPath);
        }
        response.endPropStatOK();
        response.endResponse();
    }

    public void appendCalendar(CaldavResponse response, CaldavRequest request) throws IOException {
        if (request.isLastPath("calendar")) {
            response.startResponse(URIUtil.encodePath(request.getPath()));
        } else {
            response.startResponse(URIUtil.encodePath(request.getPath()) + "/calendar");
        }
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:calendar xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("owner")) {
            if ("users".equals(request.getPathElement(1))) {
                response.appendProperty("D:owner", "<D:href>/principals/users/" + request.getPathElement(2) + "</D:href>");
            } else {
                response.appendProperty("D:owner", "<D:href>/principals" + request.getPath() + "</D:href>");
            }
        }
        if (request.hasProperty("getcontenttype")) {
            response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
        }
        if (request.hasProperty("getetag")) {
            response.appendProperty("D:getetag", session.getCalendarEtag());
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    base64Encode(session.getCalendarCtag()));
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", request.getPathElement(request.getPathLength() - 1));
        }
        response.endPropStatOK();
        response.endResponse();
    }

    public void appendInbox(CaldavResponse response, CaldavRequest request) throws IOException {
        if (request.isLastPath("inbox")) {
            response.startResponse(URIUtil.encodePath(request.getPath()));
        } else {
            response.startResponse(URIUtil.encodePath(request.getPath()) + "/inbox");
        }
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:schedule-inbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("getcontenttype")) {
            response.appendProperty("D:getcontenttype", "text/calendar; component=vevent");
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    base64Encode(session.getInboxCtag()));
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "inbox");
        }
        response.endPropStatOK();
        response.endResponse();
    }

    public void appendOutbox(CaldavResponse response, CaldavRequest request) throws IOException {
        if (request.isLastPath("outbox")) {
            response.startResponse(URIUtil.encodePath(request.getPath()));
        } else {
            response.startResponse(URIUtil.encodePath(request.getPath()) + "/outbox");
        }
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:schedule-outbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("getctag")) {
            response.appendProperty("CS:getctag", "CS=\"http://calendarserver.org/ns/\"",
                    "0");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "outbox");
        }
        response.endPropStatOK();
        response.endResponse();
    }

    public void sendGetRoot() throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Connected to DavMail<br/>");
        buffer.append("UserName :").append(userName).append("<br/>");
        buffer.append("Email :").append(session.getEmail()).append("<br/>");
        sendHttpResponse(HttpStatus.SC_OK, null, "text/html;charset=UTF-8", buffer.toString(), true);
    }

    public void sendInbox(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        appendInbox(response, request);
        if (request.getDepth() == 1) {
            DavGatewayTray.debug("Searching calendar messages...");
            List<ExchangeSession.Event> events = session.getEventMessages(request.getFolderPath());
            DavGatewayTray.debug("Found " + events.size() + " calendar messages");
            appendEventsResponses(response, request, events);
        }
        response.endMultistatus();
        response.close();
    }

    public void sendOutbox(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        appendOutbox(response, request);
        response.endMultistatus();
        response.close();
    }

    public void sendCalendar(CaldavRequest request) throws IOException {
        String folderPath = request.getFolderPath();
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        appendCalendar(response, request);
        if (request.getDepth() == 1) {
            DavGatewayTray.debug("Searching calendar events at " + folderPath + " ...");
            List<ExchangeSession.Event> events = session.getAllEvents(folderPath);
            DavGatewayTray.debug("Found " + events.size() + " calendar events");
            appendEventsResponses(response, request, events);
        }
        response.endMultistatus();
        response.close();
    }

    public void patchCalendar() throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        // just ignore calendar folder proppatch (color not supported in Exchange)
        response.endMultistatus();
        response.close();
    }

    protected String getEventFileNameFromPath(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return null;
        } else {
            return xmlDecodeName(path.substring(index + 1));
        }
    }

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
                DavGatewayTray.debug("Report event " + (++count) + "/" + total);
                DavGatewayTray.switchIcon();
                try {
                    String eventName = getEventFileNameFromPath(href);
                    if (eventName == null) {
                        notFound.add(href);
                    } else if ("inbox".equals(eventName) || "calendar".equals(eventName)) {
                        // Sunbird: just ignore
                    } else {
                        appendEventResponse(response, request, session.getEvent(folderPath, eventName));
                    }
                } catch (HttpException e) {
                    DavGatewayTray.warn("Event not found:" + href);
                    notFound.add(href);
                }
            }
        } else if (request.isPath(1, "users") && request.isPath(3, "inbox")) {
            events = session.getEventMessages(request.getFolderPath());
            appendEventsResponses(response, request, events);
        } else {
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

    public void sendUserRoot(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse(URIUtil.encodePath(request.getPath()));
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", request.getPathElement(request.getPathLength() - 1));
        }
        response.endPropStatOK();
        if (request.getDepth() == 1) {
            appendInbox(response, request);
            appendOutbox(response, request);
            appendCalendar(response, request);
        }
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    public void sendRoot(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse("/");
        response.startPropstat();

        if (request.hasProperty("principal-collection-set")) {
            response.appendProperty("D:principal-collection-set", "<D:href>/principals/users/" + session.getEmail() + "</D:href>");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", "ROOT");
        }
        response.endPropStatOK();
        response.endResponse();
        response.endMultistatus();
        response.close();
    }

    public void sendPrincipal(CaldavRequest request, String principal) throws IOException {
        // actual principal is email address
        String actualPrincipal = principal;
        if (userName.equals(principal)) {
            actualPrincipal = session.getEmail();
        }

        CaldavResponse response = new CaldavResponse(HttpStatus.SC_MULTI_STATUS);
        response.startMultistatus();
        response.startResponse("/principals/users/" + principal);
        response.startPropstat();

        if (request.hasProperty("calendar-home-set")) {
            response.appendProperty("C:calendar-home-set", "<D:href>/users/" + actualPrincipal + "/calendar</D:href>");
        }

        if (request.hasProperty("calendar-user-address-set")) {
            response.appendProperty("C:calendar-user-address-set", "<D:href>mailto:" + actualPrincipal + "</D:href>");
        }

        // no inbox/outbox for delegated calendars
        if (session.getEmail().equals(principal)) {
            if (request.hasProperty("schedule-inbox-URL")) {
                response.appendProperty("C:schedule-inbox-URL", "<D:href>/users/" + actualPrincipal + "/inbox</D:href>");
            }

            if (request.hasProperty("schedule-outbox-URL")) {
                response.appendProperty("C:schedule-outbox-URL", "<D:href>/users/" + actualPrincipal + "/outbox</D:href>");
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
                throw new IOException("Invalid request: " + body);
            }
            String fullkey = line.substring(0, index);
            String value = line.substring(index + 1);
            int semicolonIndex = fullkey.indexOf(";");
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
            ExchangeSession.FreeBusy freeBusy = session.getFreebusy(attendee, valueMap);
            if (freeBusy != null) {
                freeBusyMap.put(attendee, freeBusy);
            }
        }
        if (!freeBusyMap.isEmpty()) {
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
                        .append(attendeeKeyMap.get(attendee)).append(":").append(attendee).append("").append((char) 13).append((char) 10);
                entry.getValue().appendTo(ics);
                ics.append("END:VFREEBUSY").append((char) 13).append((char) 10)
                        .append("END:VCALENDAR");
                response.appendCalendarData(ics.toString());
                response.endRecipientResponse();

            }
            response.endScheduleResponse();
            response.close();
        } else {
            sendHttpResponse(HttpStatus.SC_NOT_FOUND, null, "text/plain", "Unknown recipient: " + valueMap.get("ATTENDEE"), true);
        }

    }


    public void sendRedirect(Map<String, String> headers, String path) throws IOException {
        StringBuilder buffer = new StringBuilder();
        if (headers.get("host") != null) {
            buffer.append("http://").append(headers.get("host"));
        }
        buffer.append(path);
        Map<String, String> responseHeaders = new HashMap<String, String>();
        responseHeaders.put("Location", buffer.toString());
        sendHttpResponse(HttpStatus.SC_MOVED_PERMANENTLY, responseHeaders);
    }

    public void sendErr(Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendErr(HttpStatus.SC_SERVICE_UNAVAILABLE, message);
    }

    public void sendUnsupported(CaldavRequest request) throws IOException {
        StringBuilder message = new StringBuilder();
        message.append("Unsupported request: ").append(request.toString());
        DavGatewayTray.error(message.toString());
        sendErr(HttpStatus.SC_BAD_REQUEST, message.toString());
    }

    public void sendErr(int status, String message) throws IOException {
        sendHttpResponse(status, null, "text/plain;charset=UTF-8", message, false);
    }

    public void sendOptions() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Allow", "OPTIONS, GET, PROPFIND, PUT, POST");
        sendHttpResponse(HttpStatus.SC_OK, headers);
    }

    public void sendUnauthorized() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("WWW-Authenticate", "Basic realm=\"" + Settings.getProperty("davmail.url") + "\"");
        sendHttpResponse(HttpStatus.SC_UNAUTHORIZED, headers);
    }

    public void sendHttpResponse(int status) throws IOException {
        sendHttpResponse(status, null, null, (byte[]) null, true);
    }

    public void sendHttpResponse(int status, Map<String, String> headers) throws IOException {
        sendHttpResponse(status, headers, null, (byte[]) null, true);
    }

    public void sendChunkedHttpResponse(int status) throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Transfer-Encoding", "chunked");
        sendHttpResponse(status, headers, "text/xml;charset=UTF-8", (byte[]) null, true);
    }

    public void sendHttpResponse(int status, Map<String, String> headers, String contentType, String content, boolean keepAlive) throws IOException {
        sendHttpResponse(status, headers, contentType, content.getBytes("UTF-8"), keepAlive);
    }

    public void sendHttpResponse(int status, Map<String, String> headers, String contentType, byte[] content, boolean keepAlive) throws IOException {
        sendClient("HTTP/1.1 " + status + " " + HttpStatus.getStatusText(status));
        sendClient("Server: DavMail Gateway");
        sendClient("DAV: 1, 2, 3, access-control, calendar-access, ticket, calendar-schedule, calendarserver-private-events");
        SimpleDateFormat formatter = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        sendClient("Date: " + formatter.format(new java.util.Date()));
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
     * @throws java.io.IOException if invalid credentials
     */
    protected void decodeCredentials(String authorization) throws IOException {
        int index = authorization.indexOf(' ');
        if (index > 0) {
            String mode = authorization.substring(0, index).toLowerCase();
            if (!"basic".equals(mode)) {
                throw new IOException("Unsupported authorization mode: " + mode);
            }
            String encodedCredentials = authorization.substring(index + 1);
            String decodedCredentials = base64Decode(encodedCredentials);
            index = decodedCredentials.indexOf(':');
            if (index > 0) {
                userName = decodedCredentials.substring(0, index);
                password = decodedCredentials.substring(index + 1);
            } else {
                throw new IOException("Invalid credentials");
            }
        } else {
            throw new IOException("Invalid credentials");
        }

    }

    /**
     * Need to encode xml for iCal
     *
     * @param name decoded name
     * @return name encoded name
     */
    protected String xmlEncodeName(String name) {
        String result = name;
        if (name.indexOf('&') >= 0) {
            result = result.replaceAll("&", "&amp;");
        }
        if (name.indexOf('<') >= 0) {
            result = result.replaceAll("<", "&lt;");
        }
        if (name.indexOf('>') >= 0) {
            result = result.replaceAll(">", "&gt;");
        }
        return result;
    }

    /**
     * Need to decode xml for iCal
     *
     * @param name encoded name
     * @return name decoded name
     */
    protected String xmlDecodeName(String name) {
        String result = name;
        if (name.indexOf("&amp;") >= 0) {
            result = result.replaceAll("&amp;", "&");
        }
        if (name.indexOf("&gt;") >= 0) {
            result = result.replaceAll("&gt;", ">");
        }
        if (name.indexOf("&lt;") >= 0) {
            result = result.replaceAll("&lt;", "<");
        }
        return result;
    }

    protected class CaldavRequest {
        protected String command;
        protected String path;
        protected String[] pathElements;
        protected Map<String, String> headers;
        protected int depth;
        protected String body;
        protected final HashSet<String> properties = new HashSet<String>();
        protected HashSet<String> hrefs;
        protected boolean isMultiGet;

        public CaldavRequest(String command, String path, Map<String, String> headers, String body) throws IOException {
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
            return "PROPFIND".equals(command);
        }

        public boolean isReport() {
            return "REPORT".equals(command);
        }

        public boolean isGet() {
            return "GET".equals(command);
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

        public boolean isLastPath(String value) {
            return value != null && value.equals(getPathElement(getPathLength() - 1));
        }

        protected String getPathElement(int index) {
            if (index < pathElements.length) {
                return pathElements[index];
            } else {
                return null;
            }
        }

        protected boolean isIcal() {
            String userAgent = headers.get("user-agent");
            return userAgent != null && userAgent.indexOf("DAVKit") >= 0;
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
                    DavGatewayTray.warn("Invalid depth value: " + depthValue);
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
                        } else if ("calendar-multiget".equals(currentElement)) {
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
                            if (isIcal()) {
                                hrefs.add(streamReader.getText());
                            } else {
                                hrefs.add(URIUtil.decode(streamReader.getText()));
                            }
                        }
                        inElement = false;
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException(e.getMessage());
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
            return command + " " + path + " Depth: " + depth + "\n" + body;
        }

        public String getFolderPath() throws IOException {
            if ("users".equals(getPathElement(1))) {
                return session.buildCalendarPath(getPathElement(2), getPathElement(3));
            } else {
                return getPath();
            }
        }
    }

    protected class CaldavResponse {
        Writer writer;

        public CaldavResponse(int status) throws IOException {
            writer = new OutputStreamWriter(new BufferedOutputStream(new OutputStream() {
                @Override
                public void write(byte[] data, int offset, int length) throws IOException {
                    sendClient(Integer.toHexString(length));
                    sendClient(data, offset, length);
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
            sendChunkedHttpResponse(status);
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        }


        public void startMultistatus() throws IOException {
            writer.write("<D:multistatus xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">");
        }

        public void startResponse(String href) throws IOException {
            writer.write("<D:response>");
            writer.write("<D:href>");
            writer.write(href);
            writer.write("</D:href>");
        }

        public void startPropstat() throws IOException {
            writer.write("<D:propstat>");
            writer.write("<D:prop>");
        }

        public void appendCalendarData(String ics) throws IOException {
            if (ics != null && ics.length() > 0) {
                ics = ics.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
                writer.write("<C:calendar-data xmlns:C=\"urn:ietf:params:xml:ns:caldav\"");
                writer.write(" C:content-type=\"text/calendar\" C:version=\"2.0\">");
                writer.write(ics);
                writer.write("</C:calendar-data>");
            }
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

        public void close() throws IOException {
            writer.close();
        }

    }
}


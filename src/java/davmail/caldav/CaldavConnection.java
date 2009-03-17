package davmail.caldav;

import davmail.AbstractConnection;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.exchange.ICSBufferedReader;
import davmail.tray.DavGatewayTray;
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
                        String path = tokens.nextToken();
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
            if (e instanceof HttpException) {
            DavGatewayTray.error(((HttpException)e).getReasonCode()+" "+((HttpException)e).getReason(), e);
            } else {
                DavGatewayTray.error(e);
            }
            try {
                sendErr(HttpStatus.SC_SERVICE_UNAVAILABLE, e);
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    protected int getDepth(Map<String, String> headers) {
        int result = 0;
        String depthValue = headers.get("depth");
        if (depthValue != null) {
            try {
                result = Integer.valueOf(depthValue);
            } catch (NumberFormatException e) {
                DavGatewayTray.warn("Invalid depth value: " + depthValue);
            }
        }
        return result;
    }

    public void handleRequest(String command, String path, Map<String, String> headers, String body) throws IOException {
        int depth = getDepth(headers);
        String[] paths = path.replaceAll("//", "/").split("/");

        // full debug trace
        if (wireLogger.isDebugEnabled()) {
            wireLogger.debug("Caldav command: " + command + " " + path + " depth: " + depth + "\n" + body);
        }

        CaldavRequest request = null;
        if ("PROPFIND".equals(command) || "REPORT".equals(command)) {
            request = new CaldavRequest(body);
        }
        if ("OPTIONS".equals(command)) {
            sendOptions();
            // redirect PROPFIND on / to current user principal
        } else if ("PROPFIND".equals(command) && (paths.length == 0 || paths.length == 1)) {
            sendRoot(request);
        } else if ("GET".equals(command) && (paths.length == 0 || paths.length == 1)) {
            sendGetRoot();
            // return current user calendar
        } else if ("calendar".equals(paths[1])) {
            StringBuilder message = new StringBuilder();
            message.append("/calendar no longer supported, recreate calendar with /users/")
                    .append(session.getEmail()).append("/calendar");
            DavGatewayTray.error(message.toString());
            sendErr(HttpStatus.SC_BAD_REQUEST, message.toString());
        } else if ("user".equals(paths[1])) {
            sendRedirect(headers, "/principals/users/" + session.getEmail());
            // principal namespace
        } else if ("PROPFIND".equals(command) && "principals".equals(paths[1]) && paths.length == 4 &&
                "users".equals(paths[2])) {
            sendPrincipal(request, paths[3]);
            // send back principal on search
        } else if ("REPORT".equals(command) && "principals".equals(paths[1]) && paths.length == 3 &&
                "users".equals(paths[2])) {
            sendPrincipal(request, session.getEmail());
            // user root
        } else if ("PROPFIND".equals(command) && "users".equals(paths[1]) && paths.length == 3) {
            sendUserRoot(request, depth, paths[2]);
        } else if ("PROPFIND".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "inbox".equals(paths[3])) {
            sendInbox(request, depth, paths[2]);
        } else if ("REPORT".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "inbox".equals(paths[3])) {
            reportEvents(request, "INBOX");
        } else if ("PROPFIND".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "outbox".equals(paths[3])) {
            sendOutbox(request, paths[2]);
        } else if ("POST".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "outbox".equals(paths[3])) {
            if (body.indexOf("VFREEBUSY") >= 0) {
                sendFreeBusy(body);
            } else {
                int status = session.sendEvent(body);
                sendHttpResponse(status, true);
            }
        } else if ("PROPFIND".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "calendar".equals(paths[3])) {
            sendCalendar(request, depth, paths[2]);
        } else if ("PROPPATCH".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "calendar".equals(paths[3])) {
            patchCalendar(request, depth, paths[2]);
        } else if ("REPORT".equals(command) && "users".equals(paths[1]) && paths.length == 4 && "calendar".equals(paths[3])
                // only current user for now
                && session.getEmail().equalsIgnoreCase(paths[2])) {
            reportEvents(request, "calendar");

        } else if ("PUT".equals(command) && "users".equals(paths[1]) && paths.length == 5 && "calendar".equals(paths[3])
                // only current user for now
                && session.getEmail().equalsIgnoreCase(paths[2])) {
            String etag = headers.get("if-match");
            String noneMatch = headers.get("if-none-match");
            ExchangeSession.EventResult eventResult = session.createOrUpdateEvent(paths[4].replaceAll("&amp;", "&"), body, etag, noneMatch);
            if (eventResult.etag != null) {
                HashMap<String, String> responseHeaders = new HashMap<String, String>();
                responseHeaders.put("ETag", eventResult.etag);
                sendHttpResponse(eventResult.status, responseHeaders, null, "", true);
            } else {
                sendHttpResponse(eventResult.status, true);
            }

        } else if ("DELETE".equals(command) && "users".equals(paths[1]) && paths.length == 5
                // only current user for now
                && session.getEmail().equalsIgnoreCase(paths[2])) {
            if ("inbox".equals(paths[3])) {
                paths[3] = "INBOX";
            }
            int status = session.deleteEvent(paths[3], paths[4].replaceAll("&amp;", "&"));
            sendHttpResponse(status, true);
        } else if ("GET".equals(command) && "users".equals(paths[1]) && paths.length == 5 && "calendar".equals(paths[3])
                // only current user for now
                && session.getEmail().equalsIgnoreCase(paths[2])) {
            ExchangeSession.Event event = session.getEvent(paths[3], paths[4]);
            sendHttpResponse(HttpStatus.SC_OK, null, "text/calendar;charset=UTF-8", event.getICS(), true);

        } else {
            StringBuilder message = new StringBuilder();
            message.append("Unsupported request: ").append(command).append(" ").append(path);
            message.append(" Depth: ").append(depth).append("\n").append(body);
            DavGatewayTray.error(message.toString());
            sendErr(HttpStatus.SC_BAD_REQUEST, message.toString());
        }
    }

    protected void appendEventsResponses(CaldavResponse response, CaldavRequest request, String path, List<ExchangeSession.Event> events) throws IOException {
        int size = events.size();
        int count = 0;
        for (ExchangeSession.Event event : events) {
            DavGatewayTray.debug("Retrieving event " + (++count) + "/" + size);
            appendEventResponse(response, request, path, event);
        }
    }

    protected void appendEventResponse(CaldavResponse response, CaldavRequest request, String path, ExchangeSession.Event event) throws IOException {
        String eventPath = event.getPath().replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("&", "&amp;");
        response.startResponse("/users/" + session.getEmail() + "/" + path + "/" + URIUtil.encodeWithinQuery(eventPath));
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

    public void appendCalendar(CaldavResponse response, String principal, CaldavRequest request) throws IOException {
        response.startResponse("/users/" + principal + "/calendar");
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:calendar xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
        }
        if (request.hasProperty("owner")) {
            response.appendProperty("D:owner", "<D:href>/principals/users/" + principal + "</D:href>");
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
            response.appendProperty("D:displayname", "calendar");
        }
        response.endPropStatOK();
        response.endResponse();
    }

    public void appendInbox(CaldavResponse response, String principal, CaldavRequest request) throws IOException {
        response.startResponse("/users/" + principal + "/inbox");
        response.startPropstat();

        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>" +
                    "<C:schedule-inbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>");
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

    public void appendOutbox(CaldavResponse response, String principal, CaldavRequest request) throws IOException {
        response.startResponse("/users/" + principal + "/outbox");
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

    public void sendInbox(CaldavRequest request, int depth, String principal) throws IOException {
        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        appendInbox(response, principal, request);
        if (depth == 1) {
            DavGatewayTray.debug("Searching calendar messages...");
            List<ExchangeSession.Event> events = session.getEventMessages();
            DavGatewayTray.debug("Found " + events.size() + " calendar messages");
            appendEventsResponses(response, request, "inbox", events);
        }
        response.endMultistatus();
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void sendOutbox(CaldavRequest request, String principal) throws IOException {
        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        appendOutbox(response, principal, request);
        response.endMultistatus();
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void sendCalendar(CaldavRequest request, int depth, String principal) throws IOException {
        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        appendCalendar(response, principal, request);
        if (depth == 1) {
            DavGatewayTray.debug("Searching calendar events...");
            List<ExchangeSession.Event> events = session.getAllEvents();
            DavGatewayTray.debug("Found " + events.size() + " calendar events");
            appendEventsResponses(response, request, "calendar", events);
        }
        response.endMultistatus();
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void patchCalendar(CaldavRequest request, int depth, String principal) throws IOException {
        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        // just ignore calendar folder proppatch (color not supported in Exchange)
        response.endMultistatus();
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    protected String getEventFileNameFromPath(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return null;
        } else {
            return path.substring(index + 1).replaceAll("&amp;", "&");
        }
    }

    public void reportEvents(CaldavRequest request, String path) throws IOException {
        List<ExchangeSession.Event> events;
        List<String> notFound = new ArrayList<String>();
        if (request.isMultiGet()) {
            events = new ArrayList<ExchangeSession.Event>();
            int count = 0;
            int total = request.getHrefs().size();
            for (String href : request.getHrefs()) {
                DavGatewayTray.debug("Report event " + (++count) + "/" + total);
                try {
                    String eventName = getEventFileNameFromPath(href);
                    if (eventName == null) {
                        notFound.add(href);
                    } else {
                        events.add(session.getEvent(path, eventName));
                    }
                } catch (HttpException e) {
                    notFound.add(href);
                }
            }
        } else if ("INBOX".equals(path)) {
            events = session.getEventMessages();
        } else {
            events = session.getAllEvents();
        }

        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        appendEventsResponses(response, request, path, events);

        // send not found events errors
        for (String href : notFound) {
            response.startResponse(URIUtil.encodePath(href));
            response.appendPropstatNotFound();
            response.endResponse();
        }
        response.endMultistatus();

        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void sendUserRoot(CaldavRequest request, int depth, String principal) throws IOException {
        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        response.startResponse("/users/" + principal);
        response.startPropstat();


        if (request.hasProperty("resourcetype")) {
            response.appendProperty("D:resourcetype", "<D:collection/>");
        }
        if (request.hasProperty("displayname")) {
            response.appendProperty("D:displayname", principal);
        }
        response.endPropStatOK();
        if (depth == 1) {
            appendInbox(response, principal, request);
            appendOutbox(response, principal, request);
            appendCalendar(response, principal, request);
        }
        response.endResponse();
        response.endMultistatus();
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void sendRoot(CaldavRequest request) throws IOException {
        CaldavResponse response = new CaldavResponse();
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
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
    }

    public void sendPrincipal(CaldavRequest request, String principal) throws IOException {
        // actual principal is email address
        String actualPrincipal = principal;
        if (userName.equals(principal)) {
            actualPrincipal = session.getEmail();
        }

        CaldavResponse response = new CaldavResponse();
        response.startMultistatus();
        response.startResponse("/principals/users/" + principal);
        response.startPropstat();

        if (request.hasProperty("calendar-home-set")) {
            response.appendProperty("C:calendar-home-set", "<D:href>/users/" + actualPrincipal + "/calendar</D:href>");
        }

        if (request.hasProperty("calendar-user-address-set")) {
            response.appendProperty("C:calendar-user-address-set", "<D:href>mailto:" + actualPrincipal + "</D:href>");
        }

        if (request.hasProperty("schedule-inbox-URL")) {
            response.appendProperty("C:schedule-inbox-URL", "<D:href>/users/" + actualPrincipal + "/inbox</D:href>");
        }

        if (request.hasProperty("schedule-outbox-URL")) {
            response.appendProperty("C:schedule-outbox-URL", "<D:href>/users/" + actualPrincipal + "/outbox</D:href>");
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
        sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, response, true);
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
        HashMap<String, String> freeBusyMap = new HashMap<String, String>();
        for (String attendee : attendees) {
            String freeBusy = session.getFreebusy(attendee, valueMap);
            if (freeBusy != null) {
                freeBusyMap.put(attendee, freeBusy);
            }
        }
        if (!freeBusyMap.isEmpty()) {
            CaldavResponse response = new CaldavResponse();
            response.startScheduleResponse();

            for (Map.Entry<String, String> entry : freeBusyMap.entrySet()) {
                String attendee = entry.getKey();
                response.startRecipientResponse(attendee);

                StringBuilder ics = new StringBuilder();
                ics.append("BEGIN:VCALENDAR").append((char) 13).append((char) 10)
                        .append("VERSION:2.0").append((char) 13).append((char) 10)
                        .append("PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN").append((char) 13).append((char) 10)
                        .append("METHOD:REPLY").append((char) 13).append((char) 10)
                        .append("BEGIN:VFREEBUSY").append((char) 13).append((char) 10)
                        .append("DTSTAMP:").append(valueMap.get("DTSTAMP")).append("").append((char) 13).append((char) 10)
                        .append("ORGANIZER:").append(valueMap.get("ORGANIZER")).append("").append((char) 13).append((char) 10)
                        .append("DTSTART:").append(valueMap.get("DTSTART")).append("").append((char) 13).append((char) 10)
                        .append("DTEND:").append(valueMap.get("DTEND")).append("").append((char) 13).append((char) 10)
                        .append("UID:").append(valueMap.get("UID")).append("").append((char) 13).append((char) 10)
                        .append(attendeeKeyMap.get(attendee)).append(":").append(attendee).append("").append((char) 13).append((char) 10);
                if (entry.getValue().length() > 0) {
                    ics.append("FREEBUSY;FBTYPE=BUSY-UNAVAILABLE:").append(entry.getValue()).append("").append((char) 13).append((char) 10);
                }
                ics.append("END:VFREEBUSY").append((char) 13).append((char) 10)
                        .append("END:VCALENDAR");
                response.appendCalendarData(ics.toString());
                response.endRecipientResponse();

            }
            response.endScheduleResponse();
            sendHttpResponse(HttpStatus.SC_OK, null, response, true);
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
        sendHttpResponse(HttpStatus.SC_MOVED_PERMANENTLY, responseHeaders, true);
    }

    public void sendErr(int status, Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendErr(status, message);
    }

    public void sendErr(int status, String message) throws IOException {
        sendHttpResponse(status, null, "text/plain;charset=UTF-8", message, false);
    }

    public void sendOptions() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Allow", "OPTIONS, GET, PROPFIND, PUT, POST");
        sendHttpResponse(HttpStatus.SC_OK, headers, true);
    }

    public void sendUnauthorized() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("WWW-Authenticate", "Basic realm=\"" + Settings.getProperty("davmail.url") + "\"");
        sendHttpResponse(HttpStatus.SC_UNAUTHORIZED, headers, true);
    }

    public void sendHttpResponse(int status, boolean keepAlive) throws IOException {
        sendHttpResponse(status, null, null, (byte[]) null, keepAlive);
    }

    public void sendHttpResponse(int status, Map<String, String> headers, boolean keepAlive) throws IOException {
        sendHttpResponse(status, headers, null, (byte[]) null, keepAlive);
    }

    public void sendHttpResponse(int status, Map<String, String> headers, CaldavResponse response, boolean keepAlive) throws IOException {
        sendHttpResponse(status, headers, "text/xml;charset=UTF-8", response.getBytes(), keepAlive);
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
        } else {
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

    protected static class CaldavRequest {
        protected final HashSet<String> properties = new HashSet<String>();
        protected HashSet<String> hrefs;
        protected boolean isMultiGet;

        public CaldavRequest(String body) throws IOException {
            // parse body
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
                            hrefs.add(URIUtil.decode(streamReader.getText()));
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
    }

    protected static class CaldavResponse {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer;

        public CaldavResponse() throws IOException {
            writer = new OutputStreamWriter(baos, "UTF-8");
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
            writer.write("</D:prop>");
            writer.write("<D:status>HTTP/1.1 200 OK</D:status>");
            writer.write("</D:propstat>");
        }

        public void appendPropstatNotFound() throws IOException {
            writer.write("<D:propstat>");
            writer.write("<D:status>HTTP/1.1 404 Not Found</D:status>");
            writer.write("</D:propstat>");
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
            writer.write("<C:response>");
            writer.write("<C:recipient>");
            writer.write("<D:href>");
            writer.write(recipient);
            writer.write("</D:href>");
            writer.write("</C:recipient>");
            writer.write("<C:request-status>2.0;Success</C:request-status>");
        }

        public void endRecipientResponse() throws IOException {
            writer.write("</C:response>");
        }

        public void endScheduleResponse() throws IOException {
            writer.write("</C:schedule-response>");
        }

        public void close() throws IOException {
            try {
                writer.close();
            } finally {
                baos.close();
            }
        }

        public byte[] getBytes() throws IOException {
            close();
            return baos.toByteArray();
        }
    }
}


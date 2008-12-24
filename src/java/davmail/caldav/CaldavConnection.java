package davmail.caldav;

import davmail.AbstractConnection;
import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.auth.AuthenticationException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handle a caldav connection.
 */
public class CaldavConnection extends AbstractConnection {
    protected boolean closed = false;

    // Initialize the streams and start the thread
    public CaldavConnection(Socket clientSocket) {
        super("CaldavConnection", clientSocket, "UTF-8");
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
            int actualSize = in.read(buffer);
            if (actualSize < 0) {
                throw new IOException("End of stream reached reading content");
            }
            return new String(buffer, 0, actualSize);
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
                                    sendErr(HttpStatus.SC_UNAUTHORIZED, e.getMessage());
                                }
                            }
                            handleRequest(command, path, headers, content);
                        }
                    } else {
                        sendErr(HttpStatus.SC_NOT_IMPLEMENTED, "Invalid URI");
                    }
                }

                os.flush();
            }
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug("Closing connection on timeout");
        } catch (SocketException e) {
            DavGatewayTray.debug("Connection closed");
        } catch (IOException e) {
            DavGatewayTray.error(e);
            try {
                sendErr(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
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
        if ("OPTIONS".equals(command)) {
            sendOptions();
        } else if ("PROPFIND".equals(command)
                && ("/user/".equals(path) || "/user".equals(path))
                && body != null) {
            CaldavRequest request = new CaldavRequest(body);
            StringBuilder buffer = new StringBuilder();
            buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            buffer.append("<D:multistatus xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n");
            buffer.append("    <D:response>\n");
            buffer.append("        <D:href>/user</D:href>\n");
            buffer.append("        <D:propstat>\n");
            buffer.append("            <D:prop>\n");
            if (request.hasProperty("calendar-home-set")) {
                buffer.append("                <C:calendar-home-set>\n");
                buffer.append("                    <D:href>/calendar</D:href>\n");
                buffer.append("                </C:calendar-home-set>");
            }

            if (request.hasProperty("calendar-user-address-set")) {
                buffer.append("                <C:calendar-user-address-set>\n");
                buffer.append("                    <D:href>mailto:").append(session.getEmail()).append("</D:href>\n");
                buffer.append("                </C:calendar-user-address-set>");
            }

            if (request.hasProperty("schedule-inbox-URL")) {
                buffer.append("                <C:schedule-inbox-URL>\n");
                buffer.append("                    <D:href>/inbox</D:href>\n");
                buffer.append("                </C:schedule-inbox-URL>");
            }

            if (request.hasProperty("schedule-outbox-URL")) {
                buffer.append("                <C:schedule-outbox-URL>\n");
                buffer.append("                    <D:href>/outbox</D:href>\n");
                buffer.append("                </C:schedule-outbox-URL>");
            }

            buffer.append("            </D:prop>\n");
            buffer.append("            <D:status>HTTP/1.1 200 OK</D:status>\n");
            buffer.append("        </D:propstat>\n");
            buffer.append("      </D:response>\n");
            buffer.append("</D:multistatus>\n");
            sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, "text/xml;charset=UTF-8", buffer.toString(), true);

        } else if ("PROPFIND".equals(command)
                && ("/calendar/".equals(path) || "/calendar".equals(path))
                && body != null) {
            CaldavRequest request = new CaldavRequest(body);

            StringBuilder buffer = new StringBuilder();
            buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            buffer.append("<D:multistatus xmlns:D=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n");
            buffer.append("    <D:response>\n");
            buffer.append("        <D:href>/calendar</D:href>\n");
            buffer.append("        <D:propstat>\n");
            buffer.append("            <D:prop>\n");

            if (request.hasProperty("resourcetype")) {
                buffer.append("                <D:resourcetype>\n");
                buffer.append("                    <D:collection/>\n");
                buffer.append("                    <C:calendar xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>\n");
                buffer.append("                </D:resourcetype>\n");
            }
            if (request.hasProperty("owner")) {
                buffer.append("                <D:owner>\n");
                buffer.append("                    <D:href>/user</D:href>\n");
                buffer.append("                </D:owner>\n");
            }
            if (request.hasProperty("getctag")) {
                buffer.append("                <CS:getctag>")
                        .append(base64Encode(session.getCalendarEtag()))
                        .append("</CS:getctag>\n");
            }
            buffer.append("            </D:prop>\n");
            buffer.append("            <D:status>HTTP/1.1 200 OK</D:status>\n");
            buffer.append("        </D:propstat>\n");
            buffer.append("    </D:response>\n");
            buffer.append("</D:multistatus>\n");

            HashMap<String, String> responseHeaders = new HashMap<String, String>();
            sendHttpResponse(HttpStatus.SC_MULTI_STATUS, responseHeaders, "text/xml;charset=UTF-8", buffer.toString(), true);

            // inbox is always empty
        } else if ("PROPFIND".equals(command)
                && ("/inbox/".equals(path) || "/inbox".equals(path))
                && body != null) {
            CaldavRequest request = new CaldavRequest(body);

            StringBuilder buffer = new StringBuilder();
            buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            buffer.append("<D:multistatus xmlns:D=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n");
            buffer.append("    <D:response>\n");
            buffer.append("        <D:href>/inbox</D:href>\n");
            buffer.append("        <D:propstat>\n");
            buffer.append("            <D:prop>\n");

            if (request.hasProperty("resourcetype")) {
                buffer.append("                <D:resourcetype>\n");
                buffer.append("                    <D:collection/>\n");
                buffer.append("                    <C:schedule-inbox xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>\n");
                buffer.append("                </D:resourcetype>\n");
            }
            if (request.hasProperty("getctag")) {
                buffer.append("                <CS:getctag>0</CS:getctag>\n");
            }
            buffer.append("            </D:prop>\n");
            buffer.append("            <D:status>HTTP/1.1 200 OK</D:status>\n");
            buffer.append("        </D:propstat>\n");
            buffer.append("    </D:response>\n");
            buffer.append("</D:multistatus>\n");

            HashMap<String, String> responseHeaders = new HashMap<String, String>();
            sendHttpResponse(HttpStatus.SC_MULTI_STATUS, responseHeaders, "text/xml;charset=UTF-8", buffer.toString(), true);
        } else if ("REPORT".equals(command)
                && ("/calendar/".equals(path) || "/calendar".equals(path))
                && depth == 1 && body != null) {
            CaldavRequest request = new CaldavRequest(body);

            List<ExchangeSession.Event> events;
            List<String> notFound = new ArrayList<String>();
            if (request.isMultiGet()) {
                events = new ArrayList<ExchangeSession.Event>();
                for (String href : request.getHrefs()) {
                    try {
                        events.add(session.getEvent(href.substring("/calendar/".length())));
                    } catch (HttpException e) {
                        notFound.add(href);
                    }
                }
            } else {
                events = session.getAllEvents();
            }

            StringBuilder buffer = new StringBuilder();
            buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<D:multistatus xmlns:D=\"DAV:\">\n");
            for (ExchangeSession.Event event : events) {

                String eventPath = event.getPath().replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
                buffer.append("<D:response>\n");
                buffer.append("        <D:href>/calendar").append(eventPath).append("</D:href>\n");
                buffer.append("        <D:propstat>\n");
                buffer.append("            <D:prop>\n");
                if (request.hasProperty("calendar-data")) {
                    String ics = event.getICS();
                    if (ics != null && ics.length() > 0) {
                        ics = ics.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
                        buffer.append("                <C:calendar-data xmlns:C=\"urn:ietf:params:xml:ns:caldav\"\n");
                        buffer.append("                    C:content-type=\"text/calendar\" C:version=\"2.0\">");
                        buffer.append(ics);
                        buffer.append("</C:calendar-data>\n");
                    }
                }
                if (request.hasProperty("getetag")) {
                    buffer.append("                <D:getetag>").append(event.getEtag()).append("</D:getetag>\n");
                }
                buffer.append("            </D:prop>\n");
                buffer.append("            <D:status>HTTP/1.1 200 OK</D:status>\n");
                buffer.append("        </D:propstat>\n");
                buffer.append("    </D:response>\n");

            }
            // send not found events errors
            for (String href : notFound) {
                buffer.append("<D:response>\n");
                buffer.append("        <D:href>").append(href).append("</D:href>\n");
                buffer.append("        <D:propstat>\n");
                buffer.append("            <D:status>HTTP/1.1 404 Not Found</D:status>\n");
                buffer.append("        </D:propstat>\n");
                buffer.append("    </D:response>\n");
            }
            buffer.append("</D:multistatus>");

            sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, "text/xml;charset=UTF-8", buffer.toString(), true);

        } else if ("REPORT".equals(command)
                && ("/inbox/".equals(path) || "/inbox".equals(path))) {
            // inbox is always empty
            StringBuilder buffer = new StringBuilder();
            buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<D:multistatus xmlns:D=\"DAV:\">\n");
            buffer.append("</D:multistatus>");

            sendHttpResponse(HttpStatus.SC_MULTI_STATUS, null, "text/xml;charset=UTF-8", buffer.toString(), true);

        } else if ("PUT".equals(command) && path.startsWith("/calendar/")) {
            String etag = headers.get("if-match");
            int status = session.createOrUpdateEvent(path.substring("/calendar/".length()), body, etag);
            sendHttpResponse(status, true);

        } else if ("POST".equals(command) && path.startsWith("/outbox")) {
            Map<String, String> valueMap = new HashMap<String, String>();
            Map<String, String> keyMap = new HashMap<String, String>();
            BufferedReader reader = new BufferedReader(new StringReader(body));
            String line;
            String key = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(" ") && "ATTENDEE".equals(key)) {
                    valueMap.put(key, valueMap.get(key) + line.substring(1));
                } else {
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
                    valueMap.put(key, value);
                    keyMap.put(key, fullkey);
                }
            }
            String response = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "   <C:schedule-response xmlns:D=\"DAV:\"\n" +
                    "                xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                    "   <C:response>\n" +
                    "     <C:recipient>\n" +
                    "       <D:href>" + valueMap.get("ATTENDEE") + "</D:href>\n" +
                    "     </C:recipient>\n" +
                    "     <C:request-status>2.0;Success</C:request-status>\n" +
                    "     <C:calendar-data>BEGIN:VCALENDAR\n" +
                    "VERSION:2.0\n" +
                    "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN\n" +
                    "METHOD:REPLY\n" +
                    "BEGIN:VFREEBUSY\n" +
                    "DTSTAMP:" + valueMap.get("DTSTAMP") + "\n" +
                    "ORGANIZER:" + valueMap.get("ORGANIZER") + "\n" +
                    "DTSTART:" + valueMap.get("DTSTART") + "\n" +
                    "DTEND:" + valueMap.get("DTEND") + "\n" +
                    "UID:" + valueMap.get("UID") + "\n" +
                    keyMap.get("ATTENDEE") + ";" + valueMap.get("ATTENDEE") + "\n" +
                    "FREEBUSY;FBTYPE=BUSY-UNAVAILABLE:" + session.getFreebusy(valueMap) + "\n" +
                    "END:VFREEBUSY\n" +
                    "END:VCALENDAR" +
                    "</C:calendar-data>\n" +
                    "   </C:response>\n" +
                    "   </C:schedule-response>";
            sendHttpResponse(HttpStatus.SC_OK, null, "text/xml;charset=UTF-8", response, true);

        } else if ("DELETE".equals(command) && path.startsWith("/calendar/")) {
            int status = session.deleteEvent(path.substring("/calendar/".length()));
            sendHttpResponse(status, true);
        } else {
            DavGatewayTray.error("Unsupported command: " + command + " " + path + " Depth: " + depth + "\n" + body);
            sendErr(HttpStatus.SC_BAD_REQUEST, "Unsupported command: " + command);
        }

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
        headers.put("DAV", "1, 2, 3, access-control, calendar-access, ticket, calendar-schedule");
        sendHttpResponse(HttpStatus.SC_OK, headers, null, null, true);
    }

    public void sendUnauthorized() throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("WWW-Authenticate", "Basic realm=\"" + Settings.getProperty("davmail.url") + "\"");
        sendHttpResponse(HttpStatus.SC_UNAUTHORIZED, headers, null, null, true);
    }

    public void sendHttpResponse(int status, boolean keepAlive) throws IOException {
        sendHttpResponse(status, null, null, null, keepAlive);
    }

    public void sendHttpResponse(int status, Map<String, String> headers, String contentType, String content, boolean keepAlive) throws IOException {
        sendClient("HTTP/1.1 " + status + " " + HttpStatus.getStatusText(status));
        sendClient("Server: DavMail Gateway");
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
        sendClient("Connection: " + (keepAlive ? "keep-alive" : "close"));
        closed = !keepAlive;
        if (content != null && content.length() > 0) {
            sendClient("Content-Length: " + content.getBytes("UTF-8").length);
        } else {
            sendClient("Content-Length: 0");
        }
        sendClient("");
        if (content != null && content.length() > 0) {
            sendClient(content.getBytes("UTF-8"));
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

    protected class CaldavRequest {
        protected HashSet<String> properties = new HashSet<String>();
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
                            hrefs.add(streamReader.getText());
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
}


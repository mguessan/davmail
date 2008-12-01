package davmail.exchange;

import davmail.Settings;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
import org.apache.webdav.lib.Property;
import org.apache.webdav.lib.ResponseEntity;
import org.apache.webdav.lib.WebdavResource;
import org.apache.webdav.lib.methods.SearchMethod;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.mail.internet.MimeUtility;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exchange session through Outlook Web Access (DAV)
 */
public class ExchangeSession {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

    /**
     * exchange message properties needed to rebuild mime message
     */
    protected static final Vector<String> MESSAGE_REQUEST_PROPERTIES = new Vector<String>();

    static {
        MESSAGE_REQUEST_PROPERTIES.add("DAV:uid");
        MESSAGE_REQUEST_PROPERTIES.add("urn:schemas:mailheader:content-class");

        // size
        MESSAGE_REQUEST_PROPERTIES.add("http://schemas.microsoft.com/mapi/proptag/x0e080003");
    }

    private static final int DEFAULT_KEEP_DELAY = 30;

    /**
     * Date parser from Exchange format
     */
    private final SimpleDateFormat dateParser;

    /**
     * Base Exchange URL
     */
    private String baseUrl;

    /**
     * Various standard mail boxes Urls
     */
    private String inboxUrl;
    private String deleteditemsUrl;
    private String sendmsgUrl;
    private String draftsUrl;
    private String calendarUrl;

    /**
     * Base user mailboxes path (used to select folder)
     */
    private String mailPath;
    private String currentFolderUrl;
    private WebdavResource wdr = null;

    /**
     * Create an exchange session for the given URL.
     * The session is not actually established until a call to login()
     */
    ExchangeSession() {
        // SimpleDateFormat are not thread safe, need to create one instance for
        // each session
        dateParser = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        dateParser.setTimeZone(new SimpleTimeZone(0, "GMT"));
        LOGGER.debug("Session " + this + " created");
    }

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url exchange base URL
     * @return true if basic authentication detected
     * @throws java.io.IOException unable to connect to exchange
     */
    protected boolean isBasicAuthentication(String url) throws IOException {
        return DavGatewayHttpClientFacade.getHttpStatus(url) == HttpStatus.SC_UNAUTHORIZED;
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param initmethod form body http method
     * @return logon method
     */
    protected PostMethod buildLogonMethod(HttpClient httpClient, HttpMethod initmethod) throws IOException {

        PostMethod logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(initmethod.getResponseBodyAsStream());
            List<TagNode> forms = node.getElementListByName("form", true);
            if (forms.size() == 1) {
                TagNode form = forms.get(0);
                String logonMethodPath = form.getAttributeByName("action");

                // allow relative URLs
                if (!logonMethodPath.startsWith("/")) {
                    String path = initmethod.getPath();
                    int end = path.lastIndexOf('/');
                    if (end >= 0) {
                        logonMethodPath = path.substring(0, end + 1) + logonMethodPath;
                    }
                }
                logonMethod = new PostMethod(logonMethodPath);

                List<TagNode> inputList = form.getElementListByName("input", true);
                for (TagNode input : inputList) {
                    String type = input.getAttributeByName("type");
                    String name = input.getAttributeByName("name");
                    String value = input.getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        logonMethod.addParameter(name, value);
                    }
                }
            } else {
                List<TagNode> frameList = node.getElementListByName("frame", true);
                if (frameList.size() == 1) {
                    String src = frameList.get(0).getAttributeByName("src");
                    if (src != null) {
                        LOGGER.debug("Frames detected in form page, try frame content");
                        initmethod.releaseConnection();
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing login form at " + initmethod.getURI());
        } finally {
            initmethod.releaseConnection();
        }

        if (logonMethod == null) {
            throw new IOException("Authentication form not found at " + initmethod.getURI() + ", retry with " + initmethod.getURI() + "exchange");
        }
        return logonMethod;
    }

    protected HttpMethod formLogin(HttpClient httpClient, HttpMethod initmethod, String userName, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");
        // build logon method with actual destination (baseUrl)
        HttpMethod logonMethod = buildLogonMethod(httpClient, initmethod);
        ((PostMethod) logonMethod).addParameter("username", userName);
        ((PostMethod) logonMethod).addParameter("password", password);
        logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
        return logonMethod;
    }

    protected String getMailPath(HttpMethod method) {
        String result = null;
        // get user mail URL from html body (multi frame)
        BufferedReader mainPageReader = null;
        try {
            mainPageReader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
            String line;
            // find base url
            final String BASE_HREF = "<base href=\"";
            //noinspection StatementWithEmptyBody
            while ((line = mainPageReader.readLine()) != null && line.toLowerCase().indexOf(BASE_HREF) == -1) {
            }
            if (line != null) {
                int start = line.toLowerCase().indexOf(BASE_HREF) + BASE_HREF.length();
                int end = line.indexOf("\"", start);
                String mailBoxBaseHref = line.substring(start, end);
                URL baseURL = new URL(mailBoxBaseHref);
                result = baseURL.getPath();
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing main page at " + method.getPath());
        } finally {
            if (mainPageReader != null) {
                try {
                    mainPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing main page at " + method.getPath());
                }
            }
            method.releaseConnection();
        }

        return result;
    }

    void login(String userName, String password) throws IOException {
        LOGGER.debug("Session " + this + " login");
        try {
            baseUrl = Settings.getProperty("davmail.url");

            boolean isBasicAuthentication = isBasicAuthentication(baseUrl);

            // get proxy configuration from setttings properties
            URL urlObject = new URL(baseUrl);
            // webdavresource is unable to create the correct url type
            HttpURL httpURL;
            if (baseUrl.startsWith("http://")) {
                httpURL = new HttpURL(userName, password,
                        urlObject.getHost(), urlObject.getPort());
            } else if (baseUrl.startsWith("https://")) {
                httpURL = new HttpsURL(userName, password,
                        urlObject.getHost(), urlObject.getPort());
            } else {
                throw new IllegalArgumentException("Invalid URL: " + baseUrl);
            }
            wdr = new WebdavResource(httpURL, WebdavResource.NOACTION, 0);

            // set httpclient timeout to 30 seconds
            //wdr.retrieveSessionInstance().setTimeout(30000);

            // get the internal HttpClient instance
            HttpClient httpClient = wdr.retrieveSessionInstance();

            DavGatewayHttpClientFacade.configureClient(httpClient);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            HttpMethod method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, baseUrl);

            if (!isBasicAuthentication) {
                method = formLogin(httpClient, method, userName, password);
                // reexecute method with new base URL
//                method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, baseUrl);
            }
            int status = method.getStatusCode();

            // User may be authenticated, get various session information
            if (status != HttpStatus.SC_OK) {
                HttpException ex = new HttpException();
                ex.setReasonCode(status);
                ex.setReason(method.getStatusText());
                throw ex;
            }
            // test form based authentication
            String queryString = method.getQueryString();
            if (queryString != null && queryString.contains("reason=2")) {
                method.releaseConnection();
                if (userName != null && userName.contains("\\")) {
                    throw new HttpException("Authentication failed: invalid user or password");
                } else {
                    throw new HttpException("Authentication failed: invalid user or password, " +
                            "retry with domain\\user");
                }
            }

            mailPath = getMailPath(method);

            if (mailPath == null) {
                throw new HttpException(baseUrl + " not found in body, authentication failed: password expired ?");
            }

            // got base http mailbox http url
            wdr.setPath(mailPath);

            // Retrieve inbox and trash URLs
            Vector<String> reqProps = new Vector<String>();
            reqProps.add("urn:schemas:httpmail:inbox");
            reqProps.add("urn:schemas:httpmail:deleteditems");
            reqProps.add("urn:schemas:httpmail:sendmsg");
            reqProps.add("urn:schemas:httpmail:drafts");
            reqProps.add("urn:schemas:httpmail:calendar");

            Enumeration foldersEnum = wdr.propfindMethod(0, reqProps);
            if (!foldersEnum.hasMoreElements()) {
                throw new IOException("Unable to get mail folders");
            }
            ResponseEntity inboxResponse = (ResponseEntity) foldersEnum.
                    nextElement();
            Enumeration inboxPropsEnum = inboxResponse.getProperties();
            if (!inboxPropsEnum.hasMoreElements()) {
                throw new IOException("Unable to get mail folders");
            }
            while (inboxPropsEnum.hasMoreElements()) {
                Property inboxProp = (Property) inboxPropsEnum.nextElement();
                if ("inbox".equals(inboxProp.getLocalName())) {
                    inboxUrl = URIUtil.decode(inboxProp.getPropertyAsString());
                }
                if ("deleteditems".equals(inboxProp.getLocalName())) {
                    deleteditemsUrl = URIUtil.decode(inboxProp.
                            getPropertyAsString());
                }
                if ("sendmsg".equals(inboxProp.getLocalName())) {
                    sendmsgUrl = URIUtil.decode(inboxProp.
                            getPropertyAsString());
                }
                if ("drafts".equals(inboxProp.getLocalName())) {
                    draftsUrl = URIUtil.decode(inboxProp.
                            getPropertyAsString());
                }
                if ("calendar".equals(inboxProp.getLocalName())) {
                    calendarUrl = URIUtil.decode(inboxProp.
                            getPropertyAsString());
                }
            }

            // set current folder to Inbox
            currentFolderUrl = inboxUrl;

            LOGGER.debug("Inbox URL : " + inboxUrl);
            LOGGER.debug("Trash URL : " + deleteditemsUrl);
            LOGGER.debug("Send URL : " + sendmsgUrl);
            LOGGER.debug("Drafts URL : " + draftsUrl);
            // TODO : sometimes path, sometimes Url ?
            deleteditemsUrl = URIUtil.getPath(deleteditemsUrl);
            wdr.setPath(URIUtil.getPath(inboxUrl));

        } catch (Exception exc) {
            StringBuffer message = new StringBuffer();
            message.append("DavMail login exception: ");
            if (exc.getMessage() != null) {
                message.append(exc.getMessage());
            } else if (exc instanceof HttpException) {
                message.append(((HttpException) exc).getReasonCode());
                String httpReason = ((HttpException) exc).getReason();
                if (httpReason != null) {
                    message.append(" ");
                    message.append(httpReason);
                }
            } else {
                message.append(exc);
            }
            try {
                message.append("\nWebdav status:");
                message.append(wdr.getStatusCode());

                String webdavStatusMessage = wdr.getStatusMessage();
                if (webdavStatusMessage != null) {
                    message.append(webdavStatusMessage);
                }
            } catch (Exception e) {
                LOGGER.error("Exception getting status from " + wdr);
            }

            LOGGER.error(message.toString());
            throw new IOException(message.toString());
        }
    }

    /**
     * Close session.
     * This will only close http client, not the actual Exchange session
     *
     * @throws IOException if unable to close Webdav context
     */
    public void close() throws IOException {
        LOGGER.debug("Session " + this + " closed");
        wdr.close();
    }

    /**
     * Create message in current folder
     *
     * @param subject     message subject line
     * @param messageBody mail body
     * @throws java.io.IOException when unable to create message
     */
    public void createMessage(String subject, String messageBody) throws IOException {
        createMessage(currentFolderUrl, subject, messageBody);
    }

    /**
     * Create message in specified folder.
     * Will overwrite an existing message with same subject in the same folder
     *
     * @param folderUrl   Exchange folder URL
     * @param subject     message subject line
     * @param messageBody mail body
     * @throws java.io.IOException when unable to create message
     */
    public void createMessage(String folderUrl, String subject, String messageBody) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(folderUrl + "/" + subject + ".EML");

        PutMethod putmethod = new PutMethod(messageUrl);
        // TODO : test, bcc ?
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        InputStream bodyStream = null;
        try {
            // use same encoding as client socket reader
            bodyStream = new ByteArrayInputStream(messageBody.getBytes());
            putmethod.setRequestBody(bodyStream);
            int code = wdr.retrieveSessionInstance().executeMethod(putmethod);

            if (code == HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Overwritten message " + messageUrl);
            } else if (code != HttpURLConnection.HTTP_CREATED) {
                throw new IOException("Unable to create message " + code + " " + putmethod.getStatusLine());
            }
        } finally {
            if (bodyStream != null) {
                try {
                    bodyStream.close();
                } catch (IOException e) {
                    LOGGER.error(e);
                }
            }
            putmethod.releaseConnection();
        }
    }

    protected Message buildMessage(ResponseEntity responseEntity) throws URIException {
        Message message = new Message();
        message.messageUrl = URIUtil.decode(responseEntity.getHref());
        Enumeration propertiesEnum = responseEntity.getProperties();
        while (propertiesEnum.hasMoreElements()) {
            Property prop = (Property) propertiesEnum.nextElement();
            String localName = prop.getLocalName();

            if ("x0e080003".equals(localName)) {
                message.size = Integer.parseInt(prop.getPropertyAsString());
            } else if ("uid".equals(localName)) {
                message.uid = prop.getPropertyAsString();
            } else if ("content-class".equals(prop.getLocalName())) {
                message.contentClass = prop.getPropertyAsString();
            }
        }

        return message;
    }

    public Message getMessage(String messageUrl) throws IOException {

        // TODO switch according to Log4J log level
        //wdr.setDebug(4);
        //wdr.propfindMethod(messageUrl, 0);
        Enumeration messageEnum = wdr.propfindMethod(messageUrl, 0, MESSAGE_REQUEST_PROPERTIES);
        //wdr.setDebug(0);

        // 201 created in some cases ?!?
        if ((wdr.getStatusCode() != HttpURLConnection.HTTP_OK && wdr.getStatusCode() != HttpURLConnection.HTTP_CREATED)
                || !messageEnum.hasMoreElements()) {
            throw new IOException("Unable to get message: " + wdr.getStatusCode()
                    + " " + wdr.getStatusMessage());
        }
        ResponseEntity entity = (ResponseEntity) messageEnum.nextElement();

        return buildMessage(entity);

    }

    public List<Message> getAllMessages() throws IOException {
        List<Message> messages = new ArrayList<Message>();
        //wdr.setDebug(4);
        //wdr.propfindMethod(currentFolderUrl, 1);
        // one level search
        Enumeration folderEnum = wdr.propfindMethod(currentFolderUrl, 1, MESSAGE_REQUEST_PROPERTIES);
        //wdr.setDebug(0);
        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();

            Message message = buildMessage(entity);
            if ("urn:content-classes:message".equals(message.contentClass) ||
                    "urn:content-classes:calendarmessage".equals(message.contentClass) ||
                    "urn:content-classes:recallmessage".equals(message.contentClass) ||
                    "urn:content-classes:appointment".equals(message.contentClass) ||
                    "urn:content-classes:dsn".equals(message.contentClass)) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * Delete oldest messages in trash.
     * keepDelay is the number of days to keep messages in trash before delete
     *
     * @throws IOException when unable to purge messages
     */
    public void purgeOldestTrashMessages() throws IOException {
        int keepDelay = Settings.getIntProperty("davmail.keepDelay");
        if (keepDelay == 0) {
            keepDelay = DEFAULT_KEEP_DELAY;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -keepDelay);
        LOGGER.debug("Delete messages in trash since " + cal.getTime());
        long keepTimestamp = cal.getTimeInMillis();

        Vector<String> deleteRequestProperties = new Vector<String>();
        deleteRequestProperties.add("DAV:getlastmodified");
        deleteRequestProperties.add("urn:schemas:mailheader:content-class");

        Enumeration folderEnum = wdr.propfindMethod(deleteditemsUrl, 1, deleteRequestProperties);
        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            String messageUrl = URIUtil.decode(entity.getHref());
            String lastModifiedString = null;
            String contentClass = null;
            Enumeration propertiesEnum = entity.getProperties();
            while (propertiesEnum.hasMoreElements()) {
                Property prop = (Property) propertiesEnum.nextElement();
                String localName = prop.getLocalName();
                if ("getlastmodified".equals(localName)) {
                    lastModifiedString = prop.getPropertyAsString();
                } else if ("content-class".equals(prop.getLocalName())) {
                    contentClass = prop.getPropertyAsString();
                }
            }
            if ("urn:content-classes:message".equals(contentClass) &&
                    lastModifiedString != null && lastModifiedString.length() > 0) {
                Date parsedDate;
                try {
                    parsedDate = dateParser.parse(lastModifiedString);
                    if (parsedDate.getTime() < keepTimestamp) {
                        LOGGER.debug("Delete " + messageUrl + " last modified " + parsedDate);
                        wdr.deleteMethod(messageUrl);
                    }
                } catch (ParseException e) {
                    LOGGER.warn("Invalid message modified date " + lastModifiedString + " on " + messageUrl);
                }
            }

        }
    }

    public void sendMessage(BufferedReader reader) throws IOException {
        String subject = "davmailtemp";
        String line = reader.readLine();
        StringBuffer mailBuffer = new StringBuffer();
        while (!".".equals(line)) {
            mailBuffer.append(line);
            mailBuffer.append("\n");
            line = reader.readLine();

            // patch thunderbird html in reply for correct outlook display
            if (line.startsWith("<head>")) {
                line += "\n  <style> blockquote { display: block; margin: 1em 0px; padding-left: 1em; border-left: solid; border-color: blue; border-width: thin;}</style>";
            }
            if (line.startsWith("Subject")) {
                subject = MimeUtility.decodeText(line.substring(8).trim());
                // '/' is invalid as message URL
                subject = subject.replaceAll("/", "_xF8FF_");
                // '?' is also invalid
                subject = subject.replaceAll("\\?", "");
                // TODO : test & in subject
            }
        }

        createMessage(draftsUrl, subject, mailBuffer.toString());

        // warning : slide library expects *unencoded* urls
        String tempUrl = draftsUrl + "/" + subject + ".eml";
        boolean sent = wdr.moveMethod(tempUrl, sendmsgUrl);
        if (!sent) {
            throw new IOException("Unable to send message: " + wdr.getStatusCode()
                    + " " + wdr.getStatusMessage());
        }

    }

    /**
     * Select current folder.
     * Folder name can be logical names INBOX or TRASH (translated to local names),
     * relative path to user base folder or absolute path.
     *
     * @param folderName folder name
     * @return Folder object
     * @throws IOException when unable to change folder
     */
    public Folder selectFolder(String folderName) throws IOException {
        Folder folder = new Folder();
        folder.folderUrl = null;
        if ("INBOX".equals(folderName)) {
            folder.folderUrl = inboxUrl;
        } else if ("TRASH".equals(folderName)) {
            folder.folderUrl = deleteditemsUrl;
            // absolute folder path
        } else if (folderName != null && folderName.startsWith("/")) {
            folder.folderUrl = folderName;
        } else {
            folder.folderUrl = mailPath + folderName;
        }

        Vector<String> reqProps = new Vector<String>();
        reqProps.add("urn:schemas:httpmail:unreadcount");
        reqProps.add("DAV:childcount");
        Enumeration folderEnum = wdr.propfindMethod(folder.folderUrl, 0, reqProps);

        if (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            Enumeration propertiesEnum = entity.getProperties();
            while (propertiesEnum.hasMoreElements()) {
                Property prop = (Property) propertiesEnum.nextElement();
                if ("unreadcount".equals(prop.getLocalName())) {
                    folder.unreadCount = Integer.parseInt(prop.getPropertyAsString());
                }
                if ("childcount".equals(prop.getLocalName())) {
                    folder.childCount = Integer.parseInt(prop.getPropertyAsString());
                }
            }

        } else {
            throw new IOException("Folder not found: " + folder.folderUrl);
        }
        currentFolderUrl = folder.folderUrl;
        return folder;
    }

    public class Folder {
        public String folderUrl;
        public int childCount;
        public int unreadCount;
    }

    public class Message {
        public String messageUrl;
        public String uid;
        public int size;
        public String contentClass;

        public void write(OutputStream os) throws IOException {
            HttpMethod method = null;
            BufferedReader reader = null;
            try {
                method = new GetMethod(URIUtil.encodePath(messageUrl));
                method.setRequestHeader("Content-Type", "text/xml; charset=utf-8");
                method.setRequestHeader("Translate", "f");
                wdr.retrieveSessionInstance().executeMethod(method);

                boolean inHTML = false;

                reader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
                OutputStreamWriter isoWriter = new OutputStreamWriter(os);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (".".equals(line)) {
                        line = "..";
                        // patch text/calendar to include utf-8 encoding
                    } else if ("Content-Type: text/calendar;".equals(line)) {
                        StringBuffer headerBuffer = new StringBuffer();
                        headerBuffer.append(line);
                        while ((line = reader.readLine()) != null && line.startsWith("\t")) {
                            headerBuffer.append((char) 13);
                            headerBuffer.append((char) 10);
                            headerBuffer.append(line);
                        }
                        if (headerBuffer.indexOf("charset") < 0) {
                            headerBuffer.append(";charset=utf-8");
                        }
                        headerBuffer.append((char) 13);
                        headerBuffer.append((char) 10);
                        headerBuffer.append(line);
                        line = headerBuffer.toString();
                        // detect html body to patch Exchange html body
                    } else if (line.startsWith("<html")) {
                        inHTML = true;
                    } else if (inHTML && "</html>".equals(line)) {
                        inHTML = false;
                    }
                    if (inHTML) {
                        //    line = line.replaceAll("&#8217;", "'");
                        //    line = line.replaceAll("&#8230;", "...");
                    }
                    isoWriter.write(line);
                    isoWriter.write((char) 13);
                    isoWriter.write((char) 10);
                }
                isoWriter.flush();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        LOGGER.warn("Error closing message input stream", e);
                    }
                }
                if (method != null) {
                    method.releaseConnection();
                }
            }
        }

        public void delete() throws IOException {
            String destination = deleteditemsUrl + messageUrl.substring(messageUrl.lastIndexOf("/"));
            LOGGER.debug("Deleting : " + messageUrl + " to " + destination);

            wdr.moveMethod(messageUrl, destination);
            if (wdr.getStatusCode() == HttpURLConnection.HTTP_PRECON_FAILED) {
                int count = 2;
                // name conflict, try another name
                while (wdr.getStatusCode() == HttpURLConnection.HTTP_PRECON_FAILED) {
                    wdr.moveMethod(messageUrl, destination.substring(0, destination.lastIndexOf('.')) + "-" + count++ + ".eml");
                }
            }

            LOGGER.debug("Deleted to :" + destination + " " + wdr.getStatusCode() + " " + wdr.getStatusMessage());
        }

    }

    public WebdavResource getWebDavResource() throws IOException {
        return wdr;
    }

    public class Event {
        protected String href;
        protected String etag;

        public String getICS() throws IOException {
            DavGatewayTray.debug("Get event: " + href);
            StringBuilder buffer = new StringBuilder();
            GetMethod method = new GetMethod(URIUtil.encodePath(href));
            method.setRequestHeader("Content-Type", "text/xml; charset=utf-8");
            method.setRequestHeader("Translate", "f");
            BufferedReader eventReader = null;
            try {
                int status = wdr.retrieveSessionInstance().executeMethod(method);
                if (status != HttpStatus.SC_OK) {
                    DavGatewayTray.warn("Unable to get event at " + href + " status: " + status);
                }
                eventReader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8"));
                String line;
                boolean inbody = false;
                while ((line = eventReader.readLine()) != null) {
                    if ("BEGIN:VCALENDAR".equals(line)) {
                        inbody = true;
                    }
                    if (inbody) {
                        buffer.append(line);
                        buffer.append((char) 13);
                        buffer.append((char) 10);
                    }
                    if ("END:VCALENDAR".equals(line)) {
                        inbody = false;
                    }
                }

            } finally {
                if (eventReader != null) {
                    try {
                        eventReader.close();
                    } catch (IOException e) {
                        LOGGER.error("Error parsing event at " + method.getPath());
                    }
                }
                method.releaseConnection();
            }
            return buffer.toString();
        }

        public String getPath() throws URIException {
            return href.substring(calendarUrl.length());
        }

        public String getEtag() {
            return etag;
        }
    }

    public List<Event> getAllEvents() throws IOException {
        List<Event> events = new ArrayList<Event>();
        String searchRequest = "<?xml version=\"1.0\"?>\n" +
                "<d:searchrequest xmlns:d=\"DAV:\">\n" +
                "        <d:sql> Select \"DAV:getetag\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + calendarUrl + "\"')\n" +
                "                WHERE NOT \"urn:schemas:calendar:instancetype\" = 1\n" +
                "                AND \"DAV:contentclass\" = 'urn:content-classes:appointment'\n" +
                "                AND \"urn:schemas:calendar:dtstart\" > '2008/11/01 00:00:00'\n" +
                "                ORDER BY \"urn:schemas:calendar:dtstart\" ASC\n" +
                "         </d:sql>\n" +
                "</d:searchrequest>";
        SearchMethod searchMethod = new SearchMethod(calendarUrl, searchRequest);
        try {
            int status = wdr.retrieveSessionInstance().executeMethod(searchMethod);
            // Also accept OK sent by buggy servers.
            if (status != HttpStatus.SC_MULTI_STATUS
                    && status != HttpStatus.SC_OK) {
                HttpException ex = new HttpException();
                ex.setReasonCode(status);
                throw ex;
            }

            Enumeration calendarEnum = searchMethod.getResponses();
            while (calendarEnum.hasMoreElements()) {
                ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                        nextElement();
                String href = calendarResponse.getHref();
                Event event = new Event();
                event.href = URIUtil.decode(href);
                String contentclass = null;
                Enumeration propertiesEnumeration = calendarResponse.getProperties();
                while (propertiesEnumeration.hasMoreElements()) {
                    Property property = (Property) propertiesEnumeration.nextElement();
                    if ("getetag".equals(property.getLocalName())) {
                        event.etag = property.getPropertyAsString();
                    }
                    /*
                    if ("contentclass".equals(property.getLocalName())) {
                        contentclass = property.getPropertyAsString();
                    }
                    */
                }
                // filter folder and non appointment objects
                //if ("urn:content-classes:appointment".equals(contentclass)) {
                events.add(event);
                //}
            }
        } finally {
            searchMethod.releaseConnection();
        }
        return events;
    }

    public Event getEvent(String path) throws IOException {
        // TODO : refactor with getAllEvents
        Event event = new Event();
        final Vector<String> EVENT_REQUEST_PROPERTIES = new Vector<String>();
        EVENT_REQUEST_PROPERTIES.add("DAV:getetag");

        //wdr.setDebug(4);
        Enumeration calendarEnum = wdr.propfindMethod(calendarUrl + "/" + path, 0, EVENT_REQUEST_PROPERTIES);
        //wdr.setDebug(0);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar event");
        }
        ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                nextElement();
        String href = calendarResponse.getHref();
        event.href = URIUtil.decode(href);
        Enumeration propertiesEnumeration = calendarResponse.getProperties();
        while (propertiesEnumeration.hasMoreElements()) {
            Property property = (Property) propertiesEnumeration.nextElement();
            if ("getetag".equals(property.getLocalName())) {
                event.etag = property.getPropertyAsString();
            }
        }
        return event;
    }

    public int createOrUpdateEvent(String path, String icsBody, String etag) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(calendarUrl + "/" + URIUtil.decode(path));
        String uid = path.substring(0, path.lastIndexOf("."));
        PutMethod putmethod = new PutMethod(messageUrl);
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Overwrite", "f");
        if (etag != null) {
            // TODO
            putmethod.setRequestHeader("If-Match", etag);
        }
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        StringBuilder body = new StringBuilder();
        body.append("Content-Transfer-Encoding: 7bit\n" +
                "Content-class: urn:content-classes:appointment\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: multipart/alternative;\n" +
                "\tboundary=\"----=_NextPart_" + uid + "\"\n" +
                "\n" +
                "This is a multi-part message in MIME format.\n" +
                "\n" +
                "------=_NextPart_" + uid + "\n" +
                "Content-class: urn:content-classes:appointment\n" +
                "Content-Type: text/calendar;\n" +
                "\tmethod=REQUEST;\n" +
                "\tcharset=\"utf-8\"\n" +
                "Content-Transfer-Encoding: 8bit\n\n");
        body.append(new String(icsBody.getBytes("UTF-8"), "ISO-8859-1"));
        body.append("------=_NextPart_" + uid + "--\n");
        putmethod.setRequestBody(body.toString());
        int status;
        try {
            status = wdr.retrieveSessionInstance().executeMethod(putmethod);

            if (status == HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Overwritten event " + messageUrl);
            } else if (status != HttpURLConnection.HTTP_CREATED) {
                throw new IOException("Unable to create message " + status + " " + putmethod.getStatusLine());
            }
        } finally {
            putmethod.releaseConnection();
        }
        return status;
    }

    public int deleteEvent(String path) throws IOException {
        wdr.deleteMethod(calendarUrl + "/" + URIUtil.decode(path));
        int status = wdr.getStatusCode();
        return status;
    }

    public String getCalendarEtag() throws IOException {
        String etag = null;
        //wdr.setDebug(4);
        Enumeration calendarEnum = wdr.propfindMethod(calendarUrl, 0);
        //wdr.setDebug(0);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar object");
        }
        while (calendarEnum.hasMoreElements()) {
            ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                    nextElement();
            Enumeration propertiesEnumeration = calendarResponse.getProperties();
            while (propertiesEnumeration.hasMoreElements()) {
                Property property = (Property) propertiesEnumeration.nextElement();
                if ("http://schemas.microsoft.com/repl/".equals(property.getNamespaceURI())
                        && "contenttag".equals(property.getLocalName())) {
                    etag = property.getPropertyAsString();
                }
            }
        }
        if (etag == null) {
            throw new IOException("Unable to get calendar etag");
        }
        return etag;
    }

    /**
     * Get current Exchange user name
     *
     * @return user name
     * @throws java.io.IOException on error
     */
    public String getUserName() throws IOException {
        int index = mailPath.lastIndexOf("/", mailPath.length() - 2);
        if (index >= 0 && mailPath.endsWith("/")) {
            return mailPath.substring(index + 1, mailPath.length() - 1);
        } else {
            throw new IOException("Invalid mail path: " + mailPath);
        }
    }

    /**
     * Get current user email
     *
     * @return user email
     * @throws java.io.IOException on error
     */
    public String getEmail() throws IOException {
        String email = null;
        GetMethod getMethod = new GetMethod("/public/?Cmd=galfind&AN=" + getUserName());
        XMLStreamReader reader = null;
        try {
            int status = wdr.retrieveSessionInstance().executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException("Unable to get user email from: " + getMethod.getPath());
            }
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);

            reader = inputFactory.createXMLStreamReader(getMethod.getResponseBodyAsStream());
            boolean inEM = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "EM".equals(reader.getLocalName())) {
                    inEM = true;
                } else if (event == XMLStreamConstants.CHARACTERS && inEM) {
                    email = reader.getText();
                    inEM = false;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                LOGGER.error(e);
            }
            getMethod.releaseConnection();
        }
        if (email == null) {
            throw new IOException("Unable to get user email from: " + getMethod.getPath());
        }

        return email;
    }

    public String getFreebusy(Map<String, String> valueMap) throws IOException {
        String result = null;

        String startDateValue = valueMap.get("DTSTART");
        String endDateValue = valueMap.get("DTEND");
        String attendee = valueMap.get("ATTENDEE");
        if (attendee.startsWith("mailto:")) {
            attendee = attendee.substring("mailto:".length());
        }
        int interval = 30;

        SimpleDateFormat icalParser = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        icalParser.setTimeZone(new SimpleTimeZone(0, "GMT"));

        SimpleDateFormat shortIcalParser = new SimpleDateFormat("yyyyMMdd");
        shortIcalParser.setTimeZone(new SimpleTimeZone(0, "GMT"));

        SimpleDateFormat owaFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        owaFormatter.setTimeZone(new SimpleTimeZone(0, "GMT"));

        String url = null;
        Date startDate = null;
        Date endDate = null;
        try {
            if (startDateValue.length() == 8) {
                startDate = shortIcalParser.parse(startDateValue);
            } else {
                startDate = icalParser.parse(startDateValue);
            }
            if (endDateValue.length() == 8) {
                endDate = shortIcalParser.parse(endDateValue);
            } else {
                endDate = icalParser.parse(endDateValue);
            }
            url = "/public/?cmd=freebusy" +
                    "&start=" + owaFormatter.format(startDate) +
                    "&end=" + owaFormatter.format(endDate) +
                    "&interval=" + interval +
                    "&u=SMTP:" + attendee;
        } catch (ParseException e) {
            throw new IOException(e.getMessage());
        }

        GetMethod getMethod = new GetMethod(url);
        getMethod.setRequestHeader("Content-Type", "text/xml");

        try {
            int status = wdr.retrieveSessionInstance().executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException("Unable to get free-busy from: " + getMethod.getPath());
            }
            // TODO : parse
            String body = getMethod.getResponseBodyAsString();
            int startIndex = body.lastIndexOf("<a:fbdata>");
            int endIndex = body.lastIndexOf("</a:fbdata>");
            if (startIndex >= 0 && endIndex >= 0) {
                String fbdata = body.substring(startIndex + "<a:fbdata>".length(), endIndex);
                Calendar currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                currentCal.setTime(startDate);

                StringBuilder busyBuffer = new StringBuilder();
                boolean isBusy = fbdata.charAt(0) != '0';
                if (isBusy) {
                    busyBuffer.append(icalParser.format(currentCal.getTime()));
                }
                for (int i = 1; i < fbdata.length(); i++) {
                    currentCal.add(Calendar.MINUTE, interval);
                    if (isBusy && fbdata.charAt(i) == '0') {
                        // busy -> non busy
                        busyBuffer.append('/').append(icalParser.format(currentCal.getTime()));
                    } else if (!isBusy && fbdata.charAt(i) != '0') {
                        // non busy -> busy
                        if (busyBuffer.length() > 0) {
                            busyBuffer.append(',');
                        }
                        busyBuffer.append(icalParser.format(currentCal.getTime()));
                    }
                    isBusy = fbdata.charAt(i) != '0';
                }
                result = busyBuffer.toString();
            }
        } finally {
            getMethod.releaseConnection();
        }
        if (result == null) {
            throw new IOException("Unable to get user email from: " + getMethod.getPath());
        }

        return result;
    }

}

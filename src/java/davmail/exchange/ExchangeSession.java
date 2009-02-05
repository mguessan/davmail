package davmail.exchange;

import davmail.Settings;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.Base64;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
import org.apache.webdav.lib.Property;
import org.apache.webdav.lib.ResponseEntity;
import org.apache.webdav.lib.WebdavResource;
import org.apache.webdav.lib.methods.MoveMethod;
import org.apache.webdav.lib.methods.PropPatchMethod;
import org.apache.webdav.lib.methods.SearchMethod;
import org.apache.webdav.lib.methods.CopyMethod;
import org.htmlcleaner.CommentToken;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

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

    public static final SimpleTimeZone GMT_TIMEZONE = new SimpleTimeZone(0, "GMT");

    /**
     * exchange message properties needed to rebuild mime message
     */
    protected static final Vector<String> MESSAGE_REQUEST_PROPERTIES = new Vector<String>();

    static {
        MESSAGE_REQUEST_PROPERTIES.add("DAV:uid");
        // size
        MESSAGE_REQUEST_PROPERTIES.add("http://schemas.microsoft.com/mapi/proptag/x0e080003");
    }

    protected static final Vector<String> EVENT_REQUEST_PROPERTIES = new Vector<String>();

    static {
        EVENT_REQUEST_PROPERTIES.add("DAV:getetag");
    }

    protected static final Vector<String> WELL_KNOWN_FOLDERS = new Vector<String>();

    static {
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:inbox");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:deleteditems");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:sentitems");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:sendmsg");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:drafts");
        WELL_KNOWN_FOLDERS.add("urn:schemas:httpmail:calendar");
    }

    public static final HashMap<String, String> PRIORITIES = new HashMap<String, String>();

    static {
        PRIORITIES.put("-2", "5 (Lowest)");
        PRIORITIES.put("-1", "4 (Low)");
        PRIORITIES.put("1", "2 (High)");
        PRIORITIES.put("2", "1 (Highest)");
    }

    /**
     * Date parser/formatter from Exchange format
     */
    private final SimpleDateFormat dateFormatter;
    private final SimpleDateFormat dateParser;


    /**
     * Various standard mail boxes Urls
     */
    private String inboxUrl;
    private String deleteditemsUrl;
    private String sentitemsUrl;
    private String sendmsgUrl;
    private String draftsUrl;
    private String calendarUrl;

    /**
     * Base user mailboxes path (used to select folder)
     */
    private String mailPath;
    private String email;
    private WebdavResource wdr = null;

    private final ExchangeSessionFactory.PoolKey poolKey;

    private boolean disableGalLookup = false;

    ExchangeSessionFactory.PoolKey getPoolKey() {
        return poolKey;
    }

    /**
     * Create an exchange session for the given URL.
     * The session is not actually established until a call to login()
     *
     * @param poolKey session pool key
     */
    ExchangeSession(ExchangeSessionFactory.PoolKey poolKey) {
        this.poolKey = poolKey;
        // SimpleDateFormat are not thread safe, need to create one instance for
        // each session
        dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        dateFormatter.setTimeZone(GMT_TIMEZONE);

        dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        dateParser.setTimeZone(GMT_TIMEZONE);

        LOGGER.debug("Session " + this + " created");
    }

    public boolean isExpired() {
        boolean isExpired = false;
        try {
            wdr.propfindMethod(0);
            int status = wdr.getStatusCode();

            if (status != HttpStatus.SC_MULTI_STATUS) {
                isExpired = true;
            }

        } catch (IOException e) {
            isExpired = true;
        }

        return isExpired;
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

    protected String getAbsolutePath(HttpMethod method, String path) {
        String absolutePath = path;
        // allow relative path
        if (!absolutePath.startsWith("/")) {
            String currentPath = method.getPath();
            int end = currentPath.lastIndexOf('/');
            if (end >= 0) {
                absolutePath = currentPath.substring(0, end + 1) + absolutePath;
            }
        }
        return absolutePath;
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param httpClient httpClient instance
     * @param initmethod form body http method
     * @return logon method
     * @throws java.io.IOException on error
     */
    protected PostMethod buildLogonMethod(HttpClient httpClient, HttpMethod initmethod) throws IOException {

        PostMethod logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(initmethod.getResponseBodyAsStream());
            List forms = node.getElementListByName("form", true);
            if (forms.size() == 1) {
                TagNode form = (TagNode) forms.get(0);
                String logonMethodPath = form.getAttributeByName("action");

                logonMethod = new PostMethod(getAbsolutePath(initmethod, logonMethodPath));

                List inputList = form.getElementListByName("input", true);
                for (Object input : inputList) {
                    String type = ((TagNode) input).getAttributeByName("type");
                    String name = ((TagNode) input).getAttributeByName("name");
                    String value = ((TagNode) input).getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        logonMethod.addParameter(name, value);
                    }
                }
            } else {
                List frameList = node.getElementListByName("frame", true);
                if (frameList.size() == 1) {
                    String src = ((TagNode) frameList.get(0)).getAttributeByName("src");
                    if (src != null) {
                        LOGGER.debug("Frames detected in form page, try frame content");
                        initmethod.releaseConnection();
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    }
                } else {
                    // another failover for script based logon forms (Exchange 2007)
                    List scriptList = node.getElementListByName("script", true);
                    for (Object script : scriptList) {
                        List contents = ((TagNode) script).getChildren();
                        for (Object content : contents) {
                            if (content instanceof CommentToken) {
                                String scriptValue = ((CommentToken) content).getCommentedContent();
                                int a_sUrlIndex = scriptValue.indexOf("var a_sUrl = \"");
                                int a_sLgnIndex = scriptValue.indexOf("var a_sLgn = \"");
                                if (a_sUrlIndex >= 0 && a_sLgnIndex >= 0) {
                                    a_sUrlIndex += "var a_sUrl = \"".length();
                                    a_sLgnIndex += "var a_sLgn = \"".length();
                                    int a_sUrlEndIndex = scriptValue.indexOf("\"", a_sUrlIndex);
                                    int a_sLgnEndIndex = scriptValue.indexOf("\"", a_sLgnIndex);
                                    if (a_sUrlEndIndex >= 0 && a_sLgnEndIndex >= 0) {
                                        String src = getAbsolutePath(initmethod,
                                                scriptValue.substring(a_sLgnIndex, a_sLgnEndIndex) +
                                                        scriptValue.substring(a_sUrlIndex, a_sUrlEndIndex));
                                        LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                                    }
                                } else {
                                    a_sLgnIndex = scriptValue.indexOf("var a_sLgnQS = \"");
                                    if (a_sUrlIndex >= 0 && a_sLgnIndex >= 0) {
                                        a_sUrlIndex += "var a_sUrl = \"".length();
                                        a_sLgnIndex += "var a_sLgnQS = \"".length();
                                        int a_sUrlEndIndex = scriptValue.indexOf("\"", a_sUrlIndex);
                                        int a_sLgnEndIndex = scriptValue.indexOf("\"", a_sLgnIndex);
                                        if (a_sUrlEndIndex >= 0 && a_sLgnEndIndex >= 0) {
                                            String src = initmethod.getPath() +
                                                    scriptValue.substring(a_sLgnIndex, a_sLgnEndIndex) +
                                                    scriptValue.substring(a_sUrlIndex, a_sUrlEndIndex);
                                            LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                            HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                            logonMethod = buildLogonMethod(httpClient, newInitMethod);
                                        }
                                    }
                                }
                            }
                        }


                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing login form at " + initmethod.getURI());
        } finally {
            initmethod.releaseConnection();
        }

        if (logonMethod == null) {
            throw new IOException("Authentication form not found at " + initmethod.getURI());
        }
        return logonMethod;
    }

    protected HttpMethod formLogin(HttpClient httpClient, HttpMethod initmethod, String userName, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");

        HttpMethod logonMethod = buildLogonMethod(httpClient, initmethod);
        ((PostMethod) logonMethod).addParameter("username", userName);
        ((PostMethod) logonMethod).addParameter("password", password);
        logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
        return logonMethod;
    }

    protected void buildMailPath(HttpMethod method) throws HttpException {
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
                mailPath = baseURL.getPath();
                LOGGER.debug("Base href found in body, mailPath is " + mailPath);
                buildEmail();
                LOGGER.debug("Current user email is " + email);
            } else {
                // failover for Exchange 2007 : build standard mailbox link with email
                buildEmail();
                mailPath = "/exchange/" + email + "/";
                LOGGER.debug("Current user email is " + email + ", mailPath is " + mailPath);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing main page at " + method.getPath(), e);
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

        if (mailPath == null) {
            throw new AuthenticationException("Unable to build mail path, authentication failed: password expired ?");
        }
        if (email == null) {
            throw new AuthenticationException("Unable to get email, authentication failed: password expired ?");
        }
    }

    void login() throws IOException {
        LOGGER.debug("Session " + this + " login");
        try {
            boolean isBasicAuthentication = isBasicAuthentication(poolKey.url);

            // get proxy configuration from setttings properties
            URL urlObject = new URL(poolKey.url);
            // webdavresource is unable to create the correct url type
            HttpURL httpURL;
            if (poolKey.url.startsWith("http://")) {
                httpURL = new HttpURL(poolKey.userName, poolKey.password,
                        urlObject.getHost(), urlObject.getPort());
            } else if (poolKey.url.startsWith("https://")) {
                httpURL = new HttpsURL(poolKey.userName, poolKey.password,
                        urlObject.getHost(), urlObject.getPort());
            } else {
                throw new IllegalArgumentException("Invalid URL: " + poolKey.url);
            }
            wdr = new WebdavResource(httpURL, WebdavResource.NOACTION, 0);

            // get the internal HttpClient instance
            HttpClient httpClient = wdr.retrieveSessionInstance();

            DavGatewayHttpClientFacade.configureClient(httpClient);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            HttpMethod method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, poolKey.url);

            if (!isBasicAuthentication) {
                method = formLogin(httpClient, method, poolKey.userName, poolKey.password);
            }
            int status = method.getStatusCode();

            if (status == HttpStatus.SC_UNAUTHORIZED) {
                throw new AuthenticationException("Authentication failed: invalid user or password");
            } else if (status != HttpStatus.SC_OK) {
                HttpException ex = new HttpException();
                ex.setReasonCode(status);
                ex.setReason(method.getStatusText());
                throw ex;
            }
            // test form based authentication
            String queryString = method.getQueryString();
            if (queryString != null && queryString.contains("reason=2")) {
                method.releaseConnection();
                if (poolKey.userName != null && poolKey.userName.contains("\\")) {
                    throw new AuthenticationException("Authentication failed: invalid user or password");
                } else {
                    throw new AuthenticationException("Authentication failed: invalid user or password, " +
                            "retry with domain\\user");
                }
            }

            buildMailPath(method);

            // got base http mailbox http url
            wdr.setPath(mailPath);
            getWellKnownFolders();
            // set current folder to Inbox
            wdr.setPath(URIUtil.getPath(inboxUrl));

        } catch (AuthenticationException exc) {
            LOGGER.error(exc.toString());
            throw exc;
        } catch (IOException exc) {
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

            LOGGER.error(message.toString());
            throw new IOException(message.toString());
        }
    }

    protected void getWellKnownFolders() throws IOException {
        // Retrieve well known URLs
        Enumeration foldersEnum = wdr.propfindMethod(0, WELL_KNOWN_FOLDERS);
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
                deleteditemsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("sentitems".equals(inboxProp.getLocalName())) {
                sentitemsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("sendmsg".equals(inboxProp.getLocalName())) {
                sendmsgUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("drafts".equals(inboxProp.getLocalName())) {
                draftsUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
            if ("calendar".equals(inboxProp.getLocalName())) {
                calendarUrl = URIUtil.decode(inboxProp.getPropertyAsString());
            }
        }
        LOGGER.debug("Inbox URL : " + inboxUrl +
                " Trash URL : " + deleteditemsUrl +
                " Sent URL : " + sentitemsUrl +
                " Send URL : " + sendmsgUrl +
                " Drafts URL : " + draftsUrl +
                " Calendar URL : " + calendarUrl
        );
    }

    /**
     * Create message in specified folder.
     * Will overwrite an existing message with same subject in the same folder
     *
     * @param folderUrl      Exchange folder URL
     * @param messageName    message name
     * @param properties     message properties (flags)
     * @param messageBody    mail body
     * @throws java.io.IOException when unable to create message
     */
    public void createMessage(String folderUrl, String messageName, HashMap<String, String> properties, String messageBody) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(folderUrl + "/" + messageName + ".EML");
        PropPatchMethod patchMethod;
        // create the message first as draft
        if (properties.containsKey("draft")) {
            patchMethod = new PropPatchMethod(messageUrl);
            try {
                // update message with blind carbon copy and other flags
                addProperties(patchMethod, properties);
                int statusCode = wdr.retrieveSessionInstance().executeMethod(patchMethod);
                if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                    throw new IOException("Unable to create message " + messageUrl + ": " + statusCode + " " + patchMethod.getStatusLine());
                }

            } finally {
                patchMethod.releaseConnection();
            }
        }

        PutMethod putmethod = new PutMethod(messageUrl);
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        InputStream bodyStream = null;
        try {
            // use same encoding as client socket reader
            bodyStream = new ByteArrayInputStream(messageBody.getBytes());
            putmethod.setRequestBody(bodyStream);
            int code = wdr.retrieveSessionInstance().executeMethod(putmethod);

            if (code != HttpStatus.SC_OK && code != HttpStatus.SC_CREATED) {
                throw new IOException("Unable to create message " + messageUrl + ": " + code + " " + putmethod.getStatusLine());
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

        // add bcc and other properties
        if (properties.size() > 0) {
            patchMethod = new PropPatchMethod(messageUrl);
            try {
                // update message with blind carbon copy and other flags
                addProperties(patchMethod, properties);
                int statusCode = wdr.retrieveSessionInstance().executeMethod(patchMethod);
                if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                    throw new IOException("Unable to patch message " + messageUrl + ": " + statusCode + " " + patchMethod.getStatusLine());
                }

            } finally {
                patchMethod.releaseConnection();
            }
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
            } else if ("read".equals(localName)) {
                message.read = "1".equals(prop.getPropertyAsString());
            } else if ("x10830003".equals(localName)) {
                message.junk = "1".equals(prop.getPropertyAsString());
            } else if ("x10900003".equals(localName)) {
                message.flagged = "2".equals(prop.getPropertyAsString());
            } else if ("x0E070003".equals(localName)) {
                message.draft = "9".equals(prop.getPropertyAsString());
            } else if ("x10810003".equals(localName)) {
                message.answered = "102".equals(prop.getPropertyAsString()) || "103".equals(prop.getPropertyAsString());
                message.forwarded = "104".equals(prop.getPropertyAsString());
            } else if ("isdeleted".equals(localName)) {
                message.deleted = "1".equals(prop.getPropertyAsString());
            } else if ("message-id".equals(prop.getLocalName())) {
                message.messageId = prop.getPropertyAsString();
                if (message.messageId.startsWith("<") && message.messageId.endsWith(">")) {
                    message.messageId = message.messageId.substring(1, message.messageId.length() - 1);
                }
            }
        }

        return message;
    }

    public Message getMessage(String messageUrl) throws IOException {

        Enumeration messageEnum = wdr.propfindMethod(messageUrl, 0, MESSAGE_REQUEST_PROPERTIES);

        if ((wdr.getStatusCode() != HttpURLConnection.HTTP_OK)
                || !messageEnum.hasMoreElements()) {
            throw new IOException("Unable to get message: " + wdr.getStatusCode()
                    + " " + wdr.getStatusMessage());
        }
        ResponseEntity entity = (ResponseEntity) messageEnum.nextElement();

        return buildMessage(entity);

    }

    protected void addProperties(PropPatchMethod patchMethod, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if ("read".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("read", entry.getValue(), "e", "urn:schemas:httpmail:");
            } else if ("junk".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10830003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("flagged".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10900003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("answered".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10810003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
                if ("102".equals(entry.getValue())) {
                    patchMethod.addPropertyToSet("x10800003", "261", "f", "http://schemas.microsoft.com/mapi/proptag/");
                }
            } else if ("forwarded".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x10810003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
                if ("104".equals(entry.getValue())) {
                    patchMethod.addPropertyToSet("x10800003", "262", "f", "http://schemas.microsoft.com/mapi/proptag/");
                }
            } else if ("bcc".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("bcc", entry.getValue(), "b", "urn:schemas:mailheader:");
            } else if ("draft".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("x0E070003", entry.getValue(), "f", "http://schemas.microsoft.com/mapi/proptag/");
            } else if ("deleted".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("isdeleted", entry.getValue(), "d", "DAV:");
            } else if ("datereceived".equals(entry.getKey())) {
                patchMethod.addPropertyToSet("datereceived", entry.getValue(), "e", "urn:schemas:httpmail:");
            }
        }
    }

    public void updateMessage(Message message, Map<String, String> properties) throws IOException {
        PropPatchMethod patchMethod = new PropPatchMethod(URIUtil.encodePathQuery(message.messageUrl));
        try {
            addProperties(patchMethod, properties);
            int statusCode = wdr.retrieveSessionInstance().executeMethod(patchMethod);
            if (statusCode != HttpStatus.SC_MULTI_STATUS) {
                throw new IOException("Unable to update message properties");
            }

        } finally {
            patchMethod.releaseConnection();
        }
    }

    public MessageList getAllMessages(String folderName) throws IOException {
        String folderUrl = getFolderPath(folderName);
        MessageList messages = new MessageList();
        String searchRequest = "Select \"DAV:uid\", \"http://schemas.microsoft.com/mapi/proptag/x0e080003\"" +
                "                ,\"http://schemas.microsoft.com/mapi/proptag/x10830003\", \"http://schemas.microsoft.com/mapi/proptag/x10900003\"" +
                "                ,\"http://schemas.microsoft.com/mapi/proptag/x0E070003\", \"http://schemas.microsoft.com/mapi/proptag/x10810003\"" +
                "                ,\"urn:schemas:mailheader:message-id\", \"urn:schemas:httpmail:read\", \"DAV:isdeleted\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE \"DAV:ishidden\" = False AND \"DAV:isfolder\" = False\n" +
                "                ORDER BY \"urn:schemas:httpmail:date\" ASC";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(wdr.retrieveSessionInstance(), folderUrl, searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();

            Message message = buildMessage(entity);
            messages.add(message);
        }
        Collections.sort(messages);
        return messages;
    }

    public List<Folder> getSubFolders(String folderName, boolean recursive) throws IOException {
        String mode = recursive ? "DEEP" : "SHALLOW";
        List<Folder> folders = new ArrayList<Folder>();
        String searchRequest = "Select \"DAV:nosubs\", \"DAV:hassubs\"," +
                "                \"DAV:hassubs\",\"urn:schemas:httpmail:unreadcount\"" +
                "                FROM Scope('" + mode + " TRAVERSAL OF \"" + getFolderPath(folderName) + "\"')\n" +
                "                WHERE \"DAV:ishidden\" = False AND \"DAV:isfolder\" = True \n" +
                "                      AND (\"DAV:contentclass\"='urn:content-classes:mailfolder' OR \"DAV:contentclass\"='urn:content-classes:folder')";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(wdr.retrieveSessionInstance(), mailPath, searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            folders.add(buildFolder(entity));
        }
        return folders;
    }

    protected Folder buildFolder(ResponseEntity entity) throws URIException {
        String href = URIUtil.decode(entity.getHref());
        Folder folder = new Folder();
        Enumeration enumeration = entity.getProperties();
        while (enumeration.hasMoreElements()) {
            Property property = (Property) enumeration.nextElement();
            if ("hassubs".equals(property.getLocalName())) {
                folder.hasChildren = "1".equals(property.getPropertyAsString());
            }
            if ("nosubs".equals(property.getLocalName())) {
                folder.noInferiors = "1".equals(property.getPropertyAsString());
            }
            if ("objectcount".equals(property.getLocalName())) {
                folder.objectCount = Integer.parseInt(property.getPropertyAsString());
            }
            if ("unreadcount".equals(property.getLocalName())) {
                folder.unreadCount = Integer.parseInt(property.getPropertyAsString());
            }
            if ("getlastmodified".equals(property.getLocalName())) {
                try {
                    folder.lastModified = dateParser.parse(property.getPropertyAsString()).getTime();
                } catch (ParseException e) {
                    LOGGER.error("Unable to parse date: " + e);
                }
            }
        }
        if (href.endsWith("/")) {
            href = href.substring(0, href.length() - 1);
        }

        // replace well known folder names
        if (href.startsWith(inboxUrl)) {
            folder.folderUrl = href.replaceFirst(inboxUrl, "INBOX");
        } else if (href.startsWith(sentitemsUrl)) {
            folder.folderUrl = href.replaceFirst(sentitemsUrl, "Sent");
        } else if (href.startsWith(draftsUrl)) {
            folder.folderUrl = href.replaceFirst(draftsUrl, "Drafts");
        } else if (href.startsWith(deleteditemsUrl)) {
            folder.folderUrl = href.replaceFirst(deleteditemsUrl, "Trash");
        } else {
            int index = href.indexOf(mailPath.substring(0, mailPath.length() - 1));
            if (index >= 0) {
                folder.folderUrl = href.substring(index + mailPath.length());
            } else {
                throw new URIException("Invalid folder url: " + folder.folderUrl);
            }
        }
        return folder;
    }

    /**
     * Delete oldest messages in trash.
     * keepDelay is the number of days to keep messages in trash before delete
     *
     * @throws IOException when unable to purge messages
     */
    public void purgeOldestTrashAndSentMessages() throws IOException {
        int keepDelay = Settings.getIntProperty("davmail.keepDelay");
        if (keepDelay != 0) {
            purgeOldestFolderMessages(deleteditemsUrl, keepDelay);
        }
        // this is a new feature, default is : do nothing
        int sentKeepDelay = Settings.getIntProperty("davmail.sentKeepDelay");
        if (sentKeepDelay != 0) {
            purgeOldestFolderMessages(sentitemsUrl, sentKeepDelay);
        }
    }

    public void purgeOldestFolderMessages(String folderUrl, int keepDelay) throws IOException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -keepDelay);
        LOGGER.debug("Delete messages in " + folderUrl + " since " + cal.getTime());

        String searchRequest = "Select \"DAV:uid\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + folderUrl + "\"')\n" +
                "                WHERE \"DAV:isfolder\" = False\n" +
                "                   AND \"DAV:getlastmodified\" &lt; '" + dateFormatter.format(cal.getTime()) + "'\n";
        Enumeration folderEnum = DavGatewayHttpClientFacade.executeSearchMethod(wdr.retrieveSessionInstance(), folderUrl, searchRequest);

        while (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            String messageUrl = URIUtil.decode(entity.getHref());

            LOGGER.debug("Delete " + messageUrl);
            wdr.deleteMethod(messageUrl);
        }
    }

    public void sendMessage(List<String> recipients, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        StringBuilder mailBuffer = new StringBuilder();
        StringBuilder recipientBuffer = new StringBuilder();
        boolean inHeader = true;
        boolean inRecipientHeader = false;
        while (!".".equals(line)) {
            mailBuffer.append(line).append("\n");
            line = reader.readLine();

            if (inHeader && line.length() == 0) {
                inHeader = false;
            }

            inRecipientHeader = inRecipientHeader && line.startsWith(" ");

            if ((inHeader && line.length() >= 3) || inRecipientHeader) {
                String prefix = line.substring(0, 3).toLowerCase();
                if ("to:".equals(prefix) || "cc:".equals(prefix) || inRecipientHeader) {
                    inRecipientHeader = true;
                    recipientBuffer.append(line);
                }
            }
            // Exchange 2007 : skip From: header
            if ((inHeader && line.length() >= 5)) {
                String prefix = line.substring(0, 5).toLowerCase();
                if ("from:".equals(prefix)) {
                    line = reader.readLine();
                }
            }
            // patch thunderbird html in reply for correct outlook display
            if (line != null && line.startsWith("<head>")) {
                mailBuffer.append(line).append("\n");
                line = "  <style> blockquote { display: block; margin: 1em 0px; padding-left: 1em; border-left: solid; border-color: blue; border-width: thin;}</style>";
            }
        }
        // remove visible recipients from list
        List<String> visibleRecipients = new ArrayList<String>();
        for (String recipient : recipients) {
            if (recipientBuffer.indexOf(recipient) >= 0) {
                visibleRecipients.add(recipient);
            }
        }
        recipients.removeAll(visibleRecipients);

        StringBuffer bccBuffer = new StringBuffer();
        for (String recipient : recipients) {
            if (bccBuffer.length() > 0) {
                bccBuffer.append(',');
            }
            bccBuffer.append("&lt;");
            bccBuffer.append(recipient);
            bccBuffer.append("&gt;");
        }

        String bcc = bccBuffer.toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        if (bcc.length() > 0) {
            properties.put("bcc", bcc);
        }

        String messageName = UUID.randomUUID().toString();

        createMessage(draftsUrl, messageName, properties, mailBuffer.toString());

        // warning : slide library expects *unencoded* urls
        String tempUrl = draftsUrl + "/" + messageName + ".EML";
        boolean sent = wdr.moveMethod(tempUrl, sendmsgUrl);
        if (!sent) {
            throw new IOException("Unable to send message: " + wdr.getStatusCode()
                    + " " + wdr.getStatusMessage());
        }

    }

    public String getFolderPath(String folderName) {
        String folderPath;
        if (folderName.startsWith("INBOX")) {
            folderPath = folderName.replaceFirst("INBOX", inboxUrl);
        } else if (folderName.startsWith("Trash")) {
            folderPath = folderName.replaceFirst("Trash", deleteditemsUrl);
        } else if (folderName.startsWith("Drafts")) {
            folderPath = folderName.replaceFirst("Drafts", draftsUrl);
        } else if (folderName.startsWith("Sent")) {
            folderPath = folderName.replaceFirst("Sent", sentitemsUrl);
            // absolute folder path
        } else if (folderName.startsWith("/")) {
            folderPath = folderName;
        } else {
            folderPath = mailPath + folderName;
        }
        return folderPath;
    }

    /**
     * Select current folder.
     * Folder name can be logical names INBOX, DRAFTS or TRASH (translated to local names),
     * relative path to user base folder or absolute path.
     *
     * @param folderName folder name
     * @return Folder object
     * @throws IOException when unable to change folder
     */
    public Folder getFolder(String folderName) throws IOException {
        Vector<String> reqProps = new Vector<String>();
        reqProps.add("DAV:hassubs");
        reqProps.add("DAV:nosubs");
        reqProps.add("DAV:objectcount");
        reqProps.add("urn:schemas:httpmail:unreadcount");
        reqProps.add("DAV:getlastmodified");
        Enumeration folderEnum = wdr.propfindMethod(getFolderPath(folderName), 0, reqProps);
        Folder folder = null;
        if (folderEnum.hasMoreElements()) {
            ResponseEntity entity = (ResponseEntity) folderEnum.nextElement();
            folder = buildFolder(entity);
            folder.folderName = folderName;
        }
        return folder;
    }

    public void createFolder(String folderName) throws IOException {
        String folderPath = getFolderPath(folderName);
        PropPatchMethod method = new PropPatchMethod(folderPath) {
            public String getName() {
                return "MKCOL";
            }
        };
        method.addPropertyToSet("outlookfolderclass", "IPF.Note", "ex", "http://schemas.microsoft.com/exchange/");
        try {
            wdr.retrieveSessionInstance().executeMethod(method);
            // ok or alredy exists
            if (method.getStatusCode() != HttpStatus.SC_MULTI_STATUS && method.getStatusCode() != HttpStatus.SC_METHOD_NOT_ALLOWED) {
                HttpException ex = new HttpException();
                ex.setReasonCode(method.getStatusCode());
                ex.setReason(method.getStatusText());
                throw ex;
            }
        } finally {
            method.releaseConnection();
        }
    }

    public void copyMessage(String messageUrl, String targetName) throws IOException {
        String targetPath = getFolderPath(targetName) + messageUrl.substring(messageUrl.lastIndexOf('/'));
        CopyMethod method = new CopyMethod(URIUtil.encodePath(messageUrl),
                URIUtil.encodePath(targetPath));
        method.setOverwrite(false);
        method.addRequestHeader("Allow-Rename", "t");
        try {
            int statusCode = wdr.retrieveSessionInstance().executeMethod(method);
            if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
                throw new HttpException("Unable to move message, target already exists");
            } else if (statusCode != HttpStatus.SC_CREATED) {
                HttpException ex = new HttpException();
                ex.setReasonCode(method.getStatusCode());
                ex.setReason(method.getStatusText());
                throw ex;
            }
        } finally {
            method.releaseConnection();
        }
    }

    public void moveFolder(String folderName, String targetName) throws IOException {
        String folderPath = getFolderPath(folderName);
        String targetPath = getFolderPath(targetName);
        MoveMethod method = new MoveMethod(URIUtil.encodePath(folderPath),
                URIUtil.encodePath(targetPath));
        method.setOverwrite(false);
        try {
            int statusCode = wdr.retrieveSessionInstance().executeMethod(method);
            if (statusCode == HttpStatus.SC_PRECONDITION_FAILED) {
                throw new HttpException("Unable to move folder, target already exists");
            } else if (statusCode != HttpStatus.SC_CREATED) {
                HttpException ex = new HttpException();
                ex.setReasonCode(method.getStatusCode());
                ex.setReason(method.getStatusText());
                throw ex;
            }
        } finally {
            method.releaseConnection();
        }
    }

    public static class Folder {
        public String folderUrl;
        public int objectCount;
        public int unreadCount;
        public boolean hasChildren;
        public boolean noInferiors;
        public long lastModified;
        public String folderName;

        public String getFlags() {
            if (noInferiors) {
                return "\\NoInferiors";
            } else if (hasChildren) {
                return "\\HasChildren";
            } else {
                return "\\HasNoChildren";
            }
        }
    }

    public class Message implements Comparable {
        public String messageUrl;
        public String uid;
        public int size;
        public String messageId;
        public boolean read;
        public boolean deleted;
        public boolean junk;
        public boolean flagged;
        public boolean draft;
        public boolean answered;
        public boolean forwarded;

        public long getUidAsLong() {
            byte[] decodedValue = Base64.decode(uid.getBytes());

            long result = 0;
            for (int i = 2; i < 9; i++) {
                result = result << 8;
                result |= decodedValue[i] & 0xff;
            }

            return result;
        }

        public String getImapFlags() {
            StringBuilder buffer = new StringBuilder();
            if (read) {
                buffer.append("\\Seen ");
            }
            if (deleted) {
                buffer.append("\\Deleted ");
            }
            if (flagged) {
                buffer.append("\\Flagged ");
            }
            if (junk) {
                buffer.append("Junk ");
            }
            if (draft) {
                buffer.append("\\Draft ");
            }
            if (answered) {
                buffer.append("\\Answered ");
            }
            if (forwarded) {
                buffer.append("$Forwarded ");
            }
            return buffer.toString().trim();
        }

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
            wdr.deleteMethod(messageUrl);
            if (wdr.getStatusCode() != HttpStatus.SC_OK) {
                HttpException ex = new HttpException();
                ex.setReasonCode(wdr.getStatusCode());
                ex.setReason(wdr.getStatusMessage());
                throw ex;
            }
        }

        public void moveToTrash() throws IOException {
            String destination = deleteditemsUrl + messageUrl.substring(messageUrl.lastIndexOf("/"));
            LOGGER.debug("Deleting : " + messageUrl + " to " + destination);
            // mark message as read
            HashMap<String,String> properties = new HashMap<String,String>();
            properties.put("read", "1");
            updateMessage(this, properties);
            MoveMethod method = new MoveMethod(URIUtil.encodePath(messageUrl),
                                           URIUtil.encodePath(destination));
            method.addRequestHeader("Overwrite", "f");
            method.addRequestHeader("Allow-rename", "t");
            method.setDebug(4);

            int status = wdr.retrieveSessionInstance().executeMethod(method);
            if (status != HttpStatus.SC_CREATED) {
                 HttpException ex = new HttpException();
                ex.setReasonCode(status);
                ex.setReason(method.getStatusText());
                throw ex;
            }
            if (method.getResponseHeader("Location")!= null) {
                destination = method.getResponseHeader("Location").getValue();
            }

            LOGGER.debug("Deleted to :" + destination + " " + wdr.getStatusCode() + " " + wdr.getStatusMessage());
        }

        public int compareTo(Object message) {
            return (int) (getUidAsLong() - ((Message) message).getUidAsLong());
        }
    }

    public class MessageList extends ArrayList<Message> {
        HashMap<Long, Message> uidMessageMap = new HashMap<Long, Message>();

        @Override
        public boolean add(Message message) {
            uidMessageMap.put(message.getUidAsLong(), message);
            return super.add(message);
        }

        public Message getByUid(long uid) {
            return uidMessageMap.get(uid);
        }
    }

    public WebdavResource getWebDavResource() {
        return wdr;
    }

    public class Event {
        protected String href;
        protected String etag;

        public String getICS() throws IOException {
            LOGGER.debug("Get event: " + href);
            StringBuilder buffer = new StringBuilder();
            GetMethod method = new GetMethod(URIUtil.encodePath(href));
            method.setRequestHeader("Content-Type", "text/xml; charset=utf-8");
            method.setRequestHeader("Translate", "f");
            BufferedReader eventReader = null;
            try {
                int status = wdr.retrieveSessionInstance().executeMethod(method);
                if (status != HttpStatus.SC_OK) {
                    LOGGER.warn("Unable to get event at " + href + " status: " + status);
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
            return fixICS(buffer.toString(), true);
        }

        public String getPath() {
            return href.substring(calendarUrl.length());
        }

        public String getEtag() {
            return etag;
        }
    }

    public List<Event> getAllEvents() throws IOException {
        int caldavPastDelay = Settings.getIntProperty("davmail.caldavPastDelay", Integer.MAX_VALUE);
        String dateCondition = "";
        if (caldavPastDelay != Integer.MAX_VALUE) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -caldavPastDelay);
            dateCondition = "                AND \"urn:schemas:calendar:dtstart\" > '" + dateFormatter.format(cal.getTime()) + "'\n";
        }

        List<Event> events = new ArrayList<Event>();
        String searchRequest = "<?xml version=\"1.0\"?>\n" +
                "<d:searchrequest xmlns:d=\"DAV:\">\n" +
                "        <d:sql> Select \"DAV:getetag\", \"urn:schemas:calendar:instancetype\"" +
                "                FROM Scope('SHALLOW TRAVERSAL OF \"" + calendarUrl + "\"')\n" +
                "                WHERE (\"urn:schemas:calendar:instancetype\" = 1\n" +
                "                OR (\"urn:schemas:calendar:instancetype\" = 0\n" +
                dateCondition +
                "                )) AND \"DAV:contentclass\" = 'urn:content-classes:appointment'\n" +
                "                ORDER BY \"urn:schemas:calendar:dtstart\" DESC\n" +
                "         </d:sql>\n" +
                "</d:searchrequest>";
        SearchMethod searchMethod = new SearchMethod(URIUtil.encodePath(calendarUrl), searchRequest);
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
                events.add(buildEvent((ResponseEntity) calendarEnum.nextElement()));
            }
        } finally {
            searchMethod.releaseConnection();
        }
        return events;
    }

    public Event getEvent(String path) throws IOException {
        Enumeration calendarEnum = wdr.propfindMethod(calendarUrl + "/" + URIUtil.decode(path), 0, EVENT_REQUEST_PROPERTIES);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar event");
        }
        return buildEvent((ResponseEntity) calendarEnum.
                nextElement());
    }

    protected Event buildEvent(ResponseEntity calendarResponse) throws URIException {
        Event event = new Event();
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

    protected String fixICS(String icsBody, boolean fromServer) throws IOException {
        // first pass : detect
        class AllDayState {
            boolean isAllDay = false;
            boolean hasCdoAllDay = false;
            boolean isCdoAllDay = false;
        }
        List<AllDayState> allDayStates = new ArrayList<AllDayState>();
        AllDayState currentAllDayState = new AllDayState();
        BufferedReader reader = null;
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(':');
                if (index >= 0) {
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    if ("DTSTART;VALUE=DATE".equals(key)) {
                        currentAllDayState.isAllDay = true;
                    } else if ("X-MICROSOFT-CDO-ALLDAYEVENT".equals(key)) {
                        currentAllDayState.hasCdoAllDay = true;
                        currentAllDayState.isCdoAllDay = "TRUE".equals(value);
                    } else if ("END:VEVENT".equals(line)) {
                        allDayStates.add(currentAllDayState);
                        currentAllDayState = new AllDayState();
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        // second pass : fix
        int count = 0;
        ICSBufferedWriter result = new ICSBufferedWriter();
        try {
            reader = new ICSBufferedReader(new StringReader(icsBody));
            String line;
            while ((line = reader.readLine()) != null) {
                if (currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE";
                } else if ("END:VEVENT".equals(line) && currentAllDayState.isAllDay && !currentAllDayState.hasCdoAllDay) {
                    result.writeLine("X-MICROSOFT-CDO-ALLDAYEVENT:TRUE");
                } else if (!currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE".equals(line)) {
                    line = "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE";
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTSTART;TZID")) {
                    line = getAllDayLine(line);
                } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTEND;TZID")) {
                    line = getAllDayLine(line);
                } else if ("BEGIN:VEVENT".equals(line)) {
                    currentAllDayState = allDayStates.get(count++);
                }
                result.writeLine(line);
            }
        } finally {
            reader.close();
        }

        return result.toString();
    }

    protected String getAllDayLine(String line) throws IOException {
        int keyIndex = line.indexOf(';');
        int valueIndex = line.lastIndexOf(':');
        int valueEndIndex = line.lastIndexOf('T');
        if (keyIndex < 0 || valueIndex < 0 || valueEndIndex < 0) {
            throw new IOException("Invalid ICS line: " + line);
        }
        String dateValue = line.substring(valueIndex + 1);
        String key = line.substring(0, keyIndex);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date;
        try {
            date = dateFormat.parse(dateValue);
        } catch (ParseException e) {
            throw new IOException("Invalid ICS line: " + line);
        }
        if ("DTEND".equals(key)) {
            date.setTime(date.getTime() - 1);
        }
        return line.substring(0, keyIndex) + ";VALUE=DATE:" + line.substring(valueIndex + 1, valueEndIndex);
    }

    public int createOrUpdateEvent(String path, String icsBody, String etag, String noneMatch) throws IOException {
        String messageUrl = URIUtil.encodePathQuery(calendarUrl + "/" + URIUtil.decode(path));
        String uid = path.substring(0, path.lastIndexOf("."));
        PutMethod putmethod = new PutMethod(messageUrl);
        putmethod.setRequestHeader("Translate", "f");
        putmethod.setRequestHeader("Overwrite", "f");
        if (etag != null) {
            putmethod.setRequestHeader("If-Match", etag);
        }
        if (noneMatch != null) {
            putmethod.setRequestHeader("If-None-Match", noneMatch);
        }
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        StringBuilder body = new StringBuilder();
        body.append("Content-Transfer-Encoding: 7bit\n" +
                "Content-class: urn:content-classes:appointment\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: multipart/alternative;\n" +
                "\tboundary=\"----=_NextPart_").append(uid).append("\"\n" +
                "\n" +
                "This is a multi-part message in MIME format.\n" +
                "\n" +
                "------=_NextPart_").append(uid).append("\n" +
                "Content-class: urn:content-classes:appointment\n" +
                "Content-Type: text/calendar;\n" +
                "\tmethod=REQUEST;\n" +
                "\tcharset=\"utf-8\"\n" +
                "Content-Transfer-Encoding: 8bit\n\n");
        body.append(new String(fixICS(icsBody, false).getBytes("UTF-8"), "ISO-8859-1"));
        body.append("------=_NextPart_").append(uid).append("--\n");
        putmethod.setRequestBody(body.toString());
        int status;
        try {
            status = wdr.retrieveSessionInstance().executeMethod(putmethod);

            if (status == HttpURLConnection.HTTP_OK) {
                LOGGER.warn("Overwritten event " + messageUrl);
            } else if (status != HttpURLConnection.HTTP_CREATED) {
                LOGGER.warn("Unable to create or update message " + status + " " + putmethod.getStatusLine());
            }
        } finally {
            putmethod.releaseConnection();
        }
        return status;
    }


    public void deleteFolder(String path) throws IOException {
        wdr.deleteMethod(getFolderPath(path));
        int status = wdr.getStatusCode();
        if (status != HttpStatus.SC_OK) {
            HttpException ex = new HttpException();
            ex.setReasonCode(status);
            ex.setReason(wdr.getStatusMessage());
            throw ex;
        }
    }

    public int deleteMessage(String path) throws IOException {
        wdr.deleteMethod(path);
        return wdr.getStatusCode();
    }

    public int deleteEvent(String path) throws IOException {
        wdr.deleteMethod(calendarUrl + "/" + URIUtil.decode(path));
        return wdr.getStatusCode();
    }

    public String getCalendarCtag() throws IOException {
        String ctag = null;
        Enumeration calendarEnum = wdr.propfindMethod(calendarUrl, 0);
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
                    ctag = property.getPropertyAsString();
                }
            }
        }
        if (ctag == null) {
            throw new IOException("Unable to get calendar ctag");
        }
        return ctag;
    }

    public String getCalendarEtag() throws IOException {
        String etag = null;
        Enumeration calendarEnum = wdr.propfindMethod(calendarUrl, 0, EVENT_REQUEST_PROPERTIES);
        if (!calendarEnum.hasMoreElements()) {
            throw new IOException("Unable to get calendar object");
        }
        while (calendarEnum.hasMoreElements()) {
            ResponseEntity calendarResponse = (ResponseEntity) calendarEnum.
                    nextElement();
            Enumeration propertiesEnumeration = calendarResponse.getProperties();
            while (propertiesEnumeration.hasMoreElements()) {
                Property property = (Property) propertiesEnumeration.nextElement();
                if ("getetag".equals(property.getLocalName())) {
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
     * Get current Exchange alias name from login name
     *
     * @return user name
     * @throws java.io.IOException on error
     */
    protected String getAliasFromLogin() throws IOException {
        // Exchange 2007 : userName is login without domain
        String userName = poolKey.userName;
        int index = userName.indexOf('\\');
        if (index >= 0) {
            userName = userName.substring(index + 1);
        }
        return userName;
    }

    /**
     * Get current Exchange alias name from mailbox name
     *
     * @return user name
     * @throws java.io.IOException on error
     */
    protected String getAliasFromMailPath() throws IOException {
        if (mailPath == null) {
            throw new IOException("Empty mail path");
        }
        int index = mailPath.lastIndexOf("/", mailPath.length() - 2);
        if (index >= 0 && mailPath.endsWith("/")) {
            return mailPath.substring(index + 1, mailPath.length() - 1);
        } else {
            throw new IOException("Invalid mail path: " + mailPath);
        }
    }

    public String getEmail(String alias) throws IOException {
        String emailResult = null;
        GetMethod getMethod = new GetMethod("/public/?Cmd=galfind&AN=" + alias);
        try {
            int status = wdr.retrieveSessionInstance().executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException("Unable to get user email from: " + getMethod.getPath());
            }
            Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
            Map<String, String> result = results.get(alias.toLowerCase());
            if (result != null) {
                emailResult = result.get("EM");
            }

        } finally {
            getMethod.releaseConnection();
        }
        return emailResult;
    }

    public void buildEmail() throws IOException {
        // first try to get email from login name
        email = getEmail(getAliasFromLogin());
        // failover: use mailbox name as alias
        if (email == null) {
            email = getEmail(getAliasFromMailPath());
        }
        if (email == null) {
            throw new IOException("Unable to get user email with alias " + getAliasFromLogin() + " or " + getAliasFromMailPath());
        }
        // normalize email
        email = email.toLowerCase();
    }

    /**
     * Get current user email
     *
     * @return user email
     * @throws java.io.IOException on error
     */
    public String getEmail() throws IOException {
        return email;
    }

    /**
     * Search users in global address book
     *
     * @param searchAttribute exchange search attribute
     * @param searchValue     search value
     * @return List of users
     * @throws java.io.IOException on error
     */
    public Map<String, Map<String, String>> galFind(String searchAttribute, String searchValue) throws IOException {
        Map<String, Map<String, String>> results;
        GetMethod getMethod = new GetMethod(URIUtil.encodePathQuery("/public/?Cmd=galfind&" + searchAttribute + "=" + searchValue));
        try {
            int status = wdr.retrieveSessionInstance().executeMethod(getMethod);
            if (status != HttpStatus.SC_OK) {
                throw new IOException(status + "Unable to find users from: " + getMethod.getURI());
            }
            results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
        } finally {
            getMethod.releaseConnection();
        }

        return results;
    }

    public void galLookup(Map<String, String> person) {
        if (!disableGalLookup) {
            GetMethod getMethod = null;
            try {
                getMethod = new GetMethod(URIUtil.encodePathQuery("/public/?Cmd=gallookup&ADDR=" + person.get("EM")));
                int status = wdr.retrieveSessionInstance().executeMethod(getMethod);
                if (status != HttpStatus.SC_OK) {
                    throw new IOException(status + "Unable to find users from: " + getMethod.getURI());
                }
                Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "person", "alias");
                // add detailed information
                if (results.size() > 0) {
                    Map<String, String> fullperson = results.get(person.get("AN"));
                    for (Map.Entry<String, String> entry : fullperson.entrySet()) {
                        person.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to gallookup person: " + person + ", disable GalLookup");
                disableGalLookup = true;
            } finally {
                if (getMethod != null) {
                    getMethod.releaseConnection();
                }
            }
        }
    }

    public String getFreebusy(Map<String, String> valueMap) throws IOException {
        String result = null;

        String startDateValue = valueMap.get("DTSTART");
        String endDateValue = valueMap.get("DTEND");
        String attendee = valueMap.get("ATTENDEE");
        if (attendee.startsWith("mailto:")) {
            attendee = attendee.substring("mailto:".length());
        }
        int interval = 15;

        SimpleDateFormat icalParser = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        icalParser.setTimeZone(GMT_TIMEZONE);

        SimpleDateFormat shortIcalParser = new SimpleDateFormat("yyyyMMdd");
        shortIcalParser.setTimeZone(GMT_TIMEZONE);

        SimpleDateFormat owaFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        owaFormatter.setTimeZone(GMT_TIMEZONE);

        String url;
        Date startDate;
        Date endDate;
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
            String body = getMethod.getResponseBodyAsString();
            int startIndex = body.lastIndexOf("<a:fbdata>");
            int endIndex = body.lastIndexOf("</a:fbdata>");
            if (startIndex >= 0 && endIndex >= 0) {
                String fbdata = body.substring(startIndex + "<a:fbdata>".length(), endIndex);
                if (fbdata.length() > 0) {
                    Calendar currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    currentCal.setTime(startDate);

                    StringBuilder busyBuffer = new StringBuilder();
                    boolean isBusy = fbdata.charAt(0) != '0' && fbdata.charAt(0) != '4';
                    if (isBusy) {
                        busyBuffer.append(icalParser.format(currentCal.getTime()));
                    }
                    boolean knownAttendee = fbdata.charAt(0) != '4';
                    for (int i = 1; i < fbdata.length(); i++) {
                        knownAttendee = knownAttendee || fbdata.charAt(i) != '4';
                        currentCal.add(Calendar.MINUTE, interval);
                        if (isBusy && fbdata.charAt(i) == '0') {
                            // busy -> non busy
                            busyBuffer.append('/').append(icalParser.format(currentCal.getTime()));
                        } else if (!isBusy && (fbdata.charAt(i) != '0') && fbdata.charAt(0) != '4') {
                            // non busy -> busy
                            if (busyBuffer.length() > 0) {
                                busyBuffer.append(',');
                            }
                            busyBuffer.append(icalParser.format(currentCal.getTime()));
                        }
                        isBusy = fbdata.charAt(i) != '0' && fbdata.charAt(0) != '4';
                    }
                    // still busy at end
                    if (isBusy) {
                        busyBuffer.append('/').append(icalParser.format(currentCal.getTime()));
                    }
                    if (knownAttendee) {
                        result = busyBuffer.toString();
                    }
                }
            }
        } finally {
            getMethod.releaseConnection();
        }

        return result;
    }

}

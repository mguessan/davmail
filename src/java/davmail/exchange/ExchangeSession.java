package davmail.exchange;

import davmail.Settings;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
import org.apache.webdav.lib.Property;
import org.apache.webdav.lib.ResponseEntity;
import org.apache.webdav.lib.WebdavResource;

import javax.mail.internet.MimeUtility;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
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
     * Various standard mail boxes Urls
     */
    private String inboxUrl;
    private String deleteditemsUrl;
    private String sendmsgUrl;
    private String draftsUrl;

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
    public ExchangeSession() {
        // SimpleDateFormat are not thread safe, need to create one instance for
        // each session
        dateParser = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        dateParser.setTimeZone(new SimpleTimeZone(0, "GMT"));
    }

    /**
     * Update http client configuration (proxy)
     *
     * @param httpClient current Http client
     */
    protected static void configureClient(HttpClient httpClient) {
        // do not send basic auth automatically
        httpClient.getState().setAuthenticationPreemptive(false);

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPassword = null;

        if (enableProxy) {
            proxyHost = Settings.getProperty("davmail.proxyHost");
            proxyPort = Settings.getIntProperty("davmail.proxyPort");
            proxyUser = Settings.getProperty("davmail.proxyUser");
            proxyPassword = Settings.getProperty("davmail.proxyPassword");
        }

        // configure proxy
        if (proxyHost != null && proxyHost.length() > 0) {
            httpClient.getHostConfiguration().setProxy(proxyHost, proxyPort);
            if (proxyUser != null && proxyUser.length() > 0) {

/*              // Only available in newer HttpClient releases, not compatible with slide library
                List authPrefs = new ArrayList();
                authPrefs.add(AuthPolicy.BASIC);
                httpClient.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,authPrefs);
*/
                // instead detect ntlm authentication (windows domain name in user name)
                int backslashindex = proxyUser.indexOf("\\");
                if (backslashindex > 0) {
                    httpClient.getState().setProxyCredentials(null, proxyHost,
                            new NTCredentials(proxyUser.substring(backslashindex + 1),
                                    proxyPassword, null,
                                    proxyUser.substring(0, backslashindex)));
                } else {
                    httpClient.getState().setProxyCredentials(null, proxyHost,
                            new UsernamePasswordCredentials(proxyUser, proxyPassword));
                }
            }
        }

    }

    public static void checkConfig() throws IOException {
        try {
            String url = Settings.getProperty("davmail.url");

            // create an HttpClient instance
            HttpClient httpClient = new HttpClient();
            configureClient(httpClient);

            // get webmail root url (will not follow redirects)
            HttpMethod testMethod = new GetMethod(url);
            testMethod.setFollowRedirects(false);
            int status = httpClient.executeMethod(testMethod);
            testMethod.releaseConnection();
            LOGGER.debug("Test configuration status: " + status);
            if (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED
                    && status != HttpStatus.SC_MOVED_TEMPORARILY) {
                throw new IOException("Unable to connect to OWA at " + url + ", status code " +
                        status + ", check configuration");
            }

        } catch (UnknownHostException exc) {
            LOGGER.error("DavMail configuration exception: \n Unknown host " + exc.getMessage(), exc);
            throw new IOException("DavMail configuration exception: \n Unknown host " + exc.getMessage(), exc);
        } catch (Exception exc) {
            LOGGER.error("DavMail configuration exception: \n" + exc.getMessage(), exc);
            throw new IOException("DavMail configuration exception: \n" + exc.getMessage(), exc);
        }

    }

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url exchange base URL
     * @return true if basic authentication detected
     * @throws java.io.IOException unable to connect to exchange
     */
    protected boolean isBasicAuthentication(String url) throws IOException {
        // create an HttpClient instance
        HttpClient httpClient = new HttpClient();
        configureClient(httpClient);
        HttpMethod testMethod = new GetMethod(url);
        int status = httpClient.executeMethod(testMethod);
        testMethod.releaseConnection();
        return status == HttpStatus.SC_UNAUTHORIZED;
    }

    public void login(String userName, String password) throws IOException {
        try {
            String url = Settings.getProperty("davmail.url");

            boolean isBasicAuthentication = isBasicAuthentication(url);

            // get proxy configuration from setttings properties
            URL urlObject = new URL(url);
            // webdavresource is unable to create the correct url type
            HttpURL httpURL;
            if (url.startsWith("http://")) {
                httpURL = new HttpURL(userName, password,
                        urlObject.getHost(), urlObject.getPort());
            } else if (url.startsWith("https://")) {
                httpURL = new HttpsURL(userName, password,
                        urlObject.getHost(), urlObject.getPort());
            } else {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            wdr = new WebdavResource(httpURL, WebdavResource.NOACTION, 0);

            // set httpclient timeout to 30 seconds
            //wdr.retrieveSessionInstance().setTimeout(30000);

            // get the internal HttpClient instance
            HttpClient httpClient = wdr.retrieveSessionInstance();

            configureClient(httpClient);

            // get webmail root url (will follow redirects)
            // providing credentials
            HttpMethod initmethod = new GetMethod(url);
            wdr.executeHttpRequestMethod(httpClient,
                    initmethod);
            if (!isBasicAuthentication) {
                LOGGER.debug("Form based authentication detected");
                if (initmethod.getPath().indexOf("exchweb/bin") == -1) {
                    LOGGER.error("DavMail configuration exception: authentication form not found at " + url +
                            " and basic authentication not requested");
                    throw new IOException("DavMail configuration exception: authentication form not found at " + url +
                            " and basic authentication not requested");
                } else {
                    PostMethod logonMethod = new PostMethod(
                            "/exchweb/bin/auth/owaauth.dll?" +
                                    "ForcedBasic=false&Basic=false&Private=true" +
                                    "&Language=No_Value"
                    );
                    logonMethod.addParameter("destination", url);
                    logonMethod.addParameter("flags", "4");
//                  logonMethod.addParameter("visusername", userName.substring(userName.lastIndexOf('\\')));
                    logonMethod.addParameter("username", userName);
                    logonMethod.addParameter("password", password);
//                  logonMethod.addParameter("SubmitCreds", "Log On");
//                  logonMethod.addParameter("forcedownlevel", "0");
                    logonMethod.addParameter("trusted", "4");

                    wdr.executeHttpRequestMethod(httpClient, logonMethod);
                    Header locationHeader = logonMethod.getResponseHeader("Location");

                    if (logonMethod.getStatusCode() != HttpURLConnection.HTTP_MOVED_TEMP ||
                            locationHeader == null ||
                            !url.equals(locationHeader.getValue())) {
                        throw new HttpException("Authentication failed");
                    }

                }
            }

            // User may be authenticated, get various session information
            HttpMethod method = new GetMethod(url);
            int status = wdr.executeHttpRequestMethod(httpClient, method);
            if (status != HttpStatus.SC_OK) {
                HttpException ex = new HttpException();
                ex.setReasonCode(status);
                ex.setReason(method.getStatusText());
                throw ex;
            }

            // get user mail URL from html body (multi frame)
            String mailboxName = method.getResponseBodyAsString();
            int beginIndex = mailboxName.indexOf(url);
            if (beginIndex < 0) {
                throw new HttpException(url + " not found in body");
            }
            mailboxName = mailboxName.substring(beginIndex);
            int endIndex = mailboxName.indexOf('"');
            if (endIndex < 0) {
                throw new HttpException(url + " not found in body");
            }
            mailboxName = mailboxName.substring(url.length(), endIndex);

            // if body is empty : wrong password, not authenticated
            if (mailboxName.length() == 0) {
                throw new HttpException("Authentication failed");
            }
            // got base http mailbox http url
            mailPath = "/exchange/" + mailboxName;
            wdr.setPath(mailPath);

            // Retrieve inbox and trash URLs
            Vector<String> reqProps = new Vector<String>();
            reqProps.add("urn:schemas:httpmail:inbox");
            reqProps.add("urn:schemas:httpmail:deleteditems");
            reqProps.add("urn:schemas:httpmail:sendmsg");
            reqProps.add("urn:schemas:httpmail:drafts");

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
        putmethod.setRequestHeader("Content-Type", "message/rfc822");
        putmethod.setRequestBody(messageBody);

        int code = wdr.retrieveSessionInstance().executeMethod(putmethod);

        if (code == HttpURLConnection.HTTP_OK) {
            LOGGER.warn("Overwritten message " + messageUrl);
        } else if (code != HttpURLConnection.HTTP_CREATED) {
            throw new IOException("Unable to create message " + code + " " + putmethod.getStatusLine());
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
                        line = line.replaceAll("&#8217;", "'");
                        line = line.replaceAll("&#8230;", "...");
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

}

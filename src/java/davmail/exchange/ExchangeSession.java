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
package davmail.exchange;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewayOTPPrompt;
import davmail.util.StringUtil;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyIterator;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.log4j.Logger;
import org.htmlcleaner.CommentToken;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.*;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exchange session through Outlook Web Access (DAV)
 */
public abstract class ExchangeSession {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

    /**
     * Reference GMT timezone to format dates
     */
    public static final SimpleTimeZone GMT_TIMEZONE = new SimpleTimeZone(0, "GMT");

    protected static final Set<String> USER_NAME_FIELDS = new HashSet<String>();

    static {
        USER_NAME_FIELDS.add("username");
        USER_NAME_FIELDS.add("txtUserName");
        USER_NAME_FIELDS.add("userid");
        USER_NAME_FIELDS.add("SafeWordUser");
    }

    protected static final Set<String> PASSWORD_FIELDS = new HashSet<String>();

    static {
        PASSWORD_FIELDS.add("password");
        PASSWORD_FIELDS.add("txtUserPass");
        PASSWORD_FIELDS.add("pw");
        PASSWORD_FIELDS.add("basicPassword");
    }

    protected static final Set<String> TOKEN_FIELDS = new HashSet<String>();

    static {
        TOKEN_FIELDS.add("SafeWordPassword");
    }

    protected static final int FREE_BUSY_INTERVAL = 15;

    protected static final String PUBLIC_ROOT = "/public";
    protected static final String CALENDAR = "calendar";
    protected static final String CONTACTS = "contacts";
    protected static final String INBOX = "INBOX";
    protected static final String SENT = "Sent";
    protected static final String DRAFTS = "Drafts";
    protected static final String TRASH = "Trash";
    protected static final String JUNK = "Junk";
    protected static final String UNSENT = "Unsent Messages";

    /**
     * Various standard mail boxes Urls
     */
    protected String inboxUrl;
    protected String deleteditemsUrl;
    protected String sentitemsUrl;
    protected String sendmsgUrl;
    protected String draftsUrl;
    protected String calendarUrl;
    protected String contactsUrl;
    protected String outboxUrl;
    protected String publicFolderUrl;

    /**
     * Base user mailboxes path (used to select folder)
     */
    protected String mailPath;
    protected String email;
    private String alias;
    protected final HttpClient httpClient;

    private final String userName;

    private boolean disableGalLookup;
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy/MM/dd HH:mm:ss";
    private static final String YYYYMMDD_T_HHMMSS_Z = "yyyyMMdd'T'HHmmss'Z'";
    private static final String YYYY_MM_DD_T_HHMMSS_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String YYYY_MM_DD_T_HHMMSS_SSS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * Logon form user name field, default is username.
     */
    private String userNameInput = "username";
    /**
     * Logon form password field, default is password.
     */
    private String passwordInput = "password";

    /**
     * Create an exchange session for the given URL.
     * The session is established for given userName and password
     *
     * @param url      Exchange url
     * @param userName user login name
     * @param password user password
     * @throws IOException on error
     */
    public ExchangeSession(String url, String userName, String password) throws IOException {
        this.userName = userName;
        try {
            boolean isBasicAuthentication = isBasicAuthentication(url);

            httpClient = DavGatewayHttpClientFacade.getInstance(url, userName, password);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            HttpMethod method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, url);

            if (isBasicAuthentication) {
                int status = method.getStatusCode();

                if (status == HttpStatus.SC_UNAUTHORIZED) {
                    method.releaseConnection();
                    throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                } else if (status != HttpStatus.SC_OK) {
                    method.releaseConnection();
                    throw DavGatewayHttpClientFacade.buildHttpException(method);
                }
            } else {
                method = formLogin(httpClient, method, userName, password);
            }

            // avoid 401 roundtrips, only if NTLM is disabled
            if (!DavGatewayHttpClientFacade.hasNTLM(httpClient)) {
                httpClient.getParams().setParameter(HttpClientParams.PREEMPTIVE_AUTHENTICATION, true);
            }

            buildSessionInfo(method);

        } catch (DavMailAuthenticationException exc) {
            LOGGER.error(exc.getMessage());
            throw exc;
        } catch (UnknownHostException exc) {
            BundleMessage message = new BundleMessage("EXCEPTION_CONNECT", exc.getClass().getName(), exc.getMessage());
            ExchangeSession.LOGGER.error(message);
            throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", message);
        } catch (IOException exc) {
            LOGGER.error(BundleMessage.formatLog("EXCEPTION_EXCHANGE_LOGIN_FAILED", exc));
            throw new DavMailException("EXCEPTION_EXCHANGE_LOGIN_FAILED", exc);
        }
        LOGGER.debug("Session " + this + " created");
    }

    protected String formatSearchDate(Date date) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS, Locale.ENGLISH);
        dateFormatter.setTimeZone(GMT_TIMEZONE);
        return dateFormatter.format(date);
    }

    protected SimpleDateFormat getZuluDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYYMMDD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected SimpleDateFormat getExchangeZuluDateFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected SimpleDateFormat getExchangeZuluDateFormatMillisecond() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(YYYY_MM_DD_T_HHMMSS_SSS_Z, Locale.ENGLISH);
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat;
    }

    protected Date parseDate(String dateString) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(GMT_TIMEZONE);
        return dateFormat.parse(dateString);
    }


    /**
     * Test if the session expired.
     *
     * @return true this session expired
     * @throws NoRouteToHostException on error
     * @throws UnknownHostException   on error
     */
    public abstract boolean isExpired() throws NoRouteToHostException, UnknownHostException;

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url exchange base URL
     * @return true if basic authentication detected
     * @throws IOException unable to connect to exchange
     */
    protected boolean isBasicAuthentication(String url) throws IOException {
        return DavGatewayHttpClientFacade.getHttpStatus(url) == HttpStatus.SC_UNAUTHORIZED;
    }

    protected String getAbsoluteUri(HttpMethod method, String path) throws URIException {
        URI uri = method.getURI();
        if (path != null) {
            // reset query string
            uri.setQuery(null);
            if (path.startsWith("/")) {
                // path is absolute, replace method path
                uri.setPath(path);
            } else if (path.startsWith("http://") || path.startsWith("https://")) {
                return path;
            } else {
                // relative path, build new path
                String currentPath = method.getPath();
                int end = currentPath.lastIndexOf('/');
                if (end >= 0) {
                    uri.setPath(currentPath.substring(0, end + 1) + path);
                } else {
                    throw new URIException(uri.getURI());
                }
            }
        }
        return uri.getURI();
    }

    protected String getScriptBasedFormURL(HttpMethod initmethod, String pathQuery) throws URIException {
        URI initmethodURI = initmethod.getURI();
        int queryIndex = pathQuery.indexOf('?');
        if (queryIndex >= 0) {
            if (queryIndex > 0) {
                // update path
                String newPath = pathQuery.substring(0, queryIndex);
                if (newPath.startsWith("/")) {
                    // absolute path
                    initmethodURI.setPath(newPath);
                } else {
                    String currentPath = initmethodURI.getPath();
                    int folderIndex = currentPath.lastIndexOf('/');
                    if (folderIndex >= 0) {
                        // replace relative path
                        initmethodURI.setPath(currentPath.substring(0, folderIndex + 1) + newPath);
                    } else {
                        // should not happen
                        initmethodURI.setPath('/' + newPath);
                    }
                }
            }
            initmethodURI.setQuery(pathQuery.substring(queryIndex + 1));
        }
        return initmethodURI.getURI();
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param httpClient httpClient instance
     * @param initmethod form body http method
     * @return logon method
     * @throws IOException on error
     */
    protected PostMethod buildLogonMethod(HttpClient httpClient, HttpMethod initmethod) throws IOException {

        PostMethod logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(initmethod.getResponseBodyAsStream());
            List forms = node.getElementListByName("form", true);
            TagNode logonForm = null;
            // select form
            if (forms.size() == 1) {
                logonForm = (TagNode) forms.get(0);
            } else if (forms.size() > 1) {
                for (Object form : forms) {
                    if ("logonForm".equals(((TagNode) form).getAttributeByName("name"))) {
                        logonForm = ((TagNode) form);
                    }
                }
            }
            if (logonForm != null) {
                String logonMethodPath = logonForm.getAttributeByName("action");

                logonMethod = new PostMethod(getAbsoluteUri(initmethod, logonMethodPath));

                List inputList = logonForm.getElementListByName("input", true);
                for (Object input : inputList) {
                    String type = ((TagNode) input).getAttributeByName("type");
                    String name = ((TagNode) input).getAttributeByName("name");
                    String value = ((TagNode) input).getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        logonMethod.addParameter(name, value);
                    }
                    // custom login form
                    if (USER_NAME_FIELDS.contains(name)) {
                        userNameInput = name;
                    } else if (PASSWORD_FIELDS.contains(name)) {
                        passwordInput = name;
                    } else if ("addr".equals(name)) {
                        // this is not a logon form but a redirect form
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    } else if (TOKEN_FIELDS.contains(name)) {
                        // one time password, ask user
                        logonMethod.addParameter(name, DavGatewayOTPPrompt.getOneTimePassword());
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
                                String sUrl = StringUtil.getToken(scriptValue, "var a_sUrl = \"", "\"");
                                String sLgn = StringUtil.getToken(scriptValue, "var a_sLgn = \"", "\"");
                                if (sLgn == null) {
                                    sLgn = StringUtil.getToken(scriptValue, "var a_sLgnQS = \"", "\"");
                                }
                                if (sUrl != null && sLgn != null) {
                                    String src = getScriptBasedFormURL(initmethod, sLgn + sUrl);
                                    LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                    HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                    logonMethod = buildLogonMethod(httpClient, newInitMethod);
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

        return logonMethod;
    }

    protected HttpMethod formLogin(HttpClient httpClient, HttpMethod initmethod, String userName, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");

        HttpMethod logonMethod = buildLogonMethod(httpClient, initmethod);
        if (logonMethod == null) {
            throw new DavMailException("EXCEPTION_AUTHENTICATION_FORM_NOT_FOUND", initmethod.getURI());
        }

        // make sure username and password fields are empty
        ((PostMethod) logonMethod).removeParameter(userNameInput);
        ((PostMethod) logonMethod).removeParameter(passwordInput);
        ((PostMethod) logonMethod).removeParameter("trusted");
        ((PostMethod) logonMethod).removeParameter("flags");
        ((PostMethod) logonMethod).addParameter(userNameInput, userName);
        ((PostMethod) logonMethod).addParameter(passwordInput, password);
        ((PostMethod) logonMethod).addParameter("trusted", "4");
        ((PostMethod) logonMethod).addParameter("flags", "4");
        logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);

        // test form based authentication
        checkFormLoginQueryString(logonMethod);

        // workaround for post logon script redirect
        if (!isAuthenticated()) {
            // try to get new method from script based redirection
            logonMethod = buildLogonMethod(httpClient, logonMethod);

            if (logonMethod != null) {
                // if logonMethod is not null, try to follow redirection
                logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
                checkFormLoginQueryString(logonMethod);
                // also check cookies
                if (!isAuthenticated()) {
                    throwAuthenticationFailed();
                }
            } else {
                // authentication failed
                throwAuthenticationFailed();
            }
        }

        return logonMethod;
    }

    /**
     * Look for session cookies.
     *
     * @return true if session cookies are available
     */
    protected boolean isAuthenticated() {
        boolean authenticated = false;
        for (Cookie cookie : httpClient.getState().getCookies()) {
            // Exchange 2003 cookies
            if (cookie.getName().startsWith("cadata") || "sessionid".equals(cookie.getName())
                    // Exchange 2007 cookie
                    || "UserContext".equals(cookie.getName())) {
                authenticated = true;
                break;
            }
        }
        return authenticated;
    }

    protected void checkFormLoginQueryString(HttpMethod logonMethod) throws DavMailAuthenticationException {
        String queryString = logonMethod.getQueryString();
        if (queryString != null && queryString.contains("reason=2")) {
            logonMethod.releaseConnection();
            throwAuthenticationFailed();
        }
    }

    protected void throwAuthenticationFailed() throws DavMailAuthenticationException {
        if (this.userName != null && this.userName.contains("\\")) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        } else {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_RETRY");
        }
    }

    protected abstract void buildSessionInfo(HttpMethod method) throws DavMailException;

    /**
     * Create message in specified folder.
     * Will overwrite an existing message with same subject in the same folder
     *
     * @param folderPath  Exchange folder path
     * @param messageName message name
     * @param properties  message properties (flags)
     * @param messageBody mail body
     * @throws IOException when unable to create message
     */
    public abstract void createMessage(String folderPath, String messageName, HashMap<String, String> properties, String messageBody) throws IOException;

    /**
     * Update given properties on message.
     *
     * @param message    Exchange message
     * @param properties Webdav properties map
     * @throws IOException on error
     */
    public abstract void updateMessage(Message message, Map<String, String> properties) throws IOException;


    /**
     * Delete Exchange message.
     *
     * @param message Exchange message
     * @throws IOException on error
     */
    public abstract void deleteMessage(Message message) throws IOException;

    /**
     * Send message to recipients, properties contains bcc recipients and other non MIME flags.
     *
     * @param properties  additional message properties
     * @param messageBody MIME message body
     * @throws IOException on error
     */
    public abstract void sendMessage(HashMap<String, String> properties, String messageBody) throws IOException;

    /**
     * Create message MIME body reader;
     *
     * @param message Exchange message
     * @return message body reader
     * @throws IOException on error
     */
    protected abstract BufferedReader getContentReader(Message message) throws IOException;

    protected static final List<String> POP_MESSAGE_ATTRIBUTES = new ArrayList<String>();

    static {
        POP_MESSAGE_ATTRIBUTES.add("uid");
        POP_MESSAGE_ATTRIBUTES.add("messageSize");
    }

    /**
     * Return folder message list with id and size only (for POP3 listener).
     *
     * @param folderName Exchange folder name
     * @return folder message list
     * @throws IOException on error
     */
    public MessageList getAllMessageUidAndSize(String folderName) throws IOException {
        return searchMessages(folderName, POP_MESSAGE_ATTRIBUTES, null);
    }

    protected static final List<String> IMAP_MESSAGE_ATTRIBUTES = new ArrayList<String>();

    static {
        IMAP_MESSAGE_ATTRIBUTES.add("uid");
        IMAP_MESSAGE_ATTRIBUTES.add("messageSize");
        IMAP_MESSAGE_ATTRIBUTES.add("imapUid");
        IMAP_MESSAGE_ATTRIBUTES.add("junk");
        IMAP_MESSAGE_ATTRIBUTES.add("flagStatus");
        IMAP_MESSAGE_ATTRIBUTES.add("messageFlags");
        IMAP_MESSAGE_ATTRIBUTES.add("lastVerbExecuted");
        IMAP_MESSAGE_ATTRIBUTES.add("read");
        IMAP_MESSAGE_ATTRIBUTES.add("deleted");
        IMAP_MESSAGE_ATTRIBUTES.add("date");
    }

    protected static final List<String> UID_MESSAGE_ATTRIBUTES = new ArrayList<String>();

    static {
        UID_MESSAGE_ATTRIBUTES.add("uid");
    }

    /**
     * Get all folder messages.
     *
     * @param folderName Exchange folder name
     * @param condition  search filter
     * @return message list
     * @throws IOException on error
     */
    public MessageList searchMessages(String folderName) throws IOException {
        return searchMessages(folderName, IMAP_MESSAGE_ATTRIBUTES, null);
    }

    /**
     * Search folder for messages matching conditions, with attributes needed by IMAP listener.
     *
     * @param folderName Exchange folder name
     * @param condition  search filter
     * @return message list
     * @throws IOException on error
     */
    public MessageList searchMessages(String folderName, Condition condition) throws IOException {
        return searchMessages(folderName, IMAP_MESSAGE_ATTRIBUTES, condition);
    }

    /**
     * Search folder for messages matching conditions, with given attributes.
     *
     * @param folderName Exchange folder name
     * @param attributes requested Webdav attributes
     * @param conditions conditions string in Exchange SQL syntax
     * @return message list
     * @throws IOException on error
     */
    public abstract MessageList searchMessages(String folderName, List<String> attributes, Condition condition) throws IOException;

    protected enum Operator {
        Or, And, Not, IsEqualTo, Like, IsGreaterThan, IsGreaterThanOrEqualTo, IsLessThan, IsNull, IsTrue, IsFalse
    }

    public abstract static class Condition {
        public abstract void appendTo(StringBuilder buffer);
    }

    public abstract static class AttributeCondition extends Condition {
        protected String attributeName;
        protected Operator operator;
        protected String value;

        protected AttributeCondition(String attributeName, Operator operator, String value) {
            this.attributeName = attributeName;
            this.operator = operator;
            this.value = value;
        }
    }

    public abstract static class MultiCondition extends Condition {
        protected Operator operator;
        protected List<Condition> conditions;

        protected MultiCondition(Operator operator, Condition... conditions) {
            this.operator = operator;
            this.conditions = Arrays.asList(conditions);
        }

        public void append(Condition condition) {
            if (condition != null) {
                conditions.add(condition);
            }
        }
    }

    public abstract static class NotCondition extends Condition {
        protected Condition condition;

        protected NotCondition(Condition condition) {
            this.condition = condition;
        }
    }

    public abstract static class MonoCondition extends Condition {
        protected String attributeName;
        protected Operator operator;

        protected MonoCondition(String attributeName, Operator operator) {
            this.attributeName = attributeName;
            this.operator = operator;
        }
    }

    public abstract MultiCondition and(Condition... condition);

    public abstract MultiCondition or(Condition... condition);

    public abstract Condition not(Condition condition);

    public abstract Condition equals(String attributeName, String value);

    public abstract Condition equals(String attributeName, int value);

    public abstract Condition headerEquals(String headerName, String value);

    public abstract Condition gte(String attributeName, String value);

    public abstract Condition gt(String attributeName, String value);

    public abstract Condition lt(String attributeName, String value);

    public abstract Condition like(String attributeName, String value);

    public abstract Condition isNull(String attributeName);

    public abstract Condition isTrue(String attributeName);

    public abstract Condition isFalse(String attributeName);

    /**
     * Search mail and generic folders under given folder.
     * Exclude calendar and contacts folders
     *
     * @param folderName Exchange folder name
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public List<Folder> getSubFolders(String folderName, boolean recursive) throws IOException {
        return getSubFolders(folderName, or(equals("folderclass", "IPF.Note"), isNull("folderclass")),
                recursive);
    }

    /**
     * Search calendar folders under given folder.
     *
     * @param folderName Exchange folder name
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public List<Folder> getSubCalendarFolders(String folderName, boolean recursive) throws IOException {
        return getSubFolders(folderName, equals("folderclass", "IPF.Appointment"), recursive);
    }

    /**
     * Search folders under given folder matching filter.
     *
     * @param folderName Exchange folder name
     * @param condition  search filter
     * @param recursive  deep search if true
     * @return list of folders
     * @throws IOException on error
     */
    public abstract List<Folder> getSubFolders(String folderName, Condition condition, boolean recursive) throws IOException;

    /**
     * Delete oldest messages in trash.
     * keepDelay is the number of days to keep messages in trash before delete
     *
     * @throws IOException when unable to purge messages
     */
    public void purgeOldestTrashAndSentMessages() throws IOException {
        int keepDelay = Settings.getIntProperty("davmail.keepDelay");
        if (keepDelay != 0) {
            purgeOldestFolderMessages(TRASH, keepDelay);
        }
        // this is a new feature, default is : do nothing
        int sentKeepDelay = Settings.getIntProperty("davmail.sentKeepDelay");
        if (sentKeepDelay != 0) {
            purgeOldestFolderMessages(SENT, sentKeepDelay);
        }
    }

    protected void purgeOldestFolderMessages(String folderPath, int keepDelay) throws IOException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -keepDelay);
        LOGGER.debug("Delete messages in " + folderPath + " not modified since " + cal.getTime());

        MessageList messages = searchMessages(folderPath, UID_MESSAGE_ATTRIBUTES,
                lt("lastmodified", formatSearchDate(cal.getTime())));

        for (Message message : messages) {
            message.delete();
        }
    }

    /**
     * Send message in reader to recipients.
     * Detect visible recipients in message body to determine bcc recipients
     *
     * @param recipients recipients list
     * @param reader     message stream
     * @throws IOException on error
     */
    public void sendMessage(List<String> recipients, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        StringBuilder mailBuffer = new StringBuilder();
        StringBuilder recipientBuffer = new StringBuilder();
        boolean inHeader = true;
        boolean inRecipientHeader = false;
        while (!".".equals(line)) {
            mailBuffer.append(line).append((char) 13).append((char) 10);
            line = reader.readLine();
            // Exchange 2007 : skip From: header
            if ((inHeader && line.length() >= 5)) {
                String prefix = line.substring(0, 5).toLowerCase();
                if ("from:".equals(prefix)) {
                    line = reader.readLine();
                }
            }

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
        }
        // remove visible recipients from list
        List<String> visibleRecipients = new ArrayList<String>();
        for (String recipient : recipients) {
            if (recipientBuffer.indexOf(recipient) >= 0) {
                visibleRecipients.add(recipient);
            }
        }
        recipients.removeAll(visibleRecipients);

        StringBuilder bccBuffer = new StringBuilder();
        for (String recipient : recipients) {
            if (bccBuffer.length() > 0) {
                bccBuffer.append(',');
            }
            bccBuffer.append('<');
            bccBuffer.append(recipient);
            bccBuffer.append('>');
        }

        String bcc = bccBuffer.toString();
        HashMap<String, String> properties = new HashMap<String, String>();
        if (bcc.length() > 0) {
            properties.put("bcc", bcc);
        }

        sendMessage(properties, mailBuffer.toString());
    }

    /**
     * Convert logical or relative folder path to absolute folder path.
     *
     * @param folderName folder name
     * @return folder path
     */
    public String getFolderPath(String folderName) {
        String folderPath;
        if (folderName.startsWith(INBOX)) {
            folderPath = folderName.replaceFirst(INBOX, inboxUrl);
        } else if (folderName.startsWith(TRASH)) {
            folderPath = folderName.replaceFirst(TRASH, deleteditemsUrl);
        } else if (folderName.startsWith(DRAFTS)) {
            folderPath = folderName.replaceFirst(DRAFTS, draftsUrl);
        } else if (folderName.startsWith(SENT)) {
            folderPath = folderName.replaceFirst(SENT, sentitemsUrl);
        } else if (folderName.startsWith(CALENDAR)) {
            folderPath = folderName.replaceFirst(CALENDAR, calendarUrl);
        } else if (folderName.startsWith(CONTACTS)) {
            folderPath = folderName.replaceFirst(CONTACTS, contactsUrl);
        } else if (folderName.startsWith("public")) {
            folderPath = publicFolderUrl + folderName.substring("public".length());
            // absolute folder path
        } else if (folderName.startsWith("/")) {
            folderPath = folderName;
        } else {
            folderPath = mailPath + folderName;
        }
        return folderPath;
    }

    /**
     * Get folder object.
     * Folder name can be logical names INBOX, Drafts, Trash or calendar,
     * or a path relative to user base folder or absolute path.
     *
     * @param folderName folder name
     * @return Folder object
     * @throws IOException on error
     */
    public abstract Folder getFolder(String folderName) throws IOException;

    /**
     * Check folder ctag and reload messages as needed.
     *
     * @param currentFolder current folder
     * @return true if folder changed
     * @throws IOException on error
     */
    public boolean refreshFolder(Folder currentFolder) throws IOException {
        Folder newFolder = getFolder(currentFolder.folderPath);
        if (currentFolder.ctag == null || !currentFolder.ctag.equals(newFolder.ctag)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Contenttag changed on " + currentFolder.folderPath + ' '
                        + currentFolder.ctag + " => " + newFolder.ctag + ", reloading messages");
            }
            currentFolder.hasChildren = newFolder.hasChildren;
            currentFolder.noInferiors = newFolder.noInferiors;
            currentFolder.unreadCount = newFolder.unreadCount;
            currentFolder.ctag = newFolder.ctag;
            currentFolder.etag = newFolder.etag;
            currentFolder.loadMessages();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create Exchange message folder.
     *
     * @param folderName logical folder name
     * @throws IOException on error
     */
    public void createMessageFolder(String folderName) throws IOException {
        createFolder(folderName, "IPF.Note");
    }

    /**
     * Create Exchange calendar folder.
     *
     * @param folderName logical folder name
     * @throws IOException on error
     */
    public void createCalendarFolder(String folderName) throws IOException {
        createFolder(folderName, "IPF.Appointment");
    }

    /**
     * Create Exchange folder with given folder class.
     *
     * @param folderName  logical folder name
     * @param folderClass folder class
     * @throws IOException on error
     */
    public abstract void createFolder(String folderName, String folderClass) throws IOException;

    /**
     * Delete Exchange folder.
     *
     * @param folderName logical folder name
     * @throws IOException on error
     */
    public abstract void deleteFolder(String folderName) throws IOException;

    /**
     * Copy message to target folder
     *
     * @param message      Exchange message
     * @param targetFolder target folder
     * @throws IOException on error
     */
    public abstract void copyMessage(Message message, String targetFolder) throws IOException;

    /**
     * Move folder to target name.
     *
     * @param folderName current folder name/path
     * @param targetName target folder name/path
     * @throws IOException on error
     */
    public abstract void moveFolder(String folderName, String targetName) throws IOException;

    protected abstract void moveToTrash(Message message) throws IOException;

    /**
     * Exchange folder with IMAP properties
     */
    public class Folder {
        /**
         * Logical (IMAP) folder path.
         */
        public String folderPath;

        /**
         * Display Name.
         */
        public String displayName;
        /**
         * Folder class (PR_CONTAINER_CLASS).
         */
        public String folderClass;
        /**
         * Folder unread message count.
         */
        public int unreadCount;
        /**
         * true if folder has subfolders (DAV:hassubs).
         */
        public boolean hasChildren;
        /**
         * true if folder has no subfolders (DAV:nosubs).
         */
        public boolean noInferiors;
        /**
         * Folder content tag (to detect folder content changes).
         */
        public String ctag;
        /**
         * Folder etag (to detect folder object changes).
         */
        public String etag;
        /**
         * Folder message list, empty before loadMessages call.
         */
        public ExchangeSession.MessageList messages;
        /**
         * PermanentURL to UID map.
         */
        private final HashMap<String, Long> uidUrlHashMap = new HashMap<String, Long>();

        /**
         * Get IMAP folder flags.
         *
         * @return folder flags in IMAP format
         */
        public String getFlags() {
            if (noInferiors) {
                return "\\NoInferiors";
            } else if (hasChildren) {
                return "\\HasChildren";
            } else {
                return "\\HasNoChildren";
            }
        }

        /**
         * Load folder messages.
         *
         * @throws IOException on error
         */
        public void loadMessages() throws IOException {
            messages = ExchangeSession.this.searchMessages(folderPath, null);
            fixUids(messages);
        }

        /**
         * Search messages in folder matching query.
         *
         * @param condition search query
         * @return message list
         * @throws IOException on error
         */
        public MessageList searchMessages(Condition condition) throws IOException {
            MessageList localMessages = ExchangeSession.this.searchMessages(folderPath, condition);
            fixUids(localMessages);
            return localMessages;
        }

        /**
         * Restore previous uids changed by a PROPPATCH (flag change).
         *
         * @param messages message list
         */
        protected void fixUids(MessageList messages) {
            boolean sortNeeded = false;
            for (Message message : messages) {
                if (uidUrlHashMap.containsKey(message.getPermanentUrl())) {
                    long previousUid = uidUrlHashMap.get(message.getPermanentUrl());
                    if (message.getImapUid() != previousUid) {
                        LOGGER.debug("Restoring IMAP uid " + message.getImapUid() + " -> " + previousUid + " for message " + message.getPermanentUrl() + " (" + message.messageUrl + ')');
                        message.setImapUid(previousUid);
                        sortNeeded = true;
                    }
                } else {
                    // add message to uid map
                    uidUrlHashMap.put(message.getPermanentUrl(), message.getImapUid());
                }
            }
            if (sortNeeded) {
                Collections.sort(messages);
            }
        }

        /**
         * Folder message count.
         *
         * @return message count
         */
        public int count() {
            return messages.size();
        }

        /**
         * Compute IMAP uidnext.
         *
         * @return max(messageuids)+1
         */
        public long getUidNext() {
            return messages.get(messages.size() - 1).getImapUid() + 1;
        }

        /**
         * Get message at index.
         *
         * @param index message index
         * @return message
         */
        public Message get(int index) {
            return messages.get(index);
        }

        /**
         * Get current folder messages imap uids
         *
         * @return imap uid list
         */
        public List<Long> getImapUidList() {
            ArrayList<Long> imapUidList = new ArrayList<Long>();
            for (ExchangeSession.Message message : messages) {
                imapUidList.add(message.getImapUid());
            }
            return imapUidList;
        }

        /**
         * Calendar folder flag.
         *
         * @return true if this is a calendar folder
         */
        public boolean isCalendar() {
            return "IPF.Appointment".equals(folderClass);
        }

        /**
         * Contact folder flag.
         *
         * @return true if this is a calendar folder
         */
        public boolean isContact() {
            return "IPF.Contact".equals(folderClass);
        }

        /**
         * drop cached message
         */
        public void clearCache() {
            messages.cachedMimeBody = null;
            messages.cachedMimeMessage = null;
            messages.cachedMessageImapUid = 0;
        }
    }

    /**
     * Exchange message.
     */
    public class Message implements Comparable<Message> {
        /**
         * enclosing message list
         */
        public MessageList messageList;
        /**
         * Message url.
         */
        public String messageUrl;
        /**
         * Message permanent url (does not change on message move).
         */
        public String permanentUrl;
        /**
         * Message uid.
         */
        public String uid;
        /**
         * Message IMAP uid, unique in folder (x0e230003).
         */
        public long imapUid;
        /**
         * MAPI message size.
         */
        public int size;
        /**
         * Message date (urn:schemas:mailheader:date).
         */
        public String date;

        /**
         * Message flag: read.
         */
        public boolean read;
        /**
         * Message flag: deleted.
         */
        public boolean deleted;
        /**
         * Message flag: junk.
         */
        public boolean junk;
        /**
         * Message flag: flagged.
         */
        public boolean flagged;
        /**
         * Message flag: draft.
         */
        public boolean draft;
        /**
         * Message flag: answered.
         */
        public boolean answered;
        /**
         * Message flag: fowarded.
         */
        public boolean forwarded;

        /**
         * Unparsed message content.
         */
        protected SharedByteArrayInputStream mimeBody;

        /**
         * Message content parsed in a MIME message.
         */
        protected MimeMessage mimeMessage;

        /**
         * IMAP uid , unique in folder (x0e230003)
         *
         * @return IMAP uid
         */
        public long getImapUid() {
            return imapUid;
        }

        /**
         * Set IMAP uid.
         *
         * @param imapUid new uid
         */
        public void setImapUid(long imapUid) {
            this.imapUid = imapUid;
        }

        /**
         * Exchange uid.
         *
         * @return uid
         */
        public String getUid() {
            return uid;
        }

        /**
         * Return permanent message url.
         *
         * @return permanent message url
         */
        public String getPermanentUrl() {
            return permanentUrl;
        }

        /**
         * Return message flags in IMAP format.
         *
         * @return IMAP flags
         */
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

        /**
         * Write MIME message to os
         *
         * @param os        output stream
         * @param doubleDot replace '.' lines with '..' (POP protocol)
         * @throws IOException on error
         */
        public void write(OutputStream os, boolean doubleDot) throws IOException {
            BufferedReader reader = getContentReader(this);
            try {
                OutputStreamWriter isoWriter = new OutputStreamWriter(os);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (doubleDot && ".".equals(line)) {
                        line = "..";
                        // patch text/calendar to include utf-8 encoding
                    } else if ("Content-Type: text/calendar;".equals(line)) {
                        StringBuilder headerBuffer = new StringBuilder();
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
                    }
                    isoWriter.write(line);
                    isoWriter.write((char) 13);
                    isoWriter.write((char) 10);
                }
                isoWriter.flush();
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOGGER.warn("Error closing message input stream", e);
                }
            }
        }

        /**
         * Load message content in a Mime message
         *
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        protected void loadMimeMessage() throws IOException, MessagingException {
            if (mimeMessage == null) {
                // try to get message content from cache
                if (this.imapUid == messageList.cachedMessageImapUid) {
                    mimeBody = messageList.cachedMimeBody;
                    mimeMessage = messageList.cachedMimeMessage;
                    LOGGER.debug("Got message content for " + imapUid + " from cache");
                } else {
                    // load message
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    write(baos, false);
                    // load and parse message
                    mimeBody = new SharedByteArrayInputStream(baos.toByteArray());
                    mimeMessage = new MimeMessage(null, mimeBody);
                    LOGGER.debug("Downloaded message content for " + imapUid + " (" + baos.size() + ')');
                }
            }
        }

        /**
         * Get message content as a Mime message.
         *
         * @return mime message
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public MimeMessage getMimeMessage() throws IOException, MessagingException {
            loadMimeMessage();
            return mimeMessage;
        }

        /**
         * Get message body size.
         *
         * @return mime message size
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public int getMimeMessageSize() throws IOException, MessagingException {
            loadMimeMessage();
            mimeBody.reset();
            return mimeBody.available();
        }

        /**
         * Get message body input stream.
         *
         * @return mime message InputStream
         * @throws IOException        on error
         * @throws MessagingException on error
         */
        public InputStream getRawInputStream() throws IOException, MessagingException {
            loadMimeMessage();
            mimeBody.reset();
            return mimeBody;
        }


        /**
         * Drop mime message to avoid keeping message content in memory,
         * keep a single message in MessageList cache to handle chunked fetch.
         */
        public void dropMimeMessage() {
            // update single message cache
            if (mimeMessage != null) {
                messageList.cachedMessageImapUid = imapUid;
                messageList.cachedMimeBody = mimeBody;
                messageList.cachedMimeMessage = mimeMessage;
            }
            mimeMessage = null;
        }

        /**
         * Delete message.
         *
         * @throws IOException on error
         */
        public void delete() throws IOException {
            LOGGER.debug("Delete " + permanentUrl + " (" + messageUrl + ")");
            deleteMessage(this);
        }

        /**
         * Move message to trash, mark message read.
         *
         * @throws IOException on error
         */
        public void moveToTrash() throws IOException {
            // mark message as read
            HashMap<String, String> properties = new HashMap<String, String>();
            properties.put("read", "1");
            updateMessage(this, properties);

            ExchangeSession.this.moveToTrash(this);
        }

        /**
         * Comparator to sort messages by IMAP uid
         *
         * @param message other message
         * @return imapUid comparison result
         */
        public int compareTo(Message message) {
            long compareValue = (imapUid - message.imapUid);
            if (compareValue > 0) {
                return 1;
            } else if (compareValue < 0) {
                return -1;
            } else {
                return 0;
            }
        }

        /**
         * Override equals, compare IMAP uids
         *
         * @param message other message
         * @return true if IMAP uids are equal
         */
        @Override
        public boolean equals(Object message) {
            return message instanceof Message && imapUid == ((Message) message).imapUid;
        }

        /**
         * Override hashCode, return imapUid hashcode.
         *
         * @return imapUid hashcode
         */
        @Override
        public int hashCode() {
            return (int) (imapUid ^ (imapUid >>> 32));
        }
    }

    /**
     * Message list, includes a single messsage cache
     */
    public static class MessageList extends ArrayList<Message> {
        /**
         * Cached message content parsed in a MIME message.
         */
        protected transient MimeMessage cachedMimeMessage;
        /**
         * Cached message uid.
         */
        protected transient long cachedMessageImapUid;
        /**
         * Cached unparsed message
         */
        protected transient SharedByteArrayInputStream cachedMimeBody;

    }

    /**
     * Generic folder item.
     */
    public abstract class Item {
        protected String href;
        protected String permanentUrl;
        public String displayName;
        public String etag;
        protected String contentClass;
        protected String noneMatch;
        /**
         * ICS content
         */
        protected String itemBody;

        /**
         * Build item instance.
         *
         * @param messageUrl   message url
         * @param contentClass content class
         * @param itemBody     item body
         * @param etag         item etag
         * @param noneMatch    none match flag
         */
        public Item(String messageUrl, String contentClass, String itemBody, String etag, String noneMatch) {
            this.href = messageUrl;
            this.contentClass = contentClass;
            this.itemBody = itemBody;
            this.etag = etag;
            this.noneMatch = noneMatch;
        }

        /**
         * Default constructor.
         */
        protected Item() {
        }

        /**
         * Return item content type
         *
         * @return content type
         */
        public abstract String getContentType();

        /**
         * Retrieve item body from Exchange
         *
         * @return item body
         * @throws HttpException on error
         */
        public abstract String getBody() throws HttpException;

        /**
         * Get event name (file name part in URL).
         *
         * @return event name
         */
        public String getName() {
            int index = href.lastIndexOf('/');
            if (index >= 0) {
                return href.substring(index + 1);
            } else {
                return href;
            }
        }

        /**
         * Get event etag (last change tag).
         *
         * @return event etag
         */
        public String getEtag() {
            return etag;
        }

        protected HttpException buildHttpException(Exception e) {
            String message = "Unable to get event " + getName() + " at " + permanentUrl + ": " + e.getMessage();
            LOGGER.warn(message);
            return new HttpException(message);
        }
    }

    /**
     * Calendar event object
     */
    public abstract class Contact extends Item {

        /**
         * @inheritDoc
         */
        public Contact(String messageUrl, String contentClass, String itemBody, String etag, String noneMatch) {
            super(messageUrl.endsWith(".vcf") ? messageUrl.substring(0, messageUrl.length() - 3) + "EML" : messageUrl, contentClass, itemBody, etag, noneMatch);
        }

        /**
         * @inheritDoc
         */
        protected Contact() {
        }

        /**
         * Convert EML extension to vcf.
         *
         * @return item name
         */
        @Override
        public String getName() {
            String name = super.getName();
            if (name.endsWith(".EML")) {
                name = name.substring(0, name.length() - 3) + "vcf";
            }
            return name;
        }

        /**
         * Compute vcard uid from name.
         *
         * @return uid
         * @throws URIException on error
         */
        protected String getUid() throws URIException {
            String uid = getName();
            int dotIndex = uid.lastIndexOf('.');
            if (dotIndex > 0) {
                uid = uid.substring(0, dotIndex);
            }
            return URIUtil.encodePath(uid);
        }

        @Override
        public String getContentType() {
            return "text/vcard";
        }

    }

    /**
     * Calendar event object.
     */
    public abstract class Event extends Item {

        /**
         * {@inheritDoc}
         */
        public Event(String messageUrl, String contentClass, String itemBody, String etag, String noneMatch) {
            super(messageUrl, contentClass, itemBody, etag, noneMatch);
        }

        /**
         * @inheritDoc
         */
        protected Event() {
        }

        @Override
        public String getContentType() {
            return "text/calendar;charset=UTF-8";
        }

        protected static final String TEXT_CALENDAR = "text/calendar";
        protected static final String APPLICATION_ICS = "application/ics";

        protected boolean isCalendarContentType(String contentType) {
            return TEXT_CALENDAR.regionMatches(true, 0, contentType, 0, TEXT_CALENDAR.length()) ||
                    APPLICATION_ICS.regionMatches(true, 0, contentType, 0, APPLICATION_ICS.length());
        }

        protected MimePart getCalendarMimePart(MimeMultipart multiPart) throws IOException, MessagingException {
            MimePart bodyPart = null;
            for (int i = 0; i < multiPart.getCount(); i++) {
                String contentType = multiPart.getBodyPart(i).getContentType();
                if (isCalendarContentType(contentType)) {
                    bodyPart = (MimePart) multiPart.getBodyPart(i);
                    break;
                } else if (contentType.startsWith("multipart")) {
                    Object content = multiPart.getBodyPart(i).getContent();
                    if (content instanceof MimeMultipart) {
                        bodyPart = getCalendarMimePart((MimeMultipart) content);
                    }
                }
            }

            return bodyPart;
        }

        protected String fixTimezoneId(String line, String validTimezoneId) {
            return StringUtil.replaceToken(line, "TZID=", ":", validTimezoneId);
        }

        protected void splitExDate(ICSBufferedWriter result, String line) {
            int cur = line.lastIndexOf(':') + 1;
            String start = line.substring(0, cur);

            for (int next = line.indexOf(',', cur); next != -1; next = line.indexOf(',', cur)) {
                String val = line.substring(cur, next);
                result.writeLine(start + val);

                cur = next + 1;
            }

            result.writeLine(start + line.substring(cur));
        }

        protected String getAllDayLine(String line) throws IOException {
            int valueIndex = line.lastIndexOf(':');
            int valueEndIndex = line.lastIndexOf('T');
            if (valueIndex < 0 || valueEndIndex < 0) {
                throw new DavMailException("EXCEPTION_INVALID_ICS_LINE", line);
            }
            int keyIndex = line.indexOf(';');
            if (keyIndex == -1) {
                keyIndex = valueIndex;
            }
            String dateValue = line.substring(valueIndex + 1, valueEndIndex);
            String key = line.substring(0, Math.min(keyIndex, valueIndex));
            return key + ";VALUE=DATE:" + dateValue;
        }

        protected String fixICS(String icsBody, boolean fromServer) throws IOException {
            // first pass : detect
            class AllDayState {
                boolean isAllDay;
                boolean hasCdoAllDay;
                boolean isCdoAllDay;
            }

            dumpIndex++;
            dumpICS(icsBody, fromServer, false);

            // Convert event class from and to iCal
            // See https://trac.calendarserver.org/browser/CalendarServer/trunk/doc/Extensions/caldav-privateevents.txt
            boolean isAppleiCal = false;
            boolean hasAttendee = false;
            boolean hasCdoBusyStatus = false;
            // detect ics event with empty timezone (all day from Lightning)
            boolean hasTimezone = false;
            String transp = null;
            String validTimezoneId = null;
            String eventClass = null;
            String organizer = null;
            String action = null;
            boolean sound = false;

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
                        } else if ("PRODID".equals(key) && line.contains("iCal")) {
                            // detect iCal created events
                            isAppleiCal = true;
                        } else if (isAppleiCal && "X-CALENDARSERVER-ACCESS".equals(key)) {
                            eventClass = value;
                        } else if (!isAppleiCal && "CLASS".equals(key)) {
                            eventClass = value;
                        } else if ("ACTION".equals(key)) {
                            action = value;
                        } else if ("ATTACH;VALUES=URI".equals(key)) {
                            // This is a marker that this event has an alarm with sound
                            sound = true;
                        } else if (key.startsWith("ORGANIZER")) {
                            if (value.startsWith("MAILTO:")) {
                                organizer = value.substring(7);
                            } else {
                                organizer = value;
                            }
                        } else if (key.startsWith("ATTENDEE")) {
                            hasAttendee = true;
                        } else if ("TRANSP".equals(key)) {
                            transp = value;
                        } else if (line.startsWith("TZID:(GMT") ||
                                // additional test for Outlook created recurring events
                                line.startsWith("TZID:GMT ")) {
                            try {
                                validTimezoneId = ResourceBundle.getBundle("timezones").getString(value);
                            } catch (MissingResourceException mre) {
                                LOGGER.warn(new BundleMessage("LOG_INVALID_TIMEZONE", value));
                            }
                        } else if ("X-MICROSOFT-CDO-BUSYSTATUS".equals(key)) {
                            hasCdoBusyStatus = true;
                        } else if ("BEGIN:VTIMEZONE".equals(line)) {
                            hasTimezone = true;
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
                    // remove empty properties
                    if ("CLASS:".equals(line) || "LOCATION:".equals(line)) {
                        continue;
                    }
                    // fix invalid exchange timezoneid
                    if (validTimezoneId != null && line.indexOf(";TZID=") >= 0) {
                        line = fixTimezoneId(line, validTimezoneId);
                    }
                    if (fromServer && line.startsWith("PRODID:") && eventClass != null) {
                        result.writeLine(line);
                        // set global calendarserver access for iCal 4
                        if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:CONFIDENTIAL");
                        } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:PRIVATE");
                        } else if (eventClass != null) {
                            result.writeLine("X-CALENDARSERVER-ACCESS:" + eventClass);
                        }
                        continue;
                    }
                    if (!fromServer && "BEGIN:VEVENT".equals(line) && !hasTimezone) {
                        result.write(ExchangeSession.this.getVTimezone().timezoneBody);
                        hasTimezone = true;
                    }
                    if (!fromServer && currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE".equals(line)) {
                        line = "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE";
                    } else if (!fromServer && "END:VEVENT".equals(line)) {
                        if (!hasCdoBusyStatus) {
                            result.writeLine("X-MICROSOFT-CDO-BUSYSTATUS:" + (!"TRANSPARENT".equals(transp) ? "BUSY" : "FREE"));
                        }
                        if (currentAllDayState.isAllDay && !currentAllDayState.hasCdoAllDay) {
                            result.writeLine("X-MICROSOFT-CDO-ALLDAYEVENT:TRUE");
                        }
                        // add organizer line to all events created in Exchange for active sync
                        if (organizer == null) {
                            result.writeLine("ORGANIZER:MAILTO:" + email);
                        }
                        if (isAppleiCal) {
                            if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                                result.writeLine("CLASS:PRIVATE");
                            } else if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                                result.writeLine("CLASS:CONFIDENTIAL");
                            } else {
                                result.writeLine("CLASS:" + eventClass);
                            }
                        }
                    } else if (!fromServer && line.startsWith("X-MICROSOFT-CDO-BUSYSTATUS:")) {
                        line = "X-MICROSOFT-CDO-BUSYSTATUS:" + (!"TRANSPARENT".equals(transp) ? "BUSY" : "FREE");
                    } else if (!fromServer && !currentAllDayState.isAllDay && "X-MICROSOFT-CDO-ALLDAYEVENT:TRUE".equals(line)) {
                        line = "X-MICROSOFT-CDO-ALLDAYEVENT:FALSE";
                    } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTSTART") && !line.startsWith("DTSTART;VALUE=DATE")) {
                        line = getAllDayLine(line);
                    } else if (fromServer && currentAllDayState.isCdoAllDay && line.startsWith("DTEND") && !line.startsWith("DTEND;VALUE=DATE")) {
                        line = getAllDayLine(line);
                    } else if (!fromServer && currentAllDayState.isAllDay && line.startsWith("DTSTART") && line.startsWith("DTSTART;VALUE=DATE")) {
                        line = "DTSTART;TZID=\"" + ExchangeSession.this.getVTimezone().timezoneId + "\":" + line.substring(19) + "T000000";
                    } else if (!fromServer && currentAllDayState.isAllDay && line.startsWith("DTEND") && line.startsWith("DTEND;VALUE=DATE")) {
                        line = "DTEND;TZID=\"" + ExchangeSession.this.getVTimezone().timezoneId + "\":" + line.substring(17) + "T000000";
                    } else if (line.startsWith("TZID:") && validTimezoneId != null) {
                        line = "TZID:" + validTimezoneId;
                    } else if ("BEGIN:VEVENT".equals(line)) {
                        currentAllDayState = allDayStates.get(count++);
                        // remove calendarserver access
                    } else if (line.startsWith("X-CALENDARSERVER-ACCESS:")) {
                        continue;
                    } else if (line.startsWith("EXDATE;TZID=") || line.startsWith("EXDATE:")) {
                        // Apple iCal doesn't support EXDATE with multiple exceptions
                        // on one line.  Split into multiple EXDATE entries (which is
                        // also legal according to the caldav standard).
                        splitExDate(result, line);
                        continue;
                    } else if (line.startsWith("X-ENTOURAGE_UUID:")) {
                        // Apple iCal doesn't understand this key, and it's entourage
                        // specific (i.e. not needed by any caldav client): strip it out
                        continue;
                    } else if (fromServer && line.startsWith("ATTENDEE;")
                            && (line.indexOf(email) >= 0)) {
                        // If this is coming from the server, strip out RSVP for this
                        // user as an attendee where the partstat is something other
                        // than PARTSTAT=NEEDS-ACTION since the RSVP confuses iCal4 into
                        // thinking the attendee has not replied

                        int rsvpSuffix = line.indexOf("RSVP=TRUE;");
                        int rsvpPrefix = line.indexOf(";RSVP=TRUE");

                        if (((rsvpSuffix >= 0) || (rsvpPrefix >= 0))
                                && (line.indexOf("PARTSTAT=") >= 0)
                                && (line.indexOf("PARTSTAT=NEEDS-ACTION") < 0)) {

                            // Strip out the "RSVP" line from the calendar entry
                            if (rsvpSuffix >= 0) {
                                line = line.substring(0, rsvpSuffix) + line.substring(rsvpSuffix + 10);
                            } else {
                                line = line.substring(0, rsvpPrefix) + line.substring(rsvpPrefix + 10);
                            }

                        }
                    } else if (line.startsWith("ACTION:")) {
                        if (fromServer && "DISPLAY".equals(action)
                                // convert DISPLAY to AUDIO only if user defined an alarm sound 
                                && Settings.getProperty("davmail.caldavAlarmSound") != null) {
                            // Convert alarm to audio for iCal
                            result.writeLine("ACTION:AUDIO");

                            if (!sound) {
                                // Add defined sound into the audio alarm
                                result.writeLine("ATTACH;VALUE=URI:" + Settings.getProperty("davmail.caldavAlarmSound"));
                            }

                            continue;
                        } else if (!fromServer && "AUDIO".equals(action)) {
                            // Use the alarm action that exchange (and blackberry) understand
                            // (exchange and blackberry don't understand audio actions)

                            result.writeLine("ACTION:DISPLAY");
                            continue;
                        }

                        // Don't recognize this type of action: pass it through

                    } else if (line.startsWith("CLASS:")) {
                        if (!fromServer && isAppleiCal) {
                            continue;
                        } else {
                            // still set calendarserver access inside event for iCal 3
                            if ("PRIVATE".equalsIgnoreCase(eventClass)) {
                                result.writeLine("X-CALENDARSERVER-ACCESS:CONFIDENTIAL");
                            } else if ("CONFIDENTIAL".equalsIgnoreCase(eventClass)) {
                                result.writeLine("X-CALENDARSERVER-ACCESS:PRIVATE");
                            } else {
                                result.writeLine("X-CALENDARSERVER-ACCESS:" + eventClass);
                            }
                        }
                        // remove organizer line if user is organizer for iPhone
                    } else if (fromServer && line.startsWith("ORGANIZER") && !hasAttendee) {
                        continue;
                    } else if (organizer != null && line.startsWith("ATTENDEE") && line.contains(organizer)) {
                        // Ignore organizer as attendee
                        continue;
                    } else if (!fromServer && line.startsWith("ATTENDEE")) {
                        line = replaceIcal4Principal(line);
                    }

                    result.writeLine(line);
                }
            } finally {
                reader.close();
            }

            String resultString = result.toString();
            dumpICS(resultString, fromServer, true);

            return resultString;
        }

        protected void dumpICS(String icsBody, boolean fromServer, boolean after) {
            String logFileDirectory = Settings.getLogFileDirectory();

            // additional setting to activate ICS dump (not available in GUI)
            int dumpMax = Settings.getIntProperty("davmail.dumpICS");
            if (dumpMax > 0) {
                if (dumpIndex > dumpMax) {
                    // Delete the oldest dump file
                    final int oldest = dumpIndex - dumpMax;
                    try {
                        File[] oldestFiles = (new File(logFileDirectory)).listFiles(new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                if (name.endsWith(".ics")) {
                                    int dashIndex = name.indexOf('-');
                                    if (dashIndex > 0) {
                                        try {
                                            int fileIndex = Integer.parseInt(name.substring(0, dashIndex));
                                            return fileIndex < oldest;
                                        } catch (NumberFormatException nfe) {
                                            // ignore
                                        }
                                    }
                                }
                                return false;
                            }
                        });
                        for (File file : oldestFiles) {
                            if (!file.delete()) {
                                LOGGER.warn("Unable to delete " + file.getAbsolutePath());
                            }
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("Error deleting ics dump: " + ex.getMessage());
                    }
                }

                StringBuilder filePath = new StringBuilder();
                filePath.append(logFileDirectory).append('/')
                        .append(dumpIndex)
                        .append(after ? "-to" : "-from")
                        .append((after ^ fromServer) ? "-server" : "-client")
                        .append(".ics");
                if ((icsBody != null) && (icsBody.length() > 0)) {
                    FileWriter fileWriter = null;
                    try {
                        fileWriter = new FileWriter(filePath.toString());
                        fileWriter.write(icsBody);
                    } catch (IOException e) {
                        LOGGER.error(e);
                    } finally {
                        if (fileWriter != null) {
                            try {
                                fileWriter.close();
                            } catch (IOException e) {
                                LOGGER.error(e);
                            }
                        }
                    }


                }
            }

        }

        protected String getICSValue(String icsBody, String prefix, String defval) throws IOException {
            // only return values in VEVENT section, not VALARM
            Stack<String> sectionStack = new Stack<String>();
            BufferedReader reader = null;

            try {
                reader = new ICSBufferedReader(new StringReader(icsBody));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("BEGIN:")) {
                        sectionStack.push(line);
                    } else if (line.startsWith("END:") && !sectionStack.isEmpty()) {
                        sectionStack.pop();
                    } else if (!sectionStack.isEmpty() && "BEGIN:VEVENT".equals(sectionStack.peek()) && line.startsWith(prefix)) {
                        return line.substring(prefix.length());
                    }
                }

            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

            return defval;
        }

        protected String getICSSummary(String icsBody) throws IOException {
            return getICSValue(icsBody, "SUMMARY:", BundleMessage.format("MEETING_REQUEST"));
        }

        protected String getICSDescription(String icsBody) throws IOException {
            return getICSValue(icsBody, "DESCRIPTION:", "");
        }

        class Participants {
            String attendees;
            String optionalAttendees;
            String organizer;
        }

        /**
         * Parse ics event for attendees and organizer.
         * For notifications, only include attendees with RSVP=TRUE or PARTSTAT=NEEDS-ACTION
         *
         * @param isNotification get only notified attendees
         * @return participants
         * @throws IOException on error
         */
        protected Participants getParticipants(boolean isNotification) throws IOException {
            HashSet<String> attendees = new HashSet<String>();
            HashSet<String> optionalAttendees = new HashSet<String>();
            String organizer = null;
            BufferedReader reader = null;
            try {
                reader = new ICSBufferedReader(new StringReader(itemBody));
                String line;
                while ((line = reader.readLine()) != null) {
                    int index = line.indexOf(':');
                    if (index >= 0) {
                        String key = line.substring(0, index);
                        String value = line.substring(index + 1);
                        int semiColon = key.indexOf(';');
                        if (semiColon >= 0) {
                            key = key.substring(0, semiColon);
                        }
                        if ("ORGANIZER".equals(key) || "ATTENDEE".equals(key)) {
                            int colonIndex = value.indexOf(':');
                            if (colonIndex >= 0) {
                                value = value.substring(colonIndex + 1);
                            }
                            value = replaceIcal4Principal(value);
                            if ("ORGANIZER".equals(key)) {
                                organizer = value;
                                // exclude current user and invalid values from recipients
                                // also exclude no action attendees
                            } else if (!email.equalsIgnoreCase(value) && value.indexOf('@') >= 0
                                    // return all attendees for user calendar folder, filter for notifications
                                    && (!isNotification
                                    // notify attendee if reply explicitly requested
                                    || (line.indexOf("RSVP=TRUE") >= 0)
                                    || (
                                    // workaround for iCal bug: do not notify if reply explicitly not requested
                                    !(line.indexOf("RSVP=FALSE") >= 0) &&
                                            ((line.indexOf("PARTSTAT=NEEDS-ACTION") >= 0
                                                    // need to include other PARTSTATs participants for CANCEL notifications
                                                    || line.indexOf("PARTSTAT=ACCEPTED") >= 0
                                                    || line.indexOf("PARTSTAT=DECLINED") >= 0
                                                    || line.indexOf("PARTSTAT=TENTATIVE") >= 0))
                            ))) {
                                if (line.indexOf("ROLE=OPT-PARTICIPANT") >= 0) {
                                    optionalAttendees.add(value);
                                } else {
                                    attendees.add(value);
                                }
                            }
                        }
                    }
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
            Participants participants = new Participants();
            participants.attendees = StringUtil.join(attendees, ", ");
            participants.optionalAttendees = StringUtil.join(optionalAttendees, ", ");
            participants.organizer = organizer;
            return participants;
        }

        protected String getICSMethod(String icsBody) {
            String icsMethod = StringUtil.getToken(icsBody, "METHOD:", "\r");
            if (icsMethod == null) {
                // default method is REQUEST
                icsMethod = "REQUEST";
            }
            return icsMethod;
        }

        public ItemResult createOrUpdate() throws IOException {
            String boundary = UUID.randomUUID().toString();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MimeOutputStreamWriter writer = new MimeOutputStreamWriter(baos);
            int status = 0;
            String method = getICSMethod(itemBody);

            writer.writeHeader("Content-Transfer-Encoding", "7bit");
            writer.writeHeader("Content-class", contentClass);
            // append date
            writer.writeHeader("Date", new Date());

            // Make sure invites have a proper subject line
            writer.writeHeader("Subject", getICSSummary(itemBody));

            if ("urn:content-classes:calendarmessage".equals(contentClass)) {
                // need to parse attendees and organizer to build recipients
                Participants participants = getParticipants(true);
                if (email.equalsIgnoreCase(participants.organizer)) {
                    // current user is organizer => notify all
                    writer.writeHeader("To", participants.attendees);
                    writer.writeHeader("Cc", participants.optionalAttendees);
                    // do not send notification if no recipients found
                    if (participants.attendees == null && participants.optionalAttendees == null) {
                        status = HttpStatus.SC_NO_CONTENT;
                    }
                } else {
                    // notify only organizer
                    writer.writeHeader("To", participants.organizer);
                    // do not send notification if no recipients found
                    if (participants.organizer == null) {
                        status = HttpStatus.SC_NO_CONTENT;
                    }
                }

            } else {
                // need to parse attendees and organizer to build recipients
                Participants participants = getParticipants(false);
                // storing appointment, full recipients header
                if (participants.attendees != null) {
                    writer.writeHeader("To", participants.attendees);
                } else {
                    // use current user as attendee
                    writer.writeHeader("To", email);
                }
                writer.writeHeader("Cc", participants.optionalAttendees);

                if (participants.organizer != null) {
                    writer.writeHeader("From", participants.organizer);
                } else {
                    writer.writeHeader("From", email);
                }
                // if not organizer, set REPLYTIME to force Outlook in attendee mode
                if (participants.organizer != null && !email.equalsIgnoreCase(participants.organizer)) {
                    if (itemBody.indexOf("METHOD:") < 0) {
                        itemBody = itemBody.replaceAll("BEGIN:VCALENDAR", "BEGIN:VCALENDAR\r\nMETHOD:REQUEST");
                    }
                    if (itemBody.indexOf("X-MICROSOFT-CDO-REPLYTIME") < 0) {
                        itemBody = itemBody.replaceAll("END:VEVENT", "X-MICROSOFT-CDO-REPLYTIME:" +
                                getZuluDateFormat().format(new Date()) + "\r\nEND:VEVENT");
                    }
                }
            }
            writer.writeHeader("MIME-Version", "1.0");
            writer.writeHeader("Content-Type", "multipart/alternative;\r\n" +
                    "\tboundary=\"----=_NextPart_" + boundary + '\"');
            writer.writeLn();
            writer.writeLn("This is a multi-part message in MIME format.");
            writer.writeLn();
            writer.writeLn("------=_NextPart_" + boundary);

            // Write a part of the message that contains the
            // ICS description so that invites contain the description text
            String description = getICSDescription(itemBody).replaceAll("\\\\[Nn]", "\r\n");

            if (description.length() > 0) {
                writer.writeHeader("Content-Type", "text/plain;\r\n" +
                        "\tcharset=\"utf-8\"");
                writer.writeHeader("content-transfer-encoding", "8bit");
                writer.writeLn();
                writer.flush();
                baos.write(description.getBytes("UTF-8"));
                writer.writeLn();
                writer.writeLn("------=_NextPart_" + boundary);
            }
            writer.writeHeader("Content-class", contentClass);
            writer.writeHeader("Content-Type", "text/calendar;\r\n" +
                    "\tmethod=" + method + ";\r\n" +
                    "\tcharset=\"utf-8\""
            );
            writer.writeHeader("Content-Transfer-Encoding", "8bit");
            writer.writeLn();
            writer.flush();
            baos.write(fixICS(itemBody, false).getBytes("UTF-8"));
            writer.writeLn();
            writer.writeLn("------=_NextPart_" + boundary + "--");
            writer.close();

            ItemResult itemResult;
            if (status == 0) {
                itemResult = createOrUpdate(baos.toByteArray());
            } else {
                itemResult = new ItemResult();
                itemResult.status = status;
            }

            return itemResult;

        }

        protected abstract ItemResult createOrUpdate(byte[] content) throws IOException;

    }

    protected static final List<String> ITEM_PROPERTIES = new ArrayList<String>();

    static {
        ITEM_PROPERTIES.add("etag");
        ITEM_PROPERTIES.add("displayname");
        // calendar CdoInstanceType
        ITEM_PROPERTIES.add("instancetype");
    }

    /**
     * Search contacts in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of contacts
     * @throws IOException on error
     */
    public List<ExchangeSession.Contact> getAllContacts(String folderName) throws IOException {
        return searchContacts(folderName, ITEM_PROPERTIES, equals("contentclass", "urn:content-classes:person"));
    }


    /**
     * Search contacts in provided folder matching the search query.
     *
     * @param folderPath  Exchange folder path
     * @param searchQuery Exchange search query
     * @return list of contacts
     * @throws IOException on error
     */
    protected abstract List<Contact> searchContacts(String folderName, List<String> attributes, Condition condition) throws IOException;

    /**
     * Search calendar messages in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of calendar messages as Event objects
     * @throws IOException on error
     */
    public List<Event> getEventMessages(String folderPath) throws IOException {
        return searchEvents(folderPath, ITEM_PROPERTIES,
                and(equals("contentclass", "urn:content-classes:calendarmessage"),
                        or(isNull("processed"), isFalse("processed"))));
    }

    /**
     * Search calendar events in provided folder.
     *
     * @param folderPath Exchange folder path
     * @return list of calendar events
     * @throws IOException on error
     */
    public List<Event> getAllEvents(String folderPath) throws IOException {
        int caldavPastDelay = Settings.getIntProperty("davmail.caldavPastDelay");
        Condition dateCondition = null;
        if (caldavPastDelay != 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -caldavPastDelay);
            dateCondition = gt("dtstart", formatSearchDate(cal.getTime()));
        }

        Condition privateCondition = null;
        if (isSharedFolder(folderPath)) {
            LOGGER.debug("Shared or public calendar: exclude private events");
            privateCondition = equals("sensitivity", 0);
        }


        return searchEvents(folderPath, ITEM_PROPERTIES,
                and(or(isNull("instancetype"),
                        equals("instancetype", 1),
                        and(equals("instancetype", 0), dateCondition)),
                        equals("contentclass", "urn:content-classes:appointment"),
                        privateCondition));
    }


    /**
     * Search calendar events or messages in provided folder matching the search query.
     *
     * @param folderPath  Exchange folder path
     * @param searchQuery Exchange search query
     * @return list of calendar messages as Event objects
     * @throws IOException on error
     */
    protected abstract List<Event> searchEvents(String folderPath, List<String> attributes, Condition condition) throws IOException;

    /**
     * convert vcf extension to EML.
     *
     * @param itemName item name
     * @return EML item name
     */
    protected String convertItemNameToEML(String itemName) {
        if (itemName.endsWith(".vcf")) {
            return itemName.substring(0, itemName.length() - 3) + "EML";
        } else {
            return itemName;
        }
    }

    /**
     * Get item named eventName in folder
     *
     * @param folderPath Exchange folder path
     * @param itemName   event name
     * @return event object
     * @throws IOException on error
     */
    public abstract Item getItem(String folderPath, String itemName) throws IOException;

    /**
     * Delete event named eventName in folder
     *
     * @param folderPath Exchange folder path
     * @param itemName   event name
     * @return HTTP status
     * @throws IOException on error
     */
    public abstract int deleteItem(String folderPath, String itemName) throws IOException;

    private static int dumpIndex;

    /**
     * Replace iCal4 (Snow Leopard) principal paths with mailto expression
     *
     * @param value attendee value or ics line
     * @return fixed value
     */
    protected String replaceIcal4Principal(String value) {
        if (value.contains("/principals/__uuids__/")) {
            return value.replaceAll("/principals/__uuids__/([^/]*)__AT__([^/]*)/", "mailto:$1@$2");
        } else {
            return value;
        }
    }

    /**
     * Event result object to hold HTTP status and event etag from an event creation/update.
     */
    public static class ItemResult {
        /**
         * HTTP status
         */
        public int status;
        /**
         * Event etag from response HTTP header
         */
        public String etag;
    }

    /**
     * Build and send the MIME message for the provided ICS event.
     *
     * @param icsBody event in iCalendar format
     * @return HTTP status
     * @throws IOException on error
     */
    public abstract int sendEvent(String icsBody) throws IOException;

    /**
     * Create or update item (event or contact) on the Exchange server
     *
     * @param folderPath Exchange folder path
     * @param itemName   event name
     * @param itemBody   event body in iCalendar format
     * @param etag       previous event etag to detect concurrent updates
     * @param noneMatch  if-none-match header value
     * @return HTTP response event result (status and etag)
     * @throws IOException on error
     */
    public ItemResult createOrUpdateItem(String folderPath, String itemName, String itemBody, String etag, String noneMatch) throws IOException {
        String messageUrl = folderPath + '/' + itemName;
        if (itemBody.startsWith("BEGIN:VCALENDAR")) {
            return internalCreateOrUpdateEvent(messageUrl, "urn:content-classes:appointment", itemBody, etag, noneMatch);
        } else if (itemBody.startsWith("BEGIN:VCARD")) {
            return internalCreateOrUpdateContact(messageUrl, "urn:content-classes:person", itemBody, etag, noneMatch);
        } else {
            throw new IOException(BundleMessage.format("EXCEPTION_INVALID_MESSAGE_CONTENT", itemBody));
        }
    }

    protected abstract ItemResult internalCreateOrUpdateContact(String messageUrl, String contentClass, String icsBody, String etag, String noneMatch) throws IOException;

    protected abstract ItemResult internalCreateOrUpdateEvent(String messageUrl, String contentClass, String icsBody, String etag, String noneMatch) throws IOException;

    /**
     * Get current Exchange alias name from login name
     *
     * @return user name
     */
    protected String getAliasFromLogin() {
        // Exchange 2007 : userName is login without domain
        String result = this.userName;
        int index = result.indexOf('\\');
        if (index >= 0) {
            result = result.substring(index + 1);
        }
        return result;
    }

    /**
     * Get current Exchange alias name from mailbox name
     *
     * @return user name
     */
    protected String getAliasFromMailPath() {
        if (mailPath == null) {
            return null;
        }
        int index = mailPath.lastIndexOf('/', mailPath.length() - 2);
        if (index >= 0 && mailPath.endsWith("/")) {
            return mailPath.substring(index + 1, mailPath.length() - 1);
        } else {
            LOGGER.warn(new BundleMessage("EXCEPTION_INVALID_MAIL_PATH", mailPath));
            return null;
        }
    }

    /**
     * Get user alias from mailbox display name over Webdav.
     *
     * @return user alias
     */
    public String getAliasFromMailboxDisplayName() {
        if (mailPath == null) {
            return null;
        }
        String displayName = null;
        try {
            Folder rootFolder = getFolder("");
            if (rootFolder == null) {
                LOGGER.warn(new BundleMessage("EXCEPTION_UNABLE_TO_GET_MAIL_FOLDER", mailPath));
            } else {
                displayName = rootFolder.displayName;
            }
        } catch (IOException e) {
            LOGGER.warn(new BundleMessage("EXCEPTION_UNABLE_TO_GET_MAIL_FOLDER", mailPath));
        }
        return displayName;
    }

    /**
     * Build Caldav calendar path for principal and folder name.
     * - prefix is current user mailbox path if principal is current user,
     * else prefix is parent folder of current user mailbox path followed by principal
     * - suffix according to well known folder names (internationalized on Exchange)
     *
     * @param principal  calendar principal
     * @param folderName requested folder name
     * @return Exchange folder path
     * @throws IOException on error
     */
    public String buildCalendarPath(String principal, String folderName) throws IOException {
        StringBuilder buffer = new StringBuilder();
        // other user calendar => replace principal folder name in mailPath
        if (principal != null && !alias.equalsIgnoreCase(principal) && !email.equalsIgnoreCase(principal)) {
            LOGGER.debug("Detected shared calendar path for principal " + principal + ", user principal is " + email);
            int index = mailPath.lastIndexOf('/', mailPath.length() - 2);
            if (index >= 0 && mailPath.endsWith("/")) {
                buffer.append(mailPath.substring(0, index + 1)).append(principal).append('/');
            } else {
                throw new DavMailException("EXCEPTION_INVALID_MAIL_PATH", mailPath);
            }
        } else if (principal != null) {
            buffer.append(mailPath);
        }

        if (folderName != null && (folderName.startsWith("calendar") || folderName.startsWith("contacts")
                // OSX address book name
                || folderName.startsWith("addressbook"))) {
            if (folderName.startsWith("calendar")) {
                // replace 'calendar' folder name with i18n name
                buffer.append(calendarUrl.substring(calendarUrl.lastIndexOf('/') + 1));
            } else {
                // replace 'contacts' folder name with i18n name
                buffer.append(contactsUrl.substring(contactsUrl.lastIndexOf('/') + 1));
            }

            // sub calendar folder => append sub folder name
            int index = folderName.indexOf('/');
            if (index >= 0) {
                buffer.append(folderName.substring(index));
            }
            // replace 'inbox' folder name with i18n name
        } else if ("inbox".equals(folderName)) {
            buffer.append(inboxUrl.substring(inboxUrl.lastIndexOf('/') + 1));
            // append folder name without replace (public folder)
        } else if (folderName != null && folderName.length() > 0) {
            buffer.append(folderName);
        }
        return buffer.toString();
    }

    /**
     * Test if folderPath is inside user mailbox.
     *
     * @param folderPath absolute folder path
     * @return true if folderPath is a public or shared folder
     */
    public boolean isSharedFolder(String folderPath) {
        return !folderPath.toLowerCase().startsWith(mailPath.toLowerCase());
    }

    /**
     * Build base path for cmd commands (galfind, gallookup).
     * This does not work with freebusy, which requires /public/
     *
     * @return cmd base path
     */
    public String getCmdBasePath() {
        if (mailPath == null) {
            if (publicFolderUrl == null) {
                return "/public/";
            } else {
                return publicFolderUrl + '/';
            }
        } else {
            return mailPath;
        }
    }

    /**
     * Get user email from global address list (galfind).
     *
     * @param alias user alias
     * @return user email
     */
    public String getEmail(String alias) {
        String emailResult = null;
        if (alias != null) {
            GetMethod getMethod = null;
            String path = null;
            try {
                path = getCmdBasePath() + "?Cmd=galfind&AN=" + URIUtil.encodeWithinQuery(alias);
                getMethod = new GetMethod(path);
                DavGatewayHttpClientFacade.executeGetMethod(httpClient, getMethod, true);
                Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
                Map<String, String> result = results.get(alias.toLowerCase());
                if (result != null) {
                    emailResult = result.get("EM");
                }
            } catch (IOException e) {
                LOGGER.debug("GET " + path + " failed: " + e + ' ' + e.getMessage());
            } finally {
                if (getMethod != null) {
                    getMethod.releaseConnection();
                }
            }
        }
        return emailResult;
    }

    /**
     * Determine user email through various means.
     *
     * @param hostName Exchange server host name for last failover
     */
    public void buildEmail(String hostName) {
        // first try to get email from login name
        alias = getAliasFromLogin();
        email = getEmail(alias);
        // failover: use mailbox name as alias
        if (email == null) {
            alias = getAliasFromMailPath();
            email = getEmail(alias);
        }
        // another failover : get alias from mailPath display name
        if (email == null) {
            alias = getAliasFromMailboxDisplayName();
            email = getEmail(alias);
        }
        if (email == null) {
            // failover : get email from Exchange 2007 Options page
            alias = getAliasFromOptions();
            email = getEmail(alias);
            // failover: get email from options
            if (alias != null && email == null) {
                email = getEmailFromOptions();
            }
        }
        if (email == null) {
            LOGGER.debug("Unable to get user email with alias " + getAliasFromLogin()
                    + " or " + getAliasFromMailPath()
                    + " or " + getAliasFromOptions()
            );
            // last failover: build email from domain name and mailbox display name
            StringBuilder buffer = new StringBuilder();
            // most reliable alias
            alias = getAliasFromMailboxDisplayName();
            if (alias == null) {
                alias = getAliasFromLogin();
            }
            if (alias != null) {
                buffer.append(alias);
                if (alias.indexOf('@') < 0) {
                    buffer.append('@');
                    int dotIndex = hostName.indexOf('.');
                    if (dotIndex >= 0) {
                        buffer.append(hostName.substring(dotIndex + 1));
                    }
                }
            }
            email = buffer.toString();
        }
    }

    static final String MAILBOX_BASE = "/cn=";

    protected String getAliasFromOptions() {
        String result = null;
        // get user mail URL from html body
        BufferedReader optionsPageReader = null;
        GetMethod optionsMethod = new GetMethod("/owa/?ae=Options&t=About");
        try {
            DavGatewayHttpClientFacade.executeGetMethod(httpClient, optionsMethod, false);
            optionsPageReader = new BufferedReader(new InputStreamReader(optionsMethod.getResponseBodyAsStream()));
            String line;
            // find mailbox full name
            //noinspection StatementWithEmptyBody
            while ((line = optionsPageReader.readLine()) != null && line.toLowerCase().indexOf(MAILBOX_BASE) == -1) {
            }
            if (line != null) {
                int start = line.toLowerCase().lastIndexOf(MAILBOX_BASE) + MAILBOX_BASE.length();
                int end = line.indexOf('<', start);
                result = line.substring(start, end);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
        } finally {
            if (optionsPageReader != null) {
                try {
                    optionsPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
                }
            }
            optionsMethod.releaseConnection();
        }

        return result;
    }

    protected String getEmailFromOptions() {
        String result = null;
        // get user mail URL from html body
        BufferedReader optionsPageReader = null;
        GetMethod optionsMethod = new GetMethod("/owa/?ae=Options&t=About");
        try {
            DavGatewayHttpClientFacade.executeGetMethod(httpClient, optionsMethod, false);
            optionsPageReader = new BufferedReader(new InputStreamReader(optionsMethod.getResponseBodyAsStream()));
            String line;
            // find email
            //noinspection StatementWithEmptyBody
            while ((line = optionsPageReader.readLine()) != null
                    && (line.indexOf('[') == -1
                    || line.indexOf('@') == -1
                    || line.indexOf(']') == -1)) {
            }
            if (line != null) {
                int start = line.toLowerCase().indexOf('[') + 1;
                int end = line.indexOf(']', start);
                result = line.substring(start, end);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
        } finally {
            if (optionsPageReader != null) {
                try {
                    optionsPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing options page at " + optionsMethod.getPath());
                }
            }
            optionsMethod.releaseConnection();
        }

        return result;
    }

    /**
     * Get current user email
     *
     * @return user email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Get current user alias
     *
     * @return user email
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Search users in global address book
     *
     * @param searchAttribute exchange search attribute
     * @param searchValue     search value
     * @return List of users
     * @throws IOException on error
     */
    public Map<String, Map<String, String>> galFind(String searchAttribute, String searchValue) throws IOException {
        Map<String, Map<String, String>> results;
        GetMethod getMethod = new GetMethod(URIUtil.encodePathQuery(getCmdBasePath() + "?Cmd=galfind&" + searchAttribute + '=' + searchValue));
        try {
            DavGatewayHttpClientFacade.executeGetMethod(httpClient, getMethod, true);
            results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "item", "AN");
        } finally {
            getMethod.releaseConnection();
        }
        LOGGER.debug("galfind " + searchAttribute + '=' + searchValue + ": " + results.size() + " result(s)");
        return results;
    }

    /**
     * Search users in contacts folder by uid.
     *
     * @param uid unique id
     * @return List of users
     * @throws IOException on error
     */
    public Map<String, Map<String, String>> contactFindByUid(String uid) throws IOException {
        return contactFind(DAV_UID_FILTER + uid + '\'');
    }

    static final String DAV_UID_FILTER = "\"DAV:uid\"='";

    /**
     * Search users in contacts folder
     *
     * @param searchFilter search filter
     * @return List of users
     * @throws IOException on error
     */
    public Map<String, Map<String, String>> contactFind(String searchFilter) throws IOException {
        // uid value in search filter (hex value)
        String filterUid = null;
        // base64 encoded uid value
        String actualFilterUid = null;

        // replace hex encoded uid with base64 uid
        if (searchFilter != null) {
            int uidStart = searchFilter.indexOf(DAV_UID_FILTER);
            if (uidStart >= 0) {
                int uidEnd = searchFilter.indexOf('\'', uidStart + DAV_UID_FILTER.length());
                if (uidEnd >= 0) {
                    try {
                        filterUid = searchFilter.substring(uidStart + DAV_UID_FILTER.length(), uidEnd);
                        actualFilterUid = new String(Base64.encodeBase64(Hex.decodeHex(filterUid.toCharArray())));
                        searchFilter = searchFilter.substring(0, uidStart + DAV_UID_FILTER.length()) + actualFilterUid + searchFilter.substring(uidEnd);
                    } catch (DecoderException e) {
                        // ignore, this is not an hex uid
                    }
                }
            }
        }
        StringBuilder searchRequest = new StringBuilder();
        searchRequest.append("Select \"DAV:uid\", " +
                "\"http://schemas.microsoft.com/exchange/extensionattribute1\"," +
                "\"http://schemas.microsoft.com/exchange/extensionattribute2\"," +
                "\"http://schemas.microsoft.com/exchange/extensionattribute3\"," +
                "\"http://schemas.microsoft.com/exchange/extensionattribute4\"," +
                "\"urn:schemas:contacts:bday\"," +
                "\"urn:schemas:contacts:businesshomepage\"," +
                "\"urn:schemas:contacts:c\"," +
                "\"urn:schemas:contacts:cn\"," +
                "\"urn:schemas:contacts:co\"," +
                "\"urn:schemas:contacts:department\"," +
                "\"urn:schemas:contacts:email1\"," +
                "\"urn:schemas:contacts:email2\"," +
                "\"urn:schemas:contacts:facsimiletelephonenumber\"," +
                "\"urn:schemas:contacts:givenName\"," +
                "\"urn:schemas:contacts:homeCity\"," +
                "\"urn:schemas:contacts:homeCountry\"," +
                "\"urn:schemas:contacts:homePhone\"," +
                "\"urn:schemas:contacts:homePostalCode\"," +
                "\"urn:schemas:contacts:homeState\"," +
                "\"urn:schemas:contacts:homeStreet\"," +
                "\"urn:schemas:contacts:l\"," +
                "\"urn:schemas:contacts:manager\"," +
                "\"urn:schemas:contacts:mobile\"," +
                "\"urn:schemas:contacts:namesuffix\"," +
                "\"urn:schemas:contacts:nickname\"," +
                "\"urn:schemas:contacts:o\"," +
                "\"urn:schemas:contacts:pager\"," +
                "\"urn:schemas:contacts:personaltitle\"," +
                "\"urn:schemas:contacts:postalcode\"," +
                "\"urn:schemas:contacts:postofficebox\"," +
                "\"urn:schemas:contacts:profession\"," +
                "\"urn:schemas:contacts:roomnumber\"," +
                "\"urn:schemas:contacts:secretarycn\"," +
                "\"urn:schemas:contacts:sn\"," +
                "\"urn:schemas:contacts:spousecn\"," +
                "\"urn:schemas:contacts:st\"," +
                "\"urn:schemas:contacts:street\"," +
                "\"urn:schemas:contacts:telephoneNumber\"," +
                "\"urn:schemas:contacts:title\"," +
                "\"urn:schemas:httpmail:textdescription\"")
                .append("                FROM Scope('SHALLOW TRAVERSAL OF \"").append(contactsUrl).append("\"')\n")
                .append("                WHERE \"DAV:contentclass\" = 'urn:content-classes:person' \n");
        if (searchFilter != null && searchFilter.length() > 0) {
            searchRequest.append("                AND ").append(searchFilter);
        }
        LOGGER.debug("contactFind: " + searchRequest);
        MultiStatusResponse[] responses = DavGatewayHttpClientFacade.executeSearchMethod(
                httpClient, URIUtil.encodePath(contactsUrl), searchRequest.toString());

        Map<String, Map<String, String>> results = new HashMap<String, Map<String, String>>();
        Map<String, String> item;
        for (MultiStatusResponse response : responses) {
            item = new HashMap<String, String>();

            DavPropertySet properties = response.getProperties(HttpStatus.SC_OK);
            DavPropertyIterator propertiesIterator = properties.iterator();
            while (propertiesIterator.hasNext()) {
                DavProperty property = propertiesIterator.nextProperty();
                String propertyName = property.getName().getName();
                String propertyValue = (String) property.getValue();
                if ("uid".equals(propertyName)) {
                    // uid is base64, reencode to hex
                    propertyValue = new String(Hex.encodeHex(Base64.decodeBase64(propertyValue.getBytes())));
                    // if actualFilterUid is not null, exclude non exact match
                    if (actualFilterUid != null && !filterUid.equals(propertyValue)) {
                        propertyValue = null;
                    }
                } else if (propertyName.startsWith("email")) {
                    if (propertyValue != null && propertyValue.startsWith("\"")) {
                        int endIndex = propertyValue.indexOf('\"', 1);
                        if (endIndex > 0) {
                            propertyValue = propertyValue.substring(1, endIndex);
                        }
                    }
                } else if ("bday".equals(propertyName)) {
                    SimpleDateFormat parser = getExchangeZuluDateFormatMillisecond();
                    try {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(parser.parse(propertyValue));
                        item.put("birthday", String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
                        item.put("birthmonth", String.valueOf(calendar.get(Calendar.MONTH) + 1));
                        item.put("birthyear", String.valueOf(calendar.get(Calendar.YEAR)));
                        propertyValue = null;
                    } catch (ParseException e) {
                        throw new IOException(e);
                    }
                } else if ("textdescription".equals(propertyName) && " \n".equals(propertyValue)) {
                    propertyValue = null;
                }
                if (propertyValue != null && propertyValue.length() > 0) {
                    item.put(propertyName, propertyValue);
                }
            }
            if (item.get("uid") != null) {
                results.put(item.get("uid"), item);
            }
        }

        LOGGER.debug("contactFind " + ((searchFilter == null) ? "" : searchFilter) + ": " + results.size() + " result(s)");
        return results;
    }

    /**
     * Get extended address book information for person with gallookup.
     * Does not work with Exchange 2007
     *
     * @param person person attributes map
     */
    public void galLookup(Map<String, String> person) {
        if (!disableGalLookup) {
            GetMethod getMethod = null;
            try {
                getMethod = new GetMethod(URIUtil.encodePathQuery(getCmdBasePath() + "?Cmd=gallookup&ADDR=" + person.get("EM")));
                DavGatewayHttpClientFacade.executeGetMethod(httpClient, getMethod, true);
                Map<String, Map<String, String>> results = XMLStreamUtil.getElementContentsAsMap(getMethod.getResponseBodyAsStream(), "person", "alias");
                // add detailed information
                if (!results.isEmpty()) {
                    Map<String, String> fullperson = results.get(person.get("AN").toLowerCase());
                    if (fullperson != null) {
                        for (Map.Entry<String, String> entry : fullperson.entrySet()) {
                            person.put(entry.getKey(), entry.getValue());
                        }
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

    /**
     * Get freebusy info for attendee between start and end date.
     *
     * @param attendee       attendee email
     * @param startDateValue start date
     * @param endDateValue   end date
     * @return FreeBusy info
     * @throws IOException on error
     */
    public FreeBusy getFreebusy(String attendee, String startDateValue, String endDateValue) throws IOException {
        attendee = replaceIcal4Principal(attendee);
        if (attendee.startsWith("mailto:") || attendee.startsWith("MAILTO:")) {
            attendee = attendee.substring("mailto:".length());
        }

        SimpleDateFormat exchangeZuluDateFormat = getExchangeZuluDateFormat();
        SimpleDateFormat icalDateFormat = getZuluDateFormat();

        String freebusyUrl;
        Date startDate;
        Date endDate;
        try {
            if (startDateValue.length() == 8) {
                startDate = parseDate(startDateValue);
            } else {
                startDate = icalDateFormat.parse(startDateValue);
            }
            if (endDateValue.length() == 8) {
                endDate = parseDate(endDateValue);
            } else {
                endDate = icalDateFormat.parse(endDateValue);
            }
            freebusyUrl = publicFolderUrl + "/?cmd=freebusy" +
                    "&start=" + exchangeZuluDateFormat.format(startDate) +
                    "&end=" + exchangeZuluDateFormat.format(endDate) +
                    "&interval=" + FREE_BUSY_INTERVAL +
                    "&u=SMTP:" + attendee;
        } catch (ParseException e) {
            throw new DavMailException("EXCEPTION_INVALID_DATES", e.getMessage());
        }

        FreeBusy freeBusy = null;
        GetMethod getMethod = new GetMethod(freebusyUrl);
        getMethod.setRequestHeader("Content-Type", "text/xml");

        try {
            DavGatewayHttpClientFacade.executeGetMethod(httpClient, getMethod, true);
            String fbdata = StringUtil.getLastToken(getMethod.getResponseBodyAsString(), "<a:fbdata>", "</a:fbdata>");
            if (fbdata != null) {
                freeBusy = new FreeBusy(icalDateFormat, startDate, fbdata);
            }
        } finally {
            getMethod.releaseConnection();
        }

        if (freeBusy != null && freeBusy.knownAttendee) {
            return freeBusy;
        } else {
            return null;
        }
    }

    /**
     * Exchange to iCalendar Free/Busy parser.
     * Free time returns 0, Tentative returns 1, Busy returns 2, and Out of Office (OOF) returns 3
     */
    public static final class FreeBusy {
        final SimpleDateFormat icalParser;
        boolean knownAttendee = true;
        static final HashMap<Character, String> FBTYPES = new HashMap<Character, String>();

        static {
            FBTYPES.put('1', "BUSY-TENTATIVE");
            FBTYPES.put('2', "BUSY");
            FBTYPES.put('3', "BUSY-UNAVAILABLE");
        }

        final HashMap<String, StringBuilder> busyMap = new HashMap<String, StringBuilder>();

        StringBuilder getBusyBuffer(char type) {
            String fbType = FBTYPES.get(Character.valueOf(type));
            StringBuilder buffer = busyMap.get(fbType);
            if (buffer == null) {
                buffer = new StringBuilder();
                busyMap.put(fbType, buffer);
            }
            return buffer;
        }

        void startBusy(char type, Calendar currentCal) {
            if (type == '4') {
                knownAttendee = false;
            } else if (type != '0') {
                StringBuilder busyBuffer = getBusyBuffer(type);
                if (busyBuffer.length() > 0) {
                    busyBuffer.append(',');
                }
                busyBuffer.append(icalParser.format(currentCal.getTime()));
            }
        }

        void endBusy(char type, Calendar currentCal) {
            if (type != '0' && type != '4') {
                getBusyBuffer(type).append('/').append(icalParser.format(currentCal.getTime()));
            }
        }

        FreeBusy(SimpleDateFormat icalParser, Date startDate, String fbdata) {
            this.icalParser = icalParser;
            if (fbdata.length() > 0) {
                Calendar currentCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                currentCal.setTime(startDate);

                startBusy(fbdata.charAt(0), currentCal);
                for (int i = 1; i < fbdata.length() && knownAttendee; i++) {
                    currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                    char previousState = fbdata.charAt(i - 1);
                    char currentState = fbdata.charAt(i);
                    if (previousState != currentState) {
                        endBusy(previousState, currentCal);
                        startBusy(currentState, currentCal);
                    }
                }
                currentCal.add(Calendar.MINUTE, FREE_BUSY_INTERVAL);
                endBusy(fbdata.charAt(fbdata.length() - 1), currentCal);
            }
        }

        /**
         * Append freebusy information to buffer.
         *
         * @param buffer String buffer
         */
        public void appendTo(StringBuilder buffer) {
            for (Map.Entry<String, StringBuilder> entry : busyMap.entrySet()) {
                buffer.append("FREEBUSY;FBTYPE=").append(entry.getKey())
                        .append(':').append(entry.getValue()).append((char) 13).append((char) 10);
            }
        }
    }

    public final class VTimezone {
        public String timezoneBody;
        public String timezoneId;

    }

    protected VTimezone vTimezone;

    protected VTimezone getVTimezone() {
        if (vTimezone == null) {
            // need to load Timezone info from OWA
            loadVtimezone();
        }
        return vTimezone;
    }

    protected abstract void loadVtimezone();

    /**
     * Return internal HttpClient instance
     *
     * @return http client
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }
}

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

package davmail.exchange.auth;

import davmail.BundleMessage;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exception.WebdavNotAvailableException;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewayOTPPrompt;
import davmail.util.StringUtil;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.htmlcleaner.BaseToken;
import org.htmlcleaner.CommentNode;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Form based Exchange authentication.
 */
public class ExchangeFormAuthenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger(ExchangeFormAuthenticator.class);

    /**
     * Various username fields found on custom Exchange authentication forms
     */
    protected static final Set<String> USER_NAME_FIELDS = new HashSet<>();
    static {
        USER_NAME_FIELDS.add("username");
        USER_NAME_FIELDS.add("txtusername");
        USER_NAME_FIELDS.add("userid");
        USER_NAME_FIELDS.add("SafeWordUser");
        USER_NAME_FIELDS.add("user_name");
        USER_NAME_FIELDS.add("login");
        USER_NAME_FIELDS.add("UserName");
    }

    /**
     * Various password fields found on custom Exchange authentication forms
     */
    protected static final Set<String> PASSWORD_FIELDS = new HashSet<>();
    static {
        PASSWORD_FIELDS.add("password");
        PASSWORD_FIELDS.add("txtUserPass");
        PASSWORD_FIELDS.add("pw");
        PASSWORD_FIELDS.add("basicPassword");
        PASSWORD_FIELDS.add("passwd");
        PASSWORD_FIELDS.add("Password");
    }

    /**
     * Various OTP (one time password) fields found on custom Exchange authentication forms.
     * Used to open OTP dialog
     */
    protected static final Set<String> TOKEN_FIELDS = new HashSet<>();
    static {
        TOKEN_FIELDS.add("SafeWordPassword");
        TOKEN_FIELDS.add("passcode");
    }


    /**
     * User provided username.
     * Old preauth syntax: preauthusername"username
     * Windows authentication with domain: domain\\username
     * Note that OSX Mail.app does not support backslash in username, set default domain in DavMail settings instead
     */
    private String username;
    /**
     * User provided password
     */
    private String password;
    /**
     * OWA or EWS url
     */
    private String url;
    /**
     * HttpClient 3 instance
     */
    private HttpClient httpClient;
    /**
     * A OTP pre-auth page may require a different username.
     */
    private String preAuthusername;

    /**
     * Logon form user name fields.
     */
    private final List<String> usernameInputs = new ArrayList<>();
    /**
     * Logon form password field, default is password.
     */
    private String passwordInput = null;
    /**
     * Tells if, during the login navigation, an OTP pre-auth page has been found.
     */
    private boolean otpPreAuthFound = false;
    /**
     * Lets the user try again a couple of times to enter the OTP pre-auth key before giving up.
     */
    private int otpPreAuthRetries = 0;
    /**
     * Maximum number of times the user can try to input again the OTP pre-auth key before giving up.
     */
    private static final int MAX_OTP_RETRIES = 3;
    // base Exchange URI after authentication
    private java.net.URI exchangeUri;


    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void authenticate() throws DavMailException {
        try {
            httpClient = DavGatewayHttpClientFacade.getInstance(url);
            // set private connection pool
            DavGatewayHttpClientFacade.createMultiThreadedHttpConnectionManager(httpClient);
            boolean isHttpAuthentication = isHttpAuthentication(httpClient, url);
            if (isHttpAuthentication) {
                DavGatewayHttpClientFacade.addNTLM(httpClient);
            }
            // clear cookies created by authentication test
            httpClient.getState().clearCookies();

            // The user may have configured an OTP pre-auth username. It is processed
            // so early because OTP pre-auth may disappear in the Exchange LAN and this
            // helps the user to not change is account settings in mail client at each network change.
            if (preAuthusername == null) {
                // Searches for the delimiter in configured username for the pre-auth user.
                // The double-quote is not allowed inside email addresses anyway.
                int doubleQuoteIndex = this.username.indexOf('"');
                if (doubleQuoteIndex > 0) {
                    preAuthusername = this.username.substring(0, doubleQuoteIndex);
                    this.username = this.username.substring(doubleQuoteIndex + 1);
                } else {
                    // No doublequote: the pre-auth user is the full username, or it is not used at all.
                    preAuthusername = this.username;
                }
            }

            DavGatewayHttpClientFacade.setCredentials(httpClient, username, password);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            HttpMethod method = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, url);

            if (!this.isAuthenticated(method)) {
                if (isHttpAuthentication) {
                    int status = method.getStatusCode();

                    if (status == HttpStatus.SC_UNAUTHORIZED) {
                        method.releaseConnection();
                        throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                    } else if (status != HttpStatus.SC_OK) {
                        method.releaseConnection();
                        throw DavGatewayHttpClientFacade.buildHttpResponseException(method);
                    }
                    // workaround for basic authentication on /exchange and form based authentication at /owa
                    if ("/owa/auth/logon.aspx".equals(method.getPath())) {
                        method = formLogin(httpClient, method, password);
                    }
                } else {
                    method = formLogin(httpClient, method, password);
                }
            }

            // avoid 401 roundtrips, only if NTLM is disabled and basic authentication enabled
            if (isHttpAuthentication && !DavGatewayHttpClientFacade.hasNTLMorNegotiate(httpClient)) {
                httpClient.getParams().setParameter(HttpClientParams.PREEMPTIVE_AUTHENTICATION, true);
            }

            exchangeUri = java.net.URI.create(method.getURI().getURI());
            method.releaseConnection();

        } catch (DavMailAuthenticationException exc) {
            close();
            LOGGER.error(exc.getMessage());
            throw exc;
        } catch (ConnectException | UnknownHostException exc) {
            close();
            BundleMessage message = new BundleMessage("EXCEPTION_CONNECT", exc.getClass().getName(), exc.getMessage());
            LOGGER.error(message);
            throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", message);
        } catch (WebdavNotAvailableException exc) {
            close();
            throw exc;
        } catch (IOException exc) {
            close();
            LOGGER.error(BundleMessage.formatLog("EXCEPTION_EXCHANGE_LOGIN_FAILED", exc));
            throw new DavMailException("EXCEPTION_EXCHANGE_LOGIN_FAILED", exc);
        }
        LOGGER.debug("Authenticated with "+url);
    }

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url        exchange base URL
     * @param httpClient httpClient instance
     * @return true if basic authentication detected
     */
    protected boolean isHttpAuthentication(HttpClient httpClient, String url) {
        return DavGatewayHttpClientFacade.getHttpStatus(httpClient, url) == HttpStatus.SC_UNAUTHORIZED;
    }

    /**
     * Look for session cookies.
     *
     * @return true if session cookies are available
     */
    protected boolean isAuthenticated(HttpMethod method) {
        boolean authenticated = false;
        if (method.getStatusCode() == HttpStatus.SC_OK
                && "/ews/services.wsdl".equalsIgnoreCase(method.getPath())) {
            // direct EWS access returned wsdl
            authenticated = true;
        } else {
            // check cookies
            for (Cookie cookie : httpClient.getState().getCookies()) {
                // Exchange 2003 cookies
                if (cookie.getName().startsWith("cadata") || "sessionid".equals(cookie.getName())
                        // Exchange 2007 cookie
                        || "UserContext".equals(cookie.getName())
                ) {
                    authenticated = true;
                    break;
                }
            }
        }
        return authenticated;
    }

    protected HttpMethod formLogin(HttpClient httpClient, HttpMethod initmethod, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");

        HttpMethod logonMethod = buildLogonMethod(httpClient, initmethod);
        if (logonMethod == null) {
            LOGGER.debug("Authentication form not found at " + initmethod.getURI() + ", trying default url");
            logonMethod = new PostMethod("/owa/auth/owaauth.dll");
        }
        logonMethod = postLogonMethod(httpClient, logonMethod, password);

        return logonMethod;
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param httpClient httpClient instance
     * @param initmethod form body http method
     * @return logon method
     * @throws IOException on error
     */
    protected HttpMethod buildLogonMethod(HttpClient httpClient, HttpMethod initmethod) throws IOException {

        HttpMethod logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        // A OTP token authentication form in a previous page could have username fields with different names
        usernameInputs.clear();

        try {
            TagNode node = cleaner.clean(initmethod.getResponseBodyAsStream());
            List<? extends TagNode> forms = node.getElementListByName("form", true);
            TagNode logonForm = null;
            // select form
            if (forms.size() == 1) {
                logonForm = forms.get(0);
            } else if (forms.size() > 1) {
                for (TagNode form : forms) {
                    if ("logonForm".equals(form.getAttributeByName("name"))) {
                        logonForm = form;
                    }
                }
            }
            if (logonForm != null) {
                String logonMethodPath = logonForm.getAttributeByName("action");

                // workaround for broken form with empty action
                if (logonMethodPath != null && logonMethodPath.length() == 0) {
                    logonMethodPath = "/owa/auth.owa";
                }

                logonMethod = new PostMethod(getAbsoluteUri(initmethod, logonMethodPath));

                // retrieve lost inputs attached to body
                List<? extends TagNode> inputList = node.getElementListByName("input", true);

                for (TagNode input : inputList) {
                    String type = input.getAttributeByName("type");
                    String name = input.getAttributeByName("name");
                    String value = input.getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        ((PostMethod) logonMethod).addParameter(name, value);
                    }
                    // custom login form
                    if (name == null) {
                        LOGGER.debug("Skip invalid input with empty name");
                    } else if (USER_NAME_FIELDS.contains(name)) {
                        usernameInputs.add(name);
                    } else if (PASSWORD_FIELDS.contains(name)) {
                        passwordInput = name;
                    } else if ("addr".equals(name)) {
                        // this is not a logon form but a redirect form
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    } else if (TOKEN_FIELDS.contains(name)) {
                        // one time password, ask it to the user
                        ((PostMethod) logonMethod).addParameter(name, DavGatewayOTPPrompt.getOneTimePassword());
                    } else if ("otc".equals(name)) {
                        // captcha image, get image and ask user
                        String pinsafeUser = getAliasFromLogin();
                        if (pinsafeUser == null) {
                            pinsafeUser = username;
                        }
                        GetMethod getMethod = new GetMethod("/PINsafeISAFilter.dll?username=" + pinsafeUser);
                        try {
                            int status = httpClient.executeMethod(getMethod);
                            if (status != HttpStatus.SC_OK) {
                                throw DavGatewayHttpClientFacade.buildHttpResponseException(getMethod);
                            }
                            BufferedImage captchaImage = ImageIO.read(getMethod.getResponseBodyAsStream());
                            ((PostMethod) logonMethod).addParameter(name, DavGatewayOTPPrompt.getCaptchaValue(captchaImage));

                        } finally {
                            getMethod.releaseConnection();
                        }
                    }
                }
            } else {
                List<? extends TagNode> frameList = node.getElementListByName("frame", true);
                if (frameList.size() == 1) {
                    String src = frameList.get(0).getAttributeByName("src");
                    if (src != null) {
                        LOGGER.debug("Frames detected in form page, try frame content");
                        initmethod.releaseConnection();
                        HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                        logonMethod = buildLogonMethod(httpClient, newInitMethod);
                    }
                } else {
                    // another failover for script based logon forms (Exchange 2007)
                    List<? extends TagNode> scriptList = node.getElementListByName("script", true);
                    for (TagNode script : scriptList) {
                        List<? extends BaseToken> contents = script.getAllChildren();
                        for (BaseToken content : contents) {
                            if (content instanceof CommentNode) {
                                String scriptValue = ((CommentNode) content).getCommentedContent();
                                String sUrl = StringUtil.getToken(scriptValue, "var a_sUrl = \"", "\"");
                                String sLgn = StringUtil.getToken(scriptValue, "var a_sLgnQS = \"", "\"");
                                if (sLgn == null) {
                                    sLgn = StringUtil.getToken(scriptValue, "var a_sLgn = \"", "\"");
                                }
                                if (sUrl != null && sLgn != null) {
                                    String src = getScriptBasedFormURL(initmethod, sLgn + sUrl);
                                    LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                    HttpMethod newInitMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, src);
                                    logonMethod = buildLogonMethod(httpClient, newInitMethod);
                                }

                            } else if (content instanceof ContentNode) {
                                // Microsoft Forefront Unified Access Gateway redirect
                                String scriptValue = ((ContentNode) content).getContent();
                                String location = StringUtil.getToken(scriptValue, "window.location.replace(\"", "\"");
                                if (location != null) {
                                    LOGGER.debug("Post logon redirect to: " + location);
                                    logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, location);
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


    protected HttpMethod postLogonMethod(HttpClient httpClient, HttpMethod logonMethod, String password) throws IOException {

        setAuthFormFields(logonMethod, httpClient, password);

        // add exchange 2010 PBack cookie in compatibility mode
        httpClient.getState().addCookie(new Cookie(httpClient.getHostConfiguration().getHost(), "PBack", "0", "/", null, false));

        logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);

        // test form based authentication
        checkFormLoginQueryString(logonMethod);

        // workaround for post logon script redirect
        if (!isAuthenticated(logonMethod)) {
            // try to get new method from script based redirection
            logonMethod = buildLogonMethod(httpClient, logonMethod);

            if (logonMethod != null) {
                if (otpPreAuthFound && otpPreAuthRetries < MAX_OTP_RETRIES) {
                    // A OTP pre-auth page has been found, it is needed to restart the login process.
                    // This applies to both the case the user entered a good OTP code (the usual login process
                    // takes place) and the case the user entered a wrong OTP code (another code will be asked to him).
                    // The user has up to MAX_OTP_RETRIES chances to input a valid OTP key.
                    return postLogonMethod(httpClient, logonMethod, password);
                }

                // if logonMethod is not null, try to follow redirection
                logonMethod = DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, logonMethod);
                checkFormLoginQueryString(logonMethod);
                // also check cookies
                if (!isAuthenticated(logonMethod)) {
                    throwAuthenticationFailed();
                }
            } else {
                // authentication failed
                throwAuthenticationFailed();
            }
        }

        // check for language selection form
        if (logonMethod != null && "/owa/languageselection.aspx".equals(logonMethod.getPath())) {
            // need to submit form
            logonMethod = submitLanguageSelectionForm(logonMethod);
        }
        return logonMethod;
    }

    protected HttpMethod submitLanguageSelectionForm(HttpMethod logonMethod) throws IOException {
        PostMethod postLanguageFormMethod;
        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(logonMethod.getResponseBodyAsStream());
            List<? extends TagNode> forms = node.getElementListByName("form", true);
            TagNode languageForm;
            // select form
            if (forms.size() == 1) {
                languageForm = forms.get(0);
            } else {
                throw new IOException("Form not found");
            }
            String languageMethodPath = languageForm.getAttributeByName("action");

            postLanguageFormMethod = new PostMethod(getAbsoluteUri(logonMethod, languageMethodPath));

            List<? extends TagNode> inputList = languageForm.getElementListByName("input", true);
            for (TagNode input : inputList) {
                String name = input.getAttributeByName("name");
                String value = input.getAttributeByName("value");
                if (name != null && value != null) {
                    postLanguageFormMethod.addParameter(name, value);
                }
            }
            List<? extends TagNode> selectList = languageForm.getElementListByName("select", true);
            for (TagNode select : selectList) {
                String name = select.getAttributeByName("name");
                List<? extends TagNode> optionList = select.getElementListByName("option", true);
                String value = null;
                for (TagNode option : optionList) {
                    if (option.getAttributeByName("selected") != null) {
                        value = option.getAttributeByName("value");
                        break;
                    }
                }
                if (name != null && value != null) {
                    postLanguageFormMethod.addParameter(name, value);
                }
            }
        } catch (IOException e) {
            String errorMessage = "Error parsing language selection form at " + logonMethod.getURI();
            LOGGER.error(errorMessage);
            throw new IOException(errorMessage);
        } finally {
            logonMethod.releaseConnection();
        }

        return DavGatewayHttpClientFacade.executeFollowRedirects(httpClient, postLanguageFormMethod);
    }

    protected void setAuthFormFields(HttpMethod logonMethod, HttpClient httpClient, String password) throws IllegalArgumentException {
        String usernameInput;
        if (usernameInputs.size() == 2) {
            String userid;
            // multiple username fields, split userid|username on |
            int pipeIndex = username.indexOf('|');
            if (pipeIndex < 0) {
                LOGGER.debug("Multiple user fields detected, please use userid|username as user name in client, except when userid is username");
                userid = username;
            } else {
                userid = username.substring(0, pipeIndex);
                username = username.substring(pipeIndex + 1);
                // adjust credentials
                DavGatewayHttpClientFacade.setCredentials(httpClient, username, password);
            }
            ((PostMethod) logonMethod).removeParameter("userid");
            ((PostMethod) logonMethod).addParameter("userid", userid);

            usernameInput = "username";
        } else if (usernameInputs.size() == 1) {
            // simple username field
            usernameInput = usernameInputs.get(0);
        } else {
            // should not happen
            usernameInput = "username";
        }
        // make sure username and password fields are empty
        ((PostMethod) logonMethod).removeParameter(usernameInput);
        if (passwordInput != null) {
            ((PostMethod) logonMethod).removeParameter(passwordInput);
        }
        ((PostMethod) logonMethod).removeParameter("trusted");
        ((PostMethod) logonMethod).removeParameter("flags");

        if (passwordInput == null) {
            // This is a OTP pre-auth page. A different username may be required.
            otpPreAuthFound = true;
            otpPreAuthRetries++;
            ((PostMethod) logonMethod).addParameter(usernameInput, preAuthusername);
        } else {
            otpPreAuthFound = false;
            otpPreAuthRetries = 0;
            // This is a regular Exchange login page
            ((PostMethod) logonMethod).addParameter(usernameInput, username);
            ((PostMethod) logonMethod).addParameter(passwordInput, password);
            ((PostMethod) logonMethod).addParameter("trusted", "4");
            ((PostMethod) logonMethod).addParameter("flags", "4");
        }
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

    protected void checkFormLoginQueryString(HttpMethod logonMethod) throws DavMailAuthenticationException {
        String queryString = logonMethod.getQueryString();
        if (queryString != null && (queryString.contains("reason=2") || queryString.contains("reason=4"))) {
            logonMethod.releaseConnection();
            throwAuthenticationFailed();
        }
    }

    protected void throwAuthenticationFailed() throws DavMailAuthenticationException {
        if (this.username != null && this.username.contains("\\")) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        } else {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_RETRY");
        }
    }

    /**
     * Get current Exchange alias name from login name
     *
     * @return user name
     */
    public String getAliasFromLogin() {
        // login is email, not alias
        if (this.username.indexOf('@') >= 0) {
            return null;
        }
        String result = this.username;
        // remove domain name
        int index = Math.max(result.indexOf('\\'), result.indexOf('/'));
        if (index >= 0) {
            result = result.substring(index + 1);
        }
        return result;
    }

    /**
     * Close session.
     * Shutdown http client connection manager
     */
    public void close() {
        DavGatewayHttpClientFacade.close(httpClient);
    }

    /**
     * Oauth token.
     * Only for Office 365 authenticators
     * @return unsupported
     */
    @Override
    public O365Token getToken() {
        throw new UnsupportedOperationException();
    }

    /**
     * Base Exchange URL.
     * Welcome page for Exchange 2003, EWS url for Exchange 2007 and later
     *
     * @return Exchange url
     */
    @Override
    public java.net.URI getExchangeUri() {
        return exchangeUri;
    }

    /**
     * Authenticated httpClient (with cookies).
     *
     * @return http client
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Actual username.
     * may be different from input username with preauth
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }
}

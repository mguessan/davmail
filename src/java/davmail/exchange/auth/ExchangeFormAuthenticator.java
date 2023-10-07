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
import davmail.http.DavGatewayOTPPrompt;
import davmail.http.HttpClientAdapter;
import davmail.http.URIUtil;
import davmail.http.request.GetRequest;
import davmail.http.request.PostRequest;
import davmail.http.request.ResponseWrapper;
import davmail.util.StringUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.log4j.Logger;
import org.htmlcleaner.BaseToken;
import org.htmlcleaner.CommentNode;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * New Exchange form authenticator based on HttpClient 4.
 */
public class ExchangeFormAuthenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

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
     * HttpClient 4 adapter
     */
    private HttpClientAdapter httpClientAdapter;
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

    /**
     * base Exchange URI after authentication
     */
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
            // create HttpClient adapter, enable pooling as this instance will be passed to ExchangeSession
            httpClientAdapter = new HttpClientAdapter(url, true);
            boolean isHttpAuthentication = isHttpAuthentication(httpClientAdapter, url);

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

            // set real credentials on http client
            httpClientAdapter.setCredentials(username, password);

            // get webmail root url
            // providing credentials
            // manually follow redirect
            GetRequest getRequest = httpClientAdapter.executeFollowRedirect(new GetRequest(url));

            if (!this.isAuthenticated(getRequest)) {
                if (isHttpAuthentication) {
                    int status = getRequest.getStatusCode();

                    if (status == HttpStatus.SC_UNAUTHORIZED) {
                        throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                    } else if (status != HttpStatus.SC_OK) {
                        throw HttpClientAdapter.buildHttpResponseException(getRequest, getRequest.getHttpResponse());
                    }
                    // workaround for basic authentication on /exchange and form based authentication at /owa
                    if ("/owa/auth/logon.aspx".equals(getRequest.getURI().getPath())) {
                        formLogin(httpClientAdapter, getRequest, password);
                    }
                } else {
                    formLogin(httpClientAdapter, getRequest, password);
                }
            }

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
        LOGGER.debug("Successfully authenticated to " + exchangeUri);
    }

    /**
     * Test authentication mode : form based or basic.
     *
     * @param url        exchange base URL
     * @param httpClient httpClientAdapter instance
     * @return true if basic authentication detected
     */
    protected boolean isHttpAuthentication(HttpClientAdapter httpClient, String url) {
        boolean isHttpAuthentication = false;
        HttpGet httpGet = new HttpGet(url);
        // Create a local context to avoid cookies in main httpClient
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(new BasicCookieStore());
        try (CloseableHttpResponse response = httpClient.execute(httpGet, context)) {
            isHttpAuthentication = response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED;
        } catch (IOException e) {
            // ignore
        }
        return isHttpAuthentication;
    }

    /**
     * Look for session cookies.
     *
     * @return true if session cookies are available
     */
    protected boolean isAuthenticated(ResponseWrapper getRequest) {
        boolean authenticated = false;
        if (getRequest.getStatusCode() == HttpStatus.SC_OK
                && "/ews/services.wsdl".equalsIgnoreCase(getRequest.getURI().getPath())) {
            // direct EWS access returned wsdl
            authenticated = true;
        } else {
            // check cookies
            for (Cookie cookie : httpClientAdapter.getCookies()) {
                // Exchange 2003 cookies
                if (cookie.getName().startsWith("cadata") || "sessionid".equals(cookie.getName())
                        // Exchange 2007 cookie
                        || "UserContext".equals(cookie.getName())
                        // Federated Authentication
                        || "TimeWindowSig".equals(cookie.getName())
                ) {
                    authenticated = true;
                    break;
                }
            }
        }
        return authenticated;
    }

    protected void formLogin(HttpClientAdapter httpClient, ResponseWrapper initRequest, String password) throws IOException {
        LOGGER.debug("Form based authentication detected");

        PostRequest postRequest = buildLogonMethod(httpClient, initRequest);
        if (postRequest == null) {
            LOGGER.debug("Authentication form not found at " + initRequest.getURI() + ", trying default url");
            postRequest = new PostRequest("/owa/auth/owaauth.dll");
        }

        exchangeUri = postLogonMethod(httpClient, postRequest, password).getURI();
    }

    /**
     * Try to find logon method path from logon form body.
     *
     * @param httpClient      httpClientAdapter instance
     * @param responseWrapper init request response wrapper
     * @return logon method
     */
    protected PostRequest buildLogonMethod(HttpClientAdapter httpClient, ResponseWrapper responseWrapper) {
        PostRequest logonMethod = null;

        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();
        // In the federated auth flow, an input field may contain a saml xml assertion with > characters
        cleaner.getProperties().setAllowHtmlInsideAttributes(true);

        // A OTP token authentication form in a previous page could have username fields with different names
        usernameInputs.clear();

        try {
            URI uri = responseWrapper.getURI();
            String responseBody = responseWrapper.getResponseBodyAsString();
            TagNode node = cleaner.clean(new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
            List<? extends TagNode> forms = node.getElementListByName("form", true);
            TagNode logonForm = null;
            // select form
            if (forms.size() == 1) {
                logonForm = forms.get(0);
            } else if (forms.size() > 1) {
                for (Object form : forms) {
                    if ("logonForm".equals(((TagNode) form).getAttributeByName("name"))) {
                        logonForm = ((TagNode) form);
                    } else if ("loginForm".equals(((TagNode) form).getAttributeByName("id"))) {
                        logonForm = ((TagNode) form);
                    }

                }
            }
            if (logonForm != null) {
                String logonMethodPath = logonForm.getAttributeByName("action");

                // workaround for broken form with empty action
                if (logonMethodPath != null && logonMethodPath.length() == 0) {
                    logonMethodPath = "/owa/auth.owa";
                }

                logonMethod = new PostRequest(getAbsoluteUri(uri, logonMethodPath));

                // retrieve lost inputs attached to body
                List<? extends TagNode> inputList = node.getElementListByName("input", true);

                for (Object input : inputList) {
                    String type = ((TagNode) input).getAttributeByName("type");
                    String name = ((TagNode) input).getAttributeByName("name");
                    String value = ((TagNode) input).getAttributeByName("value");
                    if ("hidden".equalsIgnoreCase(type) && name != null && value != null) {
                        // decode XML SAML assertion correctly from hidden field value
                        if ("wresult".equals(name)) {
                            String decoded = value.replaceAll("&quot;","\"").replaceAll("&lt;","<");
                            logonMethod.setParameter(name, decoded);
                            // The OWA accepting this assertion needs the Referer set, but it can be anything
                            logonMethod.setRequestHeader("Referer", url);
                        } else if ("wctx".equals(name)) {
                            String decoded = value.replaceAll("&amp;","&");
                            logonMethod.setParameter(name, decoded);
                        } else {
                            logonMethod.setParameter(name, value);
                        }
                    }
                    // custom login form
                    if (USER_NAME_FIELDS.contains(name) && !usernameInputs.contains(name)) {
                        usernameInputs.add(name);
                    } else if (PASSWORD_FIELDS.contains(name)) {
                        passwordInput = name;
                    } else if ("addr".equals(name)) {
                        // this is not a logon form but a redirect form
                        logonMethod = buildLogonMethod(httpClient, httpClient.executeFollowRedirect(logonMethod));
                    } else if (TOKEN_FIELDS.contains(name)) {
                        // one time password, ask it to the user
                        logonMethod.setParameter(name, DavGatewayOTPPrompt.getOneTimePassword());
                    } else if ("otc".equals(name)) {
                        // captcha image, get image and ask user
                        String pinsafeUser = getAliasFromLogin();
                        if (pinsafeUser == null) {
                            pinsafeUser = username;
                        }
                        HttpGet pinRequest = new HttpGet("/PINsafeISAFilter.dll?username=" + pinsafeUser);
                        try (CloseableHttpResponse pinResponse = httpClient.execute(pinRequest)) {
                            int status = pinResponse.getStatusLine().getStatusCode();
                            if (status != HttpStatus.SC_OK) {
                                throw HttpClientAdapter.buildHttpResponseException(pinRequest, pinResponse.getStatusLine());
                            }
                            BufferedImage captchaImage = ImageIO.read(pinResponse.getEntity().getContent());
                            logonMethod.setParameter(name, DavGatewayOTPPrompt.getCaptchaValue(captchaImage));
                        }
                    }
                }
            } else {
                List<? extends TagNode> frameList = node.getElementListByName("frame", true);
                if (frameList.size() == 1) {
                    String src = frameList.get(0).getAttributeByName("src");
                    if (src != null) {
                        LOGGER.debug("Frames detected in form page, try frame content");
                        logonMethod = buildLogonMethod(httpClient, httpClient.executeFollowRedirect(new GetRequest(src)));
                    }
                } else {
                    // another failover for script based logon forms (Exchange 2007)
                    List<? extends TagNode> scriptList = node.getElementListByName("script", true);
                    for (Object script : scriptList) {
                        List<? extends BaseToken> contents = ((TagNode) script).getAllChildren();
                        for (Object content : contents) {
                            if (content instanceof CommentNode) {
                                String scriptValue = ((CommentNode) content).getCommentedContent();
                                String sUrl = StringUtil.getToken(scriptValue, "var a_sUrl = \"", "\"");
                                String sLgn = StringUtil.getToken(scriptValue, "var a_sLgnQS = \"", "\"");
                                if (sLgn == null) {
                                    sLgn = StringUtil.getToken(scriptValue, "var a_sLgn = \"", "\"");
                                }
                                if (sUrl != null && sLgn != null) {
                                    URI src = getScriptBasedFormURL(uri, sLgn + sUrl);
                                    LOGGER.debug("Detected script based logon, redirect to form at " + src);
                                    logonMethod = buildLogonMethod(httpClient, httpClient.executeFollowRedirect(new GetRequest(src)));
                                }

                            } else if (content instanceof ContentNode) {
                                // Microsoft Forefront Unified Access Gateway redirect
                                String scriptValue = ((ContentNode) content).getContent();
                                String location = StringUtil.getToken(scriptValue, "window.location.replace(\"", "\"");
                                if (location != null) {
                                    LOGGER.debug("Post logon redirect to: " + location);
                                    logonMethod = buildLogonMethod(httpClient, httpClient.executeFollowRedirect(new GetRequest(location)));
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Error parsing login form at " + responseWrapper.getURI());
        }

        return logonMethod;
    }


    protected ResponseWrapper postLogonMethod(HttpClientAdapter httpClient, PostRequest logonMethod, String password) throws IOException {

        setAuthFormFields(logonMethod, httpClient, password);

        // add exchange 2010 PBack cookie in compatibility mode
        BasicClientCookie pBackCookie = new BasicClientCookie("PBack", "0");
        pBackCookie.setPath("/");
        pBackCookie.setDomain(httpClientAdapter.getHost());
        httpClient.addCookie(pBackCookie);

        ResponseWrapper resultRequest = httpClient.executeFollowRedirect(logonMethod);

        // test form based authentication
        checkFormLoginQueryString(resultRequest);

        // workaround for post logon script redirect
        if (!isAuthenticated(resultRequest)) {
            // try to get new method from script based redirection
            logonMethod = buildLogonMethod(httpClient, resultRequest);

            if (logonMethod != null) {
                if (otpPreAuthFound && otpPreAuthRetries < MAX_OTP_RETRIES) {
                    // A OTP pre-auth page has been found, it is needed to restart the login process.
                    // This applies to both the case the user entered a good OTP code (the usual login process
                    // takes place) and the case the user entered a wrong OTP code (another code will be asked to him).
                    // The user has up to MAX_OTP_RETRIES chances to input a valid OTP key.
                    return postLogonMethod(httpClient, logonMethod, password);
                }

                // if logonMethod is not null, try to follow redirection
                resultRequest = httpClient.executeFollowRedirect(logonMethod);

                checkFormLoginQueryString(resultRequest);
                // also check cookies
                if (!isAuthenticated(resultRequest)) {
                    throwAuthenticationFailed();
                }
            } else {
                // authentication failed
                throwAuthenticationFailed();
            }
        }

        // check for language selection form
        if ("/owa/languageselection.aspx".equals(resultRequest.getURI().getPath())) {
            // need to submit form
            resultRequest = submitLanguageSelectionForm(resultRequest.getURI(), resultRequest.getResponseBodyAsString());
        }
        return resultRequest;
    }

    protected ResponseWrapper submitLanguageSelectionForm(URI uri, String responseBodyAsString) throws IOException {
        PostRequest postLanguageFormMethod;
        // create an instance of HtmlCleaner
        HtmlCleaner cleaner = new HtmlCleaner();

        try {
            TagNode node = cleaner.clean(responseBodyAsString);
            List<? extends TagNode> forms = node.getElementListByName("form", true);
            TagNode languageForm;
            // select form
            if (forms.size() == 1) {
                languageForm = forms.get(0);
            } else {
                throw new IOException("Form not found");
            }
            String languageMethodPath = languageForm.getAttributeByName("action");

            postLanguageFormMethod = new PostRequest(getAbsoluteUri(uri, languageMethodPath));

            List<? extends TagNode> inputList = languageForm.getElementListByName("input", true);
            for (Object input : inputList) {
                String name = ((TagNode) input).getAttributeByName("name");
                String value = ((TagNode) input).getAttributeByName("value");
                if (name != null && value != null) {
                    postLanguageFormMethod.setParameter(name, value);
                }
            }
            List<? extends TagNode> selectList = languageForm.getElementListByName("select", true);
            for (Object select : selectList) {
                String name = ((TagNode) select).getAttributeByName("name");
                List<? extends TagNode> optionList = ((TagNode) select).getElementListByName("option", true);
                String value = null;
                for (Object option : optionList) {
                    if (((TagNode) option).getAttributeByName("selected") != null) {
                        value = ((TagNode) option).getAttributeByName("value");
                        break;
                    }
                }
                if (name != null && value != null) {
                    postLanguageFormMethod.setParameter(name, value);
                }
            }
        } catch (IOException | URISyntaxException e) {
            String errorMessage = "Error parsing language selection form at " + uri;
            LOGGER.error(errorMessage);
            throw new IOException(errorMessage);
        }

        return httpClientAdapter.executeFollowRedirect(postLanguageFormMethod);
    }

    protected void setAuthFormFields(HttpRequestBase logonMethod, HttpClientAdapter httpClient, String password) throws IllegalArgumentException {
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
                httpClient.setCredentials(username, password);
            }
            ((PostRequest) logonMethod).removeParameter("userid");
            ((PostRequest) logonMethod).setParameter("userid", userid);

            usernameInput = "username";
        } else if (usernameInputs.size() == 1) {
            // simple username field
            usernameInput = usernameInputs.get(0);
        } else {
            // should not happen
            usernameInput = "username";
        }
        // make sure username and password fields are empty
        ((PostRequest) logonMethod).removeParameter(usernameInput);
        if (passwordInput != null) {
            ((PostRequest) logonMethod).removeParameter(passwordInput);
        }
        ((PostRequest) logonMethod).removeParameter("trusted");
        ((PostRequest) logonMethod).removeParameter("flags");

        if (passwordInput == null) {
            // This is a OTP pre-auth page. A different username may be required.
            otpPreAuthFound = true;
            otpPreAuthRetries++;
            ((PostRequest) logonMethod).setParameter(usernameInput, preAuthusername);
        } else {
            otpPreAuthFound = false;
            otpPreAuthRetries = 0;
            // This is a regular Exchange login page
            ((PostRequest) logonMethod).setParameter(usernameInput, username);
            ((PostRequest) logonMethod).setParameter(passwordInput, password);
            ((PostRequest) logonMethod).setParameter("trusted", "4");
            ((PostRequest) logonMethod).setParameter("flags", "4");
        }
    }

    protected URI getAbsoluteUri(URI uri, String path) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(uri);
        if (path != null) {
            // reset query string
            uriBuilder.clearParameters();
            if (path.startsWith("/")) {
                // path is absolute, replace method path
                uriBuilder.setPath(path);
            } else if (path.startsWith("http://") || path.startsWith("https://")) {
                return URI.create(path);
            } else {
                // relative path, build new path
                String currentPath = uri.getPath();
                int end = currentPath.lastIndexOf('/');
                if (end >= 0) {
                    uriBuilder.setPath(currentPath.substring(0, end + 1) + path);
                } else {
                    throw new URISyntaxException(uriBuilder.build().toString(), "Invalid path");
                }
            }
        }
        return uriBuilder.build();
    }

    protected URI getScriptBasedFormURL(URI uri, String pathQuery) throws URISyntaxException, IOException {
        URIBuilder uriBuilder = new URIBuilder(uri);
        int queryIndex = pathQuery.indexOf('?');
        if (queryIndex >= 0) {
            if (queryIndex > 0) {
                // update path
                String newPath = pathQuery.substring(0, queryIndex);
                if (newPath.startsWith("/")) {
                    // absolute path
                    uriBuilder.setPath(newPath);
                } else {
                    String currentPath = uriBuilder.getPath();
                    int folderIndex = currentPath.lastIndexOf('/');
                    if (folderIndex >= 0) {
                        // replace relative path
                        uriBuilder.setPath(currentPath.substring(0, folderIndex + 1) + newPath);
                    } else {
                        // should not happen
                        uriBuilder.setPath('/' + newPath);
                    }
                }
            }
            uriBuilder.setCustomQuery(URIUtil.decode(pathQuery.substring(queryIndex + 1)));
        }
        return uriBuilder.build();
    }

    protected void checkFormLoginQueryString(ResponseWrapper logonMethod) throws DavMailAuthenticationException {
        String queryString = logonMethod.getURI().getRawQuery();
        if (queryString != null && (queryString.contains("reason=2") || queryString.contains("reason=4"))) {
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
        httpClientAdapter.close();
    }

    /**
     * Oauth token.
     * Only for Office 365 authenticators
     *
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
     * Return authenticated HttpClient 4 HttpClientAdapter
     *
     * @return HttpClientAdapter instance
     */
    public HttpClientAdapter getHttpClientAdapter() {
        return httpClientAdapter;
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


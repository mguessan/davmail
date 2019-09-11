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

import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import davmail.http.request.PostRequest;
import davmail.http.request.RestRequest;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class O365Authenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger(O365Authenticator.class);

    private static final String RESOURCE = "https://outlook.office365.com";

    private String tenantId;
    // Office 365 username
    private String username;
    // authentication userid, can be different from username
    private String userid;
    private String password;
    private O365Token token;

    public void setUsername(String username) {
        if (username.contains("|")) {
            this.userid = username.substring(0, username.indexOf("|"));
            this.username = username.substring(username.indexOf("|") + 1);
        } else {
            this.username = username;
            this.userid = username;
        }
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public O365Token getToken() {
        return token;
    }

    public URI getExchangeUri() {
        return URI.create(RESOURCE + "/EWS/Exchange.asmx");
    }

    public void authenticate() throws IOException {
        HttpClientAdapter httpClientAdapter = null;
        try {
            // common DavMail client id
            String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
            // standard native app redirectUri
            String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");
            // company tenantId or common
            tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

            // first try to load stored token
            token = O365Token.load(tenantId, clientId, redirectUri, username, password);
            if (token != null) {
                return;
            }

            URI uri = new URIBuilder()
                    .setScheme("https")
                    .setHost("login.microsoftonline.com")
                    .setPath("/"+tenantId+"/oauth2/authorize")
                    .addParameter("client_id", clientId)
                    .addParameter("response_type", "code")
                    .addParameter("redirect_uri", redirectUri)
                    .addParameter("response_mode", "query")
                    .addParameter("resource", RESOURCE)
                    .addParameter("login_hint", username)
                    // force consent
                    //.addParameter("prompt", "consent")
                    .build();
            String url = uri.toString();

            httpClientAdapter = new HttpClientAdapter(url, userid, password);

            GetRequest getMethod = new GetRequest(url);
            String responseBodyAsString = executeRequest(httpClientAdapter, getMethod);
            String code;
            if (!responseBodyAsString.contains("Config=")) {
                // we are no longer on Microsoft, try ADFS
                code = authenticateADFS(httpClientAdapter, responseBodyAsString, url);
            } else {
                JSONObject config = extractConfig(responseBodyAsString);

                String context = config.getString("sCtx"); // csts request
                String apiCanary = config.getString("apiCanary"); // canary for API calls
                String clientRequestId = config.getString("correlationId");
                String hpgact = config.getString("hpgact");
                String hpgid = config.getString("hpgid");
                String flowToken = config.getString("sFT");
                String canary = config.getString("canary");
                String sessionId = config.getString("sessionId");

                String referer = getMethod.getURI().toString();

                RestRequest getCredentialMethod = new RestRequest("https://login.microsoftonline.com/" + tenantId + "/GetCredentialType");
                getCredentialMethod.setRequestHeader("Accept", "application/json");
                getCredentialMethod.setRequestHeader("canary", apiCanary);
                getCredentialMethod.setRequestHeader("client-request-id", clientRequestId);
                getCredentialMethod.setRequestHeader("hpgact", hpgact);
                getCredentialMethod.setRequestHeader("hpgid", hpgid);
                getCredentialMethod.setRequestHeader("hpgrequestid", sessionId);
                getCredentialMethod.setRequestHeader("Referer", referer);

                final JSONObject jsonObject = new JSONObject();
                jsonObject.put("username", username);
                jsonObject.put("isOtherIdpSupported", true);
                jsonObject.put("checkPhones", false);
                jsonObject.put("isRemoteNGCSupported", false);
                jsonObject.put("isCookieBannerShown", false);
                jsonObject.put("isFidoSupported", false);
                jsonObject.put("flowToken", flowToken);
                jsonObject.put("originalRequest", context);

                getCredentialMethod.setJsonBody(jsonObject);

                JSONObject credentialType = executeRequestGetConfig(httpClientAdapter, getCredentialMethod);
                getCredentialMethod.releaseConnection();
                LOGGER.debug("CredentialType=" + credentialType);

                JSONObject credentials = credentialType.getJSONObject("Credentials");
                String federationRedirectUrl = credentials.optString("FederationRedirectUrl");

                if (federationRedirectUrl != null && !federationRedirectUrl.isEmpty()) {
                    LOGGER.debug("Detected ADFS, redirecting to " + federationRedirectUrl);
                    code = authenticateRedirectADFS(httpClientAdapter, federationRedirectUrl, url);
                } else {
                    PostRequest logonMethod = new PostRequest("https://login.microsoftonline.com/"+tenantId+"/login");
                    logonMethod.setRequestHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    logonMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

                    logonMethod.setRequestHeader("Referer", referer);

                    logonMethod.setParameter("canary", canary);
                    logonMethod.setParameter("ctx", context);
                    logonMethod.setParameter("flowToken", flowToken);
                    logonMethod.setParameter("hpgrequestid", sessionId);
                    logonMethod.setParameter("login", username);
                    logonMethod.setParameter("loginfmt", username);
                    logonMethod.setParameter("passwd", password);

                    httpClientAdapter.execute(logonMethod);
                    responseBodyAsString = logonMethod.getResponseBodyAsString();
                    if (responseBodyAsString != null && responseBodyAsString.indexOf("arrUserProofs") > 0) {
                        logonMethod = handleMfa(httpClientAdapter, logonMethod, username, clientRequestId);
                    }

                    Header locationHeader = logonMethod.getResponseHeader("Location");
                    if (locationHeader == null || !locationHeader.getValue().startsWith(redirectUri)) {
                        // extract response
                        config = extractConfig(logonMethod.getResponseBodyAsString());
                        if (config.optJSONArray("arrScopes") != null || config.optJSONArray("urlPostRedirect") != null) {
                            LOGGER.debug("Authentication successful but user consent or validation needed, please open the following url in a browser");
                            LOGGER.debug(url);
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else if (config.optString("strServiceExceptionMessage") != null) {
                            LOGGER.debug("O365 returned error: "+config.optString("strServiceExceptionMessage"));
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else if ("50126".equals(config.optString("sErrorCode"))) {
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else {
                            throw new DavMailAuthenticationException("LOG_MESSAGE", "Authentication failed, unknown error: " + config);
                        }
                    }
                    String location = locationHeader.getValue();
                    code = location.substring(location.indexOf("code=") + 5, location.indexOf("&session_state="));
                }
            }
            LOGGER.debug("Authentication Code: " + code);

            token = O365Token.build(tenantId, clientId, redirectUri, code, password);

            LOGGER.debug("Authenticated username: " + token.getUsername());
            if (!username.equalsIgnoreCase(token.getUsername())) {
                throw new IOException("Authenticated username " + token.getUsername() + " does not match " + username);
            }

        } catch (JSONException e) {
            throw new IOException(e + " " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new IOException(e + " " + e.getMessage());
        } finally {
            // do not keep login connections open
            if (httpClientAdapter != null) {
                httpClientAdapter.close();
            }
        }

    }

    private String authenticateRedirectADFS(HttpClientAdapter httpClientAdapter, String federationRedirectUrl, String authorizeUrl) throws IOException {
        // get ADFS login form
        GetRequest logonFormMethod = new GetRequest(federationRedirectUrl);

        httpClientAdapter.execute(logonFormMethod);
        String responseBodyAsString = logonFormMethod.getResponseBodyAsString();
        return authenticateADFS(httpClientAdapter, responseBodyAsString, authorizeUrl);
    }

    private String authenticateADFS(HttpClientAdapter httpClientAdapter, String responseBodyAsString, String authorizeUrl) throws IOException {
        String location;

        if (responseBodyAsString.contains("login.microsoftonline.com")) {
            LOGGER.info("Already authenticated through Basic or NTLM");
        } else {
            // parse form to get target url, authenticate as userid
            PostRequest logonMethod = new PostRequest(extract("method=\"post\" action=\"([^\"]+)\"", responseBodyAsString));
            logonMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

            logonMethod.setParameter("UserName", userid);
            logonMethod.setParameter("Password", password);
            logonMethod.setParameter("AuthMethod", "FormsAuthentication");

            httpClientAdapter.execute(logonMethod);

            if (logonMethod.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY || logonMethod.getResponseHeader("Location") == null) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }
            location = logonMethod.getResponseHeader("Location").getValue();
            GetRequest redirectMethod = new GetRequest(location);
            try {
                httpClientAdapter.execute(redirectMethod);
                responseBodyAsString = redirectMethod.getResponseBodyAsString();
            } finally {
                redirectMethod.releaseConnection();
            }
        }

        if (!responseBodyAsString.contains("login.microsoftonline.com")) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        }
        String targetUrl = extract("action=\"([^\"]+)\"", responseBodyAsString);
        String wa = extract("name=\"wa\" value=\"([^\"]+)\"", responseBodyAsString);
        String wresult = extract("name=\"wresult\" value=\"([^\"]+)\"", responseBodyAsString);
        // decode wresult
        wresult = wresult.replaceAll("&quot;", "\"");
        wresult = wresult.replaceAll("&lt;", "<");
        wresult = wresult.replaceAll("&gt;", ">");
        String wctx = extract("name=\"wctx\" value=\"([^\"]+)\"", responseBodyAsString);
        wctx = wctx.replaceAll("&amp;", "&");

        PostRequest targetMethod = new PostRequest(targetUrl);
        targetMethod.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        targetMethod.setParameter("wa", wa);
        targetMethod.setParameter("wresult", wresult);
        targetMethod.setParameter("wctx", wctx);

        httpClientAdapter.execute(targetMethod);
        responseBodyAsString = targetMethod.getResponseBodyAsString();

        LOGGER.debug(targetMethod.getURI().toString());
        LOGGER.debug(targetMethod.getStatusLine());
        LOGGER.debug(responseBodyAsString);

        if (targetMethod.getStatusCode() == HttpStatus.SC_OK) {
            JSONObject config = extractConfig(responseBodyAsString);
            if (config.optJSONArray("arrScopes") != null || config.optJSONArray("urlPostRedirect") != null) {
                LOGGER.debug("Authentication successful but user consent or validation needed, please open the following url in a browser");
                LOGGER.debug(authorizeUrl);
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }
        } else if (targetMethod.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY || targetMethod.getResponseHeader("Location") == null) {
            throw new IOException("Unknown ADFS authentication failure");
        }

        location = targetMethod.getResponseHeader("Location").getValue();
        if (location.startsWith("https://device.login.microsoftonline.com")) {
            location = processDeviceLogin(httpClientAdapter, location);
        }

        if (location.contains("code=") && location.contains("&session_state=")) {
            String code = location.substring(location.indexOf("code=") + 5, location.indexOf("&session_state="));
            LOGGER.debug("Authentication Code: " + code);
            return code;
        }
        throw new IOException("Unknown ADFS authentication failure");
    }

    private String processDeviceLogin(HttpClientAdapter httpClient, String location) throws IOException {
        String result = location;
        LOGGER.debug("Proceed to device authentication");
        GetRequest deviceLoginMethod = new GetRequest(location);

        httpClient.execute(deviceLoginMethod);
        String responseBodyAsString = deviceLoginMethod.getResponseBodyAsString();
        if (responseBodyAsString.contains("login.microsoftonline.com")) {
            String ctx = extract("name=\"ctx\" value=\"([^\"]+)\"", responseBodyAsString);
            String flowtoken = extract("name=\"flowtoken\" value=\"([^\"]+)\"", responseBodyAsString);

            PostRequest processMethod = new PostRequest(extract("action=\"([^\"]+)\"", responseBodyAsString));
            processMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

            processMethod.setParameter("ctx", ctx);
            processMethod.setParameter("flowtoken", flowtoken);

            try {
                httpClient.execute(processMethod);
            } finally {
                processMethod.releaseConnection();
            }

            if (processMethod.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY || processMethod.getResponseHeader("Location") == null) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }
            result = processMethod.getResponseHeader("Location").getValue();
        }
        return result;
    }

    private PostRequest handleMfa(HttpClientAdapter httpClientAdapter, PostRequest logonMethod, String username, String clientRequestId) throws JSONException, IOException {
        JSONObject config = extractConfig(logonMethod.getResponseBodyAsString());
        LOGGER.debug("Config=" + config);

        String urlBeginAuth = config.getString("urlBeginAuth");
        String urlEndAuth = config.getString("urlEndAuth");

        boolean isMFAMethodSupported = false;

        for (int i = 0; i < config.getJSONArray("arrUserProofs").length(); i++) {
            JSONObject authMethod = (JSONObject) config.getJSONArray("arrUserProofs").get(i);
            LOGGER.debug("Authentication method: " + authMethod.getString("authMethodId"));
            if ("PhoneAppNotification".equals(authMethod.getString("authMethodId"))) {
                LOGGER.debug("Found phone app auth method " + authMethod.getString("display"));
                isMFAMethodSupported = true;
            }
        }

        if (!isMFAMethodSupported) {
            throw new IOException("MFA authentication methods not supported");
        }

        String context = config.getString("sCtx");
        String flowToken = config.getString("sFT");

        String canary = config.getString("canary");
        String apiCanary = config.getString("apiCanary");

        String hpgrequestid = logonMethod.getResponseHeader("x-ms-request-id").getValue();
        String hpgact = config.getString("hpgact");
        String hpgid = config.getString("hpgid");

        RestRequest beginAuthMethod = new RestRequest(urlBeginAuth);
        beginAuthMethod.setRequestHeader("Accept", "application/json");
        beginAuthMethod.setRequestHeader("canary", apiCanary);
        beginAuthMethod.setRequestHeader("client-request-id", clientRequestId);
        beginAuthMethod.setRequestHeader("hpgact", hpgact);
        beginAuthMethod.setRequestHeader("hpgid", hpgid);
        beginAuthMethod.setRequestHeader("hpgrequestid", hpgrequestid);

        // only support PhoneAppNotification
        JSONObject beginAuthJson = new JSONObject();
        beginAuthJson.put("AuthMethodId", "PhoneAppNotification");
        beginAuthJson.put("Ctx", context);
        beginAuthJson.put("FlowToken", flowToken);
        beginAuthJson.put("Method", "BeginAuth");
        beginAuthMethod.setJsonBody(beginAuthJson);

        httpClientAdapter.execute(beginAuthMethod);
        config = beginAuthMethod.getJsonResponse();
        beginAuthMethod.releaseConnection();
        LOGGER.debug(config);

        if (!config.getBoolean("Success")) {
            throw new IOException("Authentication failed: " + config);
        }

        context = config.getString("Ctx");
        flowToken = config.getString("FlowToken");
        String sessionId = config.getString("SessionId");

        int i = 0;
        boolean success = false;
        while (!success && i++ < 12) {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.debug("Interrupted");
            }

            RestRequest endAuthMethod = new RestRequest(urlEndAuth);
            endAuthMethod.setRequestHeader("Accept", "application/json");
            endAuthMethod.setRequestHeader("canary", apiCanary);
            endAuthMethod.setRequestHeader("client-request-id", clientRequestId);
            endAuthMethod.setRequestHeader("hpgact", hpgact);
            endAuthMethod.setRequestHeader("hpgid", hpgid);
            endAuthMethod.setRequestHeader("hpgrequestid", hpgrequestid);

            JSONObject endAuthJson = new JSONObject();
            endAuthJson.put("AuthMethodId", "PhoneAppNotification");
            endAuthJson.put("Ctx", context);
            endAuthJson.put("FlowToken", flowToken);
            endAuthJson.put("Method", "EndAuth");
            endAuthJson.put("PollCount", "1");
            endAuthJson.put("SessionId", sessionId);

            endAuthMethod.setJsonBody(endAuthJson);

            httpClientAdapter.execute(endAuthMethod);
            config = endAuthMethod.getJsonResponse();
            endAuthMethod.releaseConnection();
            LOGGER.debug(config);
            String resultValue = config.getString("ResultValue");
            if ("PhoneAppDenied".equals(resultValue) || "PhoneAppNoResponse".equals(resultValue)) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_REASON", resultValue);
            }
            if (config.getBoolean("Success")) {
                success = true;
            }
        }
        if (!success) {
            throw new IOException("Authentication failed: " + config);
        }

        String authMethod = "PhoneAppOTP";
        String type = "22";

        context = config.getString("Ctx");
        flowToken = config.getString("FlowToken");

        // process auth
        PostRequest processAuthMethod = new PostRequest("https://login.microsoftonline.com/"+tenantId+"/SAS/ProcessAuth");
        processAuthMethod.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        processAuthMethod.setParameter("type", type);
        processAuthMethod.setParameter("request", context);
        processAuthMethod.setParameter("mfaAuthMethod", authMethod);
        processAuthMethod.setParameter("canary", canary);
        processAuthMethod.setParameter("login", username);
        processAuthMethod.setParameter("flowToken", flowToken);
        processAuthMethod.setParameter("hpgrequestid", hpgrequestid);

        httpClientAdapter.execute(processAuthMethod);
        return processAuthMethod;

    }

    private String executeRequest(HttpClientAdapter httpClientAdapter, GetRequest getRequest) throws IOException {
        LOGGER.debug(getRequest.getURI());
        httpClientAdapter.executeFollowRedirects(getRequest);
        if (getRequest.getURI().getHost().endsWith("okta.com")) {
            throw new DavMailAuthenticationException("LOG_MESSAGE", "Okta authentication not supported, please try O365Interactive");
        }

        return getRequest.getResponseBodyAsString();
    }

    private JSONObject executeRequestGetConfig(HttpClientAdapter httpClientAdapter, RestRequest restRequest) throws IOException {
        LOGGER.debug(restRequest.getURI());
        httpClientAdapter.execute(restRequest);

        JSONObject jsonResponse = restRequest.getJsonResponse();
        LOGGER.debug(jsonResponse);
        return jsonResponse;
    }

    public JSONObject extractConfig(String content) throws IOException {
        try {
            return new JSONObject(extract("Config=([^\n]+);", content));
        } catch (JSONException e1) {
            LOGGER.debug(content);
            throw new IOException("Unable to extract config from response body");
        }
    }

    public String extract(String pattern, String content) throws IOException {
        String value;
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        if (matcher.find()) {
            value = matcher.group(1);
        } else {
            throw new IOException("pattern not found");
        }
        return value;
    }

}

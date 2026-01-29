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
import davmail.Settings;
import davmail.exception.DavMailAuthenticationException;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import davmail.http.request.PostRequest;
import davmail.http.request.ResponseWrapper;
import davmail.http.request.RestRequest;
import davmail.ui.NumberMatchingFrame;
import davmail.ui.PasswordPromptDialog;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class O365Authenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger(O365Authenticator.class);

    private String tenantId;
    // Office 365 username
    private String username;
    // authentication userid, can be different from username
    private String userid;
    private String password;
    private O365Token token;

    public static String buildAuthorizeUrl(String tenantId, String clientId, String redirectUri, String username) throws IOException {
        URI uri;
        try {
            URIBuilder uriBuilder = new URIBuilder(Settings.getO365LoginUrl())
                    .addParameter("client_id", clientId)
                    .addParameter("response_type", "code")
                    .addParameter("redirect_uri", redirectUri)
                    .addParameter("response_mode", "query")
                    .addParameter("login_hint", username);

            // force consent
            //uriBuilder.addParameter("prompt", "consent");

            if ("https://outlook.live.com".equals(Settings.getOutlookUrl())) {
                // live.com endpoint, able to obtain a token but EWS endpoint does not work
                String liveAuthorizeUrl = "https://login.live.com/oauth20_authorize.srf";
                uriBuilder = new URIBuilder(liveAuthorizeUrl)
                        .addParameter("client_id", clientId)
                        .addParameter("response_type", "code")
                        .addParameter("redirect_uri", redirectUri)
                        .addParameter("response_mode", "query")
                        .addParameter("login_hint", username)
                        .addParameter("scope", "openid offline_access https://outlook.live.com/EWS.AccessAsUser.All Mail.ReadWrite MailboxSettings.Read")
                        .addParameter("resource", "https://outlook.live.com")
                //.addParameter("prompt", "consent")
                ;

            } else if (Settings.getBooleanProperty("davmail.enableGraph", false)) {
                if (Settings.getBooleanProperty("davmail.enableOidc", false)) {
                    // OIDC compliant
                    uriBuilder.setPath("/" + tenantId + "/oauth2/v2.0/authorize")
                            .addParameter("scope", Settings.getProperty("davmail.oauth.scope", "openid profile offline_access Mail.ReadWrite Calendars.ReadWrite MailboxSettings.Read Mail.ReadWrite.Shared Contacts.ReadWrite Tasks.ReadWrite Mail.Send People.Read"));
                    // return scopes with 00000003-0000-0000-c000-000000000000 scopes
                    //.addParameter("scope", "openid profile offline_access .default");
                    //.addParameter("scope", "openid profile offline_access "+Settings.getGraphUrl()+"/.default");
                    //.addParameter("scope", "openid " + Settings.getOutlookUrl() + "/EWS.AccessAsUser.All AuditLog.Read.All Calendar.ReadWrite Calendars.Read.Shared Calendars.ReadWrite Contacts.ReadWrite DataLossPreventionPolicy.Evaluate Directory.AccessAsUser.All Directory.Read.All Files.Read Files.Read.All Files.ReadWrite.All Group.Read.All Group.ReadWrite.All InformationProtectionPolicy.Read Mail.ReadWrite Mail.Send Notes.Create Organization.Read.All People.Read People.Read.All Printer.Read.All PrintJob.ReadWriteBasic SensitiveInfoType.Detect SensitiveInfoType.Read.All SensitivityLabel.Evaluate Tasks.ReadWrite TeamMember.ReadWrite.All TeamsTab.ReadWriteForChat User.Read.All User.ReadBasic.All User.ReadWrite Users.Read");
                } else {
                    // Outlook desktop relies on classic authorize endpoint
                    // on graph endpoint default scopes are scope -> AuditLog.Create Calendar.ReadWrite Calendars.Read.Shared Calendars.ReadWrite Contacts.ReadWrite DataLossPreventionPolicy.Evaluate Directory.AccessAsUser.All Directory.Read.All Files.Read Files.Read.All Files.ReadWrite.All FileStorageContainer.Selected Group.Read.All Group.ReadWrite.All InformationProtectionPolicy.Read Mail.ReadWrite Mail.Send Notes.Create Organization.Read.All People.Read People.Read.All Printer.Read.All PrinterShare.ReadBasic.All PrintJob.Create PrintJob.ReadWriteBasic Reports.Read.All SensitiveInfoType.Detect SensitiveInfoType.Read.All SensitivityLabel.Evaluate Tasks.ReadWrite TeamMember.ReadWrite.All TeamsTab.ReadWriteForChat User.Read.All User.ReadBasic.All User.ReadWrite Users.Read
                    uriBuilder.setPath("/" + tenantId + "/oauth2/authorize")
                            .addParameter("resource", Settings.getGraphUrl());
                }
                // Probably irrelevant except for graph api, see above, switch to new v2.0 OIDC compliant endpoint https://docs.microsoft.com/en-us/azure/active-directory/develop/azure-ad-endpoint-comparison
            } else if (Settings.getBooleanProperty("davmail.enableOidc", false)) {
                uriBuilder.setPath("/" + tenantId + "/oauth2/v2.0/authorize")
                        .addParameter("scope", Settings.getProperty("davmail.oauth.scope", "openid profile offline_access " + Settings.getOutlookUrl() + "/EWS.AccessAsUser.All"));
            } else {
                uriBuilder.setPath("/" + tenantId + "/oauth2/authorize")
                        .addParameter("resource", Settings.getOutlookUrl());
            }

            uri = uriBuilder.build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return uri.toString();
    }

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
        return URI.create(Settings.getO365Url());
    }

    /**
     * Return a pool enabled HttpClientAdapter instance to access O365
     *
     * @return HttpClientAdapter instance
     */
    @Override
    public HttpClientAdapter getHttpClientAdapter() {
        return new HttpClientAdapter(getExchangeUri(), username, password, true);
    }

    public void authenticate() throws IOException {
        // common DavMail client id
        String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        // standard native app redirectUri
        String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://localhost/common/oauth2/nativeclient");
        // company tenantId or common
        tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load stored token
        token = O365Token.load(tenantId, clientId, redirectUri, username, password);
        if (token != null) {
            return;
        }

        String url = O365Authenticator.buildAuthorizeUrl(tenantId, clientId, redirectUri, username);

        try (
                HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url, userid, password)
        ) {

            GetRequest getRequest = new GetRequest(url);
            String responseBodyAsString = executeFollowRedirect(httpClientAdapter, getRequest);
            String code;
            if (!responseBodyAsString.contains("Config=") && responseBodyAsString.contains("ServerData =")) {
                // live.com form
                JSONObject config = extractServerData(responseBodyAsString);

                String referer = getRequest.getURI().toString();
                code = authenticateLive(httpClientAdapter, config, referer);
            } else if (!responseBodyAsString.contains("Config=")) {
                // we are no longer on Microsoft, try ADFS
                code = authenticateADFS(httpClientAdapter, responseBodyAsString, url);
            } else {
                JSONObject config = extractConfig(responseBodyAsString);

                checkConfigErrors(config);

                String context = config.getString("sCtx"); // csts request
                String apiCanary = config.getString("apiCanary"); // canary for API calls
                String clientRequestId = config.getString("correlationId");
                String hpgact = config.getString("hpgact");
                String hpgid = config.getString("hpgid");
                String flowToken = config.getString("sFT");
                String canary = config.getString("canary");
                String sessionId = config.getString("sessionId");

                String referer = getRequest.getURI().toString();

                RestRequest getCredentialMethod = new RestRequest(Settings.getO365LoginUrl() + "/" + tenantId + "/GetCredentialType");
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

                JSONObject credentialType = httpClientAdapter.executeRestRequest(getCredentialMethod);

                LOGGER.debug("CredentialType=" + credentialType);

                JSONObject credentials = credentialType.getJSONObject("Credentials");
                String federationRedirectUrl = credentials.optString("FederationRedirectUrl");

                if (federationRedirectUrl != null && !federationRedirectUrl.isEmpty()) {
                    LOGGER.debug("Detected ADFS, redirecting to " + federationRedirectUrl);
                    code = authenticateRedirectADFS(httpClientAdapter, federationRedirectUrl, url);
                } else {
                    PostRequest logonMethod = new PostRequest(Settings.getO365LoginUrl() + "/" + tenantId + "/login");
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

                    responseBodyAsString = httpClientAdapter.executePostRequest(logonMethod);
                    URI location = logonMethod.getRedirectLocation();

                    if (responseBodyAsString != null && responseBodyAsString.contains("arrUserProofs")) {
                        logonMethod = handleMfa(httpClientAdapter, logonMethod, username, clientRequestId);
                        location = logonMethod.getRedirectLocation();
                    }

                    if (location == null || !location.toString().startsWith(redirectUri)) {
                        // extract response
                        config = extractConfig(logonMethod.getResponseBodyAsString());
                        if (config.optJSONArray("arrScopes") != null || config.optJSONArray("urlPostRedirect") != null) {
                            LOGGER.warn("Authentication successful but user consent or validation needed, please open the following url in a browser");
                            LOGGER.warn(url);
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else if ("ConvergedChangePassword".equals(config.optString("pgid"))) {
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_PASSWORD_EXPIRED");
                        } else if ("50126".equals(config.optString("sErrorCode"))) {
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else if ("50125".equals(config.optString("sErrorCode"))) {
                            throw new DavMailAuthenticationException("LOG_MESSAGE", "Your organization needs more information to keep your account secure, authenticate once in a web browser and try again");
                        } else if ("50128".equals(config.optString("sErrorCode"))) {
                            throw new DavMailAuthenticationException("LOG_MESSAGE", "Invalid domain name - No tenant-identifying information found in either the request or implied by any provided credentials.");
                        } else if (config.optString("strServiceExceptionMessage", null) != null) {
                            LOGGER.debug("O365 returned error: " + config.optString("strServiceExceptionMessage"));
                            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
                        } else {
                            throw new DavMailAuthenticationException("LOG_MESSAGE", "Authentication failed, unknown error: " + config);
                        }
                    }
                    String query = location.toString();
                    if (query.contains("code=")) {
                        code = query.substring(query.indexOf("code=") + 5, query.indexOf("&session_state="));
                    } else {
                        throw new DavMailAuthenticationException("LOG_MESSAGE", "Authentication failed, unknown error: " + query);
                    }
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
        }

    }

    private void checkConfigErrors(JSONObject config) throws DavMailAuthenticationException {
        if (config.optString("strServiceExceptionMessage", null) != null) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_REASON", config.optString("strServiceExceptionMessage"));
        }
    }

    private String authenticateLive(HttpClientAdapter httpClientAdapter, JSONObject config, String referer) throws JSONException, IOException {
        String urlPost = config.getString("urlPost");
        PostRequest logonMethod = new PostRequest(urlPost);
        logonMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        logonMethod.setRequestHeader("Referer", referer);
        String sFTTag = config.optString("sFTTag");
        String ppft = "";
        if (sFTTag.contains("value=")) {
            ppft = sFTTag.substring(sFTTag.indexOf("value=\"")+7, sFTTag.indexOf("\"/>"));
        }

        logonMethod.setParameter("PPFT", ppft);

        logonMethod.setParameter("login", username);
        logonMethod.setParameter("loginfmt", username);

        logonMethod.setParameter("passwd", password);

        String responseBodyAsString = httpClientAdapter.executePostRequest(logonMethod);
        URI location = logonMethod.getRedirectLocation();
        if (location == null) {
            if (responseBodyAsString.contains("ServerData =")) {
                String errorMessage = extractServerData(responseBodyAsString).optString("sErrTxt");
                throw new IOException("Live.com authentication failure: "+errorMessage);
            }
        } else {
            String query = location.getQuery();
            if (query.contains("code=")) {
                String code = query.substring(query.indexOf("code=") + 5);
                LOGGER.debug("Authentication Code: " + code);
                return code;
            }
        }
        throw new IOException("Unknown Live.com authentication failure");
    }

    private String authenticateRedirectADFS(HttpClientAdapter httpClientAdapter, String federationRedirectUrl, String authorizeUrl) throws IOException, JSONException {
        // get ADFS login form
        GetRequest logonFormMethod = new GetRequest(federationRedirectUrl);
        logonFormMethod = httpClientAdapter.executeFollowRedirect(logonFormMethod);
        String responseBodyAsString = logonFormMethod.getResponseBodyAsString();
        return authenticateADFS(httpClientAdapter, responseBodyAsString, authorizeUrl);
    }

    private String authenticateADFS(HttpClientAdapter httpClientAdapter, String responseBodyAsString, String authorizeUrl) throws IOException, JSONException {
        URI location;

        if (responseBodyAsString.contains(Settings.getO365LoginUrl())) {
            LOGGER.info("Already authenticated through Basic or NTLM");
        } else {
            // parse form to get target url, authenticate as userid
            PostRequest logonMethod = new PostRequest(extract("method=\"post\" action=\"([^\"]+)\"", responseBodyAsString));
            logonMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

            logonMethod.setParameter("UserName", userid);
            logonMethod.setParameter("Password", password);
            logonMethod.setParameter("AuthMethod", "FormsAuthentication");

            httpClientAdapter.executePostRequest(logonMethod);
            location = logonMethod.getRedirectLocation();
            if (location == null) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }

            GetRequest redirectMethod = new GetRequest(location);
            responseBodyAsString = httpClientAdapter.executeGetRequest(redirectMethod);
        }

        if (!responseBodyAsString.contains(Settings.getO365LoginUrl())) {
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

        responseBodyAsString = httpClientAdapter.executePostRequest(targetMethod);
        location = targetMethod.getRedirectLocation();

        LOGGER.debug(targetMethod.getURI().toString());
        LOGGER.debug(targetMethod.getReasonPhrase());
        LOGGER.debug(responseBodyAsString);

        if (targetMethod.getStatusCode() == HttpStatus.SC_OK) {
            JSONObject config = extractConfig(responseBodyAsString);
            if (config.optJSONArray("arrScopes") != null || config.optJSONArray("urlPostRedirect") != null) {
                LOGGER.warn("Authentication successful but user consent or validation needed, please open the following url in a browser");
                LOGGER.warn(authorizeUrl);
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }
        } else if (targetMethod.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY || location == null) {
            throw new IOException("Unknown ADFS authentication failure");
        }

        if (location.getHost().startsWith("device")) {
            location = processDeviceLogin(httpClientAdapter, location);
        }
        String query = location.getQuery();
        if (query == null) {
            // failover for null query with non https URI like urn:ietf:wg:oauth:2.0:oob?code=...
            query = location.getSchemeSpecificPart();
        }

        if (query.contains("code=") && query.contains("&session_state=")) {
            String code = query.substring(query.indexOf("code=") + 5, query.indexOf("&session_state="));
            LOGGER.debug("Authentication Code: " + code);
            return code;
        }
        throw new IOException("Unknown ADFS authentication failure");
    }

    private URI processDeviceLogin(HttpClientAdapter httpClient, URI location) throws IOException, JSONException {
        URI result = location;
        LOGGER.debug("Proceed to device authentication, must have access to a client certificate signed by MS-Organization-Access");
        if (Settings.isWindows() &&
                (System.getProperty("java.version").compareTo("13") < 0
                        || !"MSCAPI".equals(Settings.getProperty("davmail.ssl.clientKeystoreType")))
        ) {
            LOGGER.warn("MSCAPI and Java version 13 or higher required to access TPM protected client certificate on Windows");
        }
        GetRequest deviceLoginMethod = new GetRequest(location);

        String responseBodyAsString = httpClient.executeGetRequest(deviceLoginMethod);

        if (responseBodyAsString.contains(Settings.getO365LoginUrl())) {
            String ctx = extract("name=\"ctx\" value=\"([^\"]+)\"", responseBodyAsString);
            String flowtoken = extract("name=\"flowtoken\" value=\"([^\"]+)\"", responseBodyAsString);

            PostRequest processMethod = new PostRequest(extract("action=\"([^\"]+)\"", responseBodyAsString));
            processMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

            processMethod.setParameter("ctx", ctx);
            processMethod.setParameter("flowtoken", flowtoken);

            responseBodyAsString = httpClient.executePostRequest(processMethod);
            result = processMethod.getRedirectLocation();

            // MFA triggered after device authentication
            if (result == null && responseBodyAsString != null && responseBodyAsString.contains("arrUserProofs")) {
                processMethod = handleMfa(httpClient, processMethod, username, null);
                result = processMethod.getRedirectLocation();
            }

            if (result == null) {
                throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
            }

        }
        return result;
    }

    private PostRequest handleMfa(HttpClientAdapter httpClientAdapter, PostRequest logonMethod, String username, String clientRequestId) throws IOException, JSONException {
        JSONObject config = extractConfig(logonMethod.getResponseBodyAsString());
        LOGGER.debug("Config=" + config);

        String urlBeginAuth = config.getString("urlBeginAuth");
        String urlEndAuth = config.getString("urlEndAuth");
        // Get processAuth url from config
        String urlProcessAuth = config.optString("urlPost", Settings.getO365LoginUrl() + "/" + tenantId + "/SAS/ProcessAuth");

        boolean isMFAMethodSupported = false;
        String chosenAuthMethodId = null;
        String chosenAuthMethodPrompt = null;

        for (int i = 0; i < config.getJSONArray("arrUserProofs").length(); i++) {
            JSONObject authMethod = (JSONObject) config.getJSONArray("arrUserProofs").get(i);
            String authMethodId = authMethod.getString("authMethodId");
            LOGGER.debug("Authentication method: " + authMethodId);
            if ("PhoneAppNotification".equals(authMethodId)) {
                LOGGER.debug("Found phone app auth method " + authMethod.getString("display"));
                isMFAMethodSupported = true;
                chosenAuthMethodId = authMethodId;
                chosenAuthMethodPrompt = authMethod.getString("display");
                // prefer phone app
                break;
            }
            if ("OneWaySMS".equals(authMethodId)) {
                LOGGER.debug("Found OneWaySMS auth method " + authMethod.getString("display"));
                chosenAuthMethodId = authMethodId;
                chosenAuthMethodPrompt = authMethod.getString("display");
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

        // clientRequestId is null coming from device login
        String correlationId = clientRequestId;
        if (correlationId == null) {
            correlationId = config.getString("correlationId");
        }

        RestRequest beginAuthMethod = new RestRequest(urlBeginAuth);
        beginAuthMethod.setRequestHeader("Accept", "application/json");
        beginAuthMethod.setRequestHeader("canary", apiCanary);
        beginAuthMethod.setRequestHeader("client-request-id", correlationId);
        beginAuthMethod.setRequestHeader("hpgact", hpgact);
        beginAuthMethod.setRequestHeader("hpgid", hpgid);
        beginAuthMethod.setRequestHeader("hpgrequestid", hpgrequestid);

        // only support PhoneAppNotification
        JSONObject beginAuthJson = new JSONObject();
        beginAuthJson.put("AuthMethodId", chosenAuthMethodId);
        beginAuthJson.put("Ctx", context);
        beginAuthJson.put("FlowToken", flowToken);
        beginAuthJson.put("Method", "BeginAuth");
        beginAuthMethod.setJsonBody(beginAuthJson);

        config = httpClientAdapter.executeRestRequest(beginAuthMethod);
        LOGGER.debug(config);

        if (!config.getBoolean("Success")) {
            throw new IOException("Authentication failed: " + config);
        }

        // look for number matching value
        String entropy = config.optString("Entropy", null);

        // display number matching value to user
        NumberMatchingFrame numberMatchingFrame = null;
        if (entropy != null && !"0".equals(entropy)) {
            LOGGER.info("Number matching value for " + username + ": " + entropy);
            if (!Settings.getBooleanProperty("davmail.server") && !GraphicsEnvironment.isHeadless()) {
                numberMatchingFrame = new NumberMatchingFrame(entropy);
            }
        }

        String smsCode = retrieveSmsCode(chosenAuthMethodId, chosenAuthMethodPrompt);

        context = config.getString("Ctx");
        flowToken = config.getString("FlowToken");
        String sessionId = config.getString("SessionId");

        int i = 0;
        boolean success = false;
        try {
            while (!success && i++ < 12) {

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    LOGGER.debug("Interrupted");
                    Thread.currentThread().interrupt();
                }

                RestRequest endAuthMethod = new RestRequest(urlEndAuth);
                endAuthMethod.setRequestHeader("Accept", "application/json");
                endAuthMethod.setRequestHeader("canary", apiCanary);
                endAuthMethod.setRequestHeader("client-request-id", clientRequestId);
                endAuthMethod.setRequestHeader("hpgact", hpgact);
                endAuthMethod.setRequestHeader("hpgid", hpgid);
                endAuthMethod.setRequestHeader("hpgrequestid", hpgrequestid);

                JSONObject endAuthJson = new JSONObject();
                endAuthJson.put("AuthMethodId", chosenAuthMethodId);
                endAuthJson.put("Ctx", context);
                endAuthJson.put("FlowToken", flowToken);
                endAuthJson.put("Method", "EndAuth");
                endAuthJson.put("PollCount", "1");
                endAuthJson.put("SessionId", sessionId);

                // When in beginAuthMethod is used 'AuthMethodId': 'OneWaySMS', then in endAuthMethod is send SMS code
                // via attribute 'AdditionalAuthData'
                endAuthJson.put("AdditionalAuthData", smsCode);

                endAuthMethod.setJsonBody(endAuthJson);

                config = httpClientAdapter.executeRestRequest(endAuthMethod);
                LOGGER.debug(config);
                String resultValue = config.getString("ResultValue");
                if ("PhoneAppDenied".equals(resultValue) || "PhoneAppNoResponse".equals(resultValue)) {
                    throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_REASON", resultValue);
                }
                if ("SMSAuthFailedWrongCodeEntered".equals(resultValue)) {
                    smsCode = retrieveSmsCode(chosenAuthMethodId, chosenAuthMethodPrompt);
                }
                if (config.getBoolean("Success")) {
                    success = true;
                }
            }
        } finally {
            // close number matching frame if exists
            if (numberMatchingFrame != null && numberMatchingFrame.isVisible()) {
                final JFrame finalNumberMatchingFrame = numberMatchingFrame;
                SwingUtilities.invokeLater(() -> {
                    finalNumberMatchingFrame.setVisible(false);
                    finalNumberMatchingFrame.dispose();
                });
            }

        }
        if (!success) {
            throw new IOException("Authentication failed: " + config);
        }

        String authMethod = chosenAuthMethodId;
        String type = "22";

        context = config.getString("Ctx");
        flowToken = config.getString("FlowToken");

        // process auth
        PostRequest processAuthMethod = new PostRequest(urlProcessAuth);
        processAuthMethod.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        processAuthMethod.setParameter("type", type);
        processAuthMethod.setParameter("request", context);
        processAuthMethod.setParameter("mfaAuthMethod", authMethod);
        processAuthMethod.setParameter("canary", canary);
        processAuthMethod.setParameter("login", username);
        processAuthMethod.setParameter("flowToken", flowToken);
        processAuthMethod.setParameter("hpgrequestid", hpgrequestid);

        httpClientAdapter.executePostRequest(processAuthMethod);
        return processAuthMethod;

    }

    private String retrieveSmsCode(String chosenAuthMethodId, String chosenAuthMethodPrompt) throws IOException {
        String smsCode = null;
        if ("OneWaySMS".equals(chosenAuthMethodId)) {
            LOGGER.info("Need to retrieve SMS verification code for " + username);
            if (Settings.getBooleanProperty("davmail.server") || GraphicsEnvironment.isHeadless()) {
                // headless or server mode
                System.out.print(BundleMessage.format("UI_SMS_PHONE_CODE", chosenAuthMethodPrompt));
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                smsCode = inReader.readLine();
            } else {
                PasswordPromptDialog passwordPromptDialog = new PasswordPromptDialog(BundleMessage.format("UI_SMS_PHONE_CODE", chosenAuthMethodPrompt));
                smsCode = String.valueOf(passwordPromptDialog.getPassword());
            }
        }
        return smsCode;
    }

    private String executeFollowRedirect(HttpClientAdapter httpClientAdapter, GetRequest getRequest) throws IOException {
        LOGGER.debug(getRequest.getURI());
        ResponseWrapper responseWrapper = httpClientAdapter.executeFollowRedirect(getRequest);
        String responseHost = responseWrapper.getURI().getHost();
        if (responseHost.endsWith("okta.com")) {
            throw new DavMailAuthenticationException("LOG_MESSAGE", "Okta authentication not supported, please try O365Interactive");
        }
        return responseWrapper.getResponseBodyAsString();
    }

    public JSONObject extractConfig(String content) throws IOException {
        try {
            return new JSONObject(extract("Config=([^\n]+);", content));
        } catch (JSONException e1) {
            LOGGER.debug(content);
            throw new IOException("Unable to extract config from response body");
        }
    }

    /**
     * Live.com logon form information
     * @param content response form
     * @return parsed configuration json
     * @throws IOException on error
     */
    public JSONObject extractServerData(String content) throws IOException {
        try {
            return new JSONObject(extract("ServerData =([^\n]+);", content));
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

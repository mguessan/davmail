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
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.RestMethod;
import davmail.util.IOUtil;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class O365Authenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger(O365Authenticator.class);

    private static final String RESOURCE = "https://outlook.office365.com";
    private String username;
    private String password;
    private String bearer;
    private String refreshToken;
    private long expireson;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBearer() throws IOException {
        // TODO: detect expiration and refresh token
        if (System.currentTimeMillis() > expireson * 1000) {
            throw new IOException("Token expired");
        }
        return bearer;
    }

    public String getEWSUrl() {
        return RESOURCE + "/EWS/Exchange.asmx";
    }

    public void authenticate() throws IOException {
        try {
            // common DavMail client id
            String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
            // standard native app redirectUri
            String redirectUri = Settings.getProperty("davmail.oauth.redirectUri", "https://login.microsoftonline.com/common/oauth2/nativeclient");

            String url = "https://login.microsoftonline.com/common/oauth2/authorize"
                    + "?client_id=" + clientId
                    + "&response_type=code"
                    + "&redirect_uri=" + redirectUri
                    + "&response_mode=query"
                    + "&resource=" + RESOURCE
                    + "&login_hint=" + URIUtil.encodeWithinQuery(username);
            // force consent
            //+"&prompt=consent"

            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(url);
            GetMethod getMethod = new GetMethod(url);
            JSONObject config = executeMethod(httpClient, getMethod);

            String context = config.getString("sCtx"); // csts request
            String apiCanary = config.getString("apiCanary"); // canary for API calls
            String clientRequestId = config.getString("correlationId");
            String hpgact = config.getString("hpgact");
            String hpgid = config.getString("hpgid");
            String flowToken = config.getString("sFT");
            String canary = config.getString("canary");
            String sessionId = config.getString("sessionId");

            String referer = getMethod.getURI().toString();

            RestMethod getCredentialMethod = new RestMethod("https://login.microsoftonline.com/common/GetCredentialType");
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

            JSONObject credentialType = executeMethod(httpClient, getCredentialMethod);
            getCredentialMethod.releaseConnection();
            LOGGER.debug("CredentialType=" + credentialType);

            PostMethod logonMethod = new PostMethod("https://login.microsoftonline.com/common/login");
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

            String code;
            try {
                httpClient.executeMethod(logonMethod);
                String responseBodyAsString = logonMethod.getResponseBodyAsString();
                if (responseBodyAsString.indexOf("arrUserProofs") > 0) {
                    logonMethod.releaseConnection();
                    logonMethod = handleMfa(httpClient, logonMethod, username, clientRequestId);
                }

                Header locationHeader = logonMethod.getResponseHeader("Location");
                if (locationHeader == null || !locationHeader.getValue().startsWith(redirectUri)) {
                    // extract response
                    config = extractConfig(logonMethod.getResponseBodyAsString());
                    LOGGER.debug("Please open the following url in a browser first to confirm consent:");
                    LOGGER.debug(url);
                    throw new IOException("Authentication failed, invalid credentials or consent needed" + config);
                }
                String location = locationHeader.getValue();
                code = location.substring(location.indexOf("code=") + 5, location.indexOf("&session_state="));

                LOGGER.debug("Authentication Code: " + code);
            } finally {
                logonMethod.releaseConnection();
            }

            PostMethod tokenMethod = new PostMethod("https://login.microsoftonline.com/common/oauth2/token");
            tokenMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            tokenMethod.addParameter("grant_type", "authorization_code");
            tokenMethod.addParameter("code", code);
            tokenMethod.addParameter("redirect_uri", redirectUri);
            tokenMethod.addParameter("client_id", clientId);

            try {
                httpClient.executeMethod(tokenMethod);

                JSONObject jsonToken = new JSONObject(tokenMethod.getResponseBodyAsString());

                bearer = jsonToken.getString("access_token");
                // precious refresh token
                refreshToken = jsonToken.getString("refresh_token");
                expireson = jsonToken.getLong("expires_on");

                LOGGER.debug("Bearer expires at " + new Date(expireson * 1000) + ": " + bearer);

                String decodedBearer = IOUtil.decodeBase64AsString(bearer.substring(bearer.indexOf('.') + 1, bearer.lastIndexOf('.')) + "==");
                JSONObject tokenBody = new JSONObject(decodedBearer);
                LOGGER.debug("Token: " + tokenBody);

                LOGGER.debug("Authenticated username: " + tokenBody.getString("unique_name"));
                if (!username.equalsIgnoreCase(tokenBody.getString("unique_name"))) {
                    throw new IOException("Authenticated username " + tokenBody.getString("unique_name") + " does not match " + username);
                }
            } finally {
                tokenMethod.releaseConnection();
            }
        } catch (JSONException e) {
            throw new IOException(e + " " + e.getMessage());
        }

    }

    private PostMethod handleMfa(HttpClient httpClient, PostMethod logonMethod, String username, String clientRequestId) throws JSONException, IOException {
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

        RestMethod beginAuthMethod = new RestMethod(urlBeginAuth);
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

        httpClient.executeMethod(beginAuthMethod);
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

            RestMethod endAuthMethod = new RestMethod(urlEndAuth);
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

            httpClient.executeMethod(endAuthMethod);
            config = endAuthMethod.getJsonResponse();
            endAuthMethod.releaseConnection();
            LOGGER.debug(config);
            String resultValue = config.getString("ResultValue");
            if ("PhoneAppDenied".equals(resultValue)) {
                throw new IOException("Authentication failed: " + resultValue);
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
        PostMethod processAuthMethod = new PostMethod("https://login.microsoftonline.com/common/SAS/ProcessAuth");
        processAuthMethod.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        processAuthMethod.setParameter("type", type);
        processAuthMethod.setParameter("request", context);
        processAuthMethod.setParameter("mfaAuthMethod", authMethod);
        processAuthMethod.setParameter("canary", canary);
        processAuthMethod.setParameter("login", username);
        processAuthMethod.setParameter("flowToken", flowToken);
        processAuthMethod.setParameter("hpgrequestid", hpgrequestid);

        httpClient.executeMethod(processAuthMethod);
        return processAuthMethod;

    }

    private JSONObject executeMethod(HttpClient httpClient, GetMethod method) throws IOException {
        try {
            LOGGER.debug(method.getURI());
            httpClient.executeMethod(method);

            JSONObject config = extractConfig(method.getResponseBodyAsString());
            LOGGER.debug(config);
            return config;

        } finally {
            method.releaseConnection();
        }
    }

    private JSONObject executeMethod(HttpClient httpClient, RestMethod method) throws IOException {
        try {
            LOGGER.debug(method.getURI());
            httpClient.executeMethod(method);

            JSONObject jsonResponse = method.getJsonResponse();
            LOGGER.debug(jsonResponse);
            return jsonResponse;

        } finally {
            method.releaseConnection();
        }
    }

    public JSONObject extractConfig(String content) throws IOException {
        try {
            return new JSONObject(extract("Config=([^\n]+);", content));
        } catch (JSONException e1) {
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

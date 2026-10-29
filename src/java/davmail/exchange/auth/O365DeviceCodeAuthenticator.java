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
import davmail.http.HttpClientAdapter;
import davmail.http.request.RestRequest;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

public class O365DeviceCodeAuthenticator implements ExchangeAuthenticator {
    protected static final Logger LOGGER = Logger.getLogger(O365DeviceCodeAuthenticator.class);

    protected static class DeviceCode {
        final private String deviceCode;
        final private String message;
        final private int expiresIn;
        final private int interval;
        final private String verificationUri;
        final private String userCode;

        DeviceCode(String deviceCode, String message, int expiresIn, int interval, String verificationUri, String userCode) {
            this.deviceCode = deviceCode;
            this.message = message;
            this.expiresIn = expiresIn > 0 ? expiresIn : 900; // default to 15 minutes
            this.interval = interval > 0 ? interval : 5; // default to 5 seconds
            this.verificationUri = verificationUri;
            this.userCode = userCode;
        }

        /**
         * @return short code the user has to enter on the verification page, null if not provided by server.
         * This is not the secret device_code used for polling.
         */
        public String getUserCode() {
            return userCode;
        }

        /**
         * @return url the user has to open to enter the user code, null if not provided by server
         */
        public String getVerificationUri() {
            return verificationUri;
        }

        public String getDeviceCode() {
            return deviceCode;
        }

        public String getMessage() {
            return message;
        }

        /**
         * @return device code lifetime in seconds
         */
        public int getExpiresIn() {
            return expiresIn;
        }

        /**
         * @return minimum polling interval in seconds
         */
        public int getInterval() {
            return interval;
        }
    }

    private String username;
    private String password;
    private O365Token token;
    URI ewsUrl = URI.create(Settings.getO365Url());

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void authenticate() throws IOException {
        // common DavMail client id
        final String clientId = Settings.getProperty("davmail.oauth.clientId", "facd6cff-a294-4415-b59f-c5b01937d7bd");
        String resource;
        if (Settings.isGraphEnabled()) {
            resource = Settings.getGraphUrl();
        } else {
            resource = Settings.getOutlookUrl();
        }

        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load a stored token, redirectUri is empty with device code
        token = O365Token.load(tenantId, clientId, "", username, password);
        if (token != null) {
            return;
        }

        // build device code authorize url;
        String url;

        DeviceCode deviceCode;
        ArrayList<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("client_id", clientId));
        if (Settings.getBooleanProperty("davmail.enableOidc", Settings.isGraphEnabled())) {
            url = Settings.getO365LoginUrl() + "/" + tenantId + "/oauth2/v2.0/devicecode";
            parameters.add(new BasicNameValuePair("scope", Settings.getOauthScope()));
        } else {
            url = Settings.getO365LoginUrl() + "/" + tenantId + "/oauth2/devicecode";
            parameters.add(new BasicNameValuePair("resource", resource));
        }

        RestRequest logonMethod = new RestRequest(url, new UrlEncodedFormEntity(parameters, Consts.UTF_8));
        // Executes device code request; parses response into DeviceCode object
        try (
                HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url);
                CloseableHttpResponse response = httpClientAdapter.execute(logonMethod)
        ) {

            JSONObject deviceCodeResponse = logonMethod.handleResponse(response);
            final String error = deviceCodeResponse.optString("error_description", deviceCodeResponse.optString("error", null));
            if (error != null) {
                throw new IOException("Exception getting device code: " + error);
            }
            // expires_in and interval are numbers on the v2.0 endpoint and may be strings on the v1 endpoint
            // v2.0 endpoint returns verification_uri, v1 endpoint returns verification_url
            String verificationUri = deviceCodeResponse.optString("verification_uri", deviceCodeResponse.optString("verification_url", null));
            deviceCode = new DeviceCode(deviceCodeResponse.getString("device_code"), deviceCodeResponse.getString("message"),
                    parseSeconds(deviceCodeResponse.opt("expires_in")), parseSeconds(deviceCodeResponse.opt("interval")),
                    verificationUri, deviceCodeResponse.optString("user_code", null));
        } catch (JSONException e) {
            throw new IOException("Exception parsing device code", e);
        }

        token = pollForToken(tenantId, clientId, deviceCode);
    }

    private static int parseSeconds(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                LOGGER.debug("Invalid device code duration " + value);
            }
        }
        // 0 means use default value
        return 0;
    }

    /**
     * Poll token endpoint until user completes authorization.
     * Stops when the device code expires, the server reports a final error, or the thread is interrupted.
     *
     * @param tenantId   tenant id
     * @param clientId   client id
     * @param deviceCode device code
     * @return token
     * @throws IOException on error, expiration or interruption
     */
    protected O365Token pollForToken(String tenantId, String clientId, DeviceCode deviceCode) throws IOException {
        // Display instructions once to the user
        System.out.println(deviceCode.getMessage());

        long deadline = System.currentTimeMillis() + deviceCode.getExpiresIn() * 1000L;
        long intervalMillis = deviceCode.getInterval() * 1000L;
        // also log instructions: stdout is not visible in service mode, and the message contains url and user code
        LOGGER.info("Waiting for device code authorization. " + deviceCode.getMessage() + " Code expires in " + deviceCode.getExpiresIn()
                + " seconds, checking every " + intervalMillis / 1000 + " seconds");

        while (true) {
            try {
                //noinspection BusyWait
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                return O365Token.build(tenantId, clientId, deviceCode, password);
            } catch (O365SlowDown e) {
                // server asks to poll less frequently, RFC 8628 says add 5 seconds
                intervalMillis += 5000;
                LOGGER.info("Device code authorization pending, server requested slower polling, next check in "
                        + intervalMillis / 1000 + " seconds");
            } catch (O365AuthorizationPending e) {
                long remainingSeconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
                String message = "Device code authorization pending, " + remainingSeconds + " seconds remaining";
                if (deviceCode.getVerificationUri() != null) {
                    message += ", sign in at " + deviceCode.getVerificationUri();
                    if (deviceCode.getUserCode() != null) {
                        message += " with code " + deviceCode.getUserCode();
                    }
                }
                LOGGER.info(message);
            }

            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Device code expired after " + deviceCode.getExpiresIn()
                        + " seconds, authorization was not completed");
            }
        }
    }

    @Override
    public O365Token getToken() throws IOException {
        return token;
    }

    @Override
    public URI getExchangeUri() {
        return ewsUrl;
    }

    @Override
    public HttpClientAdapter getHttpClientAdapter() {
        return new HttpClientAdapter(getExchangeUri(), username, password, true);
    }

    public static void main(String[] argv) throws IOException {
        Settings.setDefaultSettings();
        Settings.setProperty("davmail.server", "false");
        //Settings.setProperty("davmail.enableOidc", "true");

        O365DeviceCodeAuthenticator authenticator = new O365DeviceCodeAuthenticator();
        authenticator.setUsername("");
        authenticator.authenticate();
    }
}

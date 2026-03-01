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
        DeviceCode(String deviceCode, String message) {
            this.deviceCode = deviceCode;
            this.message = message;
        }
        public String getDeviceCode() {
            return deviceCode;
        }

        public String getMessage() {
            return message;
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
        if (Settings.getBooleanProperty("davmail.enableGraph", false)) {
            resource = Settings.getGraphUrl();
        } else {
            resource = Settings.getOutlookUrl();
        }

        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load a stored token, redirectUri is empty with devicecode
        token = O365Token.load(tenantId, clientId, "", username, password);
        if (token != null) {
            return;
        }

        // build devicecode authorize url;
        String url = Settings.getO365LoginUrl() + "/" + tenantId + "/oauth2/devicecode?api-version=1.0";

        DeviceCode deviceCode;
        ArrayList<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("client_id", clientId));
        parameters.add(new BasicNameValuePair("resource", resource));
        RestRequest logonMethod = new RestRequest(url, new UrlEncodedFormEntity(parameters, Consts.UTF_8));
        // Executes device code request; parses response into DeviceCode object
        try (
                HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url);
                CloseableHttpResponse response = httpClientAdapter.execute(logonMethod)
        ) {

            JSONObject deviceCodeResponse = logonMethod.handleResponse(response);
            deviceCode = new DeviceCode(deviceCodeResponse.getString("device_code"), deviceCodeResponse.getString("message"));
        } catch (JSONException e) {
            throw new IOException("Exception parsing device code", e);
        }

        // Polls for authorization completion; builds token on success
        try {
            while (token == null) {
                System.out.println(deviceCode.getMessage());

                //noinspection BusyWait
                Thread.sleep(5000);

                try {
                    token = O365Token.build(tenantId, clientId, deviceCode, password);
                } catch (O365AuthorizationPending e) {
                    LOGGER.error("Authorization pending for device code");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted waiting for token " + e.getMessage());
            Thread.currentThread().interrupt();
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

        O365DeviceCodeAuthenticator authenticator = new O365DeviceCodeAuthenticator();
        authenticator.setUsername("");
        authenticator.authenticate();
    }
}

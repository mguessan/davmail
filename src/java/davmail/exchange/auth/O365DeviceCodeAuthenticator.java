package davmail.exchange.auth;

import davmail.Settings;
import davmail.http.HttpClientAdapter;
import davmail.http.request.PostRequest;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.net.URI;

public class O365DeviceCodeAuthenticator implements ExchangeAuthenticator {
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
        final String resource = "https://graph.microsoft.com";

        // company tenantId or common
        String tenantId = Settings.getProperty("davmail.oauth.tenantId", "common");

        // first try to load stored token
        token = O365Token.load(tenantId, clientId, "", username, password);
        if (token != null) {
            return;
        }

        // uriBuilder.setPath("/" + tenantId + "/oauth2/devicecode?api-version=1.0");
        String url = O365Authenticator.buildAuthorizeUrl(tenantId, clientId, "", username);

        HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url, username, password);

        PostRequest logonMethod = new PostRequest(Settings.getO365LoginUrl() + "/" + tenantId + "/oauth2/devicecode?api-version=1.0");
        logonMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

        logonMethod.setParameter("client_id", clientId);
        logonMethod.setParameter("resource", resource);

        String responseBodyAsString = httpClientAdapter.executePostRequest(logonMethod);

        JSONObject responseBodyAsJSON;
        DeviceCode deviceCode;
        try {
            responseBodyAsJSON = new JSONObject(responseBodyAsString);
            deviceCode = new DeviceCode(responseBodyAsJSON.getString("device_code"));
            System.out.println(responseBodyAsJSON.get("message"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        try {
            while(true) {
                Thread.sleep(5000);

                try {
                    token = O365Token.build(tenantId, clientId, deviceCode, password);
                } catch (AuthorizationPending e) {
                    // poke user
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

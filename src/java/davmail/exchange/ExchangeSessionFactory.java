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
import davmail.exception.WebdavNotAvailableException;
import davmail.exchange.auth.ExchangeAuthenticator;
import davmail.exchange.auth.ExchangeFormAuthenticator;
import davmail.exchange.dav.DavExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.awt.*;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Create ExchangeSession instances.
 */
public final class ExchangeSessionFactory {
    private static final Object LOCK = new Object();
    private static final Map<PoolKey, ExchangeSession> POOL_MAP = new HashMap<>();
    private static boolean configChecked;
    private static boolean errorSent;

    static class PoolKey {
        final String url;
        final String userName;
        final String password;

        PoolKey(String url, String userName, String password) {
            this.url = url;
            this.userName = convertUserName(userName);
            this.password = password;
        }

        @Override
        public boolean equals(Object object) {
            return object == this ||
                    object instanceof PoolKey &&
                            ((PoolKey) object).url.equals(this.url) &&
                            ((PoolKey) object).userName.equals(this.userName) &&
                            ((PoolKey) object).password.equals(this.password);
        }

        @Override
        public int hashCode() {
            return url.hashCode() + userName.hashCode() + password.hashCode();
        }
    }

    private ExchangeSessionFactory() {
    }

    /**
     * Create authenticated Exchange session
     *
     * @param userName user login
     * @param password user password
     * @return authenticated session
     * @throws IOException on error
     */
    public static ExchangeSession getInstance(String userName, String password) throws IOException {
        String baseUrl = Settings.getProperty("davmail.url", Settings.getO365Url());
        if (Settings.getBooleanProperty("davmail.server")) {
            return getInstance(baseUrl, userName, password);
        } else {
            // serialize session creation in workstation mode to avoid multiple OTP requests
            synchronized (LOCK) {
                return getInstance(baseUrl, userName, password);
            }
        }
    }

    private static String convertUserName(String userName) {
        String result = userName;
        // prepend default windows domain prefix
        String defaultDomain = Settings.getProperty("davmail.defaultDomain");
        if (defaultDomain != null && userName.indexOf('\\') < 0 && userName.indexOf('@') < 0) {
            result = defaultDomain + '\\' + userName;
        }
        return result;
    }

    /**
     * Create authenticated Exchange session
     *
     * @param baseUrl  OWA base URL
     * @param userName user login
     * @param password user password
     * @return authenticated session
     * @throws IOException on error
     */
    public static ExchangeSession getInstance(String baseUrl, String userName, String password) throws IOException {
        ExchangeSession session = null;
        try {
            String mode = Settings.getProperty("davmail.mode");
            if (Settings.O365.equals(mode)) {
                // force url with O365
                baseUrl = Settings.getO365Url();
            }

            PoolKey poolKey = new PoolKey(baseUrl, userName, password);

            synchronized (LOCK) {
                session = POOL_MAP.get(poolKey);
            }
            if (session != null) {
                ExchangeSession.LOGGER.debug("Got session " + session + " from cache");
            }

            if (session != null && session.isExpired()) {
                synchronized (LOCK) {
                    session.close();
                    ExchangeSession.LOGGER.debug("Session " + session + " for user " + session.userName + " expired");
                    session = null;
                    // expired session, remove from cache
                    POOL_MAP.remove(poolKey);
                }
            }

            if (session == null) {
                // convert old setting
                if (mode == null) {
                    if ("false".equals(Settings.getProperty("davmail.enableEws"))) {
                        mode = Settings.WEBDAV;
                    } else {
                        mode = Settings.EWS;
                    }
                }
                // check for overridden authenticator
                String authenticatorClass = Settings.getProperty("davmail.authenticator");
                if (authenticatorClass == null) {
                    switch (mode) {
                        case Settings.O365_MODERN:
                            authenticatorClass = "davmail.exchange.auth.O365Authenticator";
                            break;
                        case Settings.O365_INTERACTIVE:
                            authenticatorClass = "davmail.exchange.auth.O365InteractiveAuthenticator";
                            if (GraphicsEnvironment.isHeadless()) {
                                throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", "O365Interactive not supported in headless mode");
                            }
                            break;
                        case Settings.O365_MANUAL:
                            authenticatorClass = "davmail.exchange.auth.O365ManualAuthenticator";
                            break;
                    }
                }

                if (authenticatorClass != null) {
                    ExchangeAuthenticator authenticator = (ExchangeAuthenticator) Class.forName(authenticatorClass)
                            .getDeclaredConstructor().newInstance();
                    authenticator.setUsername(poolKey.userName);
                    authenticator.setPassword(poolKey.password);
                    authenticator.authenticate();
                    session = new EwsExchangeSession(authenticator.getExchangeUri(), authenticator.getToken(), poolKey.userName);

                } else if (Settings.EWS.equals(mode) || Settings.O365.equals(mode)
                        // direct EWS even if mode is different
                        || poolKey.url.toLowerCase().endsWith("/ews/exchange.asmx")
                        || poolKey.url.toLowerCase().endsWith("/ews/services.wsdl")) {
                    if (poolKey.url.toLowerCase().endsWith("/ews/exchange.asmx")
                            || poolKey.url.toLowerCase().endsWith("/ews/services.wsdl")) {
                        ExchangeSession.LOGGER.debug("Direct EWS authentication");
                        session = new EwsExchangeSession(poolKey.url, poolKey.userName, poolKey.password);
                    } else {
                        ExchangeSession.LOGGER.debug("OWA authentication in EWS mode");
                        ExchangeFormAuthenticator exchangeFormAuthenticator = new ExchangeFormAuthenticator();
                        exchangeFormAuthenticator.setUrl(poolKey.url);
                        exchangeFormAuthenticator.setUsername(poolKey.userName);
                        exchangeFormAuthenticator.setPassword(poolKey.password);
                        exchangeFormAuthenticator.authenticate();
                        session = new EwsExchangeSession(exchangeFormAuthenticator.getHttpClientAdapter(),
                                exchangeFormAuthenticator.getExchangeUri(), exchangeFormAuthenticator.getUsername());
                    }
                } else {
                    ExchangeFormAuthenticator exchangeFormAuthenticator = new ExchangeFormAuthenticator();
                    exchangeFormAuthenticator.setUrl(poolKey.url);
                    exchangeFormAuthenticator.setUsername(poolKey.userName);
                    exchangeFormAuthenticator.setPassword(poolKey.password);
                    exchangeFormAuthenticator.authenticate();
                    try {
                        session = new DavExchangeSession(exchangeFormAuthenticator.getHttpClientAdapter(),
                                exchangeFormAuthenticator.getExchangeUri(),
                                exchangeFormAuthenticator.getUsername());
                    } catch (WebdavNotAvailableException e) {
                        if (Settings.AUTO.equals(mode)) {
                            ExchangeSession.LOGGER.debug(e.getMessage() + ", retry with EWS");
                            session = new EwsExchangeSession(poolKey.url, poolKey.userName, poolKey.password);
                        } else {
                            throw e;
                        }
                    }
                }
                checkWhiteList(session.getEmail());
                ExchangeSession.LOGGER.debug("Created new session " + session + " for user " + poolKey.userName);
            }
            // successful login, put session in cache
            synchronized (LOCK) {
                POOL_MAP.put(poolKey, session);
            }
            // session opened, future failure will mean network down
            configChecked = true;
            // Reset so next time an problem occurs message will be sent once
            errorSent = false;
        } catch (DavMailException | IllegalStateException | NullPointerException exc) {
            throw exc;
        } catch (Exception exc) {
            handleNetworkDown(exc);
        }
        return session;
    }

    /**
     * Check if whitelist is empty or email is allowed.
     * userWhiteList is a comma separated list of values.
     * \@company.com means all domain users are allowed
     *
     * @param email user email
     */
    private static void checkWhiteList(String email) throws DavMailAuthenticationException {
        String whiteListString = Settings.getProperty("davmail.userWhiteList");
        if (whiteListString != null && !whiteListString.isEmpty()) {
            for (String whiteListvalue : whiteListString.split(",")) {
                if (whiteListvalue.startsWith("@") && email.endsWith(whiteListvalue)) {
                    return;
                } else if (email.equalsIgnoreCase(whiteListvalue)) {
                    return;
                }
            }
            ExchangeSession.LOGGER.warn(email + " not allowed by whitelist");
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        }
    }

    /**
     * Get a non expired session.
     * If the current session is not expired, return current session, else try to create a new session
     *
     * @param currentSession current session
     * @param userName       user login
     * @param password       user password
     * @return authenticated session
     * @throws IOException on error
     */
    public static ExchangeSession getInstance(ExchangeSession currentSession, String userName, String password)
            throws IOException {
        ExchangeSession session = currentSession;
        try {
            if (session.isExpired()) {
                ExchangeSession.LOGGER.debug("Session " + session + " expired, trying to open a new one");
                session = null;
                String baseUrl = Settings.getProperty("davmail.url", Settings.getO365Url());
                PoolKey poolKey = new PoolKey(baseUrl, userName, password);
                // expired session, remove from cache
                synchronized (LOCK) {
                    POOL_MAP.remove(poolKey);
                }
                session = getInstance(userName, password);
            }
        } catch (DavMailAuthenticationException exc) {
            ExchangeSession.LOGGER.debug("Unable to reopen session", exc);
            throw exc;
        } catch (Exception exc) {
            ExchangeSession.LOGGER.debug("Unable to reopen session", exc);
            handleNetworkDown(exc);
        }
        return session;
    }

    /**
     * Send a request to Exchange server to check current settings.
     *
     * @throws IOException if unable to access Exchange server
     */
    public static void checkConfig() throws IOException {
        String url = Settings.getProperty("davmail.url", Settings.getO365Url());
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new DavMailException("LOG_INVALID_URL", url);
        }
        try (
                HttpClientAdapter httpClientAdapter = new HttpClientAdapter(url);
                CloseableHttpResponse response = httpClientAdapter.execute(new GetRequest(url))
        ) {
            // get webMail root url (will not follow redirects)
            int status = response.getStatusLine().getStatusCode();
            ExchangeSession.LOGGER.debug("Test configuration status: " + status);
            if (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED
                    && !HttpClientAdapter.isRedirect(status)) {
                throw new DavMailException("EXCEPTION_CONNECTION_FAILED", url, status);
            }
            // session opened, future failure will mean network down
            configChecked = true;
            // Reset so next time an problem occurs message will be sent once
            errorSent = false;
        } catch (Exception exc) {
            handleNetworkDown(exc);
        }

    }

    private static void handleNetworkDown(Exception exc) throws DavMailException {
        if (!checkNetwork() || configChecked) {
            ExchangeSession.LOGGER.warn(BundleMessage.formatLog("EXCEPTION_NETWORK_DOWN"));
            // log full stack trace for unknown errors
            if (!((exc instanceof UnknownHostException) || (exc instanceof NetworkDownException))) {
                ExchangeSession.LOGGER.debug(exc, exc);
            }
            throw new NetworkDownException("EXCEPTION_NETWORK_DOWN");
        } else {
            BundleMessage message = new BundleMessage("EXCEPTION_CONNECT", exc.getClass().getName(), exc.getMessage());
            if (errorSent) {
                ExchangeSession.LOGGER.warn(message);
                throw new NetworkDownException("EXCEPTION_DAVMAIL_CONFIGURATION", message);
            } else {
                // Mark that an error has been sent so you only get one
                // error in a row (not a repeating string of errors).
                errorSent = true;
                ExchangeSession.LOGGER.error(message);
                throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", message);
            }
        }
    }

    /**
     * Get user password from session pool for SASL authentication
     *
     * @param userName Exchange user name
     * @return user password
     */
    public static String getUserPassword(String userName) {
        String fullUserName = convertUserName(userName);
        for (PoolKey poolKey : POOL_MAP.keySet()) {
            if (poolKey.userName.equals(fullUserName)) {
                return poolKey.password;
            }
        }
        return null;
    }

    /**
     * Check if at least one network interface is up and active (i.e. has an address)
     *
     * @return true if network available
     */
    static boolean checkNetwork() {
        boolean up = false;
        Enumeration<NetworkInterface> enumeration;
        try {
            enumeration = NetworkInterface.getNetworkInterfaces();
            if (enumeration != null) {
                while (!up && enumeration.hasMoreElements()) {
                    NetworkInterface networkInterface = enumeration.nextElement();
                    up = networkInterface.isUp() && !networkInterface.isLoopback()
                            && networkInterface.getInetAddresses().hasMoreElements();
                }
            }
        } catch (NoSuchMethodError error) {
            ExchangeSession.LOGGER.debug("Unable to test network interfaces (not available under Java 1.5)");
            up = true;
        } catch (SocketException exc) {
            ExchangeSession.LOGGER.error("DavMail configuration exception: \n Error listing network interfaces " + exc.getMessage(), exc);
        }
        return up;
    }

    /**
     * Reset config check status and clear session pool.
     */
    public static void shutdown() {
        configChecked = false;
        errorSent = false;
        synchronized (LOCK) {
            for (ExchangeSession session:POOL_MAP.values()) {
                session.close();
            }
            POOL_MAP.clear();
        }
    }
}

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
import davmail.exchange.graph.GraphExchangeSession;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.awt.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Create ExchangeSession instances.
 */
public final class ExchangeSessionFactory {
    /**
     * Serialize session creation for different users in workstation mode, to avoid multiple concurrent
     * interactive authentications. Not held while accessing the session pool.
     */
    private static final Object CREATION_LOCK = new Object();
    /**
     * Session pool. Guarded by itself.
     */
    private static final Map<PoolKey, ExchangeSession> POOL_MAP = new HashMap<>();
    /**
     * Session creations in progress, by key. Guarded by itself.
     */
    private static final Map<PoolKey, CompletableFuture<ExchangeSession>> SESSION_CREATORS = new HashMap<>();
    private static volatile boolean configChecked;
    private static volatile boolean errorSent;

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
            return Objects.hash(url, userName, password);
        }
    }

    /**
     * Run at most one session creation at a time for a given key, and share its result with concurrent callers.
     * <p>
     * With an interactive authentication (device code, valid for several minutes), concurrent connections
     * for the same user must wait for one authentication instead of each starting their own.
     * If the creation fails, callers that were waiting get the same failure instead of each retrying in turn.
     * Results are not cached here: a call made after completion starts a new creation (the session pool
     * is checked first, see {@link #getOrCreateSession}).
     *
     * @param key         session key
     * @param sessionMode configured davmail.mode, may be null
     * @return session created session
     * @throws Exception on creation failure, or InterruptedIOException if interrupted while waiting
     */
    static ExchangeSession singleFlightGetOrCreateSession(PoolKey key, String sessionMode) throws Exception {
        CompletableFuture<ExchangeSession> future;
        boolean owner = false;
        // create a future if no session creation in progress
        synchronized (SESSION_CREATORS) {
            future = SESSION_CREATORS.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                SESSION_CREATORS.put(key, future);
                owner = true;
            }
        }

        if (owner) {
            ExchangeSession session;
            try {
                session = getOrCreateSession(key, sessionMode);
            } catch (Throwable t) {
                // unregister before completing, so a later caller starts a new creation instead of joining a failed one
                removeSessionCreator(key);
                future.completeExceptionally(t);
                throw t;
            }
            removeSessionCreator(key);
            future.complete(session);
            return session;
        }

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted waiting for concurrent session creation");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            // owner failed with an Error
            throw new IllegalStateException("Concurrent session creation failed", cause);
        }
    }

    private static void removeSessionCreator(PoolKey key) {
        synchronized (SESSION_CREATORS) {
            SESSION_CREATORS.remove(key);
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
        return getInstance(baseUrl, userName, password);
    }

    /**
     * Build the session pool key, the same way for every lookup.
     * In O365 mode the URL is forced, whatever the caller or davmail.url say.
     *
     * @param mode     configured davmail.mode, may be null
     * @param baseUrl  OWA base URL
     * @param userName user login
     * @param password user password
     * @return pool key
     */
    private static PoolKey buildPoolKey(String mode, String baseUrl, String userName, String password) {
        if (Settings.O365.equals(mode)) {
            // force url with O365
            baseUrl = Settings.getO365Url();
        }
        return new PoolKey(baseUrl, userName, password);
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
            PoolKey poolKey = buildPoolKey(mode, baseUrl, userName, password);

            synchronized (POOL_MAP) {
                session = POOL_MAP.get(poolKey);
            }
            if (session != null) {
                ExchangeSession.LOGGER.debug("Got session " + session + " from cache");
            }

            if (session != null && session.isExpired()) {
                ExchangeSession.LOGGER.debug("Session " + session + " for user " + session.userName + " expired");
                // only the thread that actually removes the expired session closes it: another thread may already
                // have replaced it with a new session, which must stay in the pool
                boolean removed;
                synchronized (POOL_MAP) {
                    removed = POOL_MAP.remove(poolKey, session);
                }
                if (removed) {
                    session.close();
                }
                session = null;
            }

            if (session == null) {
                // one session creation at a time for a given user: concurrent connections wait for the
                // same authentication (device code can take minutes) instead of each starting their own.
                // The created session is published to the pool by getOrCreateSession.
                session = singleFlightGetOrCreateSession(poolKey, mode);
            }
            // session opened; future failure will mean network down
            configChecked = true;
            // Reset so next time a problem occurs message is sent once
            errorSent = false;
        } catch (DavMailException | InterruptedIOException | IllegalStateException | NullPointerException exc) {
            // not a network problem: no network down handling for a cancelled wait
            throw exc;
        } catch (Exception exc) {
            handleNetworkDown(exc);
        }
        return session;
    }

    /**
     * Get session from pool or create it, runs as the single session creation for this user.
     * Pool is checked again: a previous creation for the same user may have completed
     * after the caller checked the pool.
     *
     * @param poolKey session key
     * @param mode    configured davmail.mode, may be null
     * @return session
     * @throws Exception on error
     */
    private static ExchangeSession getOrCreateSession(PoolKey poolKey, String mode) throws Exception {
        synchronized (POOL_MAP) {
            ExchangeSession cached = POOL_MAP.get(poolKey);
            if (cached != null) {
                return cached;
            }
        }

        ExchangeSession session;
        if (Settings.getBooleanProperty("davmail.server")) {
            session = createSession(poolKey, mode);
        } else {
            // serialize session creation in workstation mode to avoid multiple OTP requests
            synchronized (CREATION_LOCK) {
                session = createSession(poolKey, mode);
            }
        }
        // publish before ending this creation, so a new caller can not start a duplicate one
        synchronized (POOL_MAP) {
            POOL_MAP.put(poolKey, session);
        }
        return session;
    }

    /**
     * Create a new session, called by at most one thread at a time for a given user.
     *
     * @param poolKey session key
     * @param mode    configured davmail.mode, may be null
     * @return new authenticated session
     * @throws Exception on error
     */
    private static ExchangeSession createSession(PoolKey poolKey, String mode) throws Exception {
        ExchangeSession session;
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
        if (authenticatorClass == null && mode.startsWith("O365")) {
            // set authenticator class based on authentication mode
            String authentication = Settings.getProperty("davmail.authentication");
            authenticatorClass = getAuthenticatorClass(authentication);
            if (authenticatorClass == null) {
                // fallback to legacy modes
                authenticatorClass = getAuthenticatorClass(mode);
            }
        }

        if (authenticatorClass != null) {
            ExchangeAuthenticator authenticator = (ExchangeAuthenticator) Class.forName(authenticatorClass)
                    .getDeclaredConstructor().newInstance();
            authenticator.setUsername(poolKey.userName);
            authenticator.setPassword(poolKey.password);
            authenticator.authenticate();

            if (Settings.isGraphEnabled()) {
                session = new GraphExchangeSession(authenticator.getHttpClientAdapter(), authenticator.getToken(), poolKey.userName);
            } else {
                session = new EwsExchangeSession(authenticator.getExchangeUri(), authenticator.getToken(), poolKey.userName);
            }

        } else if (
            // new mode
                Settings.EXCHANGE_EWS.equals(mode)
                        // legacy
                        || Settings.EWS.equals(mode) || Settings.O365.equals(mode)
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
            // Legacy Exchange 2003 WebDav mode
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
        try {
            checkWhiteList(session.getEmail());
        } catch (Throwable t) {
            // do not leak a session that will never be published to the pool
            session.close();
            throw t;
        }
        ExchangeSession.LOGGER.debug("Created new session " + session + " for user " + poolKey.userName);
        return session;
    }

    private static String getAuthenticatorClass(String authentication) throws DavMailException {
        String authenticatorClass = null;
        if (authentication != null) {
            switch (authentication) {
                case Settings.O365_INTERACTIVE:
                    authenticatorClass = "davmail.exchange.auth.O365InteractiveAuthenticator";
                    if (GraphicsEnvironment.isHeadless()) {
                        throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", "O365Interactive not supported in headless mode");
                    }
                    break;
                case Settings.O365_MANUAL:
                    authenticatorClass = "davmail.exchange.auth.O365ManualAuthenticator";
                    break;
                case Settings.O365_DEVICECODE:
                    authenticatorClass = "davmail.exchange.auth.O365DeviceCodeAuthenticator";
                    break;
                case Settings.O365_TRANSPARENT:
                case Settings.O365_MODERN:
                    authenticatorClass = "davmail.exchange.auth.O365Authenticator";
                    break;
            }
        }
        return authenticatorClass;
    }

    /**
     * Check if whitelist is empty or email is allowed.
     * userWhiteList is a comma-separated list of values.
     * \@company.com means all domain users are allowed
     *
     * @param email user email
     */
    private static void checkWhiteList(String email) throws DavMailAuthenticationException {
        String whiteListString = Settings.getProperty("davmail.userWhiteList");
        if (whiteListString != null && !whiteListString.isEmpty()) {
            for (String whiteListValue : whiteListString.split(",")) {
                if (whiteListValue.startsWith("@") && email.endsWith(whiteListValue)) {
                    return;
                } else if (email.equalsIgnoreCase(whiteListValue)) {
                    return;
                }
            }
            ExchangeSession.LOGGER.warn(email + " not allowed by whitelist");
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        }
    }

    /**
     * Get a non-expired session.
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
                PoolKey poolKey = buildPoolKey(Settings.getProperty("davmail.mode"), baseUrl, userName, password);
                // expired session, remove from cache unless already replaced by another thread
                synchronized (POOL_MAP) {
                    POOL_MAP.remove(poolKey, currentSession);
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
        String url;
        if (Settings.isGraphEnabled()) {
            // In Graph mode, check the Graph endpoint instead of the legacy EWS endpoint
            url = Settings.getGraphUrl();
        } else {
            url = Settings.getProperty("davmail.url", Settings.getO365Url());
        }
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
            // session opened; future failure will mean network down
            configChecked = true;
            // Reset so next time a problem occurs message is sent once
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
     * @param userName Exchange username
     * @return user password
     */
    public static String getUserPassword(String userName) {
        String fullUserName = convertUserName(userName);
        synchronized (POOL_MAP) {
            for (PoolKey poolKey : POOL_MAP.keySet()) {
                if (poolKey.userName.equals(fullUserName)) {
                    return poolKey.password;
                }
            }
        }
        return null;
    }

    /**
     * Check if at least one network interface is up and active (i.e., has an address)
     *
     * @return true if network available
     */
    static boolean checkNetwork() {
        boolean up = false;
        Enumeration<NetworkInterface> enumeration;
        try {
            enumeration = NetworkInterface.getNetworkInterfaces();
            while (!up && enumeration.hasMoreElements()) {
                NetworkInterface networkInterface = enumeration.nextElement();
                up = networkInterface.isUp() && !networkInterface.isLoopback()
                        && networkInterface.getInetAddresses().hasMoreElements();
                if (up) {
                    ExchangeSession.LOGGER.debug(networkInterface.getDisplayName() + " is up");
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
        synchronized (POOL_MAP) {
            for (ExchangeSession session : POOL_MAP.values()) {
                session.close();
            }
            POOL_MAP.clear();
        }
    }
}

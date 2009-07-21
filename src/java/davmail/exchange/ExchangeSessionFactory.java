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
import davmail.exception.DavMailException;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Create ExchangeSession instances.
 */
public final class ExchangeSessionFactory {
    private static final Object LOCK = new Object();
    private static final Map<PoolKey, ExchangeSession> poolMap = new HashMap<PoolKey, ExchangeSession>();
    private static boolean configChecked;

    static class PoolKey {
        public final String url;
        public final String userName;
        public final String password;

        PoolKey(String url, String userName, String password) {
            this.url = url;
            this.userName = userName;
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
        ExchangeSession session = null;
        try {
            String baseUrl = Settings.getProperty("davmail.url");
            PoolKey poolKey = new PoolKey(baseUrl, userName, password);

            synchronized (LOCK) {
                session = poolMap.get(poolKey);
            }
            if (session != null) {
                ExchangeSession.LOGGER.debug("Got session " + session + " from cache");
            }

            if (session != null && session.isExpired()) {
                ExchangeSession.LOGGER.debug("Session " + session + " expired");
                session = null;
                // expired session, remove from cache
                synchronized (LOCK) {
                    poolMap.remove(poolKey);
                }
            }

            if (session == null) {
                session = new ExchangeSession(poolKey);
                ExchangeSession.LOGGER.debug("Created new session: " + session);
            }
            // successfull login, put session in cache
            synchronized (LOCK) {
                poolMap.put(poolKey, session);
            }
            // session opened, future failure will mean network down
            configChecked = true;
        } catch (UnknownHostException exc) {
            handleNetworkDown(exc);
        } catch (NoRouteToHostException exc) {
            handleNetworkDown(exc);
        }
        return session;
    }

    public static void checkConfig() throws IOException {
        String url = Settings.getProperty("davmail.url");
        HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();
        HttpMethod testMethod = new GetMethod(url);
        try {
            // get webmail root url (will not follow redirects)
            testMethod.setFollowRedirects(false);
            testMethod.setDoAuthentication(false);
            int status = httpClient.executeMethod(testMethod);
            ExchangeSession.LOGGER.debug("Test configuration status: " + status);
            if (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED
                    && status != HttpStatus.SC_MOVED_TEMPORARILY && status != HttpStatus.SC_MOVED_PERMANENTLY) {
                throw new DavMailException("EXCEPTION_CONNECTION_FAILED", url, status);
            }
            // session opened, future failure will mean network down
            configChecked = true;

        } catch (UnknownHostException exc) {
            handleNetworkDown(exc);
        } catch (NoRouteToHostException exc) {
            handleNetworkDown(exc);
            // Could not open the port (probably because it is blocked behind a firewall)
        } catch (ConnectException exc) {
            handleNetworkDown(exc);
        } catch (NetworkDownException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", exc);
        } finally {
            testMethod.releaseConnection();
        }

    }

    private static void handleNetworkDown(Exception exc) throws DavMailException {
        if (!checkNetwork() || configChecked) {
            ExchangeSession.LOGGER.warn(BundleMessage.formatLog("EXCEPTION_NETWORK_DOWN"));
            throw new NetworkDownException("EXCEPTION_NETWORK_DOWN");
        } else {
            BundleMessage message = new BundleMessage("EXCEPTION_CONNECT", exc.getClass().getName(), exc.getMessage());
            ExchangeSession.LOGGER.error(message);
            throw new DavMailException("EXCEPTION_DAVMAIL_CONFIGURATION", message);
        }
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
            while (!up && enumeration.hasMoreElements()) {
                NetworkInterface networkInterface = enumeration.nextElement();
                up = networkInterface.isUp() && !networkInterface.isLoopback()
                        && networkInterface.getInetAddresses().hasMoreElements();
            }
        } catch (NoSuchMethodError error) {
            ExchangeSession.LOGGER.debug("Unable to test network interfaces (not available under Java 1.5)");
            up = true;
        } catch (SocketException exc) {
            ExchangeSession.LOGGER.error("DavMail configuration exception: \n Error listing network interfaces " + exc.getMessage(), exc);
        }
        return up;
    }

    public static void reset() {
        configChecked = false;
        poolMap.clear();
    }
}

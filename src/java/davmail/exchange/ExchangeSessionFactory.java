package davmail.exchange;

import davmail.Settings;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Create ExchangeSession instances.
 */
public final class ExchangeSessionFactory {
    private static final Object LOCK = new Object();
    private static final Map<PoolKey, ExchangeSession> poolMap = new HashMap<PoolKey, ExchangeSession>();

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
        try {
            String baseUrl = Settings.getProperty("davmail.url");
            PoolKey poolKey = new PoolKey(baseUrl, userName, password);

            ExchangeSession session;
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
            return session;
        } catch (IOException e) {
            if (checkNetwork()) {
                throw e;
            } else {
                throw new NetworkDownException("All network interfaces down !");
            }
        }
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
                throw new IOException("Unable to connect to OWA at " + url + ", status code " +
                        status + ", check configuration");
            }

        } catch (UnknownHostException exc) {
            String message = "DavMail configuration exception: \n";
            if (checkNetwork()) {
                message += "Unknown host " + exc.getMessage();
                ExchangeSession.LOGGER.error(message, exc);
                throw new IOException(message);
            } else {
                message = "All network interfaces down !";
                ExchangeSession.LOGGER.error(message);
                throw new NetworkDownException(message);
            }

        } catch (NetworkDownException exc) {
            throw exc;
        } catch (Exception exc) {
            ExchangeSession.LOGGER.error("DavMail configuration exception: \n" + exc.getMessage(), exc);
            throw new IOException("DavMail configuration exception: \n" + exc.getMessage());
        } finally {
            testMethod.releaseConnection();
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
        poolMap.clear();
    }
}

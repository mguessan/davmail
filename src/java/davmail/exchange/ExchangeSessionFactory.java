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
import java.util.Enumeration;

/**
 * Create ExchangeSession instances.
 */
public class ExchangeSessionFactory {
    /**
     * Create authenticated Exchange session
     *
     * @param userName user login
     * @param password user password
     * @return authenticated session
     * @throws java.io.IOException on error
     */
    public static ExchangeSession getInstance(String userName, String password) throws IOException {
        try {
            ExchangeSession session = new ExchangeSession();
            session.login(userName, password);
            return session;
        } catch (IOException e) {
            if (checkNetwork()) {
                throw e;
            } else {
                throw new IOException("All network interfaces down !");
            }
        }
    }


    public static void checkConfig() throws IOException {
        String url = Settings.getProperty("davmail.url");
        HttpMethod testMethod = new GetMethod(url);
        try {

            // create an HttpClient instance
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();

            // get webmail root url (will not follow redirects)
            testMethod.setFollowRedirects(false);
            int status = httpClient.executeMethod(testMethod);
            testMethod.releaseConnection();
            ExchangeSession.LOGGER.debug("Test configuration status: " + status);
            if (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED
                    && status != HttpStatus.SC_MOVED_TEMPORARILY) {
                throw new IOException("Unable to connect to OWA at " + url + ", status code " +
                        status + ", check configuration");
            }

        } catch (UnknownHostException exc) {
            String message = "DavMail configuration exception: \n";
            if (checkNetwork()) {
                message += "Unknown host " + exc.getMessage();
            } else {
                message += "All network interfaces down !";
            }

            ExchangeSession.LOGGER.error(message, exc);
            throw new IOException(message);
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
    protected static boolean checkNetwork() {
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
}

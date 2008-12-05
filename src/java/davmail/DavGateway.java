package davmail;

import davmail.caldav.CaldavServer;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import davmail.ldap.LdapServer;
import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * DavGateway main class
 */
public class DavGateway {
    protected DavGateway() {
    }

    private static SmtpServer smtpServer;
    private static PopServer popServer;
    private static CaldavServer caldavServer;
    private static LdapServer ldapServer;

    /**
     * Start the gateway, listen on spécified smtp and pop3 ports
     *
     * @param args command line parameter config file path
     */
    public static void main(String[] args) {

        if (args.length >= 1) {
            Settings.setConfigFilePath(args[0]);
        }

        Settings.load();
        DavGatewayTray.init();

        start();
    }

    public static void start() {
        // first stop existing servers
        DavGateway.stop();

        try {
            smtpServer = new SmtpServer(Settings.getIntProperty("davmail.smtpPort"));
            popServer = new PopServer(Settings.getIntProperty("davmail.popPort"));
            caldavServer = new CaldavServer(Settings.getIntProperty("davmail.caldavPort"));
            ldapServer = new LdapServer(Settings.getIntProperty("davmail.ldapPort"));
            smtpServer.start();
            popServer.start();
            caldavServer.start();
            ldapServer.start();

            String message = "DavMail gateway listening on SMTP port " + smtpServer.getPort() +
                    ", Caldav port " + caldavServer.getPort() +
                    ", LDAP port " + ldapServer.getPort() +
                    " and POP port " + popServer.getPort();
            DavGatewayTray.info(message);

            // check for new version
            String releasedVersion = getReleasedVersion();
            String currentVersion = getCurrentVersion();
            if (currentVersion != null && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
                DavGatewayTray.info("A new version (" + releasedVersion + ") of DavMail Gateway is available !");
            }
        } catch (IOException e) {
            DavGatewayTray.error("Exception creating server socket", e);
        }

        // register custom SSL Socket factory
        DavGatewaySSLProtocolSocketFactory.register();
    }

    protected static void stopServer(AbstractServer server) {
        if (server != null) {
            server.close();
            try {
                server.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn("Exception waiting for listener to die", e);
            }
        }
    }

    public static void stop() {
        stopServer(smtpServer);
        stopServer(popServer);
        stopServer(caldavServer);
        stopServer(ldapServer);
    }

    public static String getCurrentVersion() {
        Package davmailPackage = DavGateway.class.getPackage();
        return davmailPackage.getImplementationVersion();
    }

    public static String getReleasedVersion() {
        String version = null;
        BufferedReader versionReader = null;
        HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();
        GetMethod getMethod = new GetMethod("http://davmail.sourceforge.net/version.txt");
        try {
            httpClient.setConnectionTimeout(5000);
            int status = httpClient.executeMethod(getMethod);
            if (status == HttpStatus.SC_OK) {
                versionReader = new BufferedReader(new InputStreamReader(getMethod.getResponseBodyAsStream()));
                version = versionReader.readLine();
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (versionReader != null) {
                try {
                    versionReader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            getMethod.releaseConnection();
        }
        return version;
    }
}

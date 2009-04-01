package davmail;

import davmail.caldav.CaldavServer;
import davmail.exchange.ExchangeSessionFactory;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import davmail.ldap.LdapServer;
import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;
import davmail.tray.DavGatewayTray;
import davmail.imap.ImapServer;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;

/**
 * DavGateway main class
 */
public class DavGateway {
    protected DavGateway() {
    }

    private static SmtpServer smtpServer;
    private static PopServer popServer;
    private static ImapServer imapServer;
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
        // register custom SSL Socket factory
        DavGatewaySSLProtocolSocketFactory.register();
        try {
            // prepare HTTP connection pool
            DavGatewayHttpClientFacade.start();

            String message = "DavMail gateway listening on";
            int smtpPort = Settings.getIntProperty("davmail.smtpPort");
            if (smtpPort != 0) {
                smtpServer = new SmtpServer(smtpPort);
                smtpServer.start();
                message += " SMTP port " + smtpServer.getPort();
            }
            int popPort = Settings.getIntProperty("davmail.popPort");
            if (popPort != 0) {
                popServer = new PopServer(Settings.getIntProperty("davmail.popPort"));
                popServer.start();
                message += " POP port " + popServer.getPort();
            }
            int imapPort = Settings.getIntProperty("davmail.imapPort");
            if (imapPort != 0) {
                imapServer = new ImapServer(Settings.getIntProperty("davmail.imapPort"));
                imapServer.start();
                message += " IMAP port " + imapServer.getPort();
            }
            int caldavPort = Settings.getIntProperty("davmail.caldavPort");
            if (caldavPort != 0) {
                caldavServer = new CaldavServer(Settings.getIntProperty("davmail.caldavPort"));
                caldavServer.start();
                message += " Caldav port " + caldavServer.getPort();
            }
            int ldapPort = Settings.getIntProperty("davmail.ldapPort");
            if (ldapPort != 0) {
                ldapServer = new LdapServer(Settings.getIntProperty("davmail.ldapPort"));
                ldapServer.start();
                message += " LDAP port " + ldapServer.getPort();
            }

            DavGatewayTray.info(message);

            // check for new version
            String releasedVersion = getReleasedVersion();
            String currentVersion = getCurrentVersion();
            if (currentVersion != null && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
                DavGatewayTray.info("A new version (" + releasedVersion + ") of DavMail Gateway is available !");
            }
        } catch (BindException e) {
            DavGatewayTray.error("Unable to create server socket: the specified port is in use by another process");
        } catch (IOException e) {
            DavGatewayTray.error("Exception creating server socket", e);
        }

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
        stopServer(imapServer);
        stopServer(caldavServer);
        stopServer(ldapServer);
        // close pooled connections
        DavGatewayHttpClientFacade.stop();
        // clear session cache
        ExchangeSessionFactory.reset();
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
            int status = httpClient.executeMethod(getMethod);
            if (status == HttpStatus.SC_OK) {
                versionReader = new BufferedReader(new InputStreamReader(getMethod.getResponseBodyAsStream()));
                version = versionReader.readLine();
            }
        } catch (IOException e) {
            DavGatewayTray.debug("Exception getting released version");
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

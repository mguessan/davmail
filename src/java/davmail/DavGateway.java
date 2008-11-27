package davmail;

import davmail.caldav.CaldavServer;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
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
        int smtpPort = Settings.getIntProperty("davmail.smtpPort");
        if (smtpPort == 0) {
            smtpPort = SmtpServer.DEFAULT_PORT;
        }
        int popPort = Settings.getIntProperty("davmail.popPort");
        if (popPort == 0) {
            popPort = PopServer.DEFAULT_PORT;
        }
        int caldavPort = Settings.getIntProperty("davmail.caldavPort");
        if (caldavPort == 0) {
            caldavPort = CaldavServer.DEFAULT_PORT;
        }

        try {
            smtpServer = new SmtpServer(smtpPort);
            popServer = new PopServer(popPort);
            caldavServer = new CaldavServer(caldavPort);
            smtpServer.start();
            popServer.start();
            caldavServer.start();

            String message = "DavMail gateway listening on SMTP port " + smtpPort +
                    ", Caldav port " + caldavPort +
                    " and POP port " + popPort;
            String releasedVersion = getReleasedVersion();
            String currentVersion = getCurrentVersion();
            if (currentVersion != null && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
                message += " A new version (" + releasedVersion + ") of DavMail Gateway is available !";
            }

            DavGatewayTray.info(message);
        } catch (IOException e) {
            DavGatewayTray.error("Exception creating server socket", e);
        }

        // register custom SSL Socket factory
        DavGatewaySSLProtocolSocketFactory.register();
    }

    public static void stop() {
        if (smtpServer != null) {
            smtpServer.close();
            try {
                smtpServer.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn("Exception waiting for listener to die", e);
            }
        }
        if (popServer != null) {
            popServer.close();
            try {
                popServer.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn("Exception waiting for listener to die", e);
            }
        }
        if (caldavServer != null) {
            caldavServer.close();
            try {
                caldavServer.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn("Exception waiting for listener to die", e);
            }
        }
    }

    public static String getCurrentVersion() {
        Package davmailPackage = DavGateway.class.getPackage();
        return davmailPackage.getImplementationVersion();
    }

    public static String getReleasedVersion() {
        String version = null;
        BufferedReader versionReader = null;
        GetMethod getMethod = new GetMethod("http://davmail.sourceforge.net/version.txt");
        try {
            HttpClient httpClient = DavGatewayHttpClientFacade.getInstance();
            httpClient.setConnectionTimeout(250);
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

package davmail;

import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;
import davmail.tray.DavGatewayTray;

import java.io.IOException;

/**
 * DavGateway main class
 */
public class DavGateway {
    protected DavGateway() {
    }

    private static SmtpServer smtpServer;
    private static PopServer popServer;

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
        int smtpPort = Settings.getIntProperty("davmail.smtpPort");
        if (smtpPort == 0) {
            smtpPort = SmtpServer.DEFAULT_PORT;
        }
        int popPort = Settings.getIntProperty("davmail.popPort");
        if (popPort == 0) {
            popPort = PopServer.DEFAULT_PORT;
        }

        try {
            smtpServer = new SmtpServer(smtpPort);
            popServer = new PopServer(popPort);
            smtpServer.start();
            popServer.start();

            DavGatewayTray.info("DavMail gateway listening on SMTP port " + smtpPort +
                    " and POP port " + popPort);
        } catch (IOException e) {
            DavGatewayTray.error("Exception creating server socket", e);
        }

    }

}

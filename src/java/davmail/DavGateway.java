package davmail;

import davmail.imap.ImapServer;
import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;

/**
 * DavGateway main class
 */
public class DavGateway {
    protected static final String USAGE_MESSAGE = "Usage : java davmail.DavGateway url [smtplistenport] [pop3listenport] [imaplistenport]";

    /**
     * Start the gateway, listen on spécified smtp and pop3 ports
     */
    public static void main(String[] args) {

        int smtpPort = SmtpServer.DEFAULT_PORT;
        int popPort = PopServer.DEFAULT_PORT;
        int imapPort = ImapServer.DEFAULT_PORT;
        String url;

        if (args.length >= 1) {
            url = args[0];
            try {
                if (args.length >= 2) {
                    smtpPort = Integer.parseInt(args[1]);
                }
                if (args.length >= 3) {
                    popPort = Integer.parseInt(args[2]);
                }
                if (args.length >= 4) {
                    imapPort = Integer.parseInt(args[3]);
                }
                DavGatewayTray.init();
                                
                SmtpServer smtpServer = new SmtpServer(url, smtpPort);
                PopServer popServer = new PopServer(url, popPort);
                ImapServer imapServer = new ImapServer(url, imapPort);
                smtpServer.start();
                popServer.start();
                imapServer.start();
                DavGatewayTray.info("Listening on ports " + smtpPort + " "+popPort+" "+imapPort);
            } catch (NumberFormatException e) {
                System.out.println(DavGateway.USAGE_MESSAGE);
            }
        } else {
            System.out.println(DavGateway.USAGE_MESSAGE);
        }
    }

}

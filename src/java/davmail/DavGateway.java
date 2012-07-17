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
package davmail;

import davmail.caldav.CaldavServer;
import davmail.exception.DavMailException;
import davmail.exchange.ExchangeSessionFactory;
import davmail.http.DavGatewayHttpClientFacade;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import davmail.imap.ImapServer;
import davmail.ldap.LdapServer;
import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;
import davmail.ui.tray.DavGatewayTray;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * DavGateway main class
 */
public final class DavGateway {
    private static final Logger LOGGER = Logger.getLogger(DavGateway.class);
    private static final String HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT = "http://davmail.sourceforge.net/version.txt";

    private static boolean stopped;

    private DavGateway() {
    }

    private static final ArrayList<AbstractServer> SERVER_LIST = new ArrayList<AbstractServer>();

    /**
     * Start the gateway, listen on specified smtp and pop3 ports
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

        // server mode: all threads are daemon threads, do not let main stop
        if (Settings.getBooleanProperty("davmail.server")) {
            Runtime.getRuntime().addShutdownHook(new Thread("Shutdown") {
                @Override
                public void run() {
                    DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_INTERRUPTED"));
                    DavGateway.stop();
                    stopped = true;
                }
            });

            try {
                while (!stopped) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_INTERRUPTED"));
                stop();
                DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_STOP"));
            }

        }
    }

    /**
     * Start DavMail listeners.
     */
    public static void start() {
        // register custom SSL Socket factory
        DavGatewaySSLProtocolSocketFactory.register();

        // prepare HTTP connection pool
        DavGatewayHttpClientFacade.start();

        SERVER_LIST.clear();

        int smtpPort = Settings.getIntProperty("davmail.smtpPort");
        if (smtpPort != 0) {
            SERVER_LIST.add(new SmtpServer(smtpPort));
        }
        int popPort = Settings.getIntProperty("davmail.popPort");
        if (popPort != 0) {
            SERVER_LIST.add(new PopServer(popPort));
        }
        int imapPort = Settings.getIntProperty("davmail.imapPort");
        if (imapPort != 0) {
            SERVER_LIST.add(new ImapServer(imapPort));
        }
        int caldavPort = Settings.getIntProperty("davmail.caldavPort");
        if (caldavPort != 0) {
            SERVER_LIST.add(new CaldavServer(caldavPort));
        }
        int ldapPort = Settings.getIntProperty("davmail.ldapPort");
        if (ldapPort != 0) {
            SERVER_LIST.add(new LdapServer(ldapPort));
        }

        BundleMessage.BundleMessageList messages = new BundleMessage.BundleMessageList();
        BundleMessage.BundleMessageList errorMessages = new BundleMessage.BundleMessageList();
        for (AbstractServer server : SERVER_LIST) {
            try {
                server.bind();
                server.start();
                messages.add(new BundleMessage("LOG_PROTOCOL_PORT", server.getProtocolName(), server.getPort()));
            } catch (DavMailException e) {
                errorMessages.add(e.getBundleMessage());
            } catch (IOException e) {
                errorMessages.add(new BundleMessage("LOG_SOCKET_BIND_FAILED", server.getProtocolName(), server.getPort()));
            }
        }

        final String currentVersion = getCurrentVersion();
        boolean showStartupBanner = Settings.getBooleanProperty("davmail.showStartupBanner", true);
        if (showStartupBanner) {
            DavGatewayTray.info(new BundleMessage("LOG_DAVMAIL_GATEWAY_LISTENING",
                    currentVersion == null ? "" : currentVersion, messages));
        }
        if (!errorMessages.isEmpty()) {
            DavGatewayTray.error(new BundleMessage("LOG_MESSAGE", errorMessages));
        }

        // check for new version in a separate thread
        new Thread("CheckRelease") {
            @Override
            public void run() {
                String releasedVersion = getReleasedVersion();
                if (currentVersion != null && currentVersion.length() > 0 && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
                    DavGatewayTray.info(new BundleMessage("LOG_NEW_VERSION_AVAILABLE", releasedVersion));
                }

            }
        }.start();

    }

    /**
     * Stop all listeners, shutdown connection pool and clear session cache.
     */
    public static void stop() {
        DavGateway.stopServers();
        // close pooled connections
        DavGatewayHttpClientFacade.stop();
        // clear session cache
        ExchangeSessionFactory.reset();
        DavGatewayTray.info(new BundleMessage("LOG_GATEWAY_STOP"));
        DavGatewayTray.dispose();
    }

    /**
     * Stop all listeners and clear session cache.
     */
    public static void restart() {
        DavGateway.stopServers();
        // clear session cache
        ExchangeSessionFactory.reset();
        DavGateway.start();
    }

    private static void stopServers() {
        for (AbstractServer server : SERVER_LIST) {
            server.close();
            try {
                server.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_WAITING_SERVER_THREAD_DIE"), e);
            }
        }
    }

    /**
     * Get current DavMail version.
     *
     * @return current version
     */
    public static String getCurrentVersion() {
        Package davmailPackage = DavGateway.class.getPackage();
        String currentVersion = davmailPackage.getImplementationVersion();
        if (currentVersion == null) {
            currentVersion = "";
        }
        return currentVersion;
    }

    /**
     * Get latest released version from SourceForge.
     *
     * @return latest version
     */
    public static String getReleasedVersion() {
        String version = null;
        if (!Settings.getBooleanProperty("davmail.disableUpdateCheck")) {
            BufferedReader versionReader = null;
            GetMethod getMethod = new GetMethod(HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT);
            try {
                HttpClient httpClient = DavGatewayHttpClientFacade.getInstance(HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT);
                int status = httpClient.executeMethod(getMethod);
                if (status == HttpStatus.SC_OK) {
                    versionReader = new BufferedReader(new InputStreamReader(getMethod.getResponseBodyAsStream()));
                    version = versionReader.readLine();
                    LOGGER.debug("DavMail released version: " + version);
                }
            } catch (IOException e) {
                DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_GET_RELEASED_VERSION"));
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
        }
        return version;
    }
}

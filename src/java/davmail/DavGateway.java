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
import davmail.exchange.auth.ExchangeAuthenticator;
import davmail.http.HttpClientAdapter;
import davmail.http.request.GetRequest;
import davmail.imap.ImapServer;
import davmail.ldap.LdapServer;
import davmail.pop.PopServer;
import davmail.smtp.SmtpServer;
import davmail.ui.tray.DavGatewayTray;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * DavGateway main class
 */
public final class DavGateway {
    private static final Logger LOGGER = Logger.getLogger(DavGateway.class);
    private static final String HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT = "https://davmail.sourceforge.net/version.txt";

    private static final Object LOCK = new Object();
    private static boolean shutdown = false;

    private DavGateway() {
    }

    private static final ArrayList<AbstractServer> SERVER_LIST = new ArrayList<>();

    /**
     * Start the gateway, listen on specified smtp and pop3 ports
     *
     * @param args command line parameter config file path
     */
    public static void main(String[] args) {
        boolean notray = false;
        boolean tray = false;
        boolean server = false;
        boolean token = false;

        // check environment for davmail settings path in Docker
        String configFilePath = Settings.getConfigFilePath();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if ("-notray".equals(arg)) {
                    notray = true;
                } else if ("-tray".equals(arg)) {
                    tray = true;
                } else if ("-server".equals(arg)) {
                    server = true;
                } else if ("-token".equals(arg)) {
                    token = true;
                }
            } else {
                configFilePath = arg;
            }
        }

        Settings.setConfigFilePath(configFilePath);
        Settings.load();

        // use notray / tray to override davmail.enableTray
        if (tray) {
            Settings.setProperty("davmail.enableTray", "true");
        }
        if (notray) {
            Settings.setProperty("davmail.enableTray", "false");
        }

        if (token) {
            try {
                ExchangeAuthenticator authenticator = (ExchangeAuthenticator) Class.forName("davmail.exchange.auth.O365InteractiveAuthenticator")
                        .getDeclaredConstructor().newInstance();
                authenticator.setUsername("");
                authenticator.authenticate();
                System.out.println(authenticator.getToken().getRefreshToken());
            } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException |
                     IllegalAccessException | InvocationTargetException e) {
                System.err.println(e+" "+e.getMessage());
            }
            // force shutdown on Linux
            System.exit(0);
        } else {

            if (GraphicsEnvironment.isHeadless()) {
                // force server mode
                LOGGER.debug("Headless mode, do not create GUI");
                server = true;
            }
            if (server) {
                Settings.setProperty("davmail.server", "true");
                Settings.updateLoggingConfig();
            }


            if (Settings.getBooleanProperty("davmail.server")) {
                LOGGER.debug("Start DavMail in server mode");
            } else {
                LOGGER.debug("Start DavMail in GUI mode");
                DavGatewayTray.init();
            }

            start();

            // server mode: all threads are daemon threads, do not let main stop
            if (Settings.getBooleanProperty("davmail.server")) {
                Runtime.getRuntime().addShutdownHook(new Thread("Shutdown") {
                    @Override
                    public void run() {
                        shutdown = true;
                        DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_INTERRUPTED"));
                        DavGateway.stop();
                        synchronized (LOCK) {
                            LOCK.notifyAll();
                        }
                    }
                });

                synchronized (LOCK) {
                    try {
                        while (!shutdown) {
                            LOCK.wait();
                        }
                    } catch (InterruptedException e) {
                        DavGatewayTray.debug(new BundleMessage("LOG_GATEWAY_INTERRUPTED"));
                        Thread.currentThread().interrupt();
                    }
                }

            }
        }
    }

    /**
     * Start DavMail listeners.
     */
    public static void start() {
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
            }
        }

        final String currentVersion = getCurrentVersion();
        boolean showStartupBanner = Settings.getBooleanProperty("davmail.showStartupBanner", true);
        if (showStartupBanner) {
            DavGatewayTray.info(new BundleMessage("LOG_DAVMAIL_GATEWAY_LISTENING", currentVersion, messages));
        }
        if (!errorMessages.isEmpty()) {
            DavGatewayTray.error(new BundleMessage("LOG_MESSAGE", errorMessages));
        }

        // check for new version in a separate thread
        new Thread("CheckRelease") {
            @Override
            public void run() {
                String releasedVersion = getReleasedVersion();
                if (!currentVersion.isEmpty() && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
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
        ExchangeSessionFactory.shutdown();
        DavGatewayTray.info(new BundleMessage("LOG_GATEWAY_STOP"));
        DavGatewayTray.dispose();
    }

    /**
     * Stop all listeners and clear session cache.
     */
    public static void restart() {
        DavGateway.stopServers();
        // clear session cache
        ExchangeSessionFactory.shutdown();
        DavGateway.start();
    }

    private static void stopServers() {
        for (AbstractServer server : SERVER_LIST) {
            server.close();
            try {
                server.join();
            } catch (InterruptedException e) {
                DavGatewayTray.warn(new BundleMessage("LOG_EXCEPTION_WAITING_SERVER_THREAD_DIE"), e);
                Thread.currentThread().interrupt();
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
            try (HttpClientAdapter httpClientAdapter = new HttpClientAdapter(HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT)) {
                GetRequest getRequest = new GetRequest(HTTP_DAVMAIL_SOURCEFORGE_NET_VERSION_TXT);
                getRequest.setHeader("User-Agent", "Mozilla/5.0");
                getRequest = httpClientAdapter.executeFollowRedirect(getRequest);
                version = getRequest.getResponseBodyAsString();
                LOGGER.debug("DavMail released version: " + version);
            } catch (IOException e) {
                DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_GET_RELEASED_VERSION"));
            }
        }
        return version;
    }
}

package davmail.web;

import davmail.Settings;
import davmail.DavGateway;
import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.io.InputStream;
import java.io.IOException;

/**
 * Context Listener to start/stop DavMail
 */
public class DavGatewayServletContextListener implements ServletContextListener {
    public void contextInitialized(ServletContextEvent event) {
        InputStream settingInputStream = null;
        try {
            settingInputStream = DavGatewayServletContextListener.class.getClassLoader().getResourceAsStream("davmail.properties");
            Settings.load(settingInputStream);
            DavGateway.start();
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_SETTINGS"), e);
        } finally {
            if (settingInputStream != null) {
                try {
                    settingInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug(new BundleMessage("LOG_ERROR_CLOSING_CONFIG_FILE"), e);
                }
            }
        }
        DavGatewayTray.debug(new BundleMessage("LOG_DAVMAIL_STARTED"));
    }

    public void contextDestroyed(ServletContextEvent event) {
        DavGatewayTray.debug(new BundleMessage("LOG_STOPPING_DAVMAIL"));
        DavGateway.stop();
    }
}

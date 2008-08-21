package davmail.web;

import davmail.Settings;
import davmail.DavGateway;
import davmail.tray.DavGatewayTray;

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
            settingInputStream = DavGatewayServlet.class.getClassLoader().getResourceAsStream("davmail.properties");
            Settings.load(settingInputStream);
            DavGateway.start();
        } catch (IOException e) {
            DavGatewayTray.error("Error loading settings file from classpath: ", e);
        } finally {
            if (settingInputStream != null) {
                try {
                    settingInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }
        DavGatewayTray.debug("DavMail Gateway started");
    }

    public void contextDestroyed(ServletContextEvent event) {
        DavGatewayTray.debug("Stopping DavMail Gateway...");
        DavGateway.stop();
    }
}

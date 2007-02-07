package davmail;

import java.util.Properties;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Settings facade
 */
public class Settings {
    protected static final Properties settings = new Properties();
    protected static String configFilePath;

    public static synchronized void setConfigFilePath(String value) {
        configFilePath = value;
    }

    public static synchronized void load() {
        try {
            if (configFilePath == null) {
                configFilePath = System.getProperty("user.home") + "/.davmail.properties";
            }
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                settings.load(new FileReader(configFile));
            } else {
                settings.put("davmail.url", "http://exchangeServer");
                settings.put("davmail.popPort", "110");
                settings.put("davmail.smtpPort", "25");
                settings.put("davmail.keepDelay", "30");
                settings.put("davmail.enableProxy", "false");
                settings.put("davmail.proxyHost", "");
                settings.put("davmail.proxyPort", "");
                settings.put("davmail.proxyUser", "");
                settings.put("davmail.proxyPassword", "");
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error("Unable to load settings: ", e);
        }

    }

    public static synchronized void save() {
        try {
            settings.store(new FileWriter(configFilePath), "DavMail settings");
        } catch (IOException e) {
            DavGatewayTray.error("Unable to store settings: ", e);
        }
    }

    public static synchronized String getProperty(String property) {
        return settings.getProperty(property);
    }

    public static synchronized void setProperty(String property, String value) {
        settings.setProperty(property, value);
    }

    public static synchronized int getIntProperty(String property) {
        int value = 0;
        try {
            String propertyValue = settings.getProperty(property);
            if (propertyValue != null && propertyValue.length() > 0) {
                value = Integer.valueOf(propertyValue);
            }
        } catch (NumberFormatException e) {
            DavGatewayTray.error("Invalid setting value in " + property, e);
        }
        return value;
    }
}

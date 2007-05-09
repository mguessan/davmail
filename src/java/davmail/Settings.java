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
    protected Settings() {
    }

    private static final Properties SETTINGS = new Properties();
    private static String configFilePath;

    public static synchronized void setConfigFilePath(String value) {
        configFilePath = value;
    }

    public static synchronized void load() {
        FileReader fileReader = null;
        try {
            if (configFilePath == null) {
                //noinspection AccessOfSystemProperties
                configFilePath = System.getProperty("user.home") + "/.davmail.properties";
            }
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                fileReader = new FileReader(configFile);
                SETTINGS.load(fileReader);
            } else {
                SETTINGS.put("davmail.url", "http://exchangeServer/exchange/");
                SETTINGS.put("davmail.popPort", "110");
                SETTINGS.put("davmail.smtpPort", "25");
                SETTINGS.put("davmail.keepDelay", "30");
                SETTINGS.put("davmail.enableProxy", "false");
                SETTINGS.put("davmail.proxyHost", "");
                SETTINGS.put("davmail.proxyPort", "");
                SETTINGS.put("davmail.proxyUser", "");
                SETTINGS.put("davmail.proxyPassword", "");
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error("Unable to load settings: ", e);
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }

    }

    public static synchronized void save() {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(configFilePath);
            SETTINGS.store(fileWriter, "DavMail settings");
        } catch (IOException e) {
            DavGatewayTray.error("Unable to store settings: ", e);
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }
    }

    public static synchronized String getProperty(String property) {
        return SETTINGS.getProperty(property);
    }

    public static synchronized void setProperty(String property, String value) {
        SETTINGS.setProperty(property, value);
    }

    public static synchronized int getIntProperty(String property) {
        int value = 0;
        try {
            String propertyValue = SETTINGS.getProperty(property);
            if (propertyValue != null && propertyValue.length() > 0) {
                value = Integer.valueOf(propertyValue);
            }
        } catch (NumberFormatException e) {
            DavGatewayTray.error("Invalid setting value in " + property, e);
        }
        return value;
    }
}

package davmail;

import davmail.tray.DavGatewayTray;

import java.util.Properties;
import java.io.*;

/**
 * Settings facade
 */
public class Settings {
    protected Settings() {
    }

    private static final Properties SETTINGS = new Properties();
    private static String configFilePath;
    private static boolean isFirstStart;

    public static synchronized void setConfigFilePath(String value) {
        configFilePath = value;
    }

    public static boolean isFirstStart() {
        return isFirstStart;
    }

    public static synchronized void load(InputStream inputStream) throws IOException {
        SETTINGS.load(inputStream);
    }

    public static synchronized void load() {
        FileInputStream fileInputStream = null;
        try {
            if (configFilePath == null) {
                //noinspection AccessOfSystemProperties
                configFilePath = System.getProperty("user.home") + "/.davmail.properties";
            }
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                fileInputStream = new FileInputStream(configFile);
                load(fileInputStream);
            } else {
                isFirstStart = true;

                // first start : set default values, ports above 1024 for linux
                SETTINGS.put("davmail.url", "http://exchangeServer/exchange/");
                SETTINGS.put("davmail.popPort", "1110");
                SETTINGS.put("davmail.smtpPort", "1025");
                SETTINGS.put("davmail.keepDelay", "30");
                SETTINGS.put("davmail.allowRemote", "false");
                SETTINGS.put("davmail.enableProxy", "false");
                SETTINGS.put("davmail.proxyHost", "");
                SETTINGS.put("davmail.proxyPort", "");
                SETTINGS.put("davmail.proxyUser", "");
                SETTINGS.put("davmail.proxyPassword", "");
                SETTINGS.put("davmail.server", "false");
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error("Unable to load settings: ", e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }

    }

    public static synchronized void save() {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(configFilePath);
            SETTINGS.store(fileOutputStream, "DavMail settings");
        } catch (IOException e) {
            DavGatewayTray.error("Unable to store settings: ", e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
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
                value = Integer.parseInt(propertyValue);
            }
        } catch (NumberFormatException e) {
            DavGatewayTray.error("Invalid setting value in " + property, e);
        }
        return value;
    }

    public static synchronized boolean getBooleanProperty(String property) {
        String propertyValue = SETTINGS.getProperty(property);
        return "true".equals(propertyValue);
    }
}

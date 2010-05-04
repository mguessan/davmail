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

import davmail.ui.tray.DavGatewayTray;

import java.util.*;
import java.io.*;

import org.apache.log4j.*;

/**
 * Settings facade.
 * DavMail settings are stored in the .davmail.properties file in current
 * user home directory or in the file specified on the command line.
 */
public final class Settings {
    private Settings() {
    }

    private static final Properties SETTINGS = new Properties() {
        @Override
        public synchronized Enumeration<Object> keys() {
            Enumeration keysEnumeration = super.keys();
            TreeSet<String> sortedKeySet = new TreeSet<String>();
            while (keysEnumeration.hasMoreElements()) {
                sortedKeySet.add((String) keysEnumeration.nextElement());
            }
            final Iterator<String> sortedKeysIterator = sortedKeySet.iterator();
            return new Enumeration<Object>() {

                public boolean hasMoreElements() {
                    return sortedKeysIterator.hasNext();
                }

                public Object nextElement() {
                    return sortedKeysIterator.next();
                }
            };
        }

    };
    private static String configFilePath;
    private static boolean isFirstStart;

    /**
     * Set config file path (from command line parameter).
     *
     * @param path davmail properties file path
     */
    public static synchronized void setConfigFilePath(String path) {
        configFilePath = path;
    }

    /**
     * Detect first launch (properties file does not exist).
     *
     * @return true if this is the first start with the current file path
     */
    public static synchronized boolean isFirstStart() {
        return isFirstStart;
    }

    /**
     * Load properties from provided stream (used in webapp mode).
     *
     * @param inputStream properties stream
     * @throws IOException on error
     */
    public static synchronized void load(InputStream inputStream) throws IOException {
        SETTINGS.load(inputStream);
        updateLoggingConfig();
    }

    /**
     * Load properties from current file path (command line or default).
     */
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

                // first start : set default values, ports above 1024 for unix/linux
                setDefaultSettings();
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_LOAD_SETTINGS"), e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug(new BundleMessage("LOG_ERROR_CLOSING_CONFIG_FILE"), e);
                }
            }
        }
        updateLoggingConfig();
    }

    /**
     * Set all settings to default values.
     * Ports above 1024 for unix/linux
     */
    public static void setDefaultSettings() {
        SETTINGS.put("davmail.url", "https://exchangeServer/exchange/");
        SETTINGS.put("davmail.popPort", "1110");
        SETTINGS.put("davmail.imapPort", "1143");
        SETTINGS.put("davmail.smtpPort", "1025");
        SETTINGS.put("davmail.caldavPort", "1080");
        SETTINGS.put("davmail.ldapPort", "1389");
        SETTINGS.put("davmail.keepDelay", "30");
        SETTINGS.put("davmail.sentKeepDelay", "90");
        SETTINGS.put("davmail.caldavPastDelay", "90");
        SETTINGS.put("davmail.imapIdleDelay", "");
        SETTINGS.put("davmail.allowRemote", Boolean.FALSE.toString());
        SETTINGS.put("davmail.bindAddress", "");
        SETTINGS.put("davmail.useSystemProxies", Boolean.TRUE.toString());
        SETTINGS.put("davmail.enableProxy", Boolean.FALSE.toString());
        SETTINGS.put("davmail.proxyHost", "");
        SETTINGS.put("davmail.proxyPort", "");
        SETTINGS.put("davmail.proxyUser", "");
        SETTINGS.put("davmail.proxyPassword", "");
        SETTINGS.put("davmail.server", Boolean.FALSE.toString());
        SETTINGS.put("davmail.server.certificate.hash", "");
        SETTINGS.put("davmail.caldavAlarmSound", "");
        SETTINGS.put("davmail.forceActiveSyncUpdate", Boolean.FALSE.toString());
        SETTINGS.put("davmail.showStartupBanner", Boolean.TRUE.toString());
        SETTINGS.put("davmail.ssl.keystoreType", "");
        SETTINGS.put("davmail.ssl.keystoreFile", "");
        SETTINGS.put("davmail.ssl.keystorePass", "");
        SETTINGS.put("davmail.ssl.keyPass", "");
        SETTINGS.put("davmail.ssl.clientKeystoreType", "");
        SETTINGS.put("davmail.ssl.clientKeystoreFile", "");
        SETTINGS.put("davmail.ssl.clientKeystorePass", "");
        SETTINGS.put("davmail.ssl.pkcs11Library", "");
        SETTINGS.put("davmail.ssl.pkcs11Config", "");

        // logging
        SETTINGS.put("log4j.rootLogger", Level.WARN.toString());
        SETTINGS.put("log4j.logger.davmail", Level.DEBUG.toString());
        SETTINGS.put("log4j.logger.httpclient.wire", Level.WARN.toString());
        SETTINGS.put("log4j.logger.org.apache.commons.httpclient", Level.WARN.toString());
        SETTINGS.put("davmail.logFilePath", "");
    }

    /**
     * Return DavMail log file path
     *
     * @return full log file path
     */
    public static String getLogFilePath() {
        String logFilePath = Settings.getProperty("davmail.logFilePath");
        // use default log file path on Mac OS X
        if ((logFilePath == null || logFilePath.length() == 0)) {
            if (System.getProperty("os.name").toLowerCase().startsWith("mac os x")) {
                logFilePath = System.getProperty("user.home") + "/Library/Logs/DavMail/davmail.log";
            }
        } else {
            File logFile = new File(logFilePath);
            if (logFile.isDirectory()) {
                logFilePath += "/davmail.log";
            }
        }
        return logFilePath;
    }

    /**
     * Return DavMail log file directory
     *
     * @return full log file directory
     */
    public static String getLogFileDirectory() {
        String logFilePath = getLogFilePath();
        if (logFilePath == null || logFilePath.length() == 0) {
            return ".";
        }
        int lastSlashIndex = logFilePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            lastSlashIndex = logFilePath.lastIndexOf('\\');
        }
        if (lastSlashIndex >= 0) {
            return logFilePath.substring(0, lastSlashIndex);
        } else {
            return ".";
        }
    }

    /**
     * Update Log4J config from settings.
     */
    private static void updateLoggingConfig() {
        String logFilePath = getLogFilePath();

        Logger rootLogger = Logger.getRootLogger();
        try {
            if (logFilePath != null && logFilePath.length() > 0) {
                File logFile = new File(logFilePath);
                // create parent directory if needed
                File logFileDir = logFile.getParentFile();
                if (logFileDir != null && !logFileDir.exists()) {
                    if (!logFileDir.mkdirs()) {
                        DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_CREATE_LOG_FILE_DIR"));
                        throw new IOException();
                    }
                }
            } else {
                logFilePath = "davmail.log";
            }
            // Build file appender
            RollingFileAppender fileAppender = ((RollingFileAppender) rootLogger.getAppender("FileAppender"));
            if (fileAppender == null) {
                fileAppender = new RollingFileAppender();
                fileAppender.setName("FileAppender");
                fileAppender.setMaxBackupIndex(2);
                fileAppender.setMaxFileSize("1MB");
                fileAppender.setLayout(new PatternLayout("%d{ISO8601} %-5p [%t] %c %x - %m%n"));
            }
            fileAppender.setFile(logFilePath, true, false, 8192);
            rootLogger.addAppender(fileAppender);
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_SET_LOG_FILE_PATH"));
        }

        // update logging levels
        Settings.setLoggingLevel("rootLogger", Settings.getLoggingLevel("rootLogger"));
        Settings.setLoggingLevel("davmail", Settings.getLoggingLevel("davmail"));
        Settings.setLoggingLevel("httpclient.wire", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.commons.httpclient", Settings.getLoggingLevel("org.apache.commons.httpclient"));
    }

    /**
     * Save settings in current file path (command line or default).
     */
    public static synchronized void save() {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(configFilePath);
            SETTINGS.store(fileOutputStream, "DavMail settings");
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_STORE_SETTINGS"), e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug(new BundleMessage("LOG_ERROR_CLOSING_CONFIG_FILE"), e);
                }
            }
        }
        updateLoggingConfig();
    }

    /**
     * Get a property value as String.
     *
     * @param property property name
     * @return property value
     */
    public static synchronized String getProperty(String property) {
        String value = SETTINGS.getProperty(property);
        // return null on empty value
        if (value != null && value.length() == 0) {
            value = null;
        }
        return value;
    }

    /**
     * Set a property value.
     *
     * @param property property name
     * @param value    property value
     */
    public static synchronized void setProperty(String property, String value) {
        if (value != null) {
            SETTINGS.setProperty(property, value);
        } else {
            SETTINGS.setProperty(property, "");
        }
    }

    /**
     * Get a property value as int.
     *
     * @param property property name
     * @return property value
     */
    public static synchronized int getIntProperty(String property) {
        return getIntProperty(property, 0);
    }

    /**
     * Get a property value as int, return default value if null.
     *
     * @param property     property name
     * @param defaultValue default property value
     * @return property value
     */
    public static synchronized int getIntProperty(String property, int defaultValue) {
        int value = defaultValue;
        try {
            String propertyValue = SETTINGS.getProperty(property);
            if (propertyValue != null && propertyValue.length() > 0) {
                value = Integer.parseInt(propertyValue);
            }
        } catch (NumberFormatException e) {
            DavGatewayTray.error(new BundleMessage("LOG_INVALID_SETTING_VALUE", property), e);
        }
        return value;
    }

    /**
     * Get a property value as boolean.
     *
     * @param property property name
     * @return property value
     */
    public static synchronized boolean getBooleanProperty(String property) {
        String propertyValue = SETTINGS.getProperty(property);
        return Boolean.parseBoolean(propertyValue);
    }

    /**
     * Get a property value as boolean.
     *
     * @param property property name
     * @param defaultValue default property value
     * @return property value
     */
    public static synchronized boolean getBooleanProperty(String property, boolean defaultValue) {
        boolean value = defaultValue;
        String propertyValue = SETTINGS.getProperty(property);
        if (propertyValue != null && propertyValue.length() > 0) {
            value = Boolean.parseBoolean(propertyValue);
        }
        return value;
    }

    /**
     * Build logging properties prefix.
     *
     * @param category logging category
     * @return prefix
     */
    private static String getLoggingPrefix(String category) {
        String prefix;
        if ("rootLogger".equals(category)) {
            prefix = "log4j.";
        } else {
            prefix = "log4j.logger.";
        }
        return prefix;
    }

    /**
     * Return Log4J logging level for the category.
     *
     * @param category logging category
     * @return logging level
     */
    public static synchronized Level getLoggingLevel(String category) {
        String prefix = getLoggingPrefix(category);
        String currentValue = SETTINGS.getProperty(prefix + category);

        if (currentValue != null && currentValue.length() > 0) {
            return Level.toLevel(currentValue);
        } else if ("rootLogger".equals(category)) {
            return Logger.getRootLogger().getLevel();
        } else {
            return Logger.getLogger(category).getLevel();
        }
    }

    /**
     * Set Log4J logging level for the category
     *
     * @param category logging category
     * @param level    logging level
     */
    public static synchronized void setLoggingLevel(String category, Level level) {
        String prefix = getLoggingPrefix(category);
        SETTINGS.setProperty(prefix + category, level.toString());
        if ("rootLogger".equals(category)) {
            Logger.getRootLogger().setLevel(level);
        } else {
            Logger.getLogger(category).setLevel(level);
        }
    }

    /**
     * Change and save a single property.
     *
     * @param property property name
     * @param value    property value
     */
    public static synchronized void saveProperty(String property, String value) {
        Settings.load();
        Settings.setProperty(property, value);
        Settings.save();
    }

}

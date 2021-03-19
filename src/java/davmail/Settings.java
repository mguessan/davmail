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
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import static org.apache.http.util.TextUtils.isEmpty;

/**
 * Settings facade.
 * DavMail settings are stored in the .davmail.properties file in current
 * user home directory or in the file specified on the command line.
 */
public final class Settings {

    public static final String O365_URL = "https://outlook.office.com/EWS/Exchange.asmx";
    public static final String O365 = "O365";
    public static final String O365_MODERN = "O365Modern";
    public static final String O365_INTERACTIVE = "O365Interactive";
    public static final String O365_MANUAL = "O365Manual";
    public static final String WEBDAV = "WebDav";
    public static final String EWS = "EWS";
    public static final String AUTO = "Auto";

    private Settings() {
    }

    private static final Properties SETTINGS = new Properties() {
        @Override
        public synchronized Enumeration<Object> keys() {
            Enumeration<Object> keysEnumeration = super.keys();
            TreeSet<String> sortedKeySet = new TreeSet<>();
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
        SETTINGS.put("davmail.mode", "EWS");
        SETTINGS.put("davmail.url", O365_URL);
        SETTINGS.put("davmail.popPort", "1110");
        SETTINGS.put("davmail.imapPort", "1143");
        SETTINGS.put("davmail.smtpPort", "1025");
        SETTINGS.put("davmail.caldavPort", "1080");
        SETTINGS.put("davmail.ldapPort", "1389");
        SETTINGS.put("davmail.clientSoTimeout", "");
        SETTINGS.put("davmail.keepDelay", "30");
        SETTINGS.put("davmail.sentKeepDelay", "0");
        SETTINGS.put("davmail.caldavPastDelay", "0");
        SETTINGS.put("davmail.caldavAutoSchedule", Boolean.TRUE.toString());
        SETTINGS.put("davmail.imapIdleDelay", "");
        SETTINGS.put("davmail.folderSizeLimit", "");
        SETTINGS.put("davmail.enableKeepAlive", Boolean.FALSE.toString());
        SETTINGS.put("davmail.allowRemote", Boolean.FALSE.toString());
        SETTINGS.put("davmail.bindAddress", "");
        SETTINGS.put("davmail.useSystemProxies", Boolean.FALSE.toString());
        SETTINGS.put("davmail.enableProxy", Boolean.FALSE.toString());
        SETTINGS.put("davmail.enableKerberos", "false");
        SETTINGS.put("davmail.disableUpdateCheck", "false");
        SETTINGS.put("davmail.proxyHost", "");
        SETTINGS.put("davmail.proxyPort", "");
        SETTINGS.put("davmail.proxyUser", "");
        SETTINGS.put("davmail.proxyPassword", "");
        SETTINGS.put("davmail.noProxyFor", "");
        SETTINGS.put("davmail.server", Boolean.FALSE.toString());
        SETTINGS.put("davmail.server.certificate.hash", "");
        SETTINGS.put("davmail.caldavAlarmSound", "");
        SETTINGS.put("davmail.carddavReadPhoto", Boolean.TRUE.toString());
        SETTINGS.put("davmail.forceActiveSyncUpdate", Boolean.FALSE.toString());
        SETTINGS.put("davmail.showStartupBanner", Boolean.TRUE.toString());
        SETTINGS.put("davmail.disableGuiNotifications", Boolean.FALSE.toString());
        SETTINGS.put("davmail.disableTrayActivitySwitch", Boolean.FALSE.toString());
        SETTINGS.put("davmail.imapAutoExpunge", Boolean.TRUE.toString());
        SETTINGS.put("davmail.imapAlwaysApproxMsgSize", Boolean.FALSE.toString());
        SETTINGS.put("davmail.popMarkReadOnRetr", Boolean.FALSE.toString());
        SETTINGS.put("davmail.smtpSaveInSent", Boolean.TRUE.toString());
        SETTINGS.put("davmail.ssl.keystoreType", "");
        SETTINGS.put("davmail.ssl.keystoreFile", "");
        SETTINGS.put("davmail.ssl.keystorePass", "");
        SETTINGS.put("davmail.ssl.keyPass", "");
        SETTINGS.put("davmail.ssl.clientKeystoreType", "");
        SETTINGS.put("davmail.ssl.clientKeystoreFile", "");
        SETTINGS.put("davmail.ssl.clientKeystorePass", "");
        SETTINGS.put("davmail.ssl.pkcs11Library", "");
        SETTINGS.put("davmail.ssl.pkcs11Config", "");
        SETTINGS.put("davmail.ssl.nosecurepop", Boolean.FALSE.toString());
        SETTINGS.put("davmail.ssl.nosecureimap", Boolean.FALSE.toString());
        SETTINGS.put("davmail.ssl.nosecuresmtp", Boolean.FALSE.toString());
        SETTINGS.put("davmail.ssl.nosecurecaldav", Boolean.FALSE.toString());
        SETTINGS.put("davmail.ssl.nosecureldap", Boolean.FALSE.toString());

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
        // set default log file path
        if ((logFilePath == null || logFilePath.length() == 0)) {
            if (Settings.getBooleanProperty("davmail.server")) {
                logFilePath = "davmail.log";
            } else if (System.getProperty("os.name").toLowerCase().startsWith("mac os x")) {
                // store davmail.log in OSX Logs directory
                logFilePath = System.getProperty("user.home") + "/Library/Logs/DavMail/davmail.log";
            } else {
                // store davmail.log in user home folder
                logFilePath = System.getProperty("user.home") + "/davmail.log";
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
    public static void updateLoggingConfig() {
        String logFilePath = getLogFilePath();

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
            synchronized (Logger.getRootLogger()) {
                // Build file appender
                FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("FileAppender");
                if (fileAppender == null) {
                    String logFileSize = Settings.getProperty("davmail.logFileSize");
                    if (logFileSize == null || logFileSize.length() == 0) {
                        logFileSize = "1MB";
                    }
                    // set log file size to 0 to use an external rotation mechanism, e.g. logrotate
                    if ("0".equals(logFileSize)) {
                        fileAppender = new FileAppender();
                    } else {
                        fileAppender = new RollingFileAppender();
                        ((RollingFileAppender) fileAppender).setMaxBackupIndex(2);
                        ((RollingFileAppender) fileAppender).setMaxFileSize(logFileSize);
                    }
                    fileAppender.setName("FileAppender");
                    fileAppender.setEncoding("UTF-8");
                    fileAppender.setLayout(new PatternLayout("%d{ISO8601} %-5p [%t] %c %x - %m%n"));
                }
                fileAppender.setFile(logFilePath, true, false, 8192);
                Logger.getRootLogger().addAppender(fileAppender);
            }

            // disable ConsoleAppender in gui mode
            ConsoleAppender consoleAppender = (ConsoleAppender) Logger.getRootLogger().getAppender("ConsoleAppender");
            if (consoleAppender != null) {
                if (Settings.getBooleanProperty("davmail.server")) {
                    consoleAppender.setThreshold(Level.ALL);
                } else {
                    consoleAppender.setThreshold(Level.OFF);
                }
            }

        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_SET_LOG_FILE_PATH"));
        }

        // update logging levels
        Settings.setLoggingLevel("rootLogger", Settings.getLoggingLevel("rootLogger"));
        Settings.setLoggingLevel("davmail", Settings.getLoggingLevel("davmail"));
        Settings.setLoggingLevel("httpclient.wire", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.commons.httpclient", Settings.getLoggingLevel("org.apache.commons.httpclient"));
        // set logging levels for HttpClient 4
        Settings.setLoggingLevel("org.apache.http.wire", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.http.conn.ssl", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.http", Settings.getLoggingLevel("org.apache.commons.httpclient"));
    }

    /**
     * Save settings in current file path (command line or default).
     */
    public static synchronized void save() {
        // configFilePath is null in some test cases
        if (configFilePath != null) {
            // clone settings
            Properties properties = new Properties();
            properties.putAll(SETTINGS);
            // file lines
            ArrayList<String> lines = new ArrayList<>();

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFilePath), StandardCharsets.ISO_8859_1))) {
                readLines(lines, properties);

                for (String value : lines) {
                    writer.write(value);
                    writer.newLine();
                }

                // write remaining lines
                Enumeration<?> propertyEnumeration = properties.propertyNames();
                while (propertyEnumeration.hasMoreElements()) {
                    String propertyName = (String) propertyEnumeration.nextElement();
                    writer.write(propertyName + "=" + escapeValue(properties.getProperty(propertyName)));
                    writer.newLine();
                }
            } catch (IOException e) {
                DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_STORE_SETTINGS"), e);
            }
        }
        updateLoggingConfig();
    }

    private static void readLines(ArrayList<String> lines, Properties properties) {
        try {
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.ISO_8859_1))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(convertLine(line, properties));
                    }
                }
            }
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_LOAD_SETTINGS"), e);
        }
    }

    /**
     * Convert input property line to new line with value from properties.
     * Preserve comments
     *
     * @param line       input line
     * @param properties new property values
     * @return new line
     */
    private static String convertLine(String line, Properties properties) {
        String comment = "";
        int hashIndex = line.indexOf('#');
        if (hashIndex >= 0) {
            comment = line.substring(hashIndex);
            line = line.substring(0, hashIndex);
        }
        int index = line.indexOf('=');
        if (index >= 0) {
            String key = line.substring(0, index);
            String value = properties.getProperty(key);
            if (value != null) {
                // build property with new value
                line = key + "=" + escapeValue(value);
                // remove property from source
                properties.remove(key);
            }
        }
        return line + comment;
    }

    /**
     * Escape backslash in value.
     *
     * @param value value
     * @return escaped value
     */
    private static String escapeValue(String value) {
        StringBuilder buffer = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '\\') {
                buffer.append('\\');
            }
            buffer.append(c);
        }
        return buffer.toString();
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
     * Get property value or default.
     *
     * @param property     property name
     * @param defaultValue default property value
     * @return property value
     */
    public static synchronized String getProperty(String property, String defaultValue) {
        String value = getProperty(property);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }


    /**
     * Get a property value as char[].
     *
     * @param property property name
     * @return property value
     */
    public static synchronized char[] getCharArrayProperty(String property) {
        String propertyValue = Settings.getProperty(property);
        char[] value = null;
        if (propertyValue != null) {
            value = propertyValue.toCharArray();
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
     * @param property     property name
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

    public static synchronized String loadRefreshToken(String username) {
        String tokenFilePath = Settings.getProperty("davmail.oauth.tokenFilePath");
        if (isEmpty(tokenFilePath)) {
            return Settings.getProperty("davmail.oauth." + username.toLowerCase() + ".refreshToken");
        } else {
            return loadtokenFromFile(tokenFilePath, username.toLowerCase());
        }
    }


    public static synchronized void storeRefreshToken(String username, String refreshToken) {
        String tokenFilePath = Settings.getProperty("davmail.oauth.tokenFilePath");
        if (isEmpty(tokenFilePath)) {
            Settings.setProperty("davmail.oauth." + username.toLowerCase() + ".refreshToken", refreshToken);
            Settings.save();
        } else {
            savetokentoFile(tokenFilePath, username.toLowerCase(), refreshToken);
        }
    }

    /**
     * Persist token in davmail.oauth.tokenFilePath.
     *
     * @param tokenFilePath token file path
     * @param username      username
     * @param refreshToken  Oauth refresh token
     */
    private static void savetokentoFile(String tokenFilePath, String username, String refreshToken) {
        try {
            checkCreateTokenFilePath(tokenFilePath);
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(tokenFilePath)) {
                properties.load(fis);
            }
            properties.setProperty(username, refreshToken);
            try (FileOutputStream fos = new FileOutputStream(tokenFilePath)) {
                properties.store(fos, "Oauth tokens");
            }
        } catch (IOException e) {
            Logger.getLogger(Settings.class).warn(e + " " + e.getMessage());
        }
    }

    /**
     * Load token from davmail.oauth.tokenFilePath.
     *
     * @param tokenFilePath token file path
     * @param username      username
     * @return encrypted token value
     */
    private static String loadtokenFromFile(String tokenFilePath, String username) {
        try {
            checkCreateTokenFilePath(tokenFilePath);
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(tokenFilePath)) {
                properties.load(fis);
            }
            return properties.getProperty(username);
        } catch (IOException e) {
            Logger.getLogger(Settings.class).warn(e + " " + e.getMessage());
        }
        return null;
    }

    private static void checkCreateTokenFilePath(String tokenFilePath) throws IOException {
        File file = new File(tokenFilePath);
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            //noinspection ResultOfMethodCallIgnored
            parentFile.mkdirs();
        }
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();
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
     * Get all properties that are in the specified scope, that is, that start with '&lt;scope&gt;.'.
     *
     * @param scope start of property name
     * @return properties
     */
    public static synchronized Properties getSubProperties(String scope) {
        final String keyStart;
        if (scope == null || scope.length() == 0) {
            keyStart = "";
        } else if (scope.endsWith(".")) {
            keyStart = scope;
        } else {
            keyStart = scope + '.';
        }
        Properties result = new Properties();
        for (Map.Entry<Object, Object> entry : SETTINGS.entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith(keyStart)) {
                String value = (String) entry.getValue();
                result.setProperty(key.substring(keyStart.length()), value);
            }
        }
        return result;
    }

    /**
     * Set Log4J logging level for the category
     *
     * @param category logging category
     * @param level    logging level
     */
    public static synchronized void setLoggingLevel(String category, Level level) {
        if (level != null) {
            String prefix = getLoggingPrefix(category);
            SETTINGS.setProperty(prefix + category, level.toString());
            if ("rootLogger".equals(category)) {
                Logger.getRootLogger().setLevel(level);
            } else {
                Logger.getLogger(category).setLevel(level);
            }
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

    /**
     * Test if running on Windows
     *
     * @return true on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    /**
     * Test if running on Linux
     *
     * @return true on Linux
     */
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().startsWith("linux");
    }

    public static boolean isUnix() {
        return isLinux() ||
                System.getProperty("os.name").toLowerCase().startsWith("freebsd");
    }

}

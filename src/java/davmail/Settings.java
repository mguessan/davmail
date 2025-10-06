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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
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

    private static final Logger LOGGER = Logger.getLogger(Settings.class);

    public static final String OUTLOOK_URL = "https://outlook.office365.com";
    public static final String O365_URL = OUTLOOK_URL + "/EWS/Exchange.asmx";

    public static final String GRAPH_URL = "https://graph.microsoft.com";

    public static final String O365_LOGIN_URL = "https://login.microsoftonline.com";

    public static final String O365 = "O365";
    public static final String O365_MODERN = "O365Modern";
    public static final String O365_INTERACTIVE = "O365Interactive";
    public static final String O365_MANUAL = "O365Manual";
    public static final String WEBDAV = "WebDav";
    public static final String EWS = "EWS";
    public static final String AUTO = "Auto";

    public static final String EDGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36 Edg/90.0.818.49";

    private Settings() {
    }

    private static final Properties SETTINGS_PROPERTIES = new Properties() {
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
        SETTINGS_PROPERTIES.load(inputStream);
        updateLoggingConfig();
    }

    /**
     * Load properties from current file path (command line or default).
     */
    public static synchronized void load() {
        try {
            if (configFilePath == null) {
                //noinspection AccessOfSystemProperties
                configFilePath = System.getProperty("user.home") + "/.davmail.properties";
            }
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(configFile)) {
                    load(fileInputStream);
                }
            } else {
                isFirstStart = true;

                // first start : set default values, ports above 1024 for unix/linux
                setDefaultSettings();
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_LOAD_SETTINGS"), e);
        }
        updateLoggingConfig();
    }

    /**
     * Set all settings to default values.
     * Ports above 1024 for unix/linux
     */
    public static void setDefaultSettings() {
        SETTINGS_PROPERTIES.put("davmail.mode", "EWS");
        SETTINGS_PROPERTIES.put("davmail.url", getO365Url());
        SETTINGS_PROPERTIES.put("davmail.popPort", "1110");
        SETTINGS_PROPERTIES.put("davmail.imapPort", "1143");
        SETTINGS_PROPERTIES.put("davmail.smtpPort", "1025");
        SETTINGS_PROPERTIES.put("davmail.caldavPort", "1080");
        SETTINGS_PROPERTIES.put("davmail.ldapPort", "1389");
        SETTINGS_PROPERTIES.put("davmail.clientSoTimeout", "");
        SETTINGS_PROPERTIES.put("davmail.keepDelay", "30");
        SETTINGS_PROPERTIES.put("davmail.sentKeepDelay", "0");
        SETTINGS_PROPERTIES.put("davmail.caldavPastDelay", "0");
        SETTINGS_PROPERTIES.put("davmail.caldavAutoSchedule", Boolean.TRUE.toString());
        SETTINGS_PROPERTIES.put("davmail.imapIdleDelay", "");
        SETTINGS_PROPERTIES.put("davmail.folderSizeLimit", "");
        SETTINGS_PROPERTIES.put("davmail.enableKeepAlive", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.allowRemote", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.bindAddress", "");
        SETTINGS_PROPERTIES.put("davmail.useSystemProxies", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.enableProxy", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.enableKerberos", "false");
        SETTINGS_PROPERTIES.put("davmail.disableUpdateCheck", "false");
        SETTINGS_PROPERTIES.put("davmail.proxyHost", "");
        SETTINGS_PROPERTIES.put("davmail.proxyPort", "");
        SETTINGS_PROPERTIES.put("davmail.proxyUser", "");
        SETTINGS_PROPERTIES.put("davmail.proxyPassword", "");
        SETTINGS_PROPERTIES.put("davmail.noProxyFor", "");
        SETTINGS_PROPERTIES.put("davmail.server", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.server.certificate.hash", "");
        SETTINGS_PROPERTIES.put("davmail.caldavAlarmSound", "");
        SETTINGS_PROPERTIES.put("davmail.carddavReadPhoto", Boolean.TRUE.toString());
        SETTINGS_PROPERTIES.put("davmail.forceActiveSyncUpdate", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.showStartupBanner", Boolean.TRUE.toString());
        SETTINGS_PROPERTIES.put("davmail.disableGuiNotifications", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.disableTrayActivitySwitch", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.imapAutoExpunge", Boolean.TRUE.toString());
        SETTINGS_PROPERTIES.put("davmail.imapAlwaysApproxMsgSize", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.popMarkReadOnRetr", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.smtpSaveInSent", Boolean.TRUE.toString());
        SETTINGS_PROPERTIES.put("davmail.ssl.keystoreType", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.keystoreFile", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.keystorePass", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.keyPass", "");
        if (isWindows()) {
            // default to MSCAPI on windows for native client certificate access
            SETTINGS_PROPERTIES.put("davmail.ssl.clientKeystoreType", "MSCAPI");
        } else {
            SETTINGS_PROPERTIES.put("davmail.ssl.clientKeystoreType", "");
        }
        SETTINGS_PROPERTIES.put("davmail.ssl.clientKeystoreFile", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.clientKeystorePass", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.pkcs11Library", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.pkcs11Config", "");
        SETTINGS_PROPERTIES.put("davmail.ssl.nosecurepop", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.ssl.nosecureimap", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.ssl.nosecuresmtp", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.ssl.nosecurecaldav", Boolean.FALSE.toString());
        SETTINGS_PROPERTIES.put("davmail.ssl.nosecureldap", Boolean.FALSE.toString());

        // logging
        SETTINGS_PROPERTIES.put("log4j.rootLogger", Level.WARN.toString());
        SETTINGS_PROPERTIES.put("log4j.logger.davmail", Level.DEBUG.toString());
        SETTINGS_PROPERTIES.put("log4j.logger.httpclient.wire", Level.WARN.toString());
        SETTINGS_PROPERTIES.put("log4j.logger.httpclient", Level.WARN.toString());
        SETTINGS_PROPERTIES.put("davmail.logFilePath", "");
    }

    /**
     * Return DavMail log file path
     *
     * @return full log file path
     */
    public static String getLogFilePath() {
        String logFilePath = Settings.getProperty("davmail.logFilePath");
        // set default log file path
        if ((logFilePath == null || logFilePath.isEmpty())) {
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
        if (logFilePath == null || logFilePath.isEmpty()) {
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
            if (logFilePath != null && !logFilePath.isEmpty()) {
                File logFile = new File(logFilePath);
                // create parent directory if needed
                File logFileDir = logFile.getParentFile();
                if (logFileDir != null && !logFileDir.exists() && (!logFileDir.mkdirs())) {
                    DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_CREATE_LOG_FILE_DIR"));
                    throw new IOException();

                }
            } else {
                logFilePath = "davmail.log";
            }
            synchronized (Logger.getRootLogger()) {
                // Build file appender
                FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("FileAppender");
                if (fileAppender == null) {
                    String logFileSize = Settings.getProperty("davmail.logFileSize");
                    if (logFileSize == null || logFileSize.isEmpty()) {
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
                    consoleAppender.setThreshold(Level.ALL);
                }
            }

        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_SET_LOG_FILE_PATH"));
        }

        // update logging levels
        Settings.setLoggingLevel("rootLogger", Settings.getLoggingLevel("rootLogger"));
        Settings.setLoggingLevel("davmail", Settings.getLoggingLevel("davmail"));
        // set logging levels for HttpClient 4
        Settings.setLoggingLevel("org.apache.http.wire", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.http.conn.ssl", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.http", Settings.getLoggingLevel("httpclient"));
    }

    /**
     * Save settings in current file path (command line or default).
     */
    public static synchronized void save() {
        // configFilePath is null in some test cases
        if (configFilePath != null) {
            // clone settings
            Properties properties = new Properties();
            properties.putAll(SETTINGS_PROPERTIES);
            // file lines
            ArrayList<String> lines = new ArrayList<>();

            // try to make .davmail.properties file readable by user only on create
            Path path = Paths.get(configFilePath);
            if (!Files.exists(path) && isUnix()) {
                FileAttribute<?> permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
                try {
                    Files.createFile(path, permissions);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage());
                }
            }

            readLines(lines, properties);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(configFilePath)), StandardCharsets.ISO_8859_1))) {
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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(configFile.toPath()), StandardCharsets.ISO_8859_1))) {
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
        int hashIndex = line.indexOf('#');
        int equalsIndex = line.indexOf('=');
        // allow # in values, no a comment
        // comments are pass through
        if (equalsIndex >= 0 && (hashIndex < 0 || hashIndex >= equalsIndex)) {
            String key = line.substring(0, equalsIndex);
            String value = properties.getProperty(key);
            if (value != null) {
                // build property with new value
                line = key + "=" + escapeValue(value);
                // remove property from source
                properties.remove(key);
            }
        }
        return line;
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
        String value = SETTINGS_PROPERTIES.getProperty(property);
        // return null on empty value
        if (value != null && value.isEmpty()) {
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
            SETTINGS_PROPERTIES.setProperty(property, value);
        } else {
            SETTINGS_PROPERTIES.setProperty(property, "");
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
            String propertyValue = SETTINGS_PROPERTIES.getProperty(property);
            if (propertyValue != null && !propertyValue.isEmpty()) {
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
        String propertyValue = SETTINGS_PROPERTIES.getProperty(property);
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
        String propertyValue = SETTINGS_PROPERTIES.getProperty(property);
        if (propertyValue != null && !propertyValue.isEmpty()) {
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
        if (parentFile != null && (parentFile.mkdirs())) {
            LOGGER.info("Created token file directory " + parentFile.getAbsolutePath());

        }
        if (file.createNewFile()) {
            LOGGER.info("Created token file " + tokenFilePath);
        }
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
        String currentValue = SETTINGS_PROPERTIES.getProperty(prefix + category);

        if (currentValue != null && !currentValue.isEmpty()) {
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
        if (scope == null || scope.isEmpty()) {
            keyStart = "";
        } else if (scope.endsWith(".")) {
            keyStart = scope;
        } else {
            keyStart = scope + '.';
        }
        Properties result = new Properties();
        for (Map.Entry<Object, Object> entry : SETTINGS_PROPERTIES.entrySet()) {
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
            SETTINGS_PROPERTIES.setProperty(prefix + category, level.toString());
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

    public static String getUserAgent() {
        return getProperty("davmail.userAgent", Settings.EDGE_USER_AGENT);
    }

    public static String getOutlookUrl() {
        String tld = getProperty("davmail.tld");
        String outlookUrl = getProperty("davmail.outlookUrl");
        if (outlookUrl != null) {
            return outlookUrl;
        } else if (tld == null) {
            return OUTLOOK_URL;
        } else {
            return "https://outlook.office365." + tld;
        }
    }

    public static String getO365Url() {
        String tld = getProperty("davmail.tld");
        String outlookUrl = getProperty("davmail.outlookUrl");
        if (outlookUrl != null) {
            return outlookUrl + "/EWS/Exchange.asmx";
        } else if (tld == null) {
            return O365_URL;
        } else {
            return "https://outlook.office365." + tld + "/EWS/Exchange.asmx";
        }
    }

    /**
     * Handle custom graph endpoints.
     * See <a href="https://learn.microsoft.com/en-us/graph/deployments">...</a>
     * @return graph endpoint url
     */
    public static String getGraphUrl() {
        String tld = getProperty("davmail.tld");
        String graphUrl = getProperty("davmail.graphUrl");
        if (graphUrl != null) {
            return graphUrl;
        } else if (tld == null) {
            return GRAPH_URL;
        } else {
            return "https://graph.microsoft." + tld;
        }
    }

    public static String getO365LoginUrl() {
        String tld = getProperty("davmail.tld");
        String loginUrl = getProperty("davmail.loginUrl");
        if (loginUrl != null) {
            return loginUrl;
        } else if (tld == null) {
            return O365_LOGIN_URL;
        } else {
            return "https://login.microsoftonline." + tld;
        }
    }

    public static boolean isSWTAvailable() {
        boolean isSWTAvailable = false;
        ClassLoader classloader = Settings.class.getClassLoader();
        try {
            // trigger ClassNotFoundException
            classloader.loadClass("org.eclipse.swt.SWT");
            isSWTAvailable = true;
        } catch (Throwable e) {
            LOGGER.info(new BundleMessage("LOG_SWT_NOT_AVAILABLE"));
        }
        return isSWTAvailable;
    }

    public static boolean isJFXAvailable() {
        boolean isJFXAvailable = false;
        try {
            Class.forName("javafx.application.Platform");
            isJFXAvailable = true;
        } catch (ClassNotFoundException | NullPointerException e) {
            LOGGER.warn("Unable to load JavaFX (OpenJFX), switch to manual mode");
        }
        return isJFXAvailable;
    }
}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2026  DavMail contributors
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
package davmail.exchange;

import davmail.Settings;

import org.apache.log4j.Level;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

/**
 * Standalone utility to dump all VTIMEZONE definitions from an Exchange/O365 mailbox.
 * <p>
 * This is <strong>not</strong> a JUnit test: it is a {@code main}-based runner so it can be
 * executed on demand to regenerate {@code timezones.properties}. For each Windows timezone
 * name known in the {@code stdtimezones} resource bundle, it sets {@code davmail.timezoneId} and
 * asks the session for the matching VTIMEZONE via {@link ExchangeSession#getVTimezone()}.
 * <p>
 * <strong>EWS is required.</strong> Only {@link davmail.exchange.ews.EwsExchangeSession}
 * produces a VTIMEZONE by round-tripping a temporary calendar item through the server
 * (server-side event&rarr;iCalendar conversion). The Graph backend has no such formatter and
 * would merely echo the local {@code vtimezones.properties} table, defeating the purpose.
 * This runner therefore forces an EWS session and refuses to run over Graph.
 * <p>
 * Usage: create a {@code test.properties} file in the working directory with valid credentials
 * ({@code davmail.username}, {@code davmail.password}, and the O365/Exchange settings), then run
 * this class. The regenerated definitions are written to {@code timezones.properties}.
 */
public final class DumpVTimezones {

    private DumpVTimezones() {
    }

    public static void main(String[] args) throws IOException {
        loadTestProperties();
        forceEwsSession();

        ExchangeSession session = ExchangeSessionFactory.getInstance(
                Settings.getProperty("davmail.username"),
                Settings.getProperty("davmail.password"));
        if (session == null) {
            throw new IOException("Unable to open Exchange session, check test.properties credentials");
        }

        Properties tzProperties = new SortedProperties();
        // Iterate the modern Windows timezone names (keys of stdtimezones.properties). These are the
        // valid identifiers to submit to the server. The timezones.properties bundle must NOT be used
        // here: it also contains legacy GMT-offset display strings (e.g. "(GMT+05.00) Sverdlovsk/Tashkent")
        // that are reverse-lookup keys, not timezone ids, and would only produce "Unknown timezone" noise.
        Set<String> tzReference = ResourceBundle.getBundle("stdtimezones").keySet();

        // EwsExchangeSession.loadVtimezone() logs the full SOAP request and stack trace at WARN
        // for every zone the server does not recognize. Silence those two loggers during the dump
        // so unsupported zones are reported once, cleanly, in the summary below.
        Settings.setLoggingLevel("davmail.exchange.ExchangeSession", Level.ERROR);
        Settings.setLoggingLevel("davmail.exchange.ews.EWSMethod", Level.ERROR);
        Settings.setLoggingLevel("davmail.exchange.ews.EwsExchangeSession", Level.ERROR);

        List<String> skipped = new ArrayList<>();
        int count = 0;
        for (String tz : new TreeSet<>(tzReference)) {
            // reset per iteration so the session reloads the VTIMEZONE for the new id
            Settings.setProperty("davmail.timezoneId", tz);
            session.vTimezone = null;

            VObject timezone = session.getVTimezone();
            if (timezone != null && timezone.getProperty("TZID") != null) {
                String tzid = timezone.getPropertyValue("TZID");
                tzProperties.put(tzid, timezone.toString());
                count++;
            } else {
                skipped.add(tz);
            }
        }

        File output = new File("timezones.properties");
        try (FileOutputStream fileOutputStream = new FileOutputStream(output)) {
            tzProperties.store(fileOutputStream, "Timezone definitions");
        }

        System.out.println("Wrote " + count + " VTIMEZONE definitions to " + output.getAbsolutePath());
        if (!skipped.isEmpty()) {
            System.out.println(skipped.size() + " timezone(s) not recognized by the server and skipped:");
            for (String tz : skipped) {
                System.out.println("  - " + tz);
            }
        }

        compareWithBundledDefinitions(tzProperties);
    }

    /**
     * Compare freshly dumped server definitions against the committed {@code vtimezones.properties}
     * bundle, i.e. the definitions the Graph backend serves from
     * {@link davmail.util.DateUtil#VTIMEZONES} (Graph has no server-side VTIMEZONE generator).
     *
     * @param dumped TZID -&gt; VTIMEZONE string just dumped from the server
     */
    private static void compareWithBundledDefinitions(Properties dumped) {
        ResourceBundle bundled = ResourceBundle.getBundle("vtimezones");
        Set<String> bundledKeys = bundled.keySet();

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();

        // zones the server returned
        for (String tzid : new TreeSet<>(dumped.stringPropertyNames())) {
            String serverValue = dumped.getProperty(tzid);
            if (!bundledKeys.contains(tzid)) {
                added.add(tzid);
            } else if (!serverValue.equals(bundled.getString(tzid))) {
                changed.add(tzid);
            }
        }
        // zones present in the bundle but not returned by the server
        for (String tzid : new TreeSet<>(bundledKeys)) {
            if (!dumped.containsKey(tzid)) {
                removed.add(tzid);
            }
        }

        System.out.println();
        System.out.println("=== Comparison with bundled vtimezones.properties (Graph backend source) ===");
        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            System.out.println("No differences: bundled definitions match the server.");
            return;
        }

        if (!added.isEmpty()) {
            System.out.println(added.size() + " new on server, missing from bundle:");
            for (String tzid : added) {
                System.out.println("  + " + tzid);
            }
        }
        if (!changed.isEmpty()) {
            System.out.println(changed.size() + " changed (bundle differs from server):");
            for (String tzid : changed) {
                System.out.println("  ~ " + tzid);
                System.out.println("      bundle: " + bundled.getString(tzid));
                System.out.println("      server: " + dumped.getProperty(tzid));
            }
        }
        if (!removed.isEmpty()) {
            System.out.println(removed.size() + " in bundle but not returned by server (likely unsupported on this tenant):");
            for (String tzid : removed) {
                System.out.println("  - " + tzid);
            }
        }
    }

    /**
     * Load DavMail settings from {@code test.properties} the same way the test harness does.
     */
    private static void loadTestProperties() throws IOException {
        File testFile = new File("test.properties");
        if (!testFile.exists()) {
            throw new IOException("Please create a test.properties file with davmail.username and davmail.password");
        }
        Settings.setDefaultSettings();
        Settings.setConfigFilePath("test.properties");
        Settings.setProperty("davmail.server", "true");
        try (FileInputStream fis = new FileInputStream(testFile)) {
            Settings.load(fis);
        }
        Settings.updateLoggingConfig();
    }

    /**
     * Force an EWS-backed session. Graph cannot produce VTIMEZONE definitions, so a Graph mode
     * is rewritten to the EWS equivalent and Graph is explicitly disabled.
     */
    private static void forceEwsSession() {
        Settings.setProperty("davmail.enableGraph", "false");
        Settings.setProperty("davmail.mode", Settings.O365_EWS);
    }

    /**
     * {@link Properties} subclass that stores keys in sorted order, for a stable, diffable output file.
     */
    private static class SortedProperties extends Properties {
        @Override
        public synchronized Enumeration<Object> keys() {
            TreeSet<String> sortedKeys = new TreeSet<>();
            Enumeration<Object> keysEnumeration = super.keys();
            while (keysEnumeration.hasMoreElements()) {
                sortedKeys.add((String) keysEnumeration.nextElement());
            }
            final Iterator<String> sortedKeysIterator = sortedKeys.iterator();
            return new Enumeration<Object>() {
                public boolean hasMoreElements() {
                    return sortedKeysIterator.hasNext();
                }

                public Object nextElement() {
                    return sortedKeysIterator.next();
                }
            };
        }
    }
}

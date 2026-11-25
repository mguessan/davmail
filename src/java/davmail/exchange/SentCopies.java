/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2026  Franklin Tranié
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
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Message-IDs that DavMail sent while Exchange saved its own copy in Sent.
 * <p>
 * Exchange saves a copy of every message sent through Graph (always) or EWS
 * and WebDav (when davmail.smtpSaveInSent is true). Some IMAP clients, for example
 * MailMate, then APPEND their own copy to Sent, so every sent message shows up twice.
 * When davmail.imapDropSentCopy is true, the IMAP APPEND handler asks {@link #consume}
 * and drops the client copy when the server already saved one.
 * <p>
 * This is a heuristic: only an APPEND to the Sent folder of the same user, with the
 * same Message-ID, less than 15 minutes after the send, is dropped. The state is
 * in memory only.
 */
public final class SentCopies {
    private static final Logger LOGGER = Logger.getLogger(SentCopies.class);

    /**
     * Clients append right after the SMTP send; a copy that arrives later
     * (offline client, retry) is kept.
     */
    static final long TTL_MILLIS = 15 * 60 * 1000L;
    static final int MAX_ENTRIES = 1000;

    private static final Map<String, Long> SENT = new LinkedHashMap<>();

    private SentCopies() {
    }

    /**
     * @return true if davmail.imapDropSentCopy is true
     */
    public static boolean enabled() {
        return Settings.getBooleanProperty("davmail.imapDropSentCopy", false);
    }

    /**
     * Remember a message the server saved in Sent.
     *
     * @param user      user email
     * @param messageId Message-ID header of the sent message
     */
    public static void record(String user, String messageId) {
        String key = buildKey(user, messageId);
        if (key == null || !enabled()) {
            return;
        }
        synchronized (SENT) {
            long now = System.currentTimeMillis();
            expire(now);
            SENT.remove(key);
            SENT.put(key, now);
            while (SENT.size() > MAX_ENTRIES) {
                Iterator<String> iterator = SENT.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    /**
     * Check if a client APPEND to folderPath duplicates a copy the server
     * already saved, and forget the Message-ID if so: one APPEND is dropped
     * per send.
     *
     * @param user       user email
     * @param folderPath IMAP folder path of the APPEND
     * @param messageId  Message-ID header of the appended message
     * @return true when the APPEND should be dropped
     */
    public static boolean consume(String user, String folderPath, String messageId) {
        String key = buildKey(user, messageId);
        if (key == null || !enabled() || !ExchangeSession.SENT.equalsIgnoreCase(folderPath)) {
            return false;
        }
        synchronized (SENT) {
            expire(System.currentTimeMillis());
            boolean found = SENT.remove(key) != null;
            if (found) {
                LOGGER.info("Dropping APPEND of " + messageId + " to " + folderPath + ": Exchange saved its own copy");
            }
            return found;
        }
    }

    static String buildKey(String user, String messageId) {
        if (user == null || messageId == null || messageId.trim().isEmpty()) {
            return null;
        }
        return user.toLowerCase(Locale.ROOT) + ' ' + messageId.trim().toLowerCase(Locale.ROOT);
    }

    private static void expire(long now) {
        Iterator<Long> iterator = SENT.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next() > TTL_MILLIS) {
                iterator.remove();
            } else {
                // entries are in insertion order
                break;
            }
        }
    }

    /**
     * For tests.
     */
    static void clear() {
        synchronized (SENT) {
            SENT.clear();
        }
    }
}

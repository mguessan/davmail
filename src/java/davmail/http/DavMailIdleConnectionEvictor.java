/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
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

package davmail.http;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * Single thread for all connection managers.
 * close idle connections
 */
public class DavMailIdleConnectionEvictor {
    static final Logger LOGGER = Logger.getLogger(DavMailIdleConnectionEvictor.class);

    // connection manager set
    private static final HashSet<HttpClientConnectionManager> connectionManagers = new HashSet<>();

    private static final long sleepTimeMs = 1000;
    private static final long maxIdleTimeMs = 3000;

    private static Thread thread;

    private static void initEvictorThread() {
        if (thread == null) {
            thread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(sleepTimeMs);
                        synchronized (connectionManagers) {
                            // iterate over connection managers
                            for (HttpClientConnectionManager connectionManager : connectionManagers) {
                                connectionManager.closeExpiredConnections();
                                if (maxIdleTimeMs > 0) {
                                    connectionManager.closeIdleConnections(maxIdleTimeMs, TimeUnit.MILLISECONDS);
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LOGGER.warn("Thread interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (final Exception ex) {
                    LOGGER.error(ex);
                }

            }, "Connection evictor");
            thread.setDaemon(true);
        }
    }

    public static void shutdown() throws InterruptedException {
        thread.interrupt();
        // wait for thread to shutdown
        thread.join(sleepTimeMs);
        thread = null;
    }

    /**
     * Add connection manager to evictor thread.
     *
     * @param connectionManager connection manager
     */
    public static void addConnectionManager(HttpClientConnectionManager connectionManager) {
        synchronized (connectionManagers) {
            initEvictorThread();
            connectionManagers.add(connectionManager);
        }
    }

    /**
     * Remove connection manager from evictor thread.
     *
     * @param connectionManager connection manager
     */
    public static void removeConnectionManager(HttpClientConnectionManager connectionManager) {
        synchronized (connectionManagers) {
            connectionManagers.remove(connectionManager);
        }
    }
}

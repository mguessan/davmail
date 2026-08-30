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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single thread for all connection managers.
 * Close idle connections
 */
public class DavMailIdleConnectionEvictor {
    protected static final Logger LOGGER = Logger.getLogger(DavMailIdleConnectionEvictor.class);

    // Thread-safe set avoids needing coarse-grained synchronized blocks during socket cleanup
    private static final Set<HttpClientConnectionManager> connectionManagers = ConcurrentHashMap.newKeySet();

    private static final long sleepTimeMs = 1000L * 60;
    private static final long maxIdleTimeMs = 1000L * 60 * 5;

    private static ScheduledExecutorService scheduler = null;

    private static synchronized void initEvictorThread() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
                final AtomicInteger count = new AtomicInteger();
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "PoolEvictor-" + count.getAndIncrement());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error(e.getMessage(), e));
                    return thread;
                }
            });

            // use scheduleWithFixedDelay to avoid catchup wave on laptop wakeup from sleep
            scheduler.scheduleWithFixedDelay(() -> {
                // Make sure thread never exits on error
                try {
                    for (HttpClientConnectionManager connectionManager : connectionManagers) {
                        try {
                            connectionManager.closeExpiredConnections();
                            if (maxIdleTimeMs > 0) {
                                connectionManager.closeIdleConnections(maxIdleTimeMs, TimeUnit.MILLISECONDS);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Error closing idle connections on manager: " + e.getMessage(), e);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.error("Unexpected error in connection pool evictor task", t);
                }
            }, sleepTimeMs, sleepTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Shutdown the connection manager evictor thread.
     * @throws InterruptedException on error
     */
    public static synchronized void shutdown() throws InterruptedException {
        if (scheduler != null) {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(sleepTimeMs, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Timed out waiting for tasks to complete");
            }
            // make sure we don't reuse old connection managers
            connectionManagers.clear();
            scheduler = null;
        }
    }

    /**
     * Add a connection manager to the eviction monitor set.
     * @param connectionManager connection manager to monitor
     */
    public static void addConnectionManager(HttpClientConnectionManager connectionManager) {
        if (connectionManager != null) {
            initEvictorThread();
            connectionManagers.add(connectionManager);
        }
    }

    /**
     * Remove the connection manager from the eviction monitor set.
     * @param connectionManager connection manager to remove
     */
    public static void removeConnectionManager(HttpClientConnectionManager connectionManager) {
        if (connectionManager != null) {
            connectionManagers.remove(connectionManager);
        }
    }
}
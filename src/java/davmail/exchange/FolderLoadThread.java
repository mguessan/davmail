/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2013  Mickael Guessant
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Load folder messages in a separate thread.
 */
public class FolderLoadThread extends Thread {
    private static final Logger LOGGER = Logger.getLogger(FolderLoadThread.class);

    boolean isComplete = false;
    ExchangeSession.Folder folder;
    IOException exception;

    FolderLoadThread(String threadName, ExchangeSession.Folder folder) {
        super(threadName + "-LoadFolder");
        setDaemon(true);
        this.folder = folder;
    }

    public void run() {
        try {
            folder.loadMessages();
        } catch (IOException e) {
            exception = e;
        } catch (Exception e) {
            LOGGER.error(e+" "+e.getMessage(), e);
            exception = new IOException(e.getMessage(), e);
        } finally {
            isComplete = true;
        }
    }

    /**
     * Load folder in a separate thread.
     *
     * @param folder       current folder
     * @param outputStream client connection
     * @throws InterruptedException on error
     * @throws IOException          on error
     */
    public static void loadFolder(ExchangeSession.Folder folder, OutputStream outputStream) throws IOException {
        FolderLoadThread folderLoadThread = new FolderLoadThread(currentThread().getName(), folder);
        folderLoadThread.start();
        while (true) {
            try {
                folderLoadThread.join(20000);
		if (folderLoadThread.isComplete) {
		    break;
		}
            } catch (InterruptedException e) {
                LOGGER.warn("Thread interrupted", e);
                Thread.currentThread().interrupt();
            }
            LOGGER.debug("Still loading " + folder.folderPath + " (" + folder.count() + " messages)");
            if (Settings.getBooleanProperty("davmail.enableKeepAlive", false)) {
                try {
                    outputStream.write(' ');
                    outputStream.flush();
                } catch (SocketException e) {
                    folderLoadThread.interrupt();
                    throw e;
                }
            }
        }
        if (folderLoadThread.exception != null) {
            throw folderLoadThread.exception;
        }

    }
}
package davmail.exchange;

import davmail.Settings;
import org.apache.log4j.Logger;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Message load thread.
 * Used to avoid timeouts over POP and IMAP
 */
public class MessageLoadThread extends Thread {
    private static final Logger LOGGER = Logger.getLogger(MessageLoadThread.class);

    protected boolean isComplete = false;
    protected ExchangeSession.Message message;
    protected IOException ioException;
    protected MessagingException messagingException;

    protected MessageLoadThread(String threadName, ExchangeSession.Message message) {
        super(threadName + "-LoadMessage");
        setDaemon(true);
        this.message = message;
    }

    public void run() {
        try {
            message.loadMimeMessage();
        } catch (IOException e) {
            ioException = e;
        } catch (MessagingException e) {
            messagingException = e;
        } finally {
            isComplete = true;
        }
    }

    /**
     * Load mime message in a separate thread if over 1MB.
     * Send a space character every ten seconds to avoid client timeouts
     *
     * @param message      message
     * @param outputStream output stream
     * @throws IOException        on error
     * @throws MessagingException on error
     */
    public static void loadMimeMessage(ExchangeSession.Message message, OutputStream outputStream) throws IOException, MessagingException {
        if (message.size < 1024 * 1024) {
            message.loadMimeMessage();
        } else {
            LOGGER.debug("Load large message " + (message.size / 1024) + "KB uid " + message.getUid() + " imapUid " + message.getImapUid() + " in a separate thread");
            try {
                MessageLoadThread messageLoadThread = new MessageLoadThread(currentThread().getName(), message);
                messageLoadThread.start();
                while (!messageLoadThread.isComplete) {
                    messageLoadThread.join(10000);
                    LOGGER.debug("Still loading uid " + message.getUid() + " imapUid " + message.getImapUid());
                    if (Settings.getBooleanProperty("davmail.enableKeepAlive", false)) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (SocketException e) {
                            // client closed connection, stop thread
                            message.dropMimeMessage();
                            messageLoadThread.interrupt();
                            throw e;
                        }
                    }
                }
                if (messageLoadThread.ioException != null) {
                    throw messageLoadThread.ioException;
                }
                if (messageLoadThread.messagingException != null) {
                    throw messageLoadThread.messagingException;
                }
            } catch (InterruptedException e) {
                throw new IOException(e + " " + e.getMessage());
            }
        }
    }
}
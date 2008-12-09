package davmail.smtp;

import davmail.AbstractConnection;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

/**
 * Dav Gateway smtp connection implementation
 */
public class SmtpConnection extends AbstractConnection {
    protected static final int INITIAL = 0;
    protected static final int AUTHENTICATED = 1;
    protected static final int STARTMAIL = 2;
    protected static final int RECIPIENT = 3;
    protected static final int MAILDATA = 4;
    protected static final int LOGIN = 5;
    public static final int PASSWORD = 6;

    // Initialize the streams and start the thread
    public SmtpConnection(Socket clientSocket) {
        super("SmtpConnection", clientSocket, null);
    }

    public void run() {
        String line;
        StringTokenizer tokens;
        List<String> recipients = new ArrayList<String>();

        try {
            ExchangeSessionFactory.checkConfig();
            sendClient("220 DavMail SMTP ready at " + new Date());
            for (; ;) {
                line = readClient();
                // unable to read line, connection closed ?
                if (line == null) {
                    break;
                }

                tokens = new StringTokenizer(line);
                if (tokens.hasMoreTokens()) {
                    String command = tokens.nextToken();

                    if (state == LOGIN) {
                        // AUTH LOGIN, read userName
                        userName = base64Decode(line);
                        sendClient("334 " + base64Encode("Password:"));
                        state = PASSWORD;
                    } else if (state == PASSWORD) {
                        // AUTH LOGIN, read password
                        password = base64Decode(line);
                        authenticate();
                    } else if ("QUIT".equalsIgnoreCase(command)) {
                        sendClient("221 Closing connection");
                        break;
                    } else if ("EHLO".equals(command)) {
                        sendClient("250-" + tokens.nextToken());
                        // inform server that AUTH is supported
                        // actually it is mandatory (only way to get credentials)
                        sendClient("250-AUTH LOGIN PLAIN");
                        sendClient("250 Hello");
                    } else if ("HELO".equals(command)) {
                        sendClient("250 Hello");
                    } else if ("AUTH".equals(command)) {
                        if (tokens.hasMoreElements()) {
                            String authType = tokens.nextToken();
                            if ("PLAIN".equals(authType) && tokens.hasMoreElements()) {
                                decodeCredentials(tokens.nextToken());
                                authenticate();
                            } else if ("LOGIN".equals(authType)) {
                                sendClient("334 " + base64Encode("Username:"));
                                state = LOGIN;
                            } else {
                                sendClient("451 Error : unknown authentication type");
                            }
                        } else {
                            sendClient("451 Error : authentication type not specified");
                        }
                    } else if ("MAIL".equals(command)) {
                        if (state == AUTHENTICATED) {
                            state = STARTMAIL;
                            recipients.clear();
                            sendClient("250 Sender OK");
                        } else {
                            state = INITIAL;
                            sendClient("503 Bad sequence of commands");
                        }
                    } else if ("RCPT".equals(command)) {
                        if (state == STARTMAIL || state == RECIPIENT) {
                            if (line.startsWith("RCPT TO:")) {
                                state = RECIPIENT;
                                try {
                                    InternetAddress internetAddress = new InternetAddress(line.substring("RCPT TO:".length()));
                                    recipients.add(internetAddress.getAddress());
                                } catch (AddressException e) {
                                    throw new IOException("Invalid recipient: "+line);
                                }
                                sendClient("250 Recipient OK");
                            } else {
                                sendClient("500 Unrecognized command");
                            }

                        } else {
                            state = AUTHENTICATED;
                            sendClient("503 Bad sequence of commands");
                        }
                    } else if ("DATA".equals(command)) {
                        if (state == RECIPIENT) {
                            state = MAILDATA;
                            sendClient("354 Start mail input; end with <CRLF>.<CRLF>");

                            try {
                                session.sendMessage(recipients, in);
                                state = AUTHENTICATED;
                                sendClient("250 Queued mail for delivery");
                            } catch (Exception e) {
                                DavGatewayTray.error("Authentication failed", e);
                                state = AUTHENTICATED;
                                sendClient("451 Error : " + e + " " + e.getMessage());
                            }

                        } else {
                            state = AUTHENTICATED;
                            sendClient("503 Bad sequence of commands");
                        }
                    }

                } else {
                    sendClient("500 Unrecognized command");
                }

                os.flush();
            }

        } catch (SocketException e) {
            DavGatewayTray.debug("Connection closed");
        } catch (IOException e) {
            DavGatewayTray.error(e.getMessage());
            try {
                sendClient("500 " + e.getMessage());
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    /**
     * Create authenticated session with Exchange server
     *
     * @throws IOException on error
     */
    protected void authenticate() throws IOException {
        try {
            session = ExchangeSessionFactory.getInstance(userName, password);
            sendClient("235 OK Authenticated");
            state = AUTHENTICATED;
        } catch (Exception e) {
            DavGatewayTray.error(e);
            String message = e.getMessage();
            if (message == null) {
                message = e.toString();
            }
            message = message.replaceAll("\\n", " ");
            sendClient("554 Authenticated failed " + message);
            state = INITIAL;
        }

    }

    /**
     * Decode SMTP credentials
     *
     * @param encodedCredentials smtp encoded credentials
     * @throws java.io.IOException if invalid credentials
     */
    protected void decodeCredentials(String encodedCredentials) throws IOException {
        String decodedCredentials = base64Decode(encodedCredentials);
        int index = decodedCredentials.indexOf((char) 0, 1);
        if (index > 0) {
            userName = decodedCredentials.substring(1, index);
            password = decodedCredentials.substring(index + 1);
        } else {
            throw new IOException("Invalid credentials");
        }
    }

}


package davmail;

import org.apache.log4j.Priority;

/**
 * Gateway tray interface common to SWT and pure java implementations
 */
public interface DavGatewayTrayInterface {
    void switchIcon();
    void resetIcon();
    void displayMessage(String message, Priority priority);
    void init();
}

package davmail.tray;

import org.apache.log4j.Priority;

import java.awt.*;

/**
 * Gateway tray interface common to SWT and pure java implementations
 */
public interface DavGatewayTrayInterface {
    void switchIcon();
    void resetIcon();
    Image getFrameIcon();
    void displayMessage(String message, Priority priority);
    void init();
}

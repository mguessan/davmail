package davmail.ui.tray;

import org.apache.log4j.Level;

import java.awt.*;

/**
 * Gateway tray interface common to SWT and pure java implementations
 */
public interface DavGatewayTrayInterface {
    void switchIcon();

    void resetIcon();

    void inactiveIcon();

    boolean isActive();

    Image getFrameIcon();

    void displayMessage(String message, Level level);

    void init();
}

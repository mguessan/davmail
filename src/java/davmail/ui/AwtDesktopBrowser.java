package davmail.ui;

import java.io.IOException;
import java.net.URI;
import java.awt.*;

/**
 * Wrapper class to call Java6 Desktop class to launch default browser.
 */
public final class AwtDesktopBrowser {
    private AwtDesktopBrowser() {
    }

    public static void browse(URI location) throws IOException {
        Desktop desktop = Desktop.getDesktop();
        desktop.browse(location);
    }

}

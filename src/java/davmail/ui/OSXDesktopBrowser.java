package davmail.ui;

import java.io.IOException;
import java.net.URI;

/**
 * Failover: Runtime.exec open URL
 */
public class OSXDesktopBrowser {
    private OSXDesktopBrowser() {
    }

    public static void browse(URI location) throws IOException {
       Runtime.getRuntime().exec("open "+location.toString());
    }
}

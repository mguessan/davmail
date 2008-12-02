package davmail.ui;

import org.eclipse.swt.program.Program;

import java.io.IOException;
import java.net.URI;

/**
 *  Wrapper class to call SWT Program class to launch default browser.
 */
public class SwtDesktopBrowser {
    private SwtDesktopBrowser() {
    }

    public static void browse(URI location) throws IOException {
        Program.launch(location.toString());
    }

}

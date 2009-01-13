package davmail.ui;

import org.eclipse.swt.program.Program;

import java.net.URI;

/**
 *  Wrapper class to call SWT Program class to launch default browser.
 */
public final class SwtDesktopBrowser {
    private SwtDesktopBrowser() {
    }

    public static void browse(URI location) {
        Program.launch(location.toString());
    }

}

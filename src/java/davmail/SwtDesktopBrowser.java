package davmail;

import org.eclipse.swt.program.Program;

import java.io.IOException;
import java.net.URI;

/**
 *  Wrapper class to call SWT Program class to launch default browser.
 */
public class SwtDesktopBrowser {
    public static void browse(URI location) throws IOException {
        Program.launch(location.toString());
    }

}

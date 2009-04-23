package davmail.ui.tray;

import davmail.ui.OSXAdapter;
import davmail.BundleMessage;

/**
 * Extended Awt tray with OSX extensions.
 */
public class OSXAwtGatewayTray extends AwtGatewayTray {
    @SuppressWarnings({"SameReturnValue"})
    public boolean quit() {
        return true;
    }

    @Override
    protected void createAndShowGUI() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        super.createAndShowGUI();
        try {
            OSXAdapter.setAboutHandler(this, AwtGatewayTray.class.getDeclaredMethod("about", (Class[]) null));
            OSXAdapter.setPreferencesHandler(this, AwtGatewayTray.class.getDeclaredMethod("preferences", (Class[]) null));
            OSXAdapter.setQuitHandler(this, OSXAwtGatewayTray.class.getDeclaredMethod("quit", (Class[]) null));
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_OSXADAPTER"), e);
        }
    }
}

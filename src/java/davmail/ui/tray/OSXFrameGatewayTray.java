package davmail.ui.tray;

import davmail.ui.OSXAdapter;
import davmail.BundleMessage;
import davmail.DavGateway;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * MacOSX specific frame to handle menu
 */
public class OSXFrameGatewayTray extends FrameGatewayTray {

    @SuppressWarnings({"SameReturnValue"})
    public boolean quit() {
        DavGateway.stop();
        // dispose frames
        settingsFrame.dispose();
        aboutFrame.dispose();
        if (logBrokerMonitor != null) {
            logBrokerMonitor.dispose();
        }
        return true;
    }

    @Override
    protected void buildMenu() {
        // create a popup menu
        JMenu menu = new JMenu(BundleMessage.format("UI_LOGS"));
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        mainFrame.setJMenuBar(menuBar);

        JMenuItem logItem = new JMenuItem(BundleMessage.format("UI_SHOW_LOGS"));
        logItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showLogs();
            }
        });
        menu.add(logItem);
    }


    @Override
    protected void createAndShowGUI() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        super.createAndShowGUI();
        try {
            OSXAdapter.setAboutHandler(this, FrameGatewayTray.class.getDeclaredMethod("about", (Class[]) null));
            OSXAdapter.setPreferencesHandler(this, FrameGatewayTray.class.getDeclaredMethod("preferences", (Class[]) null));
            OSXAdapter.setQuitHandler(this, OSXFrameGatewayTray.class.getDeclaredMethod("quit", (Class[]) null));
        } catch (Exception e) {
            DavGatewayTray.error(new BundleMessage("LOG_ERROR_LOADING_OSXADAPTER"), e);
        }
    }
}

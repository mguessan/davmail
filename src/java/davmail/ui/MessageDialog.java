package davmail.ui;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextArea;

import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;

public class MessageDialog extends JDialog {
	
    public MessageDialog(String message) {
        setModal(false);
        setUndecorated(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        try {
            setIconImages(DavGatewayTray.getFrameIcons());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }
        JTextArea messageArea = new JTextArea(message);
        messageArea.setFont(messageArea.getFont().deriveFont(28f));
        messageArea.setEditable(false);
        messageArea.getCaret().setVisible(false);
        add(messageArea);
        setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 - getSize().width / 2, getToolkit().getScreenSize().height / 2 - getSize().height / 2);
        setAlwaysOnTop(true);
        pack();
        setVisible(true);        
    }


}

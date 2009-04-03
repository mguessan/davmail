package davmail.ui;

import davmail.DavGateway;
import davmail.ui.tray.DavGatewayTray;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * About frame
 */
public class AboutFrame extends JFrame {
    private final JEditorPane jEditorPane;

    public AboutFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("About DavMail Gateway");
        setIconImage(DavGatewayTray.getFrameIcon());
        try {
            JLabel imageLabel = new JLabel();
            ClassLoader classloader = this.getClass().getClassLoader();
            URL imageUrl = classloader.getResource("tray32.png");
            Image iconImage = ImageIO.read(imageUrl);
            ImageIcon icon = new ImageIcon(iconImage);
            imageLabel.setIcon(icon);
            JPanel imagePanel = new JPanel();
            imagePanel.add(imageLabel);
            add("West", imagePanel);
        } catch (IOException e) {
            DavGatewayTray.error("Unable to create icon", e);
        }

        jEditorPane = new JEditorPane();
        HTMLEditorKit htmlEditorKit = new HTMLEditorKit();
        StyleSheet stylesheet = htmlEditorKit.getStyleSheet();
        stylesheet.addRule("body { font-size:small;font-family: " + jEditorPane.getFont().getFamily() + "}");
        jEditorPane.setEditorKit(htmlEditorKit);
        jEditorPane.setContentType("text/html");
	    jEditorPane.setText(getContent());

        jEditorPane.setEditable(false);
        jEditorPane.setOpaque(false);
        jEditorPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent hle) {
                if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                    try {
                        DesktopBrowser.browse(hle.getURL().toURI());
                    } catch (URISyntaxException e) {
                        DavGatewayTray.error("Unable to open link", e);
                    }
                    dispose();
                }
            }
        });


        JPanel mainPanel = new JPanel();
        mainPanel.add(jEditorPane);
        add("Center", mainPanel);

        JPanel buttonPanel = new JPanel();
        JButton ok = new JButton("OK");
        ActionListener close = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        };
        ok.addActionListener(close);

        buttonPanel.add(ok);

        add("South", buttonPanel);

        pack();
        setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
    }

    String getContent() {
        Package davmailPackage = DavGateway.class.getPackage();
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><b>DavMail Gateway</b><br>");
        buffer.append("By Mickaël Guessant<br><br>");
        String currentVersion = davmailPackage.getImplementationVersion();
        if (currentVersion != null) {
            buffer.append("Current version: ").append(currentVersion).append("<br>");
        }
        String releasedVersion = DavGateway.getReleasedVersion();
        if (currentVersion != null && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
            buffer.append("Latest version available: ").append(releasedVersion).append("<br>" +
                    "A new version of DavMail Gateway is available.<br>" +
                    "<a href=\"http://sourceforge.net/project/platformdownload.php?group_id=184600\">Download latest version</a><br>");
        }
        buffer.append("<br>Help and setup instructions available at:<br>" +
                "<a href=\"http://davmail.sourceforge.net\">http://davmail.sourceforge.net</a><br>" +
                "<br>" +
                "To send comments or report bugs, <br>use <a href=\"http://sourceforge.net/tracker/?group_id=184600\">" +
                "DavMail Sourceforge trackers</a><br>" +
                "or contact me at <a href=\"mailto:mguessan@free.fr\">mguessan@free.fr</a>" +
                "</html>");
        return buffer.toString();
    }


    public void update() {
        jEditorPane.setText(getContent());
        pack();
    }

}

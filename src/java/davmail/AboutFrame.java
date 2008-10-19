package davmail;

import org.apache.log4j.Logger;

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
import java.net.URL;

/**
 * About frame
 */
public class AboutFrame extends JFrame {
    protected static final Logger LOGGER = Logger.getLogger(AboutFrame.class);

    public AboutFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("About DavMail");
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
            LOGGER.error("Unable to create icon", e);
        }
        Package davmailPackage = this.getClass().getPackage();
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><b>DavMail Gateway</b><br>");
        String version = davmailPackage.getImplementationVersion();
        if (version != null) {
            buffer.append("<b>Version ").append(version).append("</b><br>");
        }
        buffer.append("By Mickaël Guessant<br>" +
                "<br>" +
                "Help and setup instructions available at:<br>" +
                "<a href=\"http://davmail.sourceforge.net\">http://davmail.sourceforge.net</a><br>" +
                "<br>" +
                "To send comments or report bugs, <br>use <a href=\"http://sourceforge.net/tracker/?group_id=184600\">" +
                "DavMail Sourceforge trackers</a><br>" +
                "or contact me at <a href=\"mailto:mguessan@free.fr\">mguessan@free.fr</a>" +
                "</html>");
        JEditorPane jEditorPane = new JEditorPane("text/html", buffer.toString());
        StyleSheet stylesheet = ((HTMLEditorKit) jEditorPane.getEditorKit()).getStyleSheet();
        stylesheet.addRule("body { font-size:small;font-family: " + jEditorPane.getFont().getFamily() + "}");

        jEditorPane.setEditable(false);
        jEditorPane.setOpaque(false);
        jEditorPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent hle) {
                if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                    try {
                        Desktop desktop = Desktop.getDesktop();
                        desktop.browse(hle.getURL().toURI());
                        dispose();
                    } catch (Exception e) {
                        LOGGER.error("Unable to open link", e);
                    }
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

}

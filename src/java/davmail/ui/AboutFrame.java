/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.ui;

import davmail.DavGateway;
import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;
import davmail.ui.browser.DesktopBrowser;

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

    /**
     * About frame.
     */
    public AboutFrame() {
        DavGatewayTray.setLookAndFeel();
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setTitle(BundleMessage.format("UI_ABOUT_DAVMAIL"));
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
            add(BorderLayout.WEST, imagePanel);
        } catch (IOException e) {
            DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_CREATE_ICON"), e);
        }

        jEditorPane = new JEditorPane();
        HTMLEditorKit htmlEditorKit = new HTMLEditorKit();
        StyleSheet stylesheet = htmlEditorKit.getStyleSheet();
        stylesheet.addRule("body { font-size:small;font-family: " + jEditorPane.getFont().getFamily() + '}');
        jEditorPane.setEditorKit(htmlEditorKit);
        jEditorPane.setContentType("text/html");
        jEditorPane.setText(getContent(null));

        jEditorPane.setEditable(false);
        jEditorPane.setOpaque(false);
        jEditorPane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent hle) {
                if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                    try {
                        DesktopBrowser.browse(hle.getURL().toURI());
                    } catch (URISyntaxException e) {
                        DavGatewayTray.error(new BundleMessage("LOG_UNABLE_TO_OPEN_LINK"), e);
                    }
                    setVisible(false);
                }
            }
        });


        JPanel mainPanel = new JPanel();
        mainPanel.add(jEditorPane);
        add(BorderLayout.CENTER, mainPanel);

        JPanel buttonPanel = new JPanel();
        JButton ok = new JButton(BundleMessage.format("UI_BUTTON_OK"));
        ActionListener close = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                setVisible(false);
            }
        };
        ok.addActionListener(close);

        buttonPanel.add(ok);

        add(BorderLayout.SOUTH, buttonPanel);

        pack();
        setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
    }

    String getContent(String releasedVersion) {
        Package davmailPackage = DavGateway.class.getPackage();
        StringBuilder buffer = new StringBuilder();
        buffer.append(BundleMessage.format("UI_ABOUT_DAVMAIL_AUTHOR"));
        String currentVersion = davmailPackage.getImplementationVersion();
        if (currentVersion != null) {
            buffer.append(BundleMessage.format("UI_CURRENT_VERSION", currentVersion));
        }
        if (currentVersion != null && releasedVersion != null && currentVersion.compareTo(releasedVersion) < 0) {
            buffer.append(BundleMessage.format("UI_LATEST_VERSION", releasedVersion));
        }
        buffer.append(BundleMessage.format("UI_HELP_INSTRUCTIONS"));
        return buffer.toString();
    }


    /**
     * Update about frame content with current released version.
     */
    public void update() {
        jEditorPane.setText(getContent(DavGateway.getReleasedVersion()));
        pack();
    }

}

/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
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

package davmail.exchange.auth;

import davmail.BundleMessage;
import davmail.ui.browser.DesktopBrowser;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URISyntaxException;

public class O365ManualAuthenticatorDialog extends JDialog {
    final JTextField codeField = new JTextField(15);
    protected String code;

    /**
     * Get Oauth authentication code.
     *
     * @return authentication code
     */
    public String getCode() {
        if (code.contains("code=") && code.contains("&session_state=")) {
            code = code.substring(code.indexOf("code=")+5, code.indexOf("&session_state="));
        }
        return code;
    }

    /**
     * Get credentials.
     *
     * @param initUrl Kerberos prompt from callback handler
     */
    public O365ManualAuthenticatorDialog(String initUrl) {
        setAlwaysOnTop(true);

        // TODO setTitle(BundleMessage.format("UI_O365_MANUAL_PROMPT"));
        setTitle("Office 365 Manual authentication");

        try {
            setIconImages(DavGatewayTray.getFrameIcons());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }

        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.X_AXIS));

        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.questionIcon"));
        messagePanel.add(imageLabel);

        JEditorPane jEditorPane = new JEditorPane();
        HTMLEditorKit htmlEditorKit = new HTMLEditorKit();
        StyleSheet stylesheet = htmlEditorKit.getStyleSheet();
        Font font = jEditorPane.getFont();
        stylesheet.addRule("body { font-size:small;font-family: " + ((font==null)?"Arial":font.getFamily()) + '}');
        jEditorPane.setEditorKit(htmlEditorKit);
        jEditorPane.setContentType("text/html");
        jEditorPane.setText(getContent(initUrl));

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
                }
            }
        });
        messagePanel.add(jEditorPane);


        JPanel credentialPanel = new JPanel(new GridLayout(1, 2));

        JLabel promptLabel = new JLabel("Authentication code: ");
        promptLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        promptLabel.setVerticalAlignment(SwingConstants.CENTER);

        credentialPanel.add(promptLabel);

        codeField.setMaximumSize(codeField.getPreferredSize());
        codeField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                code = codeField.getText();
                setVisible(false);
            }
        });
        credentialPanel.add(codeField);

        add(messagePanel, BorderLayout.NORTH);
        add(credentialPanel, BorderLayout.CENTER);
        add(getButtonPanel(), BorderLayout.SOUTH);
        setModal(true);

        pack();
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                        getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setAlwaysOnTop(true);
        setVisible(true);
    }

    private String getContent(String initUrl) {
        return "Please open the following url: <a href=\""+initUrl+"\">"+"Office 365 login"+"</a>," +
                "<br/> proceed through authentication steps and " +
                "<br/> paste back the final url that contains authentication code (blank page)";
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(BundleMessage.format("UI_BUTTON_OK"));
        JButton cancelButton = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                code = codeField.getText();
                setVisible(false);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                code = null;
                setVisible(false);
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

}

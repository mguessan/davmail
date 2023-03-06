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

package davmail.ui;

import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Display number matching value during O365 MFA process.
 */
public class NumberMatchingFrame extends JFrame {

    /**
     * Number matching dialog.
     *
     * @param entropy number matching value from Azure AD
     */
    public NumberMatchingFrame(String entropy) {
        setAlwaysOnTop(true);

        setTitle(BundleMessage.format("UI_O365_MFA_NUMBER_MATCHING"));
        try {
            setIconImages(DavGatewayTray.getFrameIcons());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }



        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
        imageLabel.setText(BundleMessage.format("UI_O365_MFA_NUMBER_MATCHING_PROMPT", entropy));
        infoPanel.add(imageLabel);
        add(infoPanel, BorderLayout.NORTH);
        add(getButtonPanel(), BorderLayout.SOUTH);

        pack();
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                        getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setAlwaysOnTop(true);

        // auto close after 1 minute
        Timer timer=new Timer(60000, evt -> {
            NumberMatchingFrame.this.setVisible(false);
            NumberMatchingFrame.this.dispose();
        });
        timer.start();
        setVisible(true);
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(BundleMessage.format("UI_BUTTON_OK"));
        okButton.addActionListener(evt -> {
            setVisible(false);
            dispose();
        });

        buttonPanel.add(okButton);
        return buttonPanel;
    }

}

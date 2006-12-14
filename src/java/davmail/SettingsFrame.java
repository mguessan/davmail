package davmail;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * DavMail settings frame
 */
public class SettingsFrame extends JFrame {
    public SettingsFrame() {
        setTitle("DavMail Settings");

        JPanel panel = new JPanel(new GridLayout(3, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Gateway settings"));

        final TextField urlField = new TextField("", 20);

        Label urlLabel = new Label("OWA url:");
        urlLabel.setAlignment(Label.RIGHT);
        panel.add(urlLabel);
        panel.add(urlField);

        final TextField popPortField = new TextField(4);
        Label popPortLabel = new Label("Local POP port:");
        popPortLabel.setAlignment(Label.RIGHT);
        panel.add(popPortLabel);
        panel.add(popPortField);

        final TextField smtpPortField = new TextField(4);
        Label smtpPortLabel = new Label("Local SMTP port:");
        smtpPortLabel.setAlignment(Label.RIGHT);
        panel.add(smtpPortLabel);
        panel.add(smtpPortField);

        add("North", panel);

        panel = new JPanel(new GridLayout(2, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Proxy settings"));

        final TextField httpProxyField = new TextField(System.getProperty("http.proxyHost"), 20);

        Label httpProxyLabel = new Label("Proxy server:");
        httpProxyLabel.setAlignment(Label.RIGHT);
        panel.add(httpProxyLabel);
        panel.add(httpProxyField);

        final TextField httpProxyPortField = new TextField(System.getProperty("http.proxyPort"), 4);
        Label httpProxyPortLabel = new Label("Proxy port:");
        httpProxyPortLabel.setAlignment(Label.RIGHT);
        panel.add(httpProxyPortLabel);
        panel.add(httpProxyPortField);

        // TODO : add proxy user and password

        add("Center", panel);

        panel = new JPanel();
        Button cancel = new Button("Cancel");
        Button ok = new Button("Save");
        ActionListener save = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // TODO : sava options
                setVisible(false);
            }
        };
        ok.addActionListener(save);

        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                setVisible(false);
            }
        });

        panel.add(ok);
        panel.add(cancel);

        add("South", panel);

        pack();
        setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        urlField.requestFocus();
    }
}

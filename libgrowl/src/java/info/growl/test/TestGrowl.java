/*
 * Copyright (c) 2008, Michael Stringer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Growl nor the names of its contributors may be
 *       used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <copyright holder> ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <copyright holder> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package info.growl.test;

import info.growl.Growl;
import info.growl.GrowlCallbackListener;
import info.growl.GrowlException;
import info.growl.GrowlUtils;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Simple test class for Growl.
 * 
 * @author Michael Stringer
 * @version 0.1
 */
public class TestGrowl extends JFrame {
    private static final String APP_NAME = "Test Java App";
    private static final String NOTIF_3_CALLBACK = "Notif3";

    public TestGrowl() {
	super("Growl for Java");
	setSize(320, 290);

	buildComponents();
	setVisible(true);
	setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void buildComponents() {
	getContentPane().setLayout(new GridLayout(6, 1));

	Action action = new AbstractAction("Register") {
	    public void actionPerformed(ActionEvent ae) {
		try {
		    Growl growl = GrowlUtils.getGrowlInstance(APP_NAME);

		    growl.addNotification("Test Notification 1", true);
		    growl.addNotification("Test Notification 2", false);
		    growl.addNotification("Test Notification 3", true);

		    GrowlCallbackListener listener = new GrowlCallbackListener() {
			public void notificationWasClicked(
				final String clickContext) {
			    SwingUtilities.invokeLater(new Runnable() {
				public void run() {
				    if (NOTIF_3_CALLBACK.equals(clickContext)) {
					JOptionPane
						.showMessageDialog(
							TestGrowl.this,
							"User clicked on 'Test Notification 3'");
				    }
				}
			    });
			}
		    };

		    growl.addCallbackListener(listener);
		    growl.register();
		} catch (GrowlException ge) {
		    ge.printStackTrace();
		}
	    }
	};

	getContentPane().add(new JButton(action));

	action = new AbstractAction("Send 'Test Notification 1'") {
	    public void actionPerformed(ActionEvent ae) {
		try {
		    Growl growl = GrowlUtils.getGrowlInstance(APP_NAME);

		    growl.sendNotification("Test Notification 1",
			    "Test Notif 1", "This is a test");
		} catch (GrowlException ge) {
		    ge.printStackTrace();
		}
	    }
	};
	getContentPane().add(new JButton(action));

	action = new AbstractAction("Send 'Test Notification 2'") {
	    public void actionPerformed(ActionEvent ae) {
		try {
		    Growl growl = GrowlUtils.getGrowlInstance(APP_NAME);
		    BufferedImage image = ImageIO.read(TestGrowl.class
			    .getResource("/images/duke.gif"));
		    growl.sendNotification("Test Notification 2",
			    "Test Notif 2", "This is another test", image);
		} catch (GrowlException ge) {
		    ge.printStackTrace();
		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}
	    }
	};
	getContentPane().add(new JButton(action));

	action = new AbstractAction("Test Callback 'Notification 3'") {
	    public void actionPerformed(ActionEvent ae) {
		try {
		    Growl growl = GrowlUtils.getGrowlInstance(APP_NAME);

		    growl.sendNotification("Test Notification 3",
			    "Callback Test", "Click me - I dares you!", NOTIF_3_CALLBACK);
		} catch (GrowlException ge) {
		    ge.printStackTrace();
		}
	    }
	};
	getContentPane().add(new JButton(action));

	action = new AbstractAction("Reg & Test App 2") {
	    public void actionPerformed(ActionEvent ae) {
		try {
		    Growl growl = GrowlUtils.getGrowlInstance("Other App");

		    growl.addNotification("A Notification", true);

		    BufferedImage image = ImageIO.read(TestGrowl.class
			    .getResource("/images/duke.gif"));
		    growl.setIcon(image);

		    growl.register();
		    growl.sendNotification("A Notification", "Testin",
			    "Blah de blah blah");
		} catch (GrowlException ge) {
		    ge.printStackTrace();
		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}
	    }
	};
	getContentPane().add(new JButton(action));

	action = new AbstractAction("Exit") {
	    public void actionPerformed(ActionEvent ae) {
		System.exit(0);
	    }
	};
	getContentPane().add(new JButton(action));
    }

    public static final void main(String[] args) {
	new TestGrowl();
    }
}

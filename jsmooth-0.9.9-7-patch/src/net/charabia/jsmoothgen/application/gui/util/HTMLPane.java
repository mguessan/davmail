/*
  JSmooth: a VM wrapper toolkit for Windows
  Copyright (C) 2003 Rodrigo Reyes <reyes@charabia.net>

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

 */

package net.charabia.jsmoothgen.application.gui.util;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.util.*;
import javax.swing.text.html.*;
import javax.swing.event.*;
import java.net.*;

/**
 * 
 */

public class HTMLPane extends JPanel
{
    private JScrollPane m_scroller;
  private JEditorPane m_html;
  private URL m_baseurl;

  edu.stanford.ejalbert.BrowserLauncher m_launcher;

  class Hyperactive implements HyperlinkListener {

    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {

        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        } else {
		    try {
			URL nurl = e.getURL();
			if (nurl == null)
			    nurl = new URL(m_baseurl, e.getDescription());
			if (jsmooth.Native.isAvailable())
			    {
              jsmooth.Native.shellExecute(jsmooth.Native.SHELLEXECUTE_OPEN, nurl.toString(), null, null, jsmooth.Native.SW_NORMAL);
			    }
			else
			    m_launcher.openURLinBrowser(nurl.toExternalForm());
 		    } catch (Throwable t) {
			t.printStackTrace();
		    }
		}
	    }
	}
    }


    public HTMLPane()
    {
	try {
      m_baseurl = new File(".").toURI().toURL();
	} catch (Exception ex) { ex.printStackTrace(); }
	m_html = new JEditorPane("text/html","<html></html>") {
		public boolean getScrollableTracksViewportWidth()
		{
		    return true;
		}
    };
    HTMLEditorKit hek = new HTMLEditorKit();
    m_html.setEditorKit(hek);

    m_scroller = new JScrollPane(m_html);
    setLayout(new BorderLayout());
    m_html.setEditable(false);
    add(m_scroller, BorderLayout.CENTER);
    // add(m_html, BorderLayout.CENTER);
    m_html.addHyperlinkListener(new Hyperactive());

	try {
	    m_launcher = new edu.stanford.ejalbert.BrowserLauncher();
	}catch (Exception ex)
	    {
		ex.printStackTrace();
	    }

    }

    public java.awt.Dimension getPreferredSize()
    {
	return new java.awt.Dimension(200,200);
    }

    public void setPage(URL url)
    {
	try {
	    URL u = new URL(m_baseurl, url.toExternalForm());
	    m_html.setPage(u);
	} catch (Exception ex) { ex.printStackTrace(); }
    }

    public void setText(String s)
    {
	m_html.setContentType("text/html");
	m_html.setText(s);
	m_html.setCaretPosition(0);
  }

    
}

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

package net.charabia.jsmoothgen.application.gui.editors;

import net.charabia.jsmoothgen.skeleton.*;
import net.charabia.jsmoothgen.application.*;
import net.charabia.jsmoothgen.application.gui.*;
import net.charabia.jsmoothgen.application.gui.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.io.*;
import com.l2fprod.common.swing.*;
import com.l2fprod.common.propertysheet.*;

public class ExecutableIcon extends Editor
{
    private FileSelectionTextField m_selector = new FileSelectionTextField();
    private JLabel m_iconDisplay = new JLabel("(no image)");

    public ExecutableIcon()
    {
	setLayout(new BorderLayout());
	add(BorderLayout.CENTER, m_selector);
	add(BorderLayout.SOUTH, m_iconDisplay);

	m_iconDisplay.setHorizontalAlignment(JLabel.CENTER);

	m_selector.addListener(new FileSelectionTextField.FileSelected() {
		public void fileSelected(String filename)
		{
// 		    System.out.println("new icon: " + filename);
		    setIconLocation(new File(filename));
		}
	    });
    }

    public void dataChanged()
    {
	if (getBaseDir() != null)
	    m_selector.setBaseDir(getBaseDir());

	if (m_model.getIconLocation() != null)
	    {
		m_selector.setFile(getAbsolutePath(new java.io.File(m_model.getIconLocation())));
		setIconLocation(getAbsolutePath(new java.io.File(m_model.getIconLocation())));
		
	    }
	else
	    {
		m_selector.setFile(null);
		setIconLocation(new File(""));
	    }
    }

    public void updateModel()
    {
	File f = m_selector.getFile();
	if (f != null)
	    m_model.setIconLocation(m_selector.getFile().toString());
	else
	    m_model.setIconLocation(null);
    }

    public String getLabel()
    {
	return "ICONLOCATION_LABEL";
    }

    public String getDescription()
    {
	return "ICONLOCATION_HELP";
    }

    private void setIconLocation(File iconfile)
    {
	if (iconfile.isAbsolute() == false)
	    {
		iconfile = new File(m_basedir, iconfile.toString());
	    }
	ImageIcon icon = null;
	
// 	System.out.println("setIconLocation: " + iconfile);

	if (iconfile.toString().toUpperCase().endsWith(".ICO"))
	    {
		//
		// Try to load with our ico codec...
		//
		try {
		    java.awt.image.BufferedImage image = net.charabia.util.codec.IcoCodec.getPreferredImage(iconfile);
		    if (image != null)
			{
			    icon = new ImageIcon(image);
			}
		} catch (java.io.IOException exc)
		    {
			exc.printStackTrace();
		    }
	    }
	else   // Otherwise try with the standard toolkit functions...
	    {
            BufferedImage bufferedImage;
            try {
                bufferedImage = ImageIO.read(iconfile);
                icon = new javax.swing.ImageIcon(bufferedImage, "default icon");
            } catch (IOException e) {
                e.printStackTrace();
            }

	    }


	if (icon != null)
	    {
		int width = icon.getIconWidth();
		int height = icon.getIconHeight();

		m_iconDisplay.setIcon(icon);
		m_iconDisplay.setText("");
		m_model.setIconLocation(iconfile.getAbsolutePath());
		this.validate();
		this.invalidate();
	    }
	else
	    {
		m_iconDisplay.setIcon(null);
		m_iconDisplay.setText("(no image)");
		m_model.setIconLocation(null);
	    }

	doLayout();
	invalidate();
	validate();
	repaint();
    }

    
}

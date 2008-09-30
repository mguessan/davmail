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

package net.charabia.jsmoothgen.application;

import net.charabia.jsmoothgen.skeleton.*;
import net.charabia.jsmoothgen.pe.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.lang.reflect.*;
import net.charabia.util.codec.*;

import javax.imageio.ImageIO;

public class ExeCompiler
{
    private java.util.Vector m_errors = new java.util.Vector();
    private Vector m_listeners = new Vector();
	
    public interface StepListener
    {
	public void setNewState(int percentComplete, String state);
	public void failed();
	public void complete();
    }
	
    public void addListener(ExeCompiler.StepListener listener)
    {
	m_listeners.add(listener);
		
    }
	
    public void cleanErrors()
    {
	m_errors.removeAllElements();
    }
	
    public java.util.Vector getErrors()
    {
	return m_errors;
    }
	
    public class CompilerRunner implements Runnable
    {
	private File m_skelroot;
	private SkeletonBean m_skel;
	private JSmoothModelBean m_data;
	private File m_out;
	private File m_basedir;

	public CompilerRunner(File skelroot, SkeletonBean skel, File basedir, JSmoothModelBean data, File out)
	{
	    m_skelroot = skelroot;
	    m_skel = skel;
	    m_data = data;
	    m_out = out;
	    m_basedir = basedir;
	}
		
	public void run()
	{
	    try
		{
		    compile(m_skelroot, m_skel, m_basedir, m_data, m_out);
		} catch (Exception exc)
		    {
			exc.printStackTrace();
		    }
	}
	
	public ExeCompiler getCompiler()
	{
	    return ExeCompiler.this;
	}
    }
	
    public ExeCompiler.CompilerRunner getRunnable(File skelroot, SkeletonBean skel, File basedir, JSmoothModelBean data, File out)
    {
	return new CompilerRunner(skelroot, skel, basedir, data, out);
    }
	
    public void compileAsync(File skelroot, SkeletonBean skel, File basedir, JSmoothModelBean data, File out)
    {
	Thread t = new Thread(new CompilerRunner(skelroot, skel, basedir, data, out));
	t.start();
    }
	
    public boolean compile(File skelroot, SkeletonBean skel, File basedir ,JSmoothModelBean data, File out) throws Exception
    {
	try {
	    fireStepChange(0, "Starting compilation");
	    
	    File pattern = new File(skelroot, skel.getExecutableName());
	    if (pattern.exists() == false)
		{
		    m_errors.add("Error: Can't find any skeleton at " + skelroot);
		    fireFailedChange();
		    return false;
		}
			
	    fireStepChange(10, "Scanning skeleton...");
	    PEFile pe = new PEFile(pattern);
	    pe.open();
	    PEResourceDirectory resdir = pe.getResourceDirectory();

	    boolean resb = false;
		
	    //
	    // Adds the jar only if the user selected one
	    //
	    if (data.getEmbeddedJar() == true)
		{
		    if (data.getJarLocation() == null)
			{
			    m_errors.add("Error: Jar is not specified!");
			    fireFailedChange();
			    return false;
			}
			
		    fireStepChange(40, "Loading Jar...");
		    File jarloc = concFile(basedir, new File(data.getJarLocation()));
		    if (jarloc.exists() == false)
			{
			    m_errors.add("Error: Can't find jar at " + jarloc);
			    fireFailedChange();
			    return false;
			}

		    ByteBuffer jardata = load(jarloc);

		    fireStepChange(60, "Adding Jar to Resources...");
		    resb = resdir.replaceResource(skel.getResourceCategory(), skel.getResourceJarId(), 1033, jardata);
		    if (resb == false)
			{
			    m_errors.add("Error: Can't replace jar resource! It is probably missing from the skeleton.");
			    fireFailedChange();
			    return false;
			}
		}
	    
	    fireStepChange(70, "Adding Properties to Resources...");
	    String props = PropertiesBuilder.makeProperties(basedir, data);
	    ByteBuffer propdata = convert(props);
	    resb = resdir.replaceResource(skel.getResourceCategory(), skel.getResourcePropsId(), 1033, propdata);

	    if (data.getIconLocation() != null)
		{
		    fireStepChange(80, "Loading icon...");
		    String iconpath;
		    if (new java.io.File(data.getIconLocation()).isAbsolute())
			iconpath = data.getIconLocation();
		    else
			iconpath = new java.io.File(basedir, data.getIconLocation()).getAbsolutePath();

		    Image img = getScaledImage(iconpath, 32, 32);
		    //Hashtable set = calculateColorCount(img);
		    //		    System.out.println("COLORS TOTAL 4: " + set.size());

		    if (img != null)
			{
			    net.charabia.jsmoothgen.pe.res.ResIcon32 resicon = new net.charabia.jsmoothgen.pe.res.ResIcon32(img);
			    pe.replaceDefaultIcon(resicon);
			}
		}

	    fireStepChange(90, "Saving exe...");
	    pe.dumpTo(out);
			
	    //		System.out.println("PROPERTIES:\n" + props);
			
	    fireCompleteChange();
	    return true;
	} catch (Exception exc)
	    {
		m_errors.add("Error: " + exc.getMessage());
		exc.printStackTrace();
		fireFailedChange();
		return false;
	    }
    }
	
    public Image[] loadImages(String path)
    {
	File f = new File(path);

	if (path.toUpperCase().endsWith(".ICO"))
	    {
		//
		// Try to load with our ico codec...
		//
		try {
		    java.awt.Image[] images = net.charabia.util.codec.IcoCodec.loadImages(f);
		    if ((images != null) && (images.length>0))
			{
			    return images;
			}
		} catch (java.io.IOException exc)
		    {
			exc.printStackTrace();
		    }
	    }

	// 
	// defaults to the standard java loading process
	//
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(f);
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(bufferedImage, "default icon");
            java.awt.Image[] imgs = new java.awt.Image[1];
            imgs[0] = icon.getImage();
            return imgs;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;        
    }

    public void checkImageLoaded(Image img)
    {
	MediaTracker mtrack = new MediaTracker(new Canvas());
	mtrack.addImage(img, 1);
	try {
	    mtrack.waitForAll();
	} catch (InterruptedException e) {
	}
    }

    private Hashtable calculateColorCount(Image img)
    {
	int width = img.getWidth(null);
	int height = img.getHeight(null);
	int[] pixels = new int[width*height];
	PixelGrabber grabber = new PixelGrabber(img, 0, 0, width, height, pixels, 0, width);
	try
	    {
		grabber.grabPixels();
	    } catch (InterruptedException e)
		{
		    System.err.println("interrupted waiting for pixels!");
		    //		    throw new Exception("Can't load the image provided",e);
		}
		


	Hashtable result = new Hashtable();
	int colorindex = 0;
	for (int i=0; i<pixels.length; i++)
	    {
		int pix = pixels[i];
		if (((pix>>24)&0xFF) > 0)
		    {
			pix &= 0x00FFFFFF;
			Integer pixi = new Integer(pix);
			Object o = result.get(pixi);
			if (o == null)
			    {
				result.put(pixi, new Integer(colorindex++));
			    }
			//			if (colorindex > 256)
			//			    return result;
		    }
	    }
	return result;
    }

    public BufferedImage getQuantizedImage(Image img)
    {
        // 32 bit ico file already loaded as BufferedImage
        if (img instanceof BufferedImage) {
        return (BufferedImage) img;
        } else {
    int width = img.getWidth(null);
	int height = img.getHeight(null);
	int[][] data = new int[width][height];
	
	int[] pixelbuffer = new int[width*height];
	PixelGrabber grabber = new PixelGrabber(img, 0, 0, width, height, pixelbuffer, 0, width);
	try
	    {
		grabber.grabPixels();
	    } catch (InterruptedException e)
		{
		    System.err.println("interrupted waiting for pixels!");
		    throw new RuntimeException("Can't load the image provided",e);
		}
	for (int i=0; i<pixelbuffer.length; i++)
	    {
		data[i%width][i/width] = pixelbuffer[i];
	    }
	
	int[][] savedata = new int[width][height];	

	for(int y=0;y<height;y++)
	    for (int x=0;x<width;x++)
		savedata[x][y] = data[x][y];
	
	int[] palette = net.charabia.util.codec.Quantize.quantizeImage(data, 255);
	byte[] cmap = new byte[256*4];

	for (int i=0; i<palette.length; i++)
	    {
		//		System.out.println(" i= " + (i));
		cmap[(i*4)] = (byte)((palette[i] >> 16) & 0xFF);
		cmap[(i*4)+1] = (byte)((palette[i] >> 8) & 0xFF);
		cmap[(i*4)+2] = (byte) (palette[i] & 0xFF);
		cmap[(i*4)+3] = (byte) 0xFF;
	    }

	IndexColorModel colmodel = new IndexColorModel(8, palette.length, cmap, 0, true, 0);
	BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	//
	// The normal manner of quantizing would be to run
	// result.setRGB(0,0, width, height, pixelbuffer, 0, width);
	// where result is a BufferedImage of
	// BufferedImage.TYPE_BYTE_INDEXED type. Unfortunately, I
	// couldn't make it work. So, here is a work-around that
	// should work similarly.
	//
	java.util.Hashtable set = new java.util.Hashtable();
	for (int y=0; y<height; y++)
	    {
		for (int x=0; x<width; x++)
		    {
			int alpha = (savedata[x][y]>>24)&0xFF;
			if (alpha == 0)
			    {
				result.setRGB(x,y, 0);
				// 				System.out.print(".");
			    }
			else
			    {
				int rgb = colmodel.getRGB(data[x][y]);
				rgb |= 0xFF000000;
				set.put(new Integer(rgb), new Integer(rgb));
				result.setRGB(x,y,rgb);
				// 				System.out.print("*");
			    }
		    }
		//		System.out.println("");
	    }


	return result;
        }
    }

    public Image checkImageSize(Image img, int width, int height)
    {
	int w = img.getWidth(null);
	int h = img.getHeight(null);
	if ((w == width) && (h == height))
	    return img;
	return null;
    }

    public Image getScaledImage(String path, int width, int height)
    {
	Image[] orgimages = loadImages(path);
	
	if ((orgimages == null) || (orgimages.length == 0))
	    return null;

	for (int i=0; i<orgimages.length; i++)
	    checkImageLoaded(orgimages[i]);

	//	System.out.println("Loaded " + orgimages.length + " images");
	for (int i=0; (i<orgimages.length); i++)
	    {
		int w = orgimages[i].getWidth(null);
		int h = orgimages[i].getHeight(null);
		//		System.out.println("Size of " + i + " = " + w + "," + h);
	    }	

	//
	// We prefer 32x32 pictures, then 64x64, then 16x16...
	//
	Image selected = null;
	for (int i=0; (i<orgimages.length) && (selected==null); i++)
	    selected = checkImageSize(orgimages[i], 32, 32);
	for (int i=0; (i<orgimages.length) && (selected==null); i++)
	    selected = checkImageSize(orgimages[i], 64, 64);
	for (int i=0; (i<orgimages.length) && (selected==null); i++)
	    selected = checkImageSize(orgimages[i], 16, 16);

	if (selected != null)
	    {
		return getQuantizedImage(selected);
	    }

	//
	// If there is no 32x32, 64x64, nor 16x16, then we scale the
	// biggest image to be 32x32... This should happen mainly when
	// loading an image from a png of gif file, and in most case
	// there is only one image on the array.
	//
	int maxsize = 0;
	Image biggest = null;
	for (int i=0; (i<orgimages.length) && (selected==null); i++)
	    {
		int size = orgimages[i].getWidth(null) * orgimages[i].getHeight(null);
		if (size>maxsize)
		    {
			maxsize = size;
			biggest = orgimages[i];
		    }
	    }

	if (biggest != null)
	    {
		Image result = biggest.getScaledInstance(32, 32, Image.SCALE_AREA_AVERAGING);
		checkImageLoaded(result);
		return getQuantizedImage(result);
	    }
	//
	// Here, we have failed and return null
	//
	return null;
    }


    private ByteBuffer load(File in) throws Exception
    {
	FileInputStream fis = new FileInputStream(in);
	ByteBuffer data = ByteBuffer.allocate((int)in.length());
	data.order(ByteOrder.LITTLE_ENDIAN);
	FileChannel fischan = fis.getChannel();
	fischan.read(data);
	data.position(0);
	fis.close();
		
	return data;
    }
	
    private ByteBuffer convert(String data)
    {
	ByteBuffer result = ByteBuffer.allocate(data.length()+1);
	result.position(0);
		
	for (int i=0; i<data.length(); i++)
	    {
		result.put((byte)data.charAt(i));
	    }
	result.put((byte)0);
		
	result.position(0);
	return result;
    }
	
    static public File concFile(File root, File name)
    {
	if (name.isAbsolute())
	    return name;
		
	return new File(root, name.toString());
    }
	
    public void fireStepChange(int percentComplete, String state)
    {
	for (Iterator i=m_listeners.iterator(); i.hasNext(); )
	    {
		ExeCompiler.StepListener l = (ExeCompiler.StepListener)i.next();
		l.setNewState(percentComplete, state);
	    }
    }
	
    public void fireFailedChange()
    {
	for (Iterator i=m_listeners.iterator(); i.hasNext(); )
	    {
		ExeCompiler.StepListener l = (ExeCompiler.StepListener)i.next();
		l.failed();
	    }
    }
    public void fireCompleteChange()
    {
	for (Iterator i=m_listeners.iterator(); i.hasNext(); )
	    {
		ExeCompiler.StepListener l = (ExeCompiler.StepListener)i.next();
		l.complete();
	    }
    }
	
}

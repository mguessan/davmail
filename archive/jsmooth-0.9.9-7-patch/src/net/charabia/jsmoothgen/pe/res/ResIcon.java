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

/*
 * ResIcon.java
 *
 * Created on 17 août 2003, 22:51
 */

package net.charabia.jsmoothgen.pe.res;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.awt.*;
import java.awt.image.*;

/**
 * @see
 */
public class ResIcon
{
    public long Size;            /* Size of this header in bytes DWORD*/
    public long  Width;           /* Image width in pixels LONG*/
    public long  Height;          /* Image height in pixels LONG*/
    public int  Planes;          /* Number of color planes WORD*/
    public int  BitsPerPixel;    /* Number of bits per pixel WORD*/
    /* Fields added for Windows 3.x follow this line */
    public long Compression;     /* Compression methods used DWORD*/
    public long SizeOfBitmap;    /* Size of bitmap in bytes DWORD*/
    public long  HorzResolution;  /* Horizontal resolution in pixels per meter LONG*/
    public long  VertResolution;  /* Vertical resolution in pixels per meter LONG*/
    public long ColorsUsed;      /* Number of colors in the image DWORD*/
    public long ColorsImportant; /* Minimum number of important colors DWORD*/
	
    public PaletteElement[] Palette;
    public short[] BitmapXOR;
    public short[] BitmapAND;
	
    public class PaletteElement
    {
	public int Blue;
	public int Green;
	public int Red;
	public int Reserved;
	public String toString()
	{
	    return "{"+Blue+","+Green+","+Red+","+Reserved+"}";
	}
    }
	
    public ResIcon(){};
    /**
     * Creates a new instance of ResIcon 
     * @see
     * @param in
     */
    public ResIcon(ByteBuffer in)
    {
	Size = in.getInt();
	Width = in.getInt();
	Height = in.getInt();
	Planes = in.getShort();
	BitsPerPixel = in.getShort();
	Compression = in.getInt();
	SizeOfBitmap = in.getInt();
	HorzResolution = in.getInt();
	VertResolution = in.getInt();
	ColorsUsed = in.getInt();
	ColorsImportant = in.getInt();
		
	int cols = (int)ColorsUsed;
	if (cols == 0)
	    cols = 1 << BitsPerPixel;
		
	Palette = new PaletteElement[(int)cols];
	for (int i=0; i<Palette.length; i++)
	    {
		PaletteElement el = new PaletteElement();
		el.Blue = in.get();
		el.Green = in.get();
		el.Red = in.get();
		el.Reserved = in.get();
		Palette[i] = el;
	    }

	// int xorbytes = (((int)Height/2) * (int)Width * (int)BitsPerPixel) / 8;
	int xorbytes = (((int)Height/2) * (int)Width);
	//		System.out.println("POSITION " + in.position() + " : xorbitmap = " + xorbytes + " bytes");
		
	BitmapXOR = new short[xorbytes];
	for (int i=0; i<BitmapXOR.length; i++)
	    {
		switch(BitsPerPixel)
		    {
		    case 4:
			{
			    int pix = in.get();
			    BitmapXOR[i] = (short)((pix >> 4) & 0x0F);
			    i++;
			    BitmapXOR[i] = (short)(pix & 0x0F);
			}
			break;
		    case 8:
			{
			    BitmapXOR[i] = in.get();
			}
			break;
		    }
	    }
		

	int height = (int)(Height/2);
	int rowsize = (int)Width / 8;
	if ((rowsize%4)>0)
	    {
		rowsize += 4 - (rowsize%4);
	    }
		
	//		System.out.println("POSITION " + in.position() + " : andbitmap = " + andbytes + " bytes");

	int andbytes = height * rowsize;   // (((int)Height/2) * (int)Width) / 8;
				
	BitmapAND = new short[andbytes];
	for (int i=0; i<BitmapAND.length; i++)
	    {
		BitmapAND[i] = in.get();
	    }
		
    }
	
    /** Creates a new instance based on the data of the Image argument.
     * @param img
     */	
    public ResIcon(Image img) throws Exception
    {
	int width = img.getWidth(null);
	int height = img.getHeight(null);
		
	if ((width % 8) != 0)
	    width += (7-(width%8));
		
	if ((height % 8) != 0)
	    height += (7-(height%8));
		
	//		System.out.println("FOUND WIDTH " + width + " (was " + img.getWidth(null) + ")");
	//		System.out.println("FOUND HEIGHT " + height + " (was " + img.getHeight(null) + ")");

	//	System.out.println("RESICON...");
	if (img instanceof BufferedImage)
	    {
		BufferedImage result = (BufferedImage)img;

		for (int y=0; y<result.getHeight(); y++)
		    {
			for (int x=0; x<result.getWidth(); x++)
			    {
				int rgb = result.getRGB(x, y);
				if (((rgb>>24)&0xFF)>0)
				    {
					//					System.out.print(".");
				    }
				//				else
				//				    System.out.print("*");
			    }
			//			System.out.println("");
		    }

	    }
		
	int[] pixelbuffer = new int[width*height];
	PixelGrabber grabber = new PixelGrabber(img, 0, 0, width, height, pixelbuffer, 0, width);
	try
	    {
		grabber.grabPixels();
	    } catch (InterruptedException e)
		{
		    System.err.println("interrupted waiting for pixels!");
		    throw new Exception("Can't load the image provided",e);
		}
		
	Hashtable colors = calculateColorCount(pixelbuffer);

	// FORCE ALWAYS to 8
	this.BitsPerPixel = 8;
		
	Palette = new ResIcon.PaletteElement[1 << BitsPerPixel];
	//	System.out.println("Creating palette of " + Palette.length + " colors (" + colors.size() + ")");
	for (Enumeration e=colors.keys(); e.hasMoreElements(); )
	    {
		Integer pixi = (Integer)e.nextElement();
		int pix = pixi.intValue();
		int index = ((Integer)colors.get(pixi)).intValue();
		//		System.out.println("set pixel " + index);
	
		Palette[index] = new ResIcon.PaletteElement();
		Palette[index].Blue = pix & 0xFF;
		Palette[index].Green = (pix >>  8) & 0xff;
		Palette[index].Red = (pix >> 16) & 0xff;
	    }
	for (int i=0; i<Palette.length; i++)
	    {
		if (Palette[i] == null)
		    Palette[i] = new ResIcon.PaletteElement();
	    }
		
		
	this.Size = 40;
	this.Width = width;
	this.Height = height* 2;
	this.Planes = 1;
	this.Compression = 0;
		
	this.SizeOfBitmap = 0;
	this.HorzResolution = 0;
	this.VertResolution = 0;

	this.ColorsUsed = 0;
	this.ColorsImportant = 0;

	//
	// We calculate the rowsize in bytes. It seems that it must be
	// aligned on a double word, although none of the
	// documentation I have on the icon format states so.
	//
	int rowsize = width / 8;
	if ((rowsize%4)>0)
	    {
		rowsize += 4 - (rowsize%4);
	    }

	BitmapXOR = new short[(((int)Height/2) * (int)Width * (int)BitsPerPixel) / 8];
	BitmapAND = new short[((int)Height/2) * rowsize];
		
	int bxl = BitmapXOR.length-1;
	int bal = BitmapAND.length-1;
	
	for (int i=0; i<pixelbuffer.length; i++)
	    {
		int col = i%width;
		int line = i/width;
			
		bxl = (width * height) - (((i/width)+1)*width) + (i%width);
		//		bal = ((width * height)/8) - ((line+1)*(width/8)) + (col/8);
		bal = (rowsize * height) - ((line+1)*(rowsize)) + (col/8);

		// if ((pixelbuffer[i] & 0xFF000000) != 0x00000000)

		//
		// If the color is transparent, any color will suit
		// (as it is not supposed to be displayed)
		//
		if (  (((pixelbuffer[i]>>24)& 0xFF) == 0))
		    {
			BitmapAND[ bal ] |= 1 << (7-(i%8));
			BitmapXOR[bxl] = 0xFF; // (short)getBrightest(); FF

			// 				int pixel = pixelbuffer[i] & 0x00FFFFFF;
			// 				pixel = 0x000000;
			// 				Integer icol = (Integer)colors.get(new Integer(pixel));
			// 				if (icol != null)
			// 				{
			// 					int palindex = icol.intValue();
			// 					BitmapXOR[bxl] = (short)palindex;
			// 				}
			// 				else
			// 				{
			// 				    BitmapXOR[bxl] = 0; // (short)getBrightest();
			// 				    System.out.println("Can't find TRANSP BLACK COL " + icol );
			// 				}
		    }
		else
		    {
			int pixel = pixelbuffer[i] & 0x00FFFFFF;
			// pixel = 0x000000;
			Integer icol = (Integer)colors.get(new Integer(pixel));
			if (icol != null)
			    {
				int palindex = icol.intValue();
				BitmapXOR[bxl] = (short)palindex;
			    }
		    }
	    }
    }

    private int getBrightest()
    {
	int result = 0;
	int averesult = 0;
	for (int i=0; i<Palette.length; i++)
	    {
		int ave1 = (Palette[0].Red + Palette[0].Green + Palette[0].Blue)/3;
		if (ave1 > averesult)
		    {
			averesult = ave1;
			result = i;
		    }
	    }
	return result;
    }
	
    private Hashtable calculateColorCount(int[] pixels)
    {
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
	
    /** Creates and returns a ByteBuffer containing an image under
     * the .ico format expected by Windows.
     * @return a ByteBuffer with the .ico data
     */	
    public ByteBuffer getData()
    {
	int cols = (int)ColorsUsed;
	if (cols == 0)
	    cols = 1 << BitsPerPixel;

	int rowsize = (int)Width / 8;
	if ((rowsize%4)>0)
	    {
		rowsize += 4 - (rowsize%4);
	    }

	ByteBuffer buf = ByteBuffer.allocate((int) (40 + (cols*4) + (Width*(Height/2)*BitsPerPixel)/8 + (rowsize*(Height/2))));
	buf.order(ByteOrder.LITTLE_ENDIAN);
	buf.position(0);

	buf.putInt((int)Size);
	buf.putInt((int)Width);
	buf.putInt((int)Height);
	buf.putShort((short)Planes);
	buf.putShort((short)BitsPerPixel);
	buf.putInt((int)Compression);
	buf.putInt((int)SizeOfBitmap);
	buf.putInt((int)HorzResolution);
	buf.putInt((int)VertResolution);
	buf.putInt((int)ColorsUsed);
	buf.putInt((int)ColorsImportant);

	//		System.out.println("GET DATA :: Palette.size= "+Palette.length + " // position=" + buf.position());
	for (int i=0; i<Palette.length; i++)
	    {
		PaletteElement el = Palette[i];
		buf.put((byte)el.Blue);
		buf.put((byte)el.Green);
		buf.put((byte)el.Red);
		buf.put((byte)el.Reserved);
	    }

	switch (BitsPerPixel)
	    {
	    case 4:
		{
		    for (int i=0; i<BitmapXOR.length; i+=2)
			{
			    int v1 = BitmapXOR[i];
			    int v2 = BitmapXOR[i+1];
			    buf.put((byte)( (v1<<4) | v2 ));
			}		
		}
		break;
			
	    case 8:
		{
		    //				System.out.println("GET DATA :: XORBitmap.size= "+BitmapXOR.length + " // position=" + buf.position());
		    for (int i=0; i<BitmapXOR.length; i++)
			{
			    buf.put((byte)BitmapXOR[i]);
			}				
		}
		break;
			
	    default:
		throw new RuntimeException("BitRes " + BitsPerPixel + " not supported!");
	    }
		
	//		System.out.println("GET DATA :: AndBitmap.size= "+BitmapAND.length + " // position=" + buf.position());
	for (int i=0; i<BitmapAND.length; i++)
	    {
		buf.put((byte)BitmapAND[i]);
	    }
		
	//		System.out.println("GET DATA END AT " + buf.position());
	buf.position(0);
	return buf;
    }
	
    public String toString()
    {
	StringBuffer out = new StringBuffer();
		
	out.append("Size: " + Size);
	out.append("\nWidth: " + Width);
	out.append("\nHeight: " + Height);
	out.append("\nPlanes: " + Planes);
	out.append("\nBitsPerPixel: " + BitsPerPixel);
	out.append("\nCompression: " + Compression);
	out.append("\nSizeOfBitmap: " + SizeOfBitmap);
	out.append("\nHorzResolution: " + HorzResolution);
	out.append("\nVertResolution: " + VertResolution);
	out.append("\nColorsUsed: " + ColorsUsed);
	out.append("\nColorsImportant: " + ColorsImportant);
		
	//		for (int i = 0; i<Palette.length; i++)
	//		{
	//			out.append("\n");
	//			out.append(Palette[i].toString());
	//		}
	out.append("\nBitmapXOR["+ BitmapXOR.length+ "]={");
	for (int i=0; i<BitmapXOR.length; i++)
	    {
		out.append((byte)BitmapXOR[i]);
	    }
	out.append("}\nBitmapAnd["+ BitmapAND.length +"]={");
	for (int i=0; i<BitmapAND.length; i++)
	    {
		out.append((byte)BitmapAND[i]);
	    }
		
	return out.toString();
    }
	
    public static void main(String[]args) throws Exception
    {
	net.charabia.jsmoothgen.pe.PEFile.main(args);
    }
}

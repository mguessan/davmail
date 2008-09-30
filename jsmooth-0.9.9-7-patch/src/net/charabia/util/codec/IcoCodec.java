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

package net.charabia.util.codec;

import java.io.*;
import net.charabia.util.io.BinaryInputStream;
import java.awt.image.*;
import java.awt.*;

/**
 *
 * @author  Rodrigo Reyes
 */
public class IcoCodec
{
    static public class IconDir
    {
        int idType;
        int idCount;

	public IconDir(BinaryInputStream in) throws IOException
	{
	    in.readUShortLE();
	    idType = in.readUShortLE();
	    idCount = in.readUShortLE();
	}

	public String toString()
	{
	    return "{ idType=" + idType + ", " + idCount + " }";
	}
    }

    static public class IconEntry
    {
        short  bWidth;
        short  bHeight;
        short  bColorCount;
        short  bReserved;
        int  wPlanes;
        int  wBitCount;
        long dwBytesInRes;
        long dwImageOffset;

	public IconEntry(BinaryInputStream in) throws IOException
	{
	    bWidth = in.readUByte();
	    bHeight = in.readUByte();
	    bColorCount = in.readUByte();
	    bReserved = in.readUByte();
	    wPlanes = in.readUShortLE();
	    wBitCount = in.readUShortLE();
	    dwBytesInRes = in.readUIntLE();
	    dwImageOffset = in.readUIntLE();
	}

	public String toString()
	{
	    StringBuffer buffer = new StringBuffer();
	    buffer.append("{ bWidth="+bWidth+"\n");
	    buffer.append("  bHeight="+bHeight+"\n");
	    buffer.append("  bColorCount="+bColorCount+"\n");
	    buffer.append("  wPlanes="+wPlanes+"\n");
	    buffer.append("  wBitCount="+wBitCount+"\n");
	    buffer.append("  dwBytesInRes="+dwBytesInRes+"\n");
	    buffer.append("  dwImageOffset="+dwImageOffset+"\n");
	    buffer.append("}");

	    return buffer.toString();
	}

    }

    static public class IconHeader
    {
	public long Size;            /* Size of this header in bytes DWORD 0*/
	public long Width;           /* Image width in pixels LONG 4*/
	public long Height;          /* Image height in pixels LONG 8*/
	public int  Planes;          /* Number of color planes WORD 12 */
	public int  BitsPerPixel;    /* Number of bits per pixel WORD 14 */
	/* Fields added for Windows 3.x follow this line */
	public long Compression;     /* Compression methods used DWORD 16 */
	public long SizeOfBitmap;    /* Size of bitmap in bytes DWORD 20 */
	public long HorzResolution;  /* Horizontal resolution in pixels per meter LONG 24 */
	public long VertResolution;  /* Vertical resolution in pixels per meter LONG 28*/
	public long ColorsUsed;      /* Number of colors in the image DWORD 32 */
	public long ColorsImportant; /* Minimum number of important colors DWORD 36 */

	public IconHeader(BinaryInputStream in) throws IOException
	{
	    Size = in.readUIntLE();
	    Width = in.readUIntLE();
	    Height = in.readUIntLE();
	    Planes = in.readUShortLE();
	    BitsPerPixel = in.readUShortLE();
	    Compression = in.readUIntLE();
	    SizeOfBitmap = in.readUIntLE();
	    HorzResolution = in.readUIntLE();
	    VertResolution = in.readUIntLE();
	    ColorsUsed = in.readUIntLE();
	    ColorsImportant = in.readUIntLE();
	}

	public String toString()
	{
	    StringBuffer buffer = new StringBuffer();
	    buffer.append("Size="); buffer.append(Size);
	    buffer.append("\nWidth="); buffer.append(Width);
	    buffer.append("\nHeight="); buffer.append(Height);
	    buffer.append("\nPlanes="); buffer.append(Planes);
	    buffer.append("\nBitsPerPixel="); buffer.append(BitsPerPixel);
	    buffer.append("\nCompression="); buffer.append(Compression);
	    buffer.append("\nSizeOfBitmap="); buffer.append(SizeOfBitmap);
	    buffer.append("\nHorzResolution="); buffer.append(HorzResolution);
	    buffer.append("\nVertResolution="); buffer.append(VertResolution);
	    buffer.append("\nColorsUsed="); buffer.append(ColorsUsed);
	    buffer.append("\nColorsImportant="); buffer.append(ColorsImportant);

	    return buffer.toString();
	}

    }

    static public BufferedImage[] loadImages(File f) throws IOException
    {
	InputStream istream = new FileInputStream(f);
        BufferedInputStream buffin = new BufferedInputStream(istream);
	BinaryInputStream in = new BinaryInputStream(buffin);

	try {
	    in.mark(32000);

	    IconDir dir = new IconDir(in);
	    //	    System.out.println("DIR = " + dir);

	    IconEntry[] entries = new IconEntry[dir.idCount];
	    BufferedImage[] images = new BufferedImage[dir.idCount];

	    for (int i=0; i<dir.idCount; i++)
		{
		    entries[i] = new IconEntry(in);
		    //		    System.out.println("ENTRY " + i + " = " + entries[i]);
		}

	    IconEntry entry = entries[0];
	    //	    System.out.println("ENTRYx = " + entry);

	    for (int i=0; i<dir.idCount; i++)
		{
		    in.reset();
		    in.skip(entries[i].dwImageOffset);

		    IconHeader header = new IconHeader(in);
		    //		    System.out.println("Header: " + header);

		    long toskip = header.Size - 40;
		    if (toskip>0)
			in.skip((int)toskip);

		    //		    System.out.println("skipped data");
		    BufferedImage image = new BufferedImage((int)header.Width, (int)header.Height/2,
							    BufferedImage.TYPE_INT_ARGB);

		    switch(header.BitsPerPixel)
			{
			case 4:
			case 8:
			    loadPalettedImage(in, entries[i], header, image);
			    break;
            case 32:
                load32bitsImage(in, entries[i], header, image);
                break;

            default:
			    throw new Exception("Unsupported ICO color depth: " + header.BitsPerPixel);
			}

		    images[i] = image;
		}

	    return images;

	} catch (Exception exc)
	    {
		exc.printStackTrace();
	    }

	return null;
    }

    static private void loadPalettedImage(BinaryInputStream in, IconEntry entry, IconHeader header, BufferedImage image) throws Exception
    {
	//	System.out.println("Loading image...");

	//	System.out.println("Loading palette...");

	//
	// First, load the palette
	//
	int cols = (int)header.ColorsUsed;
	if (cols == 0)
	    {
		if (entry.bColorCount != 0)
		    cols = entry.bColorCount;
		else
		    cols = 1 << header.BitsPerPixel;
	    }

	int[] redp = new int[cols];
	int[] greenp = new int[cols];
	int[] bluep = new int[cols];

 	for (int i=0; i<cols; i++)
	    {
		bluep[i] = in.readUByte();
		greenp[i] = in.readUByte();
		redp[i] = in.readUByte();
		in.readUByte();
	    }

	//	System.out.println("Palette read!");

	//
	// Set the image

	int xorbytes = (((int)header.Height/2) * (int)header.Width);
	int readbytes = 0;

	for (int y=(int)(header.Height/2)-1; y>=0; y--)
	    {
		for (int x=0; x<header.Width; x++)
		    {
			switch(header.BitsPerPixel)
			    {
			    case 4:
				{
				    int pix = in.readUByte();
				    readbytes++;

				    int col1 = (pix>>4) & 0x0F;
				    int col2 = pix & 0x0F;
				    image.setRGB(x, y, (0xFF<<24) | (redp[col1]<<16) | (greenp[col1]<<8) | bluep[col1]);
				    image.setRGB(++x, y, (0xFF<<24) | (redp[col2]<<16) | (greenp[col2]<<8) | bluep[col2]);
				}
				break;
			    case 8:
				{
				    int col1 = in.readUByte();
				    readbytes++;

				    image.setRGB(x, y, (0xFF<<24) | (redp[col1]<<16) | (greenp[col1]<<8) | bluep[col1]);
				}
				break;
			    }
		    }
	    }
	//	System.out.println("XOR data read (" + readbytes + " bytes)");

	int height = (int)(header.Height/2);

	int rowsize = (int)header.Width / 8;
	if ((rowsize%4)>0)
	    {
		rowsize += 4 - (rowsize%4);
	    }

	//	System.out.println("rowsize = " + rowsize);
	int[] andbytes = new int[rowsize * height ];

	for (int i=0; i<andbytes.length; i++)
	    andbytes[i] = in.readUByte();


	for (int y=height-1; y>=0; y--)
	    {
		for (int x=0; x<header.Width; x++)
		    {
			int offset = ((height - (y+1))*rowsize) + (x/8);
			if ( (andbytes[offset] & (1<<(7-x%8))) != 0)
			    {
				image.setRGB(x, y, 0);
			    }
		    }
	    }

	// 	for (int i=0; i<andbytes; i++)
	// 	    {
	// 		int pix = in.readUByte();
	// 		readbytes++;

	// 		int xb = (i*8) % (int)header.Width;
	// 		int yb = ((int)header.Height/2) - (((i*8) / (int)header.Width)+1);

	// 		for (int offset=7; offset>=0; offset--)
	// 		    {
	// 			//
	// 			// Modify the transparency only if necessary
	// 			//
	// 			System.out.println("SET AND (" + xb + "," + yb + ")-" + (7-offset));

	// 			if (((1<<offset) & pix)!=0)
	// 			    {
	// 				int argb = image.getRGB(xb+(7-offset), yb);
	// 				image.setRGB(xb+(7-offset), yb, argb & 0xFFFFFF);
	// 			    }
	// 		    }
	// 	    }

	//	System.out.println("AND data read (" + readbytes + " bytes total)");
    }

    static private void load32bitsImage(BinaryInputStream in, IconEntry entry, IconHeader header, BufferedImage image) throws Exception {
        for (int y = (int) ((header.Height) / 2) - 1; y >= 0; y--) {
            for (int x = 0; x < header.Width; x++) {
                byte alpha = in.readByte();
                byte red = in.readByte();
                byte green = in.readByte();
                byte blue = in.readByte();

                int pix = (alpha &0xff) | (red &0xff) <<8 | (green &0xff) << 16 | (blue &0xff) << 24;
                image.setRGB(x, y, pix);
            }
        }
    }

    static public void main(String[]args) throws Exception
    {
	File f = new File(args[0]);
	Image img = IcoCodec.loadImages(f)[0];
	//	System.out.println("img = " + img);

	javax.swing.JFrame jf = new javax.swing.JFrame("Test");
	javax.swing.JButton button = new javax.swing.JButton(new javax.swing.ImageIcon(img));
	jf.getContentPane().add(button);
	jf.pack();
	jf.setVisible(true);
    }

}

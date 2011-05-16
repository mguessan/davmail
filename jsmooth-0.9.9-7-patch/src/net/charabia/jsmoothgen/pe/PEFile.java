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
 * PEFile.java
 *
 * Created on 28 juillet 2003, 21:28
 */

package net.charabia.jsmoothgen.pe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Vector;

import net.charabia.jsmoothgen.pe.res.ResIcon;
import net.charabia.jsmoothgen.pe.res.ResIconDir;

/**
 * @author Rodrigo
 */
public class PEFile {
  private File m_file;
  private FileInputStream m_in = null;
  private FileChannel m_channel = null;

  private PEOldMSHeader m_oldmsheader;
  private PEHeader m_header;
  private Vector m_sections = new Vector();

  private PEResourceDirectory m_resourceDir;

    /**
     * Creates a new instance of PEFile
     */
    public PEFile(File f) {
    m_file = f;
  }

  public void close() throws IOException {
    m_in.close();
  }

  public void open() throws FileNotFoundException, IOException {
    m_in = new FileInputStream(m_file);
    m_channel = m_in.getChannel();

    m_oldmsheader = new PEOldMSHeader(this);

    m_oldmsheader.read();
    // m_oldmsheader.dump(System.out);
    long headoffset = m_oldmsheader.e_lfanew;

    m_header = new PEHeader(this, headoffset);
    m_header.read();
    // m_header.dump(System.out);

    int seccount = m_header.NumberOfSections;
    // System.out.println("LOADING " + seccount + " sections...");

    long offset = headoffset + (m_header.NumberOfRvaAndSizes * 8) + 24 + getPeHeaderOffset();

    for (int i = 0; i < seccount; i++) {
      // System.out.println("Offset: " + offset + " (" + this.m_channel.position());

      PESection sect = new PESection(this, offset);
      sect.read();
      // sect.dump(System.out);
      m_sections.add(sect);
      offset += 40;
    }
    // System.out.println("After sections: " + this.m_channel.position() + " (" + offset + ")");

    ByteBuffer resbuf = null;
    long resourceoffset = m_header.ResourceDirectory_VA;
    for (int i = 0; i < seccount; i++) {
      PESection sect = (PESection)m_sections.get(i);
      if (sect.VirtualAddress == resourceoffset) {
        // System.out.println("  Resource section found: " + resourceoffset);
        PEResourceDirectory prd = new PEResourceDirectory(this, sect);
        resbuf = prd.buildResource(sect.VirtualAddress);
        break;
      }
    }
  }

  private int getPeHeaderOffset() {
    int pe32Offset = 96;
    if (m_header.isPe32Plus()) {
      // It is a pe32+ header (x64)
      pe32Offset = 112;
    }
    return pe32Offset;
  }

  public FileChannel getChannel() {
    return m_channel;
  }

  public static void main(String args[]) throws IOException, CloneNotSupportedException, Exception {
    // (no)PEFile pe = new PEFile(new File("F:/Program Files/LAN Search PRO/lansearch.exe"));

    PEFile pe = new PEFile(new File("c:/scratch/rc3.exe"));
    // PEFile pe = new PEFile(new File("c:/projects/jwrap/Copie.exe"));
    // PEFile pe = new PEFile(new File("c:/projects/jwrap/test.exe"));
    // PEFile pe = new PEFile(new File("F:/Program Files/bQuery/bQuery.exe"));
    // PEFile pe = new PEFile(new File("F:/Program Files/Server Query/query.exe"));
    // PEFile pe = new PEFile(new File("F:/Program Files/AvRack/rtlrack.exe"));
    pe.open();
    System.out.println("OldMSHeader");
    pe.m_oldmsheader.dump(System.out);
    System.out.println("COFFHeader");
    pe.m_header.dump(System.out);

    // System.out.println("===============\nADDING A RES");
    // File fout = new File("F:/Documents and Settings/Rodrigo/Mes documents/projects/jsmooth/skeletons/simplewrap/gen-application.jar");
    // FileInputStream fis = new FileInputStream(fout);
    //	
    // ByteBuffer data = ByteBuffer.allocate((int)fout.length());
    // data.order(ByteOrder.LITTLE_ENDIAN);
    // FileChannel fischan = fis.getChannel();
    // fischan.read(data);
    // data.position(0);
    // fis.close();

    PEResourceDirectory resdir = pe.getResourceDirectory();
    System.out.println("ResourceDirectory");
    resdir.dump(System.out);

    // DataEntry inputResData = resdir.getData("JAVA", "#" + String.valueOf(103), "#" + String.valueOf(1033));
    // ByteBuffer inputResDataBuffer = ByteBuffer.allocate(inputResData.diskSize() + 1024);
    // inputResDataBuffer.order(ByteOrder.LITTLE_ENDIAN);
    // inputResData.buildBuffer(inputResDataBuffer, 0, 0);
    // int inputResDataBufferSize = inputResDataBuffer.position();
    // inputResDataBuffer.flip();
    // int offset = inputResDataBuffer.getInt();
    // inputResDataBuffer.position(offset);
    // StringBuilder inputResDataString = new StringBuilder(inputResDataBufferSize);
    // while (inputResDataBuffer.position() <= inputResDataBufferSize - 2) {
    // byte dummyByte = inputResDataBuffer.get();
    // inputResDataString.append((char)dummyByte);
    // }
    // // Modify the data...
    // String newInputResDataString = inputResDataString.toString();
    // newInputResDataString = newInputResDataString.replace("samplejar", "ThisIsMyJarAndOnlyMine");
    //
    // inputResDataBuffer = ByteBuffer.allocate(newInputResDataString.length() + 2);
    // for (int index = 0; index < newInputResDataString.length(); index++) { // C- do not change because buffer can be modified during loop
    // inputResDataBuffer.put((byte)newInputResDataString.charAt(index));
    // }
    // inputResDataBuffer.put((byte)0);
    // inputResDataBuffer.put((byte)0);
    // inputResDataBuffer.position(0);
    //
    // boolean resb = resdir.replaceResource("JAVA", 102, 1033, inputResDataBuffer);

    // PEResourceDirectory.DataEntry entry = resdir.getData("#14", "A", "#1033");
    // entry.Data.position(0);
    // System.out.println("DataEntry found : " + entry + " (size=" + entry.Data.remaining() + ")");
    // entry.Data.position(0);
    //	
    // ResIconDir rid = new ResIconDir(entry.Data);
    // System.out.println("ResIconDir :");
    // System.out.println(rid.toString());
    // int iconid = rid.getEntries()[0].dwImageOffset;
    // System.out.println("Icon Index: " + iconid);
    //	
    // PEResourceDirectory.DataEntry iconentry = resdir.getData("#3", "#"+iconid, "#1033");
    // iconentry.Data.position(0);
    // ResIcon icon = new ResIcon(iconentry.Data);
    // System.out.println("Icon :");
    // System.out.println(icon.toString());

    // java.awt.Image img = java.awt.Toolkit.getDefaultToolkit().getImage ("c:\\test.gif");
    // java.awt.Image img = java.awt.Toolkit.getDefaultToolkit().getImage ("c:\\gnome-day2.png");
    // java.awt.Image img = java.awt.Toolkit.getDefaultToolkit().getImage("c:\\gnome-color-browser2.png");
    //
    // java.awt.MediaTracker mt = new java.awt.MediaTracker(new javax.swing.JLabel("toto"));
    // mt.addImage(img, 1);
    // try {
    // mt.waitForAll();
    // } catch (Exception exc) {
    // exc.printStackTrace();
    // }
    //
    // ResIcon newicon = new ResIcon(img);
    //
    // pe.replaceDefaultIcon(newicon);

    // System.out.println("-----------------\nNEW ICON:");
    // System.out.println(newicon.toString());
    //	
    // rid.getEntries()[0].bWidth = (short)newicon.Width;
    // rid.getEntries()[0].bHeight = (short)(newicon.Height/2);
    // rid.getEntries()[0].bColorCount = (short)(1 <<newicon.BitsPerPixel);
    // rid.getEntries()[0].wBitCount = newicon.BitsPerPixel;
    // rid.getEntries()[0].dwBytesInRes = newicon.getData().remaining();
    //	
    // iconentry.Data = newicon.getData();
    // iconentry.Size = iconentry.Data.remaining();
    //
    // entry.setData(rid.getData());
    // System.out.println("POST CHANGE ResIconDir :");
    // System.out.println(rid.toString());

    // ResIcon test = new ResIcon(icon.getData());
    // System.out.println("PROOF-TEST:\n" + test.toString());

    // / BACK
    //	
    // rid.getEntries()[0].bWidth = (short)icon.Width;
    // rid.getEntries()[0].bHeight = (short)(icon.Height/2);
    // rid.getEntries()[0].bColorCount = (short)(1 <<icon.BitsPerPixel);
    // rid.getEntries()[0].wBitCount = icon.BitsPerPixel;
    // iconentry.Data = icon.getData();
    // iconentry.Size = iconentry.Data.remaining();

    // resdir.addNewResource("POUET", "A666", "#1033", data);

    // resdir.dump(System.out);

    // System.out.println("New size = " + resdir.size());
    File out = new File("c:/scratch/COPIE.exe");
    pe.dumpTo(out);

  }

  public PEResourceDirectory getResourceDirectory() throws IOException {
        if (m_resourceDir != null)
            return m_resourceDir;

    long resourceoffset = m_header.ResourceDirectory_VA;
    for (int i = 0; i < m_sections.size(); i++) {
      PESection sect = (PESection)m_sections.get(i);
      if (sect.VirtualAddress == resourceoffset) {
        m_resourceDir = new PEResourceDirectory(this, sect);
        return m_resourceDir;
      }
    }

    return null;
  }

  public void dumpTo(File destination) throws IOException, CloneNotSupportedException {
    int outputcount = 0;
    FileOutputStream fos = new FileOutputStream(destination);
    FileChannel out = fos.getChannel();

    //
    // Make a copy of the Header, for safe modifications
    //
    PEOldMSHeader oldmsheader = (PEOldMSHeader)this.m_oldmsheader.clone();
    PEHeader peheader = (PEHeader)m_header.clone();
    Vector sections = new Vector();
    for (int i = 0; i < m_sections.size(); i++) {
      PESection sect = (PESection)m_sections.get(i);
      PESection cs = (PESection)sect.clone();
      sections.add(cs);
    }

    //
    // First, write the old MS Header, the one starting
    // with "MZ"...
    //
    long newexeoffset = oldmsheader.e_lfanew;
    ByteBuffer msheadbuffer = oldmsheader.get();
    outputcount = out.write(msheadbuffer);
    this.m_channel.position(64);
    out.transferFrom(this.m_channel, 64, newexeoffset - 64);


    //
    // Then Write the new Header...
    //
    ByteBuffer headbuffer = peheader.get();
    out.position(newexeoffset);
    outputcount = out.write(headbuffer);

    //
    // After the header, there are all the section
    // headers...
    //
    long offset = oldmsheader.e_lfanew + (m_header.NumberOfRvaAndSizes * 8) + 24 + getPeHeaderOffset();
    out.position(offset);
    for (int i = 0; i < sections.size(); i++) {
      // System.out.println("  offset: " + out.position());
      PESection sect = (PESection)sections.get(i);

      ByteBuffer buf = sect.get();
      outputcount = out.write(buf);
    }

    //
    // Now, we write the real data: each of the section
    // and their data...
    //

    // Not sure why it's always at 1024... ?
    offset = 1024;

    //
    // Dump each section data
    //

    long virtualAddress = offset;
    if ((virtualAddress % peheader.SectionAlignment) > 0)
      virtualAddress += peheader.SectionAlignment - (virtualAddress % peheader.SectionAlignment);

    long resourceoffset = m_header.ResourceDirectory_VA;
    for (int i = 0; i < sections.size(); i++) {
      PESection sect = (PESection)sections.get(i);
      if (resourceoffset == sect.VirtualAddress) {
                //			System.out.println("Dumping RES section " + i + " at " + offset + " from " + sect.PointerToRawData + " (VA=" + virtualAddress + ")");
        out.position(offset);
        long sectoffset = offset;
        PEResourceDirectory prd = this.getResourceDirectory();
        ByteBuffer resbuf = prd.buildResource(sect.VirtualAddress);
        resbuf.position(0);

        out.write(resbuf);
        offset += resbuf.capacity();
        long rem = offset % this.m_header.FileAlignment;
                if (rem != 0)
                    offset += this.m_header.FileAlignment - rem;

        if (out.size() + 1 < offset) {
          ByteBuffer padder = ByteBuffer.allocate(1);
          out.write(padder, offset - 1);
        }

        long virtualSize = resbuf.capacity();
        if ((virtualSize % peheader.SectionAlignment) > 0)
          virtualSize += peheader.SectionAlignment - (virtualSize % peheader.SectionAlignment);

        sect.PointerToRawData = sectoffset;
        sect.SizeOfRawData = resbuf.capacity();
        if ((sect.SizeOfRawData % this.m_header.FileAlignment) > 0)
          sect.SizeOfRawData += (this.m_header.FileAlignment - (sect.SizeOfRawData % this.m_header.FileAlignment));
        sect.VirtualAddress = virtualAddress;
        sect.VirtualSize = virtualSize;
        // System.out.println("  VS=" + virtualSize + " at VA=" + virtualAddress);
        virtualAddress += virtualSize;

      } else if (sect.PointerToRawData > 0) {
                //			System.out.println("Dumping section " + i + "/" + sect.getName() + " at " + offset + " from " + sect.PointerToRawData + " (VA=" + virtualAddress + ")");
        out.position(offset);
        this.m_channel.position(sect.PointerToRawData);
        long sectoffset = offset;

        out.position(offset + sect.SizeOfRawData);
        ByteBuffer padder = ByteBuffer.allocate(1);
        out.write(padder, offset + sect.SizeOfRawData - 1);

        long outted = out.transferFrom(this.m_channel, offset, sect.SizeOfRawData);
        offset += sect.SizeOfRawData;
        // System.out.println("offset before alignment, " + offset);

        long rem = offset % this.m_header.FileAlignment;
        if (rem != 0) {
          offset += this.m_header.FileAlignment - rem;
        }
        // System.out.println("offset after alignment, " + offset);
        
        // long virtualSize = sect.SizeOfRawData;
        // if ((virtualSize % peheader.SectionAlignment)>0)
        // virtualSize += peheader.SectionAlignment - (virtualSize%peheader.SectionAlignment);

        sect.PointerToRawData = sectoffset;
        // sect.SizeOfRawData =
        sect.VirtualAddress = virtualAddress;
        // sect.VirtualSize = virtualSize;

        virtualAddress += sect.VirtualSize;
        if ((virtualAddress % peheader.SectionAlignment) > 0)
          virtualAddress += peheader.SectionAlignment - (virtualAddress % peheader.SectionAlignment);

      } else {
        // generally a BSS, with a virtual size but no
        // data in the file...
        // System.out.println("Dumping section " + i + " at " + offset + " from " + sect.PointerToRawData + " (VA=" + virtualAddress + ")");
        long virtualSize = sect.VirtualSize;
        if ((virtualSize % peheader.SectionAlignment) > 0)
          virtualSize += peheader.SectionAlignment - (virtualSize % peheader.SectionAlignment);

        sect.VirtualAddress = virtualAddress;
        // sect.VirtualSize = virtualSize;
        virtualAddress += virtualSize;

      }
    }

    // 
    // Now that all the sections have been written, we have the
    // correct VirtualAddress and Sizes, so we can update the new
    // header and all the section headers...

    peheader.updateVAAndSize(m_sections, sections);
    headbuffer = peheader.get();
    out.position(newexeoffset);
    outputcount = out.write(headbuffer);

    // peheader.dump(System.out);
    // System.out.println("Dumping the section again...");
    offset = oldmsheader.e_lfanew + (m_header.NumberOfRvaAndSizes * 8) + 24 + getPeHeaderOffset();
    out.position(offset);
    for (int i = 0; i < sections.size(); i++) {
      // System.out.println("  offset: " + out.position());
      PESection sect = (PESection)sections.get(i);
      // sect.dump(System.out);
      ByteBuffer buf = sect.get();
      outputcount = out.write(buf);
    }

    fos.flush();
    fos.close();
  }

  /*
     */

  public void replaceDefaultIcon(ResIcon icon) throws Exception {
    PEResourceDirectory resdir = getResourceDirectory();

    PEResourceDirectory.DataEntry entry = resdir.getData("#14", null, null);
    if (entry == null) {
      throw new Exception("Can't find any icon group in the file!");
    }

    entry.Data.position(0);
    // System.out.println("DataEntry found : " + entry + " (size=" + entry.Data.remaining() + ")");
    entry.Data.position(0);

    ResIconDir rid = new ResIconDir(entry.Data);
    // System.out.println("ResIconDir :");
    // System.out.println(rid.toString());
    int iconid = rid.getEntries()[0].dwImageOffset;
    // System.out.println("Icon Index: " + iconid);

    PEResourceDirectory.DataEntry iconentry = resdir.getData("#3", "#" + iconid, null);
    iconentry.Data.position(0);
    // System.out.println("Icon :");
    // System.out.println(icon.toString());

    rid.getEntries()[0].bWidth = (short)icon.Width;
    rid.getEntries()[0].bHeight = (short)(icon.Height / 2);
    rid.getEntries()[0].bColorCount = (short)(1 << icon.BitsPerPixel);
    rid.getEntries()[0].wBitCount = icon.BitsPerPixel;
    rid.getEntries()[0].dwBytesInRes = icon.getData().remaining();

    iconentry.Data = icon.getData();
    iconentry.Size = iconentry.Data.remaining();

    entry.setData(rid.getData());
  }

}

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
 * PEResourceDirectory.java
 *
 * Created on 2 aout 2003, 01:28
 */

package net.charabia.jsmoothgen.pe;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.Vector;

/**
 * @author Rodrigo
 */
public class PEResourceDirectory {

  /*
     typedef struct IMAGE_RESOURCE_DIRECTORY {
     uint32_t   Characteristics;
     uint32_t   TimeDateStamp;
     uint16_t    MajorVersion;
     uint16_t    MinorVersion;
     uint16_t    NumberOfNamedEntries;
     uint16_t    NumberOfIdEntries;
     }
    */

  public class DataEntry {
    long OffsetToData; // To update at each change
    long Size;
    long CodePage; // never changed
    long Reserved; // never changed
    ByteBuffer Data;

    public DataEntry(ByteBuffer data) {
      this.Data = data;
      this.Size = data.capacity();
    }

    public DataEntry(FileChannel chan, long offset) throws IOException {
      long orgpos = chan.position();
      chan.position(PEResourceDirectory.this.m_offsetBase + offset);
      ByteBuffer buf = ByteBuffer.allocate(16);
      buf.order(ByteOrder.LITTLE_ENDIAN);
      chan.read(buf);
      buf.position(0);

      OffsetToData = buf.getInt();
      Size = buf.getInt();
      CodePage = buf.getInt();
      Reserved = buf.getInt();

      long datapos = PEResourceDirectory.this.m_master.PointerToRawData + (OffsetToData - PEResourceDirectory.this.m_master.VirtualAddress);
      Data = ByteBuffer.allocate((int)Size);
      Data.order(ByteOrder.LITTLE_ENDIAN);
      chan.position(datapos);
      chan.read(Data);
      Data.position(0);

      chan.position(orgpos);
    }

    public int diskSize() {
      int size = 16 + (int)this.Size;
            if ((size % 4) > 0)
                size += 4 - (size % 4);
      return size;
    }

    public void dump(PrintStream out, int level) {
      indent(level, out);
      out.println("OffsetToData=" + OffsetToData);
      indent(level, out);
      out.println("Size=" + Size);
      indent(level, out);
      out.println("CodePage=" + CodePage);
      indent(level, out);
      out.println("Reserved=" + Reserved);
      indent(level, out);
      out.print("Data={ ");
      for (int i = 0; i < this.Data.capacity(); i++) {
        out.print("" + Integer.toHexString((byte)Data.get()) + ",");
      }
      out.println(" }");

    }

    private void indent(int level, PrintStream out) {
      for (int i = 0; i < level; i++)
        out.print("    ");
    }

    public int buildBuffer(ByteBuffer buffer, long virtualBaseOffset, int dataOffset) {
      // System.out.println("Building Data Entry buffer @ " + buffer.position() + " (" + dataOffset + ")");

      dataOffset = buffer.position() + 16;

      buffer.putInt((int)(dataOffset + virtualBaseOffset));
      buffer.putInt((int)Size);
      buffer.putInt((int)CodePage);
      buffer.putInt((int)Reserved);

      Data.position(0);
      buffer.put(Data);

      dataOffset += Size;
            if ((dataOffset % 4) > 0)
                dataOffset += (4 - (dataOffset % 4));

      return dataOffset;
    }

    public void setData(ByteBuffer data) {
      Data = data;
      Size = data.capacity();
    }
  }

  public class ResourceEntry {
    int Id;
    String Name;

    ImageResourceDirectory Directory;
    DataEntry Data;

    public ResourceEntry(int id, DataEntry data) {
      this.Id = id;
      this.Data = data;
    }

    public ResourceEntry(String name, DataEntry data) {
      this.Name = name;
      this.Data = data;
    }

    public ResourceEntry(int id, ImageResourceDirectory dir) {
      this.Id = id;
      this.Directory = dir;
    }

    public ResourceEntry(String name, ImageResourceDirectory dir) {
      this.Name = name;
      this.Directory = dir;
    }

    public ResourceEntry(FileChannel chan) throws IOException {
      // System.out.println("Resource Entry Offset: " + chan.position());
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.order(ByteOrder.LITTLE_ENDIAN);
      chan.read(buf);
      buf.position(0);
      long orgchanpos = chan.position();
      int val = buf.getInt();
      long offsetToData = buf.getInt();
      // System.out.println("Entry: Val=" + val);
      // System.out.println("       Off=" + offsetToData);

      if (val < 0) {
        val &= 0x7FFFFFFF;
        Name = extractStringAt(chan, val);
        Id = -1;
        // System.out.println("    String at " + val + " = " + Name);
      } else {
        Id = val;
      }

      if (offsetToData < 0) {
        offsetToData &= 0x7FFFFFFF;
        long orgpos = chan.position();
        chan.position(PEResourceDirectory.this.m_offsetBase + offsetToData);
        Directory = new PEResourceDirectory.ImageResourceDirectory(chan);
        chan.position(orgpos);
      } else {
        Data = new DataEntry(chan, offsetToData);
      }
    }

    public String extractStringAt(FileChannel chan, int offset) throws IOException {
      long orgchanpos = chan.position();
      chan.position(PEResourceDirectory.this.m_offsetBase + offset);

      ByteBuffer sizebuf = ByteBuffer.allocate(2);
      sizebuf.order(ByteOrder.LITTLE_ENDIAN);
      chan.read(sizebuf);
      sizebuf.position(0);

      int size = sizebuf.getShort();
      ByteBuffer buffer = ByteBuffer.allocate(size * 2);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      chan.read(buffer);
      buffer.position(0);

      StringBuffer buf = new StringBuffer(size);
      for (int i = 0; i < size; i++) {
        int c = buffer.getShort();
        buf.append((char)c);
      }

      chan.position(orgchanpos);
      return buf.toString();
    }

    public int diskSize() {
            int size = 8;
            if (Name != null)
                size += (Name.length() * 2) + 2;

            if (Directory != null)
                size += Directory.diskSize();
            else if (Data != null)
                size += Data.diskSize();

            if ((size % 4) > 0)
                size += 4 - (size % 4);
            return size;
        }

    public void dump(PrintStream out, int level) {
      indent(level, out);
      if (this.Name != null)
        out.println("Name=" + Name);
      else
        out.println("Id=#" + Id);

      indent(level, out);
      if (this.Directory != null) {
        out.println("ENTRY: DIRECTORY POINTER");
        this.Directory.dump(out, level + 1);
      } else {
        out.println("ENTRY: DATA ENTRY");
        Data.dump(out, level + 1);
      }
    }

    private void indent(int level, PrintStream out) {
      for (int i = 0; i < level; i++)
        out.print("    ");
    }

    public int buildBuffer(ByteBuffer buffer, long virtualBaseOffset, int dataOffset) {
      // System.out.println("Building Resource Entry buffer  " + Name + "/" + Id + " @ " + buffer.position() + " (" + dataOffset + ")");
      if (Name != null) {
        buffer.putInt((int)(dataOffset | 0x80000000));

        int stringoffset = dataOffset;
        ByteBuffer strbuf = ByteBuffer.allocate(Name.length() * 2 + 2);
        strbuf.order(ByteOrder.LITTLE_ENDIAN);

        strbuf.putShort((short)Name.length());
        for (int i = 0; i < Name.length(); i++) {
          strbuf.putShort((short)Name.charAt(i));
        }
        strbuf.position(0);

        long oldpos = buffer.position();
        buffer.position(dataOffset);
        buffer.put(strbuf);
                dataOffset += Name.length() * 2 + 2;
                if ((dataOffset % 4) != 0)
                    dataOffset += 4 - (dataOffset % 4);
                buffer.position((int) oldpos);
      } else {
        buffer.putInt(Id);
      }

      if (Directory != null) {
        buffer.putInt((int)(dataOffset | 0x80000000));

        int oldpos = buffer.position();
        buffer.position(dataOffset);
        int dirsize = Directory.buildBuffer(buffer, virtualBaseOffset);
        dataOffset = dirsize;
        buffer.position(oldpos);

      } else if (Data != null) {
        buffer.putInt(dataOffset);
        int oldpos = buffer.position();
        buffer.position(dataOffset);
        dataOffset = Data.buildBuffer(buffer, virtualBaseOffset, dataOffset);
        buffer.position(oldpos);
      } else {
        throw new RuntimeException("Directory and Data are both null!");
      }

      return dataOffset;
    }
  }

  public class ImageResourceDirectory {
    long Characteristics; // uint32_t
    long TimeDateStamp; // uint32_t
    int MajorVersion; // uint16_t
    int MinorVersion; // uint16_t
    int NumberOfNamedEntries; // uint16_t
    int NumberOfIdEntries; // uint16_t
    Vector NamedEntries = new Vector();
    Vector IdEntries = new Vector();

    public ImageResourceDirectory() {
    }

    public ImageResourceDirectory(FileChannel chan) throws IOException {
      ByteBuffer header = ByteBuffer.allocate(16);
      header.order(ByteOrder.LITTLE_ENDIAN);
      chan.read(header);
      header.position(0);

      Characteristics = header.getInt();
      TimeDateStamp = header.getInt();
      MajorVersion = header.getShort();
      MinorVersion = header.getShort();
      NumberOfNamedEntries = header.getShort();
      NumberOfIdEntries = header.getShort();

      for (int i = 0; i < NumberOfNamedEntries; i++) {
        ResourceEntry re = new ResourceEntry(chan);
        NamedEntries.add(re);
      }
      for (int i = 0; i < NumberOfIdEntries; i++) {
        ResourceEntry re = new ResourceEntry(chan);
        IdEntries.add(re);
      }
    }

    public void addNamedEntry(ResourceEntry entry) {
      this.NamedEntries.add(entry);
    }

    public void addIdEntry(ResourceEntry entry) {
      this.IdEntries.add(entry);
    }

    public void addEntry(ResourceEntry entry) {
      if (entry.Name != null)
        addNamedEntry(entry);
      else
        addIdEntry(entry);
    }

    public void dump(PrintStream out, int level) {
      indent(level, out);
      out.println("Directory: ");
      indent(level, out);
      out.println("Characteristics=" + this.Characteristics);
      indent(level, out);
      out.println("TimeDateStamp=" + this.TimeDateStamp);
      indent(level, out);
      out.println("MajorVersion=" + this.MajorVersion);
      indent(level, out);
      out.println("MinorVersion=" + this.MinorVersion);
      indent(level, out);
      out.println("NumberOfNamedEntries=" + this.NumberOfNamedEntries);
      indent(level, out);
      out.println("NumberOfIdEntries=" + this.NumberOfIdEntries);
      indent(level, out);
      out.println("Named Entries:");
      for (int i = 0; i < NumberOfNamedEntries; i++) {
        ResourceEntry re = (ResourceEntry)NamedEntries.get(i);
        re.dump(out, level + 1);
      }
      indent(level, out);
      out.println("Id Entries:");
      for (int i = 0; i < NumberOfIdEntries; i++) {
        ResourceEntry re = (ResourceEntry)IdEntries.get(i);
        re.dump(out, level + 1);
      }
    }

    private void indent(int level, PrintStream out) {
      for (int i = 0; i < level; i++)
        out.print("    ");
    }

    public int diskSize() {
      int size = 16;
      for (int i = 0; i < this.NamedEntries.size(); i++) {
        ResourceEntry re = (ResourceEntry)NamedEntries.get(i);
        size += re.diskSize();
      }
      for (int i = 0; i < this.IdEntries.size(); i++) {
        ResourceEntry re = (ResourceEntry)IdEntries.get(i);
        size += re.diskSize();
      }

            if ((size % 4) > 0)
                size += 4 - (size % 4);

      return size;
    }

    public int buildBuffer(ByteBuffer buffer, long virtualBaseOffset) {
      // System.out.println("Building Directory Entry buffer @ " + buffer.position());

      buffer.putInt((int)this.Characteristics);
      buffer.putInt((int)this.TimeDateStamp);
      buffer.putShort((short)this.MajorVersion);
      buffer.putShort((short)this.MinorVersion);
      buffer.putShort((short)this.NamedEntries.size());
      buffer.putShort((short)this.IdEntries.size());

      int dataOffset = buffer.position() + (NamedEntries.size() * 8) + (IdEntries.size() * 8);

      for (int i = 0; i < this.NamedEntries.size(); i++) {
        ResourceEntry re = (ResourceEntry)this.NamedEntries.get(i);
        dataOffset = re.buildBuffer(buffer, virtualBaseOffset, dataOffset);
      }

      for (int i = 0; i < this.IdEntries.size(); i++) {
        ResourceEntry re = (ResourceEntry)this.IdEntries.get(i);
        dataOffset = re.buildBuffer(buffer, virtualBaseOffset, dataOffset);
      }

      buffer.position(dataOffset);
      return dataOffset;
    }

    public ResourceEntry getResourceEntry(String name) {
      // If name == null, get the first entry in lexical
      // order. If no entry in lexical order, choose the
      // lowest integer id entry.
      if (name == null) {
        if (NamedEntries.size() > 0) {
          return (PEResourceDirectory.ResourceEntry)NamedEntries.get(0);
        }
        if (IdEntries.size() > 0) {
          return (PEResourceDirectory.ResourceEntry)IdEntries.get(0);
        }
        return null;
      }

      if ((name.length() > 0) && (name.charAt(0) == '#')) {
        try {
          String nb = name.substring(1);
          int i = Integer.parseInt(nb);
          return getResourceEntry(i);
        } catch (Exception exc) {
          exc.printStackTrace();
        }
      }

      for (Iterator i = this.NamedEntries.iterator(); i.hasNext();) {
        ResourceEntry re = (ResourceEntry)i.next();
        if (name.equals(re.Name)) {
          return re;
        }
      }
      return null;
    }

    public ResourceEntry getResourceEntry(int id) {
      for (Iterator i = this.IdEntries.iterator(); i.hasNext();) {
        ResourceEntry re = (ResourceEntry)i.next();
        if (id == re.Id) {
          return re;
        }
      }
      return null;
    }

  }

  PESection m_master;
  PEFile m_file;
  long m_offsetBase;

  PEResourceDirectory.ImageResourceDirectory m_root;

    /**
     * Creates a new instance of PEResourceDirectory
     */
    public PEResourceDirectory(PEFile file, PESection sect) throws IOException {
        m_master = sect;
    m_file = file;
    m_offsetBase = sect.PointerToRawData;
    init();

    // System.out.println("--------\nTOTAL SIZE: " + m_root.diskSize());

    // System.out.println("\n\n");
  }

  public void init() throws IOException {
    // / System.out.println("RESOURCE INIT");
    // System.out.println("   Offset: " + m_master.PointerToRawData);
    FileChannel chan = m_file.getChannel();
    chan.position(m_master.PointerToRawData);
    PEResourceDirectory.ImageResourceDirectory dir = new PEResourceDirectory.ImageResourceDirectory(chan);
    // System.out.println("-----------------\nDUMP\n---------------");
    m_root = dir;

    // dir.dump(System.out, 0);
  }

  public void dump(PrintStream out) {
    m_root.dump(out, 0);
  }

  public int size() {
    return m_root.diskSize();
  }

  public ByteBuffer buildResource(long virtualBaseOffset) {
    // System.out.println("BUILDING RESOURCE / VIRTUAL: " + virtualBaseOffset);
    int resourceSize = m_root.diskSize();
    ByteBuffer resbuf = ByteBuffer.allocate(resourceSize);
    resbuf.order(ByteOrder.LITTLE_ENDIAN);
    resbuf.position(0);
    m_root.buildBuffer(resbuf, virtualBaseOffset);
    return resbuf;
  }

  public PEResourceDirectory.ImageResourceDirectory getRoot() {
    return m_root;
  }

  public boolean replaceManifest(int resourceId, int langId, ByteBuffer data) {
    ResourceEntry catEntry = m_root.getResourceEntry(24);
    if ((catEntry != null) && (catEntry.Directory != null)) {
      ResourceEntry identEntry = catEntry.Directory.getResourceEntry(resourceId);
      if ((identEntry != null) && (identEntry.Directory != null)) {
        ResourceEntry langEntry = identEntry.Directory.getResourceEntry(langId);
        if ((langEntry != null) && (langEntry.Data != null)) {
          DataEntry dataslot = langEntry.Data;
          dataslot.setData(data);
          return true;
        }
      }
    }
    return false;
  }

  public boolean replaceResource(String catId, int resourceId, int langId, ByteBuffer data) {
    ResourceEntry catEntry = m_root.getResourceEntry(catId);
    if ((catEntry != null) && (catEntry.Directory != null)) {
      ResourceEntry identEntry = catEntry.Directory.getResourceEntry(resourceId);
      if ((identEntry != null) && (identEntry.Directory != null)) {
        ResourceEntry langEntry = identEntry.Directory.getResourceEntry(langId);
        if ((langEntry != null) && (langEntry.Data != null)) {
          DataEntry dataslot = langEntry.Data;
          dataslot.setData(data);
          return true;
        }
      }
    }
    return false;
  }

  public void addNewResource(String catId, String resourceId, String languageId, ByteBuffer data) {
    DataEntry dataEntry = new DataEntry(data);
    ResourceEntry languageEntry = buildResourceEntry(languageId, dataEntry);
    ImageResourceDirectory languageDir = new ImageResourceDirectory();

    languageDir.TimeDateStamp = 0x3F2CCF64;
    languageDir.addEntry(languageEntry);

    ResourceEntry identEntry = buildResourceEntry(resourceId, languageDir);

    ImageResourceDirectory identDir = new ImageResourceDirectory();
    identDir.TimeDateStamp = 0x3F2CCF64;
    identDir.addEntry(identEntry);

    ResourceEntry catEntry = buildResourceEntry(catId, identDir);
    m_root.addEntry(catEntry);
  }

  public DataEntry getData(String catId, String resourceId, String langId) {
    ResourceEntry catEntry = m_root.getResourceEntry(catId);
    if ((catEntry != null) && (catEntry.Directory != null)) {
      ResourceEntry identEntry = catEntry.Directory.getResourceEntry(resourceId);
      if ((identEntry != null) && (identEntry.Directory != null)) {
        ResourceEntry langEntry = identEntry.Directory.getResourceEntry(langId);
        if ((langEntry != null) && (langEntry.Data != null)) {
          DataEntry dataslot = langEntry.Data;
          return dataslot;
        }
      }
    }
    return null;
  }

  public ResourceEntry buildResourceEntry(String id, DataEntry data) {
    if ((id.length() > 1) && (id.charAt(0) == '#')) {
      int intid = Integer.parseInt(id.substring(1));
      return new ResourceEntry(intid, data);
    }

    return new ResourceEntry(id, data);
  }


  public ResourceEntry buildResourceEntry(String id, ImageResourceDirectory dir) {
    if ((id.length() > 1) && (id.charAt(0) == '#')) {
      int intid = Integer.parseInt(id.substring(1));
      return new ResourceEntry(intid, dir);
    }

    return new ResourceEntry(id, dir);
  }

}

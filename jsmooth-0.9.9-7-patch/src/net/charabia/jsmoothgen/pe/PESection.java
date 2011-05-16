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
 * PESection.java
 *
 * Created on 29 juillet 2003, 21:34
 */

package net.charabia.jsmoothgen.pe;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * @author Rodrigo
 */
public class PESection implements Cloneable {
    byte[] ANSI_Name; //  	Name of the Section. Can be anything (0)(8BYTES)
    long VirtualSize; // 	The size of the section when it is mapped to memory. Must be a multiple of 4096. (8)(DWORD)
    long VirtualAddress; // 	An rva to where it should be mapped in memory. (12)(DWORD)
    long SizeOfRawData; // 	The size of the section in the PE file. Must be a multiple of 512 (16)(DWORD)
    long PointerToRawData; // 	A file based offset which points to the location of this sections data (20)(DWORD)
    long PointerToRelocations; // 	In EXE's this field is meaningless, and is set 0 (24)(DWORD)
    long PointerToLinenumbers; // 	This is the file-based offset of the line number table. This field is only used for debug purposes, and is usualy set to 0 (28)(DWORD)
    int NumberOfRelocations; // 	In EXE's this field is meaningless, and is set 0 (32)(WORD)
    int NumberOfLinenumbers; // 	The number of line numbers in the line number table for this section. This field is only used for debug purposes, and is usualy set to 0 (34)(WORD)
    long Characteristics; // 	The kind of data stored in this section ie. Code, Data, Import data, Relocation data (36)(DWORD)

    private long m_baseoffset;
    private PEFile m_pe;

    /**
     * Creates a new instance of PESection
     */
    public PESection(PEFile pef, long baseoffset) {
        m_pe = pef;
        m_baseoffset = baseoffset;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public String getName() {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 8; i++)
            buffer.append((char) ANSI_Name[i]);
        return buffer.toString();
    }

    public void read() throws IOException {
        FileChannel ch = m_pe.getChannel();
        ByteBuffer head = ByteBuffer.allocate(40);
        head.order(ByteOrder.LITTLE_ENDIAN);
        ch.position(m_baseoffset);
        ch.read(head);
        head.position(0);

        ANSI_Name = new byte[8];
        for (int i = 0; i < 8; i++)
            ANSI_Name[i] = head.get();

        VirtualSize = head.getInt();
        VirtualAddress = head.getInt();
        SizeOfRawData = head.getInt();
        PointerToRawData = head.getInt();
        PointerToRelocations = head.getInt();
        PointerToLinenumbers = head.getInt();
        NumberOfRelocations = head.getShort();
        NumberOfLinenumbers = head.getShort();
        Characteristics = head.getInt();
    }

    public void dump(PrintStream out) {
        out.println("SECTION:");
        out.println("  Name= "+getName());
        out.println("  VirtualSize= " + VirtualSize + "  // 	The size of the section when it is mapped to memory. Must be a multiple of 4096. (8)(DWORD)");
        out.println("  VirtualAddress= " + VirtualAddress + "   // 	An rva to where it should be mapped in memory. (12)(DWORD)");
        out.println("  SizeOfRawData= " + SizeOfRawData + "   // 	The size of the section in the PE file. Must be a multiple of 512 (16)(DWORD)");
        out.println("  PointerToRawData= " + PointerToRawData + "   // 	A file based offset which points to the location of this sections data (20)(DWORD)");
        out.println("  PointerToRelocations= " + PointerToRelocations + "   // 	In EXE's this field is meaningless, and is set 0 (24)(DWORD)");
        out.println("  PointerToLinenumbers= " + PointerToLinenumbers + "   // 	This is the file-based offset of the line number table. This field is only used for debug purposes, and is usualy set to 0 (28)(DWORD)");
        out.println("  NumberOfRelocations= " + NumberOfRelocations + "   // 	In EXE's this field is meaningless, and is set 0 (32)(WORD)");
        out.println("  NumberOfLinenumbers= " + NumberOfLinenumbers + "   // 	The number of line numbers in the line number table for this section. This field is only used for debug purposes, and is usualy set to 0 (34)(WORD)");
        out.println("  Characteristics= " + Characteristics + "   // 	The kind of data stored in this section ie. Code, Data, Import data, Relocation data (36)(DWORD)");

    }


  public ByteBuffer get() {
    ByteBuffer head = ByteBuffer.allocate(40);
    head.order(ByteOrder.LITTLE_ENDIAN);
    head.position(0);

    for (int i = 0; i < 8; i++)
      head.put((byte)ANSI_Name[i]);

    head.putInt((int)VirtualSize);
    head.putInt((int)VirtualAddress);
    head.putInt((int)SizeOfRawData);
    head.putInt((int)PointerToRawData);
    head.putInt((int)PointerToRelocations);
    head.putInt((int)PointerToLinenumbers);
    head.putShort((short)NumberOfRelocations);
    head.putShort((short)NumberOfLinenumbers);
    head.putInt((int)Characteristics);

    head.position(0);
    return head;
  }

}

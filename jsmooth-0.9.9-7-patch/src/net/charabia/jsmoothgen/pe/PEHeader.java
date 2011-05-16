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
 * PEHeader.java
 *
 * Created on 28 juillet 2003, 21:38
 */

package net.charabia.jsmoothgen.pe;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Vector;

/**
 * @author Rodrigo Reyes
 */
public class PEHeader implements Cloneable {
  private int PeMagic; // 0
  public int Machine; // 4
  public int NumberOfSections; // 6
  public long TimeDateStamp; // 8
  public long PointerToSymbolTable; // C
  public long NumberOfSymbols; // 10
  public int SizeOfOptionalHeader; // 14
  public int Characteristics; // 16

  // Optional Header
  public int Magic; // 18
  public short MajorLinkerVersion; // 1a
  public short MinorLinkerVersion; // 1b
  public long SizeOfCode; // 1c
  public long SizeOfInitializedData; // 20
  public long SizeOfUninitializedData; // 24
  public long AddressOfEntryPoint; // 28
  public long BaseOfCode; // 2c
  public long BaseOfData; // NT additional fields. 30
  public long ImageBase; // 34
  public long SectionAlignment; // 38
  public long FileAlignment; // 3c
  public int MajorOperatingSystemVersion; // 40
  public int MinorOperatingSystemVersion; // 42
  public int MajorImageVersion; // 44
  public int MinorImageVersion; // 46
  public int MajorSubsystemVersion; // 48
  public int MinorSubsystemVersion; // 4a
  public long Reserved1; // 4c
  public long SizeOfImage; // 50
  public long SizeOfHeaders; // 54
  public long CheckSum; // 58
  public int Subsystem; // 5c
  public int DllCharacteristics; // 5e
  public long SizeOfStackReserve; // 60
  public long SizeOfStackCommit; // 64
  public long SizeOfHeapReserve; // 68
  public long SizeOfHeapCommit; // 6c
  public long LoaderFlags; // 70
  public long NumberOfRvaAndSizes; // 74

  public long ExportDirectory_VA; // 78
  public long ExportDirectory_Size; // 7c
  public long ImportDirectory_VA; // 80
  public long ImportDirectory_Size; // 84
  public long ResourceDirectory_VA; // 88
  public long ResourceDirectory_Size; // 8c
  public long ExceptionDirectory_VA; // 90
  public long ExceptionDirectory_Size; // 94
  public long SecurityDirectory_VA; // 98
  public long SecurityDirectory_Size; // 9c
  public long BaseRelocationTable_VA; // a0
  public long BaseRelocationTable_Size; // a4
  public long DebugDirectory_VA; // a8
  public long DebugDirectory_Size; // ac
  public long ArchitectureSpecificData_VA; // b0
  public long ArchitectureSpecificData_Size; // b4
  public long RVAofGP_VA; // b8
  public long RVAofGP_Size; // bc
  public long TLSDirectory_VA; // c0
  public long TLSDirectory_Size; // c4
  public long LoadConfigurationDirectory_VA; // c8
  public long LoadConfigurationDirectory_Size; // cc
  public long BoundImportDirectoryinheaders_VA; // d0
  public long BoundImportDirectoryinheaders_Size; // d4
  public long ImportAddressTable_VA; // d8
  public long ImportAddressTable_Size; // dc
  public long DelayLoadImportDescriptors_VA; // e0
  public long DelayLoadImportDescriptors_Size; // e4
  public long COMRuntimedescriptor_VA; // e8
  public long COMRuntimedescriptor_Size; // ec

  private long m_baseoffset;
  private PEFile m_pe;

    /**
     * Creates a new instance of PEHeader
     */
  public PEHeader(PEFile pef, long baseoffset) {
    m_pe = pef;
    m_baseoffset = baseoffset;
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public void read() throws IOException {
    FileChannel ch = m_pe.getChannel();
    ByteBuffer head = ByteBuffer.allocate(350);
    head.order(ByteOrder.LITTLE_ENDIAN);
    ch.position(m_baseoffset);
    ch.read(head);
    head.position(0);

    PeMagic = head.getInt();
    // System.out.println("MAGIC::: " + pemagic);
    Machine = head.getShort(); // 4
    NumberOfSections = head.getShort(); // 6
    TimeDateStamp = head.getInt(); // 8
    PointerToSymbolTable = head.getInt(); // C
    NumberOfSymbols = head.getInt(); // 10
    SizeOfOptionalHeader = head.getShort(); // 14
    Characteristics = head.getShort(); // 16
    // Optional Header

    Magic = head.getShort(); // 18
    MajorLinkerVersion = head.get(); // 1a
    MinorLinkerVersion = head.get(); // 1b
    SizeOfCode = head.getInt(); // 1c
    SizeOfInitializedData = head.getInt(); // 20
    SizeOfUninitializedData = head.getInt(); // 24
    AddressOfEntryPoint = head.getInt(); // 28
    BaseOfCode = head.getInt(); // 2c
    if (isPe32Plus()) {
      ImageBase = head.getLong(); // 34
    } else {
      BaseOfData = head.getInt(); // // NT additional fields. // 30
      ImageBase = head.getInt(); // 34
    }
    SectionAlignment = head.getInt(); // 38
    FileAlignment = head.getInt(); // 3c
    MajorOperatingSystemVersion = head.getShort(); // 40
    MinorOperatingSystemVersion = head.getShort(); // 42
    MajorImageVersion = head.getShort(); // 44
    MinorImageVersion = head.getShort(); // 46
    MajorSubsystemVersion = head.getShort(); // 48
    MinorSubsystemVersion = head.getShort(); // 4a
    Reserved1 = head.getInt(); // 4c
    SizeOfImage = head.getInt(); // 50
    SizeOfHeaders = head.getInt(); // 54
    CheckSum = head.getInt(); // 58
    Subsystem = head.getShort(); // 5c
    DllCharacteristics = head.getShort(); // 5e
    if (isPe32Plus()) {
      SizeOfStackReserve = head.getLong(); // 60
      SizeOfStackCommit = head.getLong(); // 64
      SizeOfHeapReserve = head.getLong(); // 68
      SizeOfHeapCommit = head.getLong(); // 6c
    } else {
      SizeOfStackReserve = head.getInt(); // 60
      SizeOfStackCommit = head.getInt(); // 64
      SizeOfHeapReserve = head.getInt(); // 68
      SizeOfHeapCommit = head.getInt(); // 6c
    }
    LoaderFlags = head.getInt(); // 70
    NumberOfRvaAndSizes = head.getInt(); // 74

    ExportDirectory_VA = head.getInt(); // 78
    ExportDirectory_Size = head.getInt(); // 7c
    ImportDirectory_VA = head.getInt(); // 80
    ImportDirectory_Size = head.getInt(); // 84
    ResourceDirectory_VA = head.getInt(); // 88
    ResourceDirectory_Size = head.getInt(); // 8c
    ExceptionDirectory_VA = head.getInt(); // 90
    ExceptionDirectory_Size = head.getInt(); // 94
    SecurityDirectory_VA = head.getInt(); // 98
    SecurityDirectory_Size = head.getInt(); // 9c
    BaseRelocationTable_VA = head.getInt(); // a0
    BaseRelocationTable_Size = head.getInt(); // a4
    DebugDirectory_VA = head.getInt(); // a8
    DebugDirectory_Size = head.getInt(); // ac
    ArchitectureSpecificData_VA = head.getInt(); // b0
    ArchitectureSpecificData_Size = head.getInt(); // b4
    RVAofGP_VA = head.getInt(); // b8
    RVAofGP_Size = head.getInt(); // bc
    TLSDirectory_VA = head.getInt(); // c0
    TLSDirectory_Size = head.getInt(); // c4
    LoadConfigurationDirectory_VA = head.getInt(); // c8
    LoadConfigurationDirectory_Size = head.getInt(); // cc
    BoundImportDirectoryinheaders_VA = head.getInt(); // d0
    BoundImportDirectoryinheaders_Size = head.getInt(); // d4
    ImportAddressTable_VA = head.getInt(); // d8
    ImportAddressTable_Size = head.getInt(); // dc
    DelayLoadImportDescriptors_VA = head.getInt(); // e0
    DelayLoadImportDescriptors_Size = head.getInt(); // e4
    COMRuntimedescriptor_VA = head.getInt(); // e8
    COMRuntimedescriptor_Size = head.getInt(); // ec
  }

  public void dump(PrintStream out) {
    out.println("HEADER:");
    out.println("int  Machine=" + Machine + " //  4");
    out.println("int  NumberOfSections=" + NumberOfSections + "     //  6");
    out.println("long   TimeDateStamp=" + TimeDateStamp + " //  8");
    out.println("long   PointerToSymbolTable=" + PointerToSymbolTable + "     //  C");
    out.println("long   NumberOfSymbols=" + NumberOfSymbols + " // 10");
    out.println("int  SizeOfOptionalHeader=" + SizeOfOptionalHeader + "     // 14");
    out.println("int  Characteristics=" + Characteristics + " // 16");
    // Optional Header

    out.println("int    Magic=" + Magic + "     // 18");
    out.println("short   MajorLinkerVersion=" + MajorLinkerVersion + "     // 1a");
    out.println("short   MinorLinkerVersion=" + MinorLinkerVersion + " // 1b");
    out.println("long   SizeOfCode=" + SizeOfCode + "     // 1c");
    out.println("long   SizeOfInitializedData=" + SizeOfInitializedData + " // 20");
    out.println("long   SizeOfUninitializedData=" + SizeOfUninitializedData + "     // 24");
    out.println("long   AddressOfEntryPoint=" + AddressOfEntryPoint + " // 28");
    out.println("long   BaseOfCode=" + BaseOfCode + "     // 2c");
    out.println("long   BaseOfData=" + BaseOfData + "    //    // NT additional fields. // 30");
    //    
    out.println("long   ImageBase=" + ImageBase + "     // 34");
    out.println("long   SectionAlignment=" + SectionAlignment + " // 38");
    out.println("long   FileAlignment=" + FileAlignment + "     // 3c");
    out.println("int    MajorOperatingSystemVersion=" + MajorOperatingSystemVersion + " // 40");
    out.println("int    MinorOperatingSystemVersion=" + MinorOperatingSystemVersion + "     // 42");
    out.println("int    MajorImageVersion=" + MajorImageVersion + " // 44");
    out.println("int    MinorImageVersion=" + MinorImageVersion + "     // 46");
    out.println("int    MajorSubsystemVersion=" + MajorSubsystemVersion + " // 48");
    out.println("int    MinorSubsystemVersion=" + MinorSubsystemVersion + "     // 4a");
    out.println("long   Reserved1=" + Reserved1 + "     // 4c");
    out.println("long   SizeOfImage=" + SizeOfImage + " // 50");
    out.println("long   SizeOfHeaders=" + SizeOfHeaders + "     // 54");
    out.println("long   CheckSum=" + CheckSum + "     // 58");
    out.println("int    Subsystem=" + Subsystem + " // 5c");
    out.println("int    DllCharacteristics=" + DllCharacteristics + "     // 5e");
    out.println("long   SizeOfStackReserve=" + SizeOfStackReserve + " // 60");
    out.println("long   SizeOfStackCommit=" + SizeOfStackCommit + "     // 64");
    out.println("long   SizeOfHeapReserve=" + SizeOfHeapReserve + " // 68");
    out.println("long   SizeOfHeapCommit=" + SizeOfHeapCommit + "     // 6c");
    out.println("long   LoaderFlags=" + LoaderFlags + " // 70");
    out.println("long   NumberOfRvaAndSizes=" + NumberOfRvaAndSizes + " // 74");

    out.println("long ExportDirectory_VA=" + ExportDirectory_VA + " // 78");
    out.println("long ExportDirectory_Size=" + ExportDirectory_Size + " // 7c");
    out.println("long ImportDirectory_VA=" + ImportDirectory_VA + " // 80");
    out.println("long ImportDirectory_Size=" + ImportDirectory_Size + " // 84");
    out.println("long ResourceDirectory_VA=" + ResourceDirectory_VA + " // 88");
    out.println("long ResourceDirectory_Size=" + ResourceDirectory_Size + " // 8c");
    out.println("long ExceptionDirectory_VA=" + ExceptionDirectory_VA + " // 90");
    out.println("long ExceptionDirectory_Size=" + ExceptionDirectory_Size + " // 94");
    out.println("long SecurityDirectory_VA=" + SecurityDirectory_VA + " // 98");
    out.println("long SecurityDirectory_Size=" + SecurityDirectory_Size + " // 9c");
    out.println("long BaseRelocationTable_VA=" + BaseRelocationTable_VA + " // a0");
    out.println("long BaseRelocationTable_Size=" + BaseRelocationTable_Size + " // a4");
    out.println("long DebugDirectory_VA=" + DebugDirectory_VA + " // a8");
    out.println("long DebugDirectory_Size=" + DebugDirectory_Size + " // ac");
    out.println("long ArchitectureSpecificData_VA=" + ArchitectureSpecificData_VA + " // b0");
    out.println("long ArchitectureSpecificData_Size=" + ArchitectureSpecificData_Size + " // b4");
    out.println("long RVAofGP_VA=" + RVAofGP_VA + " // b8");
    out.println("long RVAofGP_Size=" + RVAofGP_Size + " // bc");
    out.println("long TLSDirectory_VA=" + TLSDirectory_VA + " // c0");
    out.println("long TLSDirectory_Size=" + TLSDirectory_Size + " // c4");
    out.println("long LoadConfigurationDirectory_VA=" + LoadConfigurationDirectory_VA + " // c8");
    out.println("long LoadConfigurationDirectory_Size=" + LoadConfigurationDirectory_Size + " // cc");
    out.println("long BoundImportDirectoryinheaders_VA=" + BoundImportDirectoryinheaders_VA + " // d0");
    out.println("long BoundImportDirectoryinheaders_Size=" + BoundImportDirectoryinheaders_Size + " // d4");
    out.println("long ImportAddressTable_VA=" + ImportAddressTable_VA + " // d8");
    out.println("long ImportAddressTable_Size=" + ImportAddressTable_Size + " // dc");
    out.println("long DelayLoadImportDescriptors_VA=" + DelayLoadImportDescriptors_VA + " // e0");
    out.println("long DelayLoadImportDescriptors_Size=" + DelayLoadImportDescriptors_Size + " // e4");
    out.println("long COMRuntimedescriptor_VA=" + COMRuntimedescriptor_VA + " // e8");
    out.println("long COMRuntimedescriptor_Size=" + COMRuntimedescriptor_Size + " // ec");
  }

  public ByteBuffer get() {
    ByteBuffer head = ByteBuffer.allocate(16 + this.SizeOfOptionalHeader);
    head.order(ByteOrder.LITTLE_ENDIAN);
    head.position(0);

    head.putInt(PeMagic);

    head.putShort((short)Machine); // 4
    head.putShort((short)NumberOfSections); // 6
    head.putInt((int)TimeDateStamp); // 8
    head.putInt((int)PointerToSymbolTable); // C
    head.putInt((int)NumberOfSymbols); // 10
    head.putShort((short)SizeOfOptionalHeader); // 14
    head.putShort((short)Characteristics); // 16
    // Optional Header

    head.putShort((short)Magic); // 18
    head.put((byte)MajorLinkerVersion); // 1a
    head.put((byte)MinorLinkerVersion); // 1b
    head.putInt((int)SizeOfCode); // 1c
    head.putInt((int)SizeOfInitializedData); // 20
    head.putInt((int)SizeOfUninitializedData); // 24
    head.putInt((int)AddressOfEntryPoint); // 28
    head.putInt((int)BaseOfCode); // 2c
    if (isPe32Plus()) {
      head.putLong(ImageBase); // 34
    } else {
      head.putInt((int)BaseOfData); // // NT additional fields. // 30
      head.putInt((int)ImageBase); // 34
    }
    head.putInt((int)SectionAlignment); // 38
    head.putInt((int)FileAlignment); // 3c
    head.putShort((short)MajorOperatingSystemVersion); // 40
    head.putShort((short)MinorOperatingSystemVersion); // 42
    head.putShort((short)MajorImageVersion); // 44
    head.putShort((short)MinorImageVersion); // 46
    head.putShort((short)MajorSubsystemVersion); // 48
    head.putShort((short)MinorSubsystemVersion); // 4a
    head.putInt((int)Reserved1); // 4c
    head.putInt((int)SizeOfImage); // 50
    head.putInt((int)SizeOfHeaders); // 54
    head.putInt((int)CheckSum); // 58
    head.putShort((short)Subsystem); // 5c
    head.putShort((short)DllCharacteristics); // 5e
    if (isPe32Plus()) {
      head.putLong(SizeOfStackReserve); // 60
      head.putLong(SizeOfStackCommit); // 64
      head.putLong(SizeOfHeapReserve); // 68
      head.putLong(SizeOfHeapCommit); // 6c
      } else {
      head.putInt((int)SizeOfStackReserve); // 60
      head.putInt((int)SizeOfStackCommit); // 64
      head.putInt((int)SizeOfHeapReserve); // 68
      head.putInt((int)SizeOfHeapCommit); // 6c    
      }
    head.putInt((int)LoaderFlags); // 70
    head.putInt((int)NumberOfRvaAndSizes); // 74

    head.putInt((int)ExportDirectory_VA); // 78
    head.putInt((int)ExportDirectory_Size); // 7c
    head.putInt((int)ImportDirectory_VA); // 80
    head.putInt((int)ImportDirectory_Size); // 84
    head.putInt((int)ResourceDirectory_VA); // 88
    head.putInt((int)ResourceDirectory_Size); // 8c
    head.putInt((int)ExceptionDirectory_VA); // 90
    head.putInt((int)ExceptionDirectory_Size); // 94
    head.putInt((int)SecurityDirectory_VA); // 98
    head.putInt((int)SecurityDirectory_Size); // 9c
    head.putInt((int)BaseRelocationTable_VA); // a0
    head.putInt((int)BaseRelocationTable_Size); // a4
    head.putInt((int)DebugDirectory_VA); // a8
    head.putInt((int)DebugDirectory_Size); // ac
    head.putInt((int)ArchitectureSpecificData_VA); // b0
    head.putInt((int)ArchitectureSpecificData_Size); // b4
    head.putInt((int)RVAofGP_VA); // b8
    head.putInt((int)RVAofGP_Size); // bc
    head.putInt((int)TLSDirectory_VA); // c0
    head.putInt((int)TLSDirectory_Size); // c4
    head.putInt((int)LoadConfigurationDirectory_VA); // c8
    head.putInt((int)LoadConfigurationDirectory_Size); // cc
    head.putInt((int)BoundImportDirectoryinheaders_VA); // d0
    head.putInt((int)BoundImportDirectoryinheaders_Size); // d4
    head.putInt((int)ImportAddressTable_VA); // d8
    head.putInt((int)ImportAddressTable_Size); // dc
    head.putInt((int)DelayLoadImportDescriptors_VA); // e0
    head.putInt((int)DelayLoadImportDescriptors_Size); // e4
    head.putInt((int)COMRuntimedescriptor_VA); // e8
    head.putInt((int)COMRuntimedescriptor_Size); // ec

    head.position(0);
    return head;
  }

  public void updateVAAndSize(Vector oldsections, Vector newsections) {
    long codebase = findNewVA(this.BaseOfCode, oldsections, newsections);
    long codesize = findNewSize(this.BaseOfCode, oldsections, newsections);
    // System.out.println("New BaseOfCode=" + codebase + " (size=" + codesize + ")");
    this.BaseOfCode = codebase;
    this.SizeOfCode = codesize;

    this.AddressOfEntryPoint = findNewVA(this.AddressOfEntryPoint, oldsections, newsections);

    long database = findNewVA(this.BaseOfData, oldsections, newsections);
    long datasize = findNewSize(this.BaseOfData, oldsections, newsections);
    // System.out.println("New BaseOfData=" + database + " (size=" + datasize + ")");
    this.BaseOfData = database;

    long imagesize = 0;
    for (int i = 0; i < newsections.size(); i++) {
      PESection sect = (PESection)newsections.get(i);
      long curmax = sect.VirtualAddress + sect.VirtualSize;
            if (curmax > imagesize)
                imagesize = curmax;
    }
    this.SizeOfImage = imagesize;

    // this.SizeOfInitializedData = datasize;

    ExportDirectory_Size = findNewSize(ExportDirectory_VA, oldsections, newsections);
    ExportDirectory_VA = findNewVA(ExportDirectory_VA, oldsections, newsections);
    ImportDirectory_Size = findNewSize(ImportDirectory_VA, oldsections, newsections);
    ImportDirectory_VA = findNewVA(ImportDirectory_VA, oldsections, newsections);
    ResourceDirectory_Size = findNewSize(ResourceDirectory_VA, oldsections, newsections);
    ResourceDirectory_VA = findNewVA(ResourceDirectory_VA, oldsections, newsections);
    ExceptionDirectory_Size = findNewSize(ExceptionDirectory_VA, oldsections, newsections);
    ExceptionDirectory_VA = findNewVA(ExceptionDirectory_VA, oldsections, newsections);
    SecurityDirectory_Size = findNewSize(SecurityDirectory_VA, oldsections, newsections);
    SecurityDirectory_VA = findNewVA(SecurityDirectory_VA, oldsections, newsections);
    BaseRelocationTable_Size = findNewSize(BaseRelocationTable_VA, oldsections, newsections);
    BaseRelocationTable_VA = findNewVA(BaseRelocationTable_VA, oldsections, newsections);
    DebugDirectory_Size = findNewSize(DebugDirectory_VA, oldsections, newsections);
    DebugDirectory_VA = findNewVA(DebugDirectory_VA, oldsections, newsections);
    ArchitectureSpecificData_Size = findNewSize(ArchitectureSpecificData_VA, oldsections, newsections);
    ArchitectureSpecificData_VA = findNewVA(ArchitectureSpecificData_VA, oldsections, newsections);
    RVAofGP_Size = findNewSize(RVAofGP_VA, oldsections, newsections);
    RVAofGP_VA = findNewVA(RVAofGP_VA, oldsections, newsections);
    TLSDirectory_Size = findNewSize(TLSDirectory_VA, oldsections, newsections);
    TLSDirectory_VA = findNewVA(TLSDirectory_VA, oldsections, newsections);
    LoadConfigurationDirectory_Size = findNewSize(LoadConfigurationDirectory_VA, oldsections, newsections);
    LoadConfigurationDirectory_VA = findNewVA(LoadConfigurationDirectory_VA, oldsections, newsections);
    BoundImportDirectoryinheaders_Size = findNewSize(BoundImportDirectoryinheaders_VA, oldsections, newsections);
    BoundImportDirectoryinheaders_VA = findNewVA(BoundImportDirectoryinheaders_VA, oldsections, newsections);
    ImportAddressTable_Size = findNewSize(ImportAddressTable_VA, oldsections, newsections);
    ImportAddressTable_VA = findNewVA(ImportAddressTable_VA, oldsections, newsections);
    DelayLoadImportDescriptors_Size = findNewSize(DelayLoadImportDescriptors_VA, oldsections, newsections);
    DelayLoadImportDescriptors_VA = findNewVA(DelayLoadImportDescriptors_VA, oldsections, newsections);
    COMRuntimedescriptor_Size = findNewSize(COMRuntimedescriptor_VA, oldsections, newsections);
    COMRuntimedescriptor_VA = findNewVA(COMRuntimedescriptor_VA, oldsections, newsections);
  }

  public boolean isPe32Plus() {
    return Magic == 523;
  }

  private long findNewVA(long current, Vector oldsections, Vector newsections) {
    for (int i = 0; i < oldsections.size(); i++) {
      PESection sect = (PESection)oldsections.get(i);
      if (sect.VirtualAddress == current) {
        PESection newsect = (PESection)newsections.get(i);

        // System.out.println("Translation VA found for " + current + " = " + i + " (" +newsect.VirtualAddress + ")=" + newsect.getName());
        return newsect.VirtualAddress;
      } else if ((current > sect.VirtualAddress) && (current < (sect.VirtualAddress + sect.VirtualSize))) {
        long diff = current - sect.VirtualAddress;
        PESection newsect = (PESection)newsections.get(i);
                //			System.out.println("Translation VA found INSIDE " + current + " = " + i + " (" +newsect.VirtualAddress + ")=" + newsect.getName());
        return newsect.VirtualAddress + diff;
      }
    }


    return 0;
  }

  private long findNewSize(long current, Vector oldsections, Vector newsections) {
    for (int i = 0; i < oldsections.size(); i++) {
      PESection sect = (PESection)oldsections.get(i);
      if (sect.VirtualAddress == current) {
        PESection newsect = (PESection)newsections.get(i);
                //			System.out.println("Translation Size found for " + current + " = " + i + " (" +newsect.VirtualAddress + ")=" + newsect.getName());
        // System.out.println("         Old size " + sect.VirtualSize + " vs new size " + newsect.VirtualSize);
        return newsect.VirtualSize;
      }
    }
    return 0;
  }

}

/*
  JSmooth: a VM wrapper toolkit for Windows
  Copyright (C) 2003-2007 Rodrigo Reyes <reyes@charabia.net>

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Library General Public
  License as published by the Free Software Foundation; either
  version 2 of the License, or (at your option) any later version.
  
  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Library General Public License for more details.
  
  You should have received a copy of the GNU Library General Public
  License along with this library; if not, write to the Free
  Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
  
*/

#ifndef _JVMREGISTRYLOOKUP_H_
#define _JVMREGISTRYLOOKUP_H_

#include <vector>
#include <algorithm>

#include "SunJVMLauncher.h"
#include "JVMRegistryLookup.h"

/** Utility class that scans the windows registry for installed JRE.
 *
 * @author Rodrigo Reyes <reyes@charabia.net>
 */

class JVMRegistryLookup
{
 public:
  /**
   * Scans the Windows Registry for the Java Runtime Environments. If
   * no JRE is found, an empty vector is returned.
   *
   * @return a vector containing the JRE found.
   */ 
  static vector<SunJVMLauncher> lookupJVM();

 private:
  static vector<SunJVMLauncher> lookup(HKEY key, const string& path);

};


#endif

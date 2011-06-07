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

#include "JVMRegistryLookup.h"

struct jvmsorter_dec : public binary_function<const SunJVMLauncher&, const SunJVMLauncher&, bool>
{
  bool operator()(const SunJVMLauncher& s1, const SunJVMLauncher& s2)
  {
    return s2 < s1;
  }
};

vector<SunJVMLauncher> JVMRegistryLookup::lookupJVM()
{
  vector<SunJVMLauncher> res = JVMRegistryLookup::lookup(HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\Java Runtime Environment");

  vector<SunJVMLauncher> res2 = JVMRegistryLookup::lookup(HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\Java Development Kit");

  for (vector<SunJVMLauncher>::iterator i = res2.begin(); i != res2.end(); i++)
    {
      res.insert(res.end(), *i);
    }

  sort(res.begin(), res.end(), jvmsorter_dec() );

  return res;
}

vector<SunJVMLauncher> JVMRegistryLookup::lookup(HKEY key, const string& path)
{
  vector<SunJVMLauncher> result;

  HKEY hKey;
  LONG error = ERROR_SUCCESS;
  LONG val = RegOpenKeyEx(key, path.c_str(), 0, KEY_READ, &hKey);

  unsigned long buffersize = 1024;
  char buffer[1024];

  for (int i=0; RegEnumKey(hKey, i, buffer, buffersize) == ERROR_SUCCESS; i++)
    {
      int v = i;
      HKEY version;
      int foundver = RegOpenKeyEx(hKey, buffer, 0, KEY_READ, &version);
      if (foundver == ERROR_SUCCESS)
	{
	  std::string versionname(buffer);
	  HKEY runtimelib;
	  unsigned long datatype;
	  std::string runtimelibstr = "";
	  std::string javahomestr = "";

	  unsigned char *b = (unsigned char*)buffer;
	  buffersize = 1024;
	  int foundlib = RegQueryValueEx(version, TEXT("RuntimeLib"), 
					 NULL, 
					 &datatype, 
					 b, 
					 &buffersize);
			
	  if (foundlib == ERROR_SUCCESS)
            {
	      runtimelibstr = buffer;
            }

	  b = (unsigned char*)buffer;
	  buffersize = 1024;
	  int foundhome = RegQueryValueEx(version, TEXT("JavaHome"),
					  NULL, 
					  &datatype, 
					  b, 
					  &buffersize);
	  if (foundhome == ERROR_SUCCESS)
            {
	      javahomestr = buffer;
            }								

	  if ((runtimelibstr.length()>0) || (javahomestr.length()>0))
	    {
	      SunJVMLauncher vm;
	      vm.RuntimeLibPath = runtimelibstr;
	      vm.JavaHome = javahomestr;
	      vm.VmVersion = Version(versionname);
	      result.push_back(vm);
				    
	      char buffer[244];
	      sprintf(buffer, "V(%d)(%d)(%d)", vm.VmVersion.getMajor(), vm.VmVersion.getMinor(), vm.VmVersion.getSubMinor());
	      DEBUG(std::string("JVM Lookup: found VM (") + buffer + ") in registry.");
	    } 
	}

    }
    
  return result;
}



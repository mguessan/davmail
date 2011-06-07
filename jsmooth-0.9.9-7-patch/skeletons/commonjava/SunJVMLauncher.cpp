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

#include "SunJVMLauncher.h"
#include "Process.h"

#include "SunJVMDLL.h"
#include "JArgs.h"
#include "JClassProxy.h"

extern "C" {
  static jint JNICALL  myvprintf(FILE *fp, const char *format, va_list args)
  {
        DEBUG("MYPRINTF");
  }
  void JNICALL myexit(jint code)
  {
       DEBUG("EXIT CALLED FROM JVM DLL");        
       exit(code); 
  }
}

std::string SunJVMLauncher::toString() const
{
    return "<" + JavaHome + "><" + RuntimeLibPath + "><" + VmVersion.toString() + ">";
}

bool SunJVMLauncher::run(ResourceManager& resource, const string& origin, bool justInstanciate)
{
  DEBUG("Running now " + this->toString() + ", instanciate=" + (justInstanciate?"yes":"no"));

  Version max(resource.getProperty(ResourceManager:: KEY_MAXVERSION));
  Version min(resource.getProperty(ResourceManager:: KEY_MINVERSION));

  // patch proposed by zregvart: if you're using bundeled JVM, you
  // apriori know the version bundled and we can trust. The version
  // check is therefore unrequired.
  if (origin != "bundled") {
      
    if (VmVersion.isValid() == false)
      {
	DEBUG("No version identified for " + toString());
	SunJVMExe exe(this->JavaHome);
	VmVersion = exe.guessVersion();
	DEBUG("Version found: " + VmVersion.toString());
      }

    if (VmVersion.isValid() == false)
      {
	DEBUG("No version found, can't instanciate DLL without it");
	return false;
      }
  
    if (min.isValid() && (VmVersion < min))
      return false;
      
    if (max.isValid() && (max < VmVersion))
      return false;
  }

  DEBUG("Launching " + toString());
    
  //
  // search for the dll if it's not set in the registry, or if the
  // file doesn't exist
  //
  if ( (this->JavaHome.size()>0)
       && ((this->RuntimeLibPath.size() == 0) || (!FileUtils::fileExists(this->RuntimeLibPath))) )
    {
      std::string assump = FileUtils::concFile(this->JavaHome, "jre\\bin\\jvm.dll");
      std::string assump2 = FileUtils::concFile(this->JavaHome, "jre\\bin\\server\\jvm.dll"); // for JRE 1.5+
      std::string assump3 = FileUtils::concFile(this->JavaHome, "jre\\bin\\client\\jvm.dll"); // for JRE 1.5+
      std::string assump4 = FileUtils::concFile(this->JavaHome, "bin\\javai.dll"); // For JRE 1.1

      if (FileUtils::fileExists(assump))
	this->RuntimeLibPath = assump;
      else if (FileUtils::fileExists(assump2))
	this->RuntimeLibPath = assump2;
      else if (FileUtils::fileExists(assump3))
	this->RuntimeLibPath = assump3;
      else if (FileUtils::fileExists(assump4))
	this->RuntimeLibPath = assump4;
      else
	{
	  vector<string> dlls = FileUtils::recursiveSearch(this->JavaHome, string("jvm.dll"));
	  if (dlls.size() > 0)
	    this->RuntimeLibPath = dlls[0];
	}
    }

  if (FileUtils::fileExists(this->RuntimeLibPath))
    {
      DEBUG("RuntimeLibPath used: " + this->RuntimeLibPath);      
      Version v = this->VmVersion;
      if (!v.isValid())
	{
	  v = min;
	  if (!v.isValid())
	    v = Version("1.2.0");
	  DEBUG("No version, trying with " + v.toString());
	}

      m_dllrunner = new SunJVMDLL(this->RuntimeLibPath, v);
      // set up the vm parameters...
      setupVM(resource, m_dllrunner);

      if (justInstanciate)
	return m_dllrunner->instanciate();
      else
	return m_dllrunner->run(resource.getProperty(ResourceManager::KEY_MAINCLASSNAME),
				true);
    }

  return false;
}

bool SunJVMLauncher::runProc(ResourceManager& resource, bool useConsole, const string& origin)
{
  std::string classname = resource.getProperty(string(ResourceManager::KEY_MAINCLASSNAME));  

  if (VmVersion.isValid() == false)
    {
      DEBUG("No version identified for " + toString());
      SunJVMExe exe(this->JavaHome);
      VmVersion = exe.guessVersion();
      DEBUG("Version found: " + VmVersion.toString());
    }

  if (VmVersion.isValid() == false)
    return false;

  if (origin != "bundled") {
      
      Version max(resource.getProperty(ResourceManager:: KEY_MAXVERSION));
      Version min(resource.getProperty(ResourceManager:: KEY_MINVERSION));
  
      if (min.isValid() && (VmVersion < min))
        return false;
      
      if (max.isValid() && (max < VmVersion))
	return false;
  }  

  SunJVMExe exe(this->JavaHome, VmVersion);   
  setupVM(resource, &exe);
  if (exe.run(classname, useConsole))
    {
      m_exitCode = exe.getExitCode();
      return true;
    }
  return false;
}

bool SunJVMLauncher::setupVM(ResourceManager& resource, JVMBase* vm)
{
  //
  // create the properties array
  const vector<JavaProperty>& jprops = resource.getJavaProperties();
  for (int i=0; i<jprops.size(); i++)
    {
      vm->addProperty(jprops[i]);
    }

  if (resource.getProperty("maxheap") != "")
    vm->setMaxHeap( StringUtils::parseInt(resource.getProperty("maxheap") ));
  
  if (resource.getProperty("initialheap") != "")
    vm->setInitialHeap( StringUtils::parseInt(resource.getProperty("initialheap") ));

  if (resource.getProperty("vmparameter") != "")
	vm->setVmParameter( resource.getProperty("vmparameter") );
  
  if (resource.useEmbeddedJar())
    {
      std::string embj = resource.saveJarInTempFile();
      vm->addPathElement(embj);
    }
  
  std::string jnijar = resource.saveJnismoothInTempFile();
  if (jnijar != "")
    vm->addPathElement(jnijar);

  //
  // Define the classpath
  std::vector<std::string> classpath = resource.getNormalizedClassPathVector();
  for (int i=0; i<classpath.size(); i++)
    {
      vm->addPathElement(classpath[i]);
    }

  //
  // Defines the arguments passed to the java application
  //  vm->setArguments(resource.getProperty(ResourceManager::KEY_ARGUMENTS));
  std::vector<std::string> args = resource.getArguments();
  for (int i=0; i<args.size(); i++)
    {
      vm->addArgument(args[i]);
    }

}

SunJVMDLL* SunJVMLauncher::getDLL()
{
  return m_dllrunner;
}

bool operator < (const SunJVMLauncher& v1, const SunJVMLauncher& v2)
{
  return v1.VmVersion < v2.VmVersion;
}

int SunJVMLauncher::getExitCode()
{
  return m_exitCode;
}


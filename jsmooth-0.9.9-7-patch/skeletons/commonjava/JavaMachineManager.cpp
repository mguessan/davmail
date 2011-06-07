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

#include "JavaMachineManager.h"


JavaMachineManager::JavaMachineManager(ResourceManager& resman): m_resman(resman)
{
  DEBUG("Now searching the JVM installed on the system...");

    m_registryVms = JVMRegistryLookup::lookupJVM();
    m_javahomeVm = JVMEnvVarLookup::lookupJVM("JAVA_HOME");
    m_jrepathVm = JVMEnvVarLookup::lookupJVM("JRE_HOME");
    m_jdkpathVm = JVMEnvVarLookup::lookupJVM("JDK_HOME");
    m_exitCode = 0;
    m_useConsole = true;
    m_acceptExe = true;
    m_acceptDLL = true;
    m_preferDLL = false;

    if (resman.getProperty("bundledvm").length() > 0)
    {
        string bjvm = resman.getProperty("bundledvm");
        DEBUG("Found a vm bundled with the application: (" + bjvm + ")");
        m_localVMenabled = true;
	std::string home = FileUtils::concFile(resman.getCurrentDirectory(), bjvm);
        m_localVM.JavaHome = home;
    } else
    {
        m_localVMenabled = false;
    }
    DEBUG("Current directory is " + resman.getCurrentDirectory());
}

bool JavaMachineManager::run()
{
  string vmorder = m_resman.getProperty(ResourceManager::KEY_JVMSEARCH);

  if (m_localVMenabled)
    {
      if (internalRun(m_localVM, "bundled"))
	{
	  return true;
	}

//       DEBUG("Trying to use bundled VM " + m_localVM.JavaHome);        
//       if (m_localVM.runProc(m_resman, m_useConsole, "bundled"))
// 	{
// 	  m_exitCode = m_localVM.getExitCode();
// 	  return true;
// 	}
//       if (m_localVM.run(m_resman, "bundled"))
// 	return true;
    }

  if (vmorder == "")
    {
      vmorder = "registry;jdkpath;jrepath;javahome;jview;exepath";
    }
    
  DEBUG("JSmooth will now try to use the VM in the following order: " + vmorder);
    
  vector<string> jvmorder = StringUtils::split(vmorder, ";,", "");

  Version max(m_resman.getProperty(ResourceManager:: KEY_MAXVERSION));
  Version min(m_resman.getProperty(ResourceManager:: KEY_MINVERSION));

  for (vector<string>::const_iterator i = jvmorder.begin(); i != jvmorder.end(); i++)
    {
      DEBUG("------------------------------");

      if (*i == "registry")
        {
	  DEBUG("Trying to use a JVM defined in the registry (" + StringUtils::toString(m_registryVms.size()) + " available)");
	  string vms = "VM will be tried in the following order: ";
	  for (int i=0; i<m_registryVms.size(); i++)
	    {
	      vms += m_registryVms[i].VmVersion.toString();
	      vms += ";";
	    }
	  DEBUG(vms);

	  for (int i=0; i<m_registryVms.size(); i++)
            {
	      DEBUG("- Trying registry: " + m_registryVms[i].toString());

	      if (internalRun(m_registryVms[i], "registry") == true)
		return true;

	      DEBUG("Couldn't use this VM, now trying something else");
            }
        } 
      else if ((*i == "jview") && m_acceptExe)
	{
	  DEBUG("- Trying to launch the application with JVIEW");
	  if (m_jviewVm.runProc(m_resman, ! m_useConsole))
	    {
	      return true;
	    }

	} 
      else if ((*i == "javahome") && (m_javahomeVm.size()>0))
	{
	  DEBUG("- Trying to use JAVAHOME");
	  if (internalRun(m_javahomeVm[0], "jrehome"))
	    return true;
	} 
      else if ((*i == "jrepath") && (m_jrepathVm.size()>0))
	{
	  DEBUG("- Trying to use JRE_HOME");
	  if (internalRun(m_jrepathVm[0], "jrehome"))
	    return true;
	} 
      else if (*i == "exepath")
	{
	  DEBUG("- Trying to use PATH");

	  SunJVMLauncher launcher;
	  if (launcher.runProc(m_resman, m_useConsole, "path"))
	    {
	      m_exitCode = m_localVM.getExitCode();
	      return true;
	    }
	}
    }

  DEBUG("Couldn't run any suitable JVM!");
  return false;
}


bool JavaMachineManager::internalRun(SunJVMLauncher& launcher, const string& org)
{
  if (m_acceptDLL && m_preferDLL)
    {
      if (launcher.run(m_resman, org))
	return true;
    }

  if (m_acceptExe)
    {
      if (launcher.runProc(m_resman, m_useConsole, org))
	{
	  m_exitCode = launcher.getExitCode();
	  return true;
	}
    }

  if (m_acceptDLL && !m_preferDLL)
    {
      if (launcher.run(m_resman, org))
	return true;      
    }

  return false;
}



SunJVMLauncher* JavaMachineManager::runDLLFromRegistry(bool justInstanciate)
{
  string vms = "DLL VM will be tried in the following order: ";
  for (int i=0; i<m_registryVms.size(); i++)
    {
      vms += m_registryVms[i].VmVersion.toString();
      vms += ";";
    }
  DEBUG(vms);

  for (int i=0; i<m_registryVms.size(); i++)
    {
      DEBUG("- Trying registry: " + m_registryVms[i].toString());

      bool res = m_registryVms[i].run(m_resman, "registry", justInstanciate);
      
      if (res)
	return &m_registryVms[i];
    }
    
  if (m_localVMenabled) {
    if (m_localVM.run(m_resman, "bundled", justInstanciate)) {
      return &m_localVM;
    } else {
      DEBUG("Bundled VM launch failed");
    }
  }


  return NULL;
}

void JavaMachineManager::setUseConsole(bool useConsole)
{
  m_useConsole = useConsole;
}

void JavaMachineManager::setAcceptExe(bool acceptExe)
{
  m_acceptExe = acceptExe;
}

void JavaMachineManager::setAcceptDLL(bool acceptDLL)
{
  m_acceptDLL = acceptDLL;;
}

void JavaMachineManager::setPreferDLL(bool prefDLL)
{
  m_preferDLL = prefDLL;
}

int JavaMachineManager::getExitCode()
{
  return m_exitCode;
}

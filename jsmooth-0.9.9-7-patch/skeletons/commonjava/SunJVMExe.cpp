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

#include "SunJVMExe.h"

#include <vector>
#include <string>
#include "Process.h"
#include "FileUtils.h"

SunJVMExe::SunJVMExe(const std::string& jrehome)
{
  m_jrehome = jrehome;
}

SunJVMExe::SunJVMExe(const std::string& jrehome, const Version& v)
{
  m_jrehome = jrehome;
  m_version = v;
}

bool SunJVMExe::run(const std::string& mainclass, bool useconsole)
{
  if (!m_version.isValid())
    {
      m_version = guessVersion();
    }
  
  if (!m_version.isValid())
    return false;
  
  std::vector<std::string> execv;

  execv.push_back(StringUtils::requoteForCommandLine(lookUpExecutable(useconsole)));

   if (m_vmParameter != "")
    {
      std::vector<std::string> vmParameter = StringUtils::split(m_vmParameter, " ", " ", false);
      for (std::vector<std::string>::iterator i=vmParameter.begin(); i != vmParameter.end(); i++)
      {
        execv.push_back(*i);
      }
    }
    
   if (m_maxHeap > 0)
    {
      if ((m_version.getMajor()==1)&&(m_version.getMinor()==1))
	execv.push_back("-mx" + StringUtils::toString(m_maxHeap));
      else
	execv.push_back("-Xmx" + StringUtils::toString(m_maxHeap));
    }

  if (m_initialHeap > 0)
    {
      if ((m_version.getMajor()==1)&&(m_version.getMinor()==1))
	execv.push_back("-ms" + StringUtils::toString(m_initialHeap));
      else
	execv.push_back("-Xms" + StringUtils::toString(m_initialHeap));
    }

  for (int i=0; i<m_properties.size(); i++)
    if(m_properties[i].getName()[0]=='-') {
        execv.push_back( StringUtils::requoteForCommandLine(m_properties[i].getName()));
    } else {
    execv.push_back( StringUtils::requoteForCommandLine("-D" + m_properties[i].getName()) + "=" + StringUtils::requoteForCommandLine(m_properties[i].getValue()));
    }

  std::string classpath;
  if ((m_version.getMajor()==1)&&(m_version.getMinor()==1))
    classpath = getClassPath(true);
  else
    classpath = getClassPath(false);
  
  if (classpath.size() > 0)
    execv.push_back("-classpath " + StringUtils::requoteForCommandLine(classpath));

  execv.push_back(mainclass);

  for (int i=0; i<m_arguments.size(); i++)
    {
      execv.push_back( StringUtils::requoteForCommandLine(m_arguments[i]) );
    }

  std::string execmd = StringUtils::join(execv, " ");
  DEBUG("COMMAND: <" + execmd + ">");

  Process proc(execmd, useconsole);
  if (proc.run())
    {
      DEBUG("Started successfully");
      proc.join();
      m_exitCode = proc.getExitCode();
      return true;
    }
  else
    {
      DEBUG("Failed running " + execmd);
    }
  return false;
}


std::string SunJVMExe::lookUpExecutable(bool useconsole)
{
  std::string java;

  if (m_jrehome.size() == 0)
    {
      return useconsole?"java.exe":"javaw.exe";
    }

  if (useconsole)
    {
      if (FileUtils::fileExists(m_jrehome, "bin\\java.exe"))
	java = FileUtils::concFile(m_jrehome, "bin\\java.exe");
      else if (FileUtils::fileExists(m_jrehome, "bin\\jre.exe"))
	java = FileUtils::concFile(m_jrehome, "bin\\jre.exe");
      else
	{
	  std::vector<std::string> javas = FileUtils::recursiveSearch(m_jrehome, "java.exe");
	  DEBUG("REC: " + StringUtils::toString(javas));

	  if (javas.size() == 0)
	    javas = FileUtils::recursiveSearch(m_jrehome, "jre.exe");

	  if (javas.size() > 0)
	    java = javas[0];
	}
    }
  else
    {
      if (FileUtils::fileExists(m_jrehome, "bin\\javaw.exe"))
	java = FileUtils::concFile(m_jrehome, "bin\\javaw.exe");
      else if (FileUtils::fileExists(m_jrehome, "bin\\jrew.exe"))
	java = FileUtils::concFile(m_jrehome, "bin\\jrew.exe");
      else
	{
	  std::vector<std::string> javas = FileUtils::recursiveSearch(m_jrehome, "javaw.exe");

	  DEBUG("REC: " + StringUtils::toString(javas));

	  if (javas.size() == 0)
	    javas = FileUtils::recursiveSearch(m_jrehome, "jrew.exe");

	  if (javas.size() > 0)
	    java = javas[0];
	}      
    }

  return java;
}

Version SunJVMExe::guessVersion()
{
  std::string exepath = lookUpExecutable(true);
  string exeline = exepath + " -version";

  Version result;

  // Return immediatly if the exe does not exist
  if (!FileUtils::fileExists(exepath))
    return result;

  string tmpfilename = FileUtils::createTempFileName(".tmp");

  Process proc(exeline, true);
  proc.setRedirect(tmpfilename);
  proc.run();
  proc.join();

  std::string voutput = FileUtils::readFile(tmpfilename);
  vector<string> split = StringUtils::split(voutput, " \t\n\r", "\"");
  for (vector<string>::iterator i=split.begin(); i != split.end(); i++)
    {
      Version v(*i);
      if (v.isValid())
	{
	  result = v;
	  break;
	}
    }
  
  FileUtils::deleteOnReboot(tmpfilename);
  return result;
}


std::string SunJVMExe::getClassPath(bool full)
{
  std::vector<std::string> cp;
  for (std::vector<std::string>::const_iterator i=m_pathElements.begin(); i!=m_pathElements.end(); i++)
    cp.push_back(*i);

  if (full)
    {
      std::string javaexe = lookUpExecutable(true);
      std::string home = FileUtils::getParent( FileUtils::getParent( javaexe ));
      if (FileUtils::fileExists(home))
	{
	  vector<string> cpzips = FileUtils::recursiveSearch(home, "*.zip");
	  cp.insert(cp.end(), cpzips.begin(), cpzips.end());
	  vector<string> cpjars = FileUtils::recursiveSearch(home, "*.jar");
	  cp.insert(cp.end(), cpjars.begin(), cpjars.end());
	}
    }

  return StringUtils::join(cp, ";");
}

int SunJVMExe::getExitCode()
{
  return m_exitCode;
}

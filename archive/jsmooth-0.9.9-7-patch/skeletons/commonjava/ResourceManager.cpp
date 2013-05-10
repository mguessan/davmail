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

#include "ResourceManager.h"

char * const ResourceManager::KEY_MAINCLASSNAME = "mainclassname";
char * const ResourceManager::KEY_ARGUMENTS     = "arguments";
char * const ResourceManager::KEY_CLASSPATH     = "classpath";
char * const ResourceManager::KEY_JVMSEARCH     = "jvmsearch";
char * const ResourceManager::KEY_MINVERSION    = "minversion";
char * const ResourceManager::KEY_MAXVERSION    = "maxversion";
char * const ResourceManager::KEY_NOJVMMESSAGE  = "nojvmmessage";
char * const ResourceManager::KEY_NOJVMURL      = "nojvmurl";
char * const ResourceManager::KEY_BUNDLEDVM     = "bundledvm";
char * const ResourceManager::KEY_CURRENTDIR    = "currentdir";
char * const ResourceManager::KEY_EMBEDJAR      = "embedjar";

ResourceManager::ResourceManager(std::string category, int propsId, int jarId, int jniId)
{
  m_resourceCategory = category;
  m_resourcePropsId = propsId;
  m_resourceJarId = jarId;

  //
  // Load the Properties
  //
  DEBUG("Initialize properties...");
  std::string propsidstr = this->idToResourceName(propsId);
  HRSRC resprop = FindResource(NULL, propsidstr.c_str(), category.c_str());
  if (resprop != NULL)
    {
      int mainsize = 0;
      mainsize = SizeofResource(NULL, resprop);
      // char mainbuf[mainsize+1];
      HGLOBAL main = LoadResource(NULL, resprop);
      m_props.setData((const char*)main, mainsize);
    }
  else
    {
      m_lastError = "Can't find resource 'main name'";
      return;
    }

  //
  // Split the arguments
  //
  m_arguments = StringUtils::split(getProperty(KEY_ARGUMENTS, ""), " \t\n\r", "\"\'");

  //
  // loads the jar information
  // 
  std::string jaridstr = this->idToResourceName(jarId);
  HRSRC resjar = FindResource(NULL, jaridstr.c_str(), category.c_str());
  if (resjar != NULL)
    {
      m_jarSize = SizeofResource(NULL, resjar);
      m_jarHandler =  LoadResource(NULL, resjar);
    }
  else
    {
      m_lastError = "Can't find JAR resource!";
      return;
    }


  m_jnismoothSize = this->getResourceSize(jniId);
  m_jnismoothHandler = this->getResource(jniId);

  //
  // Extract the java properties from the Property
  //
  std::string jpropcountstr = m_props.get("javapropertiescount");
    
  string exepath = FileUtils::getExecutablePath();
  string exename = FileUtils::getExecutableFileName();
  string computername = FileUtils::getComputerName();
    
  int jpropcount = StringUtils::parseInt(jpropcountstr);
  DEBUG("Number of Java Parameters: "+jpropcountstr);
  for (int i=0; i<jpropcount; i++)
    {
      string namekey = string("javaproperty_name_") + StringUtils::toString(i);
      string valuekey = string("javaproperty_value_") + StringUtils::toString(i);
      string name = m_props.get(namekey);
      string value = m_props.get(valuekey);

      DEBUG("Setting up java properties SOURCE: " + name + "=" + value + " : property if exist: " +getProperty(name,""));

      value = StringUtils::replaceEnvironmentVariable(value);
      value = StringUtils::replace(value, "${EXECUTABLEPATH}", exepath);
      value = StringUtils::replace(value, "${EXECUTABLENAME}", exename);
      value = StringUtils::replace(value, "${COMPUTERNAME}", computername);
      	        
      JavaProperty jprop(name, value);
      m_javaProperties.push_back(jprop);

      DEBUG("Setting up java properties DESTINATION: " + name + "=" + value);
    }

  std::string curdirmodifier = m_props.get(ResourceManager::KEY_CURRENTDIR);
  if (curdirmodifier.length()>0)
    {
      int pos = string::npos;
      if ( (pos=curdirmodifier.find("${EXECUTABLEPATH}")) != string::npos)
	{
	  m_currentDirectory = FileUtils::concFile(exepath, curdirmodifier.substr(pos + string("${EXECUTABLEPATH}").size()));
	  //	    m_currentDirectory = StringUtils::replace(curdirmodifier, "${EXECUTABLEPATH}", exepath);
	}
      else
	{
	  DEBUG(string("Currentdirectory =") + curdirmodifier);
	  m_currentDirectory = curdirmodifier;
	  //	    m_currentDirectory = FileUtils::concFile(FileUtils::getExecutablePath(), curdirmodifier);
	  m_currentDirectory = StringUtils::replaceEnvironmentVariable(m_currentDirectory);
	}
    }
  else
    {
      m_currentDirectory = "";
    }
  //    printf("CURDIR SET TO: [%s]\n", m_currentDirectory.c_str());
}

ResourceManager::~ResourceManager()
{
    for (std::vector<std::string>::iterator i=m_deleteOnFinalize.begin(); i != m_deleteOnFinalize.end(); i++)
    {
        int res = DeleteFile(i->c_str());
    }
}

void ResourceManager::setProperty(const std::string& key, const std::string& value)
{
     m_props.set(key, value);
}

void ResourceManager::saveTemp(std::string tempname, HGLOBAL data, int size)
{
  if ((data == 0) || (size == 0))
    return;
  
  HANDLE temp = CreateFile(tempname.c_str(),
			   GENERIC_WRITE,
			   FILE_SHARE_WRITE,
			   NULL, 
			   CREATE_ALWAYS, 
			   FILE_ATTRIBUTE_NORMAL,
			   NULL);
    
  if (temp != NULL)
    {    
      DWORD reallyWritten;
      WriteFile(temp, data, size, &reallyWritten, NULL);
        
      // TODO: check the reallyWritten value for errors
        
      CloseHandle(temp);
      string s = tempname;
      //      m_deleteOnFinalize.push_back(s);
      FileUtils::deleteOnReboot(s);
    }    
}

std::string ResourceManager::getMainName()  const
{
  return getProperty(string("mainclassname"));
}

std::string ResourceManager::getProperty(const std::string& key)  const
{
  return m_props.get(key);
}

std::string ResourceManager::getProperty(const std::string& key, const std::string& def) const
{
  if (m_props.contains(key))
    return m_props.get(key);
  else
    return def;
}

bool ResourceManager::getBooleanProperty(const std::string& key) const
{
  std::string prop = getProperty(key, "0");

  if (StringUtils::parseInt(prop)==1)
    return true;

  if (StringUtils::toLowerCase(prop) == "true")
    return true;

  return false;
}

std::string ResourceManager::saveJarInTempFile()
{
  if (useEmbeddedJar() == false)
    return "";

  std::string tempfilename = FileUtils::createTempFileName(".jar");
  DEBUG("Created temporary filename to hold the jar (" + tempfilename + ")");
  saveTemp(tempfilename, m_jarHandler, m_jarSize);
  return tempfilename;
}

const vector<JavaProperty>& ResourceManager::getJavaProperties()
{
  return m_javaProperties;
}

std::vector<std::string> ResourceManager::getNormalizedClassPathVector() const
{
  std::string basepath = FileUtils::getExecutablePath();
  std::string curdirmodifier = getCurrentDirectory(); //getProperty(string(ResourceManager::KEY_CURRENTDIR));
  if (FileUtils::isAbsolute(curdirmodifier)){
    basepath = curdirmodifier;
  	DEBUG("DEBUG: (absolut) Basepath is : " + basepath);
  } else {
    basepath = FileUtils::concFile(basepath, curdirmodifier);
  	DEBUG("DEBUG: (not absolut) Basepath is : " + basepath);
  }

  std::string cp = getProperty(string(ResourceManager::KEY_CLASSPATH));
  vector<string>cps = StringUtils::split(cp, ";", "", false);
  for (int i=0; i<cps.size(); i++)
    {
      string lib = cps[i];
      DEBUG("ClassPath element is " + basepath + " + " +cps[i]);

      cps[i] = FileUtils::concFile(basepath, cps[i]);
      DEBUG("ClassPath element " + StringUtils::toString(i)+ "=" + cps[i]);
    }

  return cps;
}

std::string ResourceManager::getNormalizedClassPath() const
{
  vector<string> cps = getNormalizedClassPathVector();
  return StringUtils::join(cps, ";");
}

std::string ResourceManager::getCurrentDirectory() const
{
    return m_currentDirectory;
}

bool ResourceManager::useEmbeddedJar() const
{
  std::string value = m_props.get(ResourceManager::KEY_EMBEDJAR);
  if (value == "true")
    return true;
  return false;
}

void ResourceManager::printDebug() const
{
  DEBUG("ResourceManager resource configuration:");
  DEBUG(" - Resource category: " + m_resourceCategory);
  DEBUG(" - Current directory: " + m_currentDirectory);

  map<string, string>  props = m_props.getDataCopy();
  DEBUG(" - Property count: " + StringUtils::toString(props.size()));
  for (map<string, string>::iterator i = props.begin(); i != props.end(); i++)
    {
      DEBUG(" - Property: "  + i->first + "=<" + i->second+">");
    }
}

void ResourceManager::setUserArguments(std::vector<std::string> arguments)
{
  m_arguments.clear();

  for (std::vector<std::string>::iterator i=arguments.begin(); i != arguments.end(); i++)
    {
      addUserArgument(*i);
    }
}


void ResourceManager::addUserArgument(std::string argument)
{
	bool keyFound = false;
  if (argument.size()>3)
    {  
      int pos = argument.find("=");
      if (pos != std::string::npos)
			{
	  		string key = argument.substr(2, pos-2);
	  		string value = argument.substr(pos+1);
				string argumentType = argument.substr(0,2);
	  		if (argumentType == "-J")
	  		{
	  			DEBUG("FOUND USER ARGUMENT for JSMOOTH: [" + key + "]=[" + value + "]");
	  			keyFound = true;
	  			setProperty(key, value);
	  		}
	  		if (argumentType == "-D")
	  		{
	  			DEBUG("FOUND USER ARGUMENT for JAVA: [" + key + "]=[" + value + "]");
	  			JavaProperty jprop(key, value);
	  			keyFound = true;
      		m_javaProperties.push_back(jprop);
	  		}
			}
    }
  if (!keyFound)
    {
      m_arguments.push_back(argument);
//       setProperty(KEY_ARGUMENTS, getProperty(KEY_ARGUMENTS) + " " + StringUtils::requoteForCommandLine(StringUtils::escape(argument)) );
    }
}


std::vector<std::string> ResourceManager::getArguments()
{
  return m_arguments;
}

int ResourceManager::getResourceSize(int id)
{
  std::string propid = idToResourceName(id);
  HRSRC resprop = FindResource(NULL, propid.c_str(), m_resourceCategory.c_str());
  if (resprop != NULL)
    {
      return SizeofResource(NULL, resprop);
    }
  else
    return 0;
}

HGLOBAL ResourceManager::getResource(int id)
{
  std::string propid = idToResourceName(id);
  HRSRC resprop = FindResource(NULL, propid.c_str(), m_resourceCategory.c_str());
  if (resprop != NULL)
    {
      return LoadResource(NULL, resprop);
    }
  else
    return 0;
}


std::string ResourceManager::saveJnismoothInTempFile()
{
  if (m_jnismoothHandler == 0)
    {
      DEBUG("NO JNI SMOOTH ID !!");
      return "";
    }

  std::string tempfilename = FileUtils::createTempFileName(".jar");
  DEBUG("Saving jnismoothjar in " + tempfilename);

  DEBUG("Created temporary filename to hold the jar (" + tempfilename + ")");
  saveTemp(tempfilename, m_jnismoothHandler, m_jnismoothSize);
  return tempfilename;  
}

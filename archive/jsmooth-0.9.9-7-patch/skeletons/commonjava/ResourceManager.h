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

#ifndef RESOURCEMANAGER_H
#define RESOURCEMANAGER_H

#include <cstdio>
#include <windows.h>
#include <string>
#include <vector>

#include "common.h"
#include "Properties.h"
#include "FileUtils.h"
#include "JavaProperty.h"

/**
 * Manages the executable resources associated to a Java
 * application. This class manages the resources that are used to
 * store the data associated to a java application. Those resources
 * are: 
 * - The JAR file, stored as a raw resource.  
 * - The Property file, stored as a raw resource.
 *  
 * The Property file contains an 8-bit text, as parsed and used by the
 * Properties class, which defines information relative to the java
 * application (for instance the name of the main class, the java
 * properties, and so on).
 *
 * @author Rodrigo Reyes <reyes@charabia.net>
 */

class ResourceManager
{
 private:
  std::string m_mainName;
  std::string m_resourceCategory;
  Properties m_props;
  std::string m_currentDirectory;

  int m_resourcePropsId;
  int m_resourceJarId;
  std::string m_lastError;    
  HGLOBAL m_jarHandler;
  int m_jarSize;
  HGLOBAL m_jnismoothHandler;
  int m_jnismoothSize;

  std::vector<std::string> m_arguments;
    
  std::vector<std::string> m_deleteOnFinalize;
  std::vector<JavaProperty> m_javaProperties;
    
 public:

  static char * const KEY_MAINCLASSNAME;
  static char * const KEY_ARGUMENTS;
  static char * const KEY_CLASSPATH;
  static char * const KEY_JVMSEARCH;
  static char * const KEY_MINVERSION;
  static char * const KEY_MAXVERSION;
  static char * const KEY_NOJVMMESSAGE;
  static char * const KEY_NOJVMURL;
  static char * const KEY_BUNDLEDVM;
  static char * const KEY_CURRENTDIR;
  static char * const KEY_EMBEDJAR;

  /** 
   * Constructs a ResourceManager which extract the jar and property
   * files from the resources defined by the given parameters.  The
   * resource are loaded from the resource type and resource names
   * passed as parameters.
   *
   * @param category the resource type to look in
   * @param propsId the resource id, stored under the category type, for the property file
   * @param jarId the resource id, stored under the category type, for the jar file
   */ 
  ResourceManager(std::string category, int propsId, int jarId, int jniId = -1);

  /**
   * Default destructor.  The detructor tries to delete all the
   * temporary files that have been created by the object. This is
   * mainly the files created by the saveJarInTempFile() method.
   *
   * @sa ResourceManager::saveJarInTempFile
   */
  ~ResourceManager();
    
  /** Saves the jar in a temporary folder.  Extract the jar file
   * from the resource defined in the consructor, and saves it in
   * the temporary directory defined by the operating system.
   *
   * NOTE: if the KEY_EMBEDJAR key does not return "true", this method
   * does not save the jar, and returns an empty string ("").
   *
   * @return the filename of the temp file where the Jar can be found.
   */
  std::string saveJarInTempFile();

  std::string saveJnismoothInTempFile();

  /** Returns the name of the main class.  The main class is the
   *  class used to launch the java application. The static "public
   *  void main(String[])" method of this class is called to start
   *  the program.
   *
   * @return the name of the main class
   */ 
  std::string getMainName() const;

  /**
   * Returns the last error string. This is the string that describes
   * the last error that was raised by an operation on the object.
   * @return a string
   */
  std::string getLastErrorString()
    {
      return m_lastError;
    }
    
  /**
   * Retrieves a property value from the Properties resource defined
   * in the constructor.
   *
   * @param key the name of the property
   * @return a string that contains the value of the property, or an empty string if the property does not exist.
   */
  std::string getProperty(const std::string& key) const;
  std::string getProperty(const std::string& key, const std::string& def) const;
  bool getBooleanProperty(const std::string& key) const;

  /**
   * Adds a new property.
   *
   * @param key the name of the property
   * @param value the value associated to the property
   */
  void setProperty(const std::string& key, const std::string& value);

  std::vector<std::string> getNormalizedClassPathVector() const;
  std::string getNormalizedClassPath() const;

  /**
   * Return the list of JavaProperty elements defined in the property
   * resource. 
   *
   * @return a vector of JavaProperty elements, or an empty vector if none are defined.
   */ 
  const vector<JavaProperty>& getJavaProperties();
    
  std::string getCurrentDirectory() const;

  bool useEmbeddedJar() const;

  void printDebug() const;

  void setUserArguments(std::vector<std::string> arguments);
  void addUserArgument(std::string argument);

  std::vector<std::string> getArguments();

  int getResourceSize(int id);
  HGLOBAL getResource(int id);

 private:
  void saveTemp(std::string tempname, HGLOBAL data, int size);

  std::string idToResourceName(int id) const
    {
      char buffer[32];
      sprintf(buffer, "%d", id);
      std::string result("#");
      result += buffer;
      return result;
    }
    
};


#endif


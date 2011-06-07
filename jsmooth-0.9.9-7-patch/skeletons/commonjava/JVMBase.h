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

#ifndef __SUNJVMBASE_H_
#define __SUNJVMBASE_H_

#include <string>
#include "jni.h"

#include "Version.h"
#include "StringUtils.h"
#include "FileUtils.h"
#include "ResourceManager.h"
#include "JavaProperty.h"

/**
 * @author Rodrigo Reyes <reyes@charabia.net>
 */ 

class JVMBase
{
 protected:

  std::vector<std::string>    m_pathElements;
  std::vector<JavaProperty>   m_properties;
  int                         m_maxHeap;
  int                         m_initialHeap;
  std::vector<std::string>    m_arguments;
  std::string				  m_vmParameter;
  
 public:
  JVMBase();

  void addPathElement(const std::string& element);
  void addProperty(const JavaProperty& prop);
  void setMaxHeap(long size);
  void setInitialHeap(long size);
  void addArgument(const std::string& arg);
  void setArguments(const std::string& args);
  void setVmParameter(std::string parameter);
};


#endif

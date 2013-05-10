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

#include "JVMBase.h"

JVMBase::JVMBase()
{
  m_maxHeap = -1;
  m_initialHeap = -1;
  m_vmParameter = "";
}

void JVMBase::addPathElement(const std::string& element)
{
  m_pathElements.push_back(element);
}

void JVMBase::addProperty(const JavaProperty& prop)
{
  m_properties.push_back(prop);
}

void JVMBase::setMaxHeap(long size)
{
  m_maxHeap = size;
}

void JVMBase::setInitialHeap(long size)
{
  m_initialHeap = size;
}

void JVMBase::addArgument(const std::string& arg)
{
  //  m_arguments.push_back(StringUtils::requoteForCommandLine(arg));
  m_arguments.push_back(arg);
}


void JVMBase::setArguments(const std::string& args)
{
  m_arguments.clear();
  DEBUG("arguments:<" + args + ">");
  //   std::string ua = StringUtils::unescape(args);
  //   DEBUG("arguments unescaped:<" + ua + ">");
  vector<string> splitted = StringUtils::split(args, " \t\n\r", "\"\'", false);
  for (int i=0; i<splitted.size(); i++)
    {
      DEBUG("SPLITTED-ARG[" + StringUtils::toString(i)+"]="+ splitted[i]);
      this->addArgument(splitted[i]);
    }
}

void JVMBase::setVmParameter(std::string parameter)
{
  m_vmParameter = parameter;
}

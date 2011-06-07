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

#ifndef __VERSION_H_
#define __VERSION_H_

#include <cstdio>
#include <string>

#include "common.h"
#include "StringUtils.h"

/**
 * Manages versions as used by Sun's JVM. The version scheme used is
 * major.minor.sub, for instance 1.1.8 or 1.4.2.
 *
 * @author Rodrigo Reyes <reyes@charabia.net>
 */

class Version
{
 private:
  int m_major;
  int m_minor;
  int m_sub;

 public:
  std::string Value;

  /**
   * Creates a Version object from a string representation. This
   * string needs to start as a normal version, but don't need to
   * follow exactly the schema X.Y.Z. The Major number is mandatory,
   * but the Minor and Sub numbers are optional.
   *
   * If the string representation does not represent a valid
   * version, the object is said 'invalid', and returns false to the
   * isInvalid() method.
   *
   * @param val a string representation of the version.
   */
  Version(std::string val);

  /**
   * Creates an invalid Version object. The object created returns
   * false to the isValid() method.
   */
  Version();

  /**
   * Returns the major number of this Version.
   */
  int getMajor() const;
  /**
   * Returns the minor number of this Version.
   */
  int getMinor() const;
  /**
   * Returns the subminor number of this Version.
   */
  int getSubMinor() const;

  std::string toString() const;

  //    bool operator > (const Version& v) const;

  friend bool operator < (const Version& v1, const Version& v2);
  friend bool operator <= (const Version& v1, const Version& v2);
    
  /**
   * A version object may be invalid if it does not refer to a valid
   * version. In such a case, the behaviour of object the is altered
   * as follows:
   *
   * - The getMajor(), getMinor(), and getSubMinor() methods return 0
   * - Any comparison (< or <=) between an invalid Version object
   *   and another object (invalid or not) return true.
   */
  bool isValid() const;
    
 private:
  void parseValue(const std::string& val);

  int extractIntAt(const std::string& val, int pos) const;
    
 public:
  struct AscendingSort
  {
    bool operator()(const Version& v1, const Version& v2)
    {
      if (v1.getMajor() > v2.getMajor())
	return true;
      if (v1.getMajor() < v2.getMajor())
	return false;
                
      if (v1.getMinor() > v2.getMinor())
	return true;
      if (v1.getMinor() < v2.getMinor())
	return false;
        
      if (v1.getSubMinor() > v2.getSubMinor())
	return true;
      if (v1.getSubMinor() < v2.getSubMinor())
	return false;
                  
      return false;
    }
  };

};



#endif

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

#ifndef __FILEUTILS_H_
#define __FILEUTILS_H_

#include <windows.h>
#include <string>
#include <vector>

#include "StringUtils.h"

using namespace std;

/** File access utility class.
 * This class contains static class members that provide facilities
 * for manamement of files.
 *
 * @author Rodrigo Reyes <reyes@charabia.net>
 */

class FileUtils
{
 public:

  /**
   * Creates a temp filename.  This method builds a temporary file
   * name in the default windows temporary directory.
   *
   * @param suffix the suffix to use for the filename.
   */
  static string createTempFileName(const string& suffix);

  /**
   * Test wether a file exists.
   * @param filename the file name to check.
   * @return true if the file exists, false otherwise.
   */
  static bool fileExists(const string& filename);    

  static bool fileExists(const std::string&path, const string& filename);

  /**
   * Lookup recursively for files matching a given pattern in a given
   * path. The method scans all the directory provided and its
   * subdirectories. Each file matching the pattern is added to the
   * resulting vector of strings.
   *
   * @param path the path of the directory to scan.
   * @param pattern the file pattern, for instance "*.zip".
   * @return a vector of string that contains the result.
   */
  static vector<string> recursiveSearch(const string& path, const string& pattern);

  /**
   * Extracts the file part of a file path. Given a path of the form
   * c:\\a\\b\\c\\d or c\\d, return the last component of the file path
   * (that is, d in the previous example).
   *
   * @param filename an absolute or relative filename.
   * @return a string with the file part of the file.
   */ 
  static std::string extractFileName(const std::string& filename);

  /**
   * Returns the path where the executable application is executed
   * from. This is not the current directory, but the directory path
   * where the application was found. For instance if the application
   * was runned at c:\\programs\\bin\\test.exe, it returns
   * c:\\programs\\bin\\.
   *
   * @return a std::string text that holds the path.
   */
  static std::string getExecutablePath();


  static std::string getParent(const std::string &path);

  /**
   * Returns the name of the executable binary that was used to start
   * the application.For instance if the application
   * was runned at c:\\programs\\bin\\test.exe, it returns
   * test.exe
   *
   * @return the executable name.
   */
  static std::string getExecutableFileName();


  /**
   * Returns the name of the computer.
   * @return a std::string that holds the computer name.
   */
  static std::string getComputerName();

  static std::string concFile(std::string path, std::string file);

  static std::string getFileExtension(const std::string& filename);

  static bool isAbsolute(const std::string& filename);

  static std::string readFile(const std::string& filename);

  static void deleteOnReboot(std::string file);

};

#endif



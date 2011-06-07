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

#include "FileUtils.h"

string FileUtils::createTempFileName(const string& suffix)
{
    char temppath[512];
    int tplen = GetTempPath(512, temppath);

    int counter = 0;
    string temp;
    do {
    
       temp = string(temppath) + "temp" + StringUtils::toString(counter) + suffix;
       counter ++;
       
      } while (GetFileAttributes(temp.c_str()) !=  0xFFFFFFFF);

    return temp;
}


bool FileUtils::fileExists(const string& filename)
{
  if (filename[0] == '"')
    {
      string unquoted = StringUtils::replace(filename, "\"", "");
      return GetFileAttributes(unquoted.c_str()) != 0xFFFFFFFF;
    }
  return GetFileAttributes(filename.c_str()) != 0xFFFFFFFF;
}

bool FileUtils::fileExists(const std::string& path, const std::string& filename)
{
  std::string f = FileUtils::concFile(path, filename);
  return fileExists(f);
}

vector<string> FileUtils::recursiveSearch(const string& pathdir, const string& pattern)
{
    vector<string> result;
    string path = StringUtils::replace(pathdir, "\"", "");

    WIN32_FIND_DATA data;
    string file = path + ((path[path.length()-1]=='\\')?"":"\\") + pattern;
    
    HANDLE handle = FindFirstFile(file.c_str(), &data);
    if (handle != INVALID_HANDLE_VALUE)
    {
        result.push_back(path + ((path[path.length()-1]=='\\')?"":"\\") + data.cFileName);
        for ( ; FindNextFile(handle, &data) == TRUE ; )
        {
              result.push_back(path + ((path[path.length()-1]=='\\')?"":"\\") + data.cFileName);
        }    
    
        FindClose(handle);
    }

    handle = FindFirstFile((path + ((path[path.length()-1]=='\\')?"":"\\") + "*").c_str(), &data);
    if (handle == INVALID_HANDLE_VALUE)
        return result;

    do {
        string foundpath(data.cFileName);
        if ((foundpath != ".") && (foundpath != "..") && ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0))
        {
            string npath = path + ((path[path.length()-1]=='\\')?"":"\\") + data.cFileName;
            vector<string> tres = FileUtils::recursiveSearch(npath, pattern);
            result.insert(result.end(), tres.begin(), tres.end());
        }
    } while (FindNextFile(handle, &data));
    FindClose(handle);
    
    return result;
}

std::string FileUtils::extractFileName(const std::string& filename)
{
    int start = filename.rfind("\\", filename.length()-1);
    if (start != filename.npos)
    {
        return filename.substr(start+1);
    }
    return filename;
}

std::string FileUtils::getExecutablePath()
{
    char buffer[512];
    GetModuleFileName(NULL, buffer, 512);
    string full = buffer;
    int pos = full.rfind('\\', full.npos);
    
    return full.substr(0, pos+1);
}

std::string FileUtils::getExecutableFileName()
{
    char buffer[512];
    GetModuleFileName(NULL, buffer, 512);
    string full = buffer;
    int pos = full.rfind('\\', full.npos);
    
    return full.substr(pos+1);
}

std::string FileUtils::getParent(const std::string &path)
{
  if (path[path.length()-1] == '\\')
    return getParent( path.substr(0, path.size() - 1) );

  int pos = path.rfind('\\', path.npos);
  if (pos != path.npos)
    return path.substr(0, pos+1);
  else
    return path;
}

std::string FileUtils::getComputerName()
{
    char buffer[MAX_COMPUTERNAME_LENGTH + 1];
    DWORD size = MAX_COMPUTERNAME_LENGTH+1;
    GetComputerName(buffer, &size);
    return buffer;
}

std::string FileUtils::concFile(std::string path, std::string file)
{
  if (FileUtils::isAbsolute(file))
    return file;

  if (path.length() > 0)
    {
      if (path[path.length()-1] != '\\')
	path += '\\';
    }
  path += file;
  
  return path;
}

std::string FileUtils::getFileExtension(const std::string& filename)
{
  int pos = filename.rfind('.');
  if (pos != std::string::npos)
    {
      return filename.substr(pos+1);
    }
  return "";
}

bool FileUtils::isAbsolute(const std::string& filename)
{
  if (((filename.length()>2) && (filename[1] == ':') && (filename[2] =='\\')) || ((filename.length() >2) && (filename[0] == '\\') && (filename[1]=='\\')))
    return true;

  return false;
}

void FileUtils::deleteOnReboot(std::string file)
{
  MoveFileEx(file.c_str(), 0, MOVEFILE_DELAY_UNTIL_REBOOT);
}

std::string FileUtils::readFile(const std::string& filename)
{
  HANDLE f = CreateFile(filename.c_str(), GENERIC_READ,
			FILE_SHARE_READ, NULL,
			OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);

  std::string result;

  if (f != INVALID_HANDLE_VALUE)
    {
      char buffer[129];
      DWORD hasread;
      buffer[127] = 0;

      do {
	ReadFile(f, buffer, 127, &hasread, NULL);
	buffer[hasread] = 0;
	result += buffer;
      } while (hasread > 0);

      CloseHandle(f);
    }
  else
    {
      //      printf("Can't open file %s\n",filename.c_str());
    }

  return result;
}

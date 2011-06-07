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

#ifndef __STRINGUTILS_H_
#define __STRINGUTILS_H_

#include <cstdio>
#include <string>
#include <vector>
#include <windows.h>

using namespace std;

/**
 * Provides basic string operations.
 * 
 * @author Rodrigo Reyes <reyes@charabia.net>
 */

class StringUtils
{
 public:
  /**
   * Splits a string in several substrings.  The split method takes a
   * string as input, and outputs a vector of string. The string split
   * is done according to 2 parameters:
   *
   * - the separators parameter, defines which chars are
   * separators. Typically, this is a string that contains spaces,
   * line-feed, and tabulation chars (" \n\t").
   *
   * - the quotechars parameter, is useful when your input contains
   * quoted string that should not be broken down into substrings. For
   * instance, a typical quote char is a quote or a double quote
   * (resp. ' and "). 
   *
   * For example, is the input string is the line belows:
   *  - this is "my string"    and this is 'another one'.
   *
   * Here is the result of the call with the following parameters:
   *  - separators = \\n\\r\\t
   *  - quotechars = \\"\\'
   *
   * The output string is:
   *  - this
   *  - is
   *  - my string
   *  - and
   *  - this
   *  - is
   *  - another one
   *
   * @param str the string to be splitted
   * @param separators a string that contains all the characters to be used as separators.
   * @param quotechars a string that contains all the characters that are used as quote chars.
   */
  static vector<string> split(const string& str, const string& separators, const string& quotechars, bool handleEscape = true, bool returnSeparators = false);

  /**
   * Converts a string to an int. If the string is not a valid
   * integer, it returns 0.
   *
   * @param val the string to parse
   * @return an integer value
   */
  static int parseInt(const string& val);

  static int parseHexa(const string& val);

  /**
   * Convers an integer to a string.
   *
   * @param val an integer
   * @return a string
   */
  static string toString(int val);
  static string toHexString(int val);

  /**
   * Provides a string representation of a vector of string.  A vector
   * that contains 2 string aaa and bbb is represented as follows:
   * [aaa, bbb].
   *
   * @param seq a vector of string
   * @return a string representation
   */
  static string toString(const vector<string>& seq);

  /**
   * Copies the content of a string in a char array.
   *
   * @param from the string to copy from
   * @param to the destination array of chars
   * @param length the length of the array of char
   */ 
  static void copyTo(const string& from, char* to, int length);

  /**
   * Returns a copy of the string with the environment variables
   * replaced. The environment variable must be surrounded by %
   * signs. For each variable defined between 2 % signs, the method
   * tries to replace it by the value of the corresponding environment
   * variable. If no variable exists, it just replaces it with an
   * empty string.
   *
   * @param str the string to transform
   * @return the same string, but with all the environment variable replaced
   */
  static string replaceEnvironmentVariable(const string& str);

  static string replace(const string& str, const string& pattern, const string& replacement);
    
  static string join(const vector<string>& seq, const string& separator);
  static string trim(string& str);

  /**
   * If a string does not start with a quote ("), it returns a copy of
   * the string with enclosing quotes.
   *
   * @param str a string
   * @return a fixed copy of the string
   */
  static std::string fixQuotes(const string& str);

  static std::string escape(const string& str);
  static std::string unescape(const string& str);
  /**
   * Ensures a string is correctly quoted, the quotes are enclosing
   * the whole string, not a part of the string. For instance
   * <<"c:\\my path"\\bin>> is transformed into <<"c:\\my path\\bin">>
   *
   * @param str a string
   * @return a fixed copy of the string
   */
  static std::string requote(const string& str);
  static std::string requoteForCommandLine(const string& str);
  static std::string toLowerCase(const string& str);

  static std::string fixArgumentString(const std::string& arg);

  static std::string sizeToJavaString(int size);
  static std::string sizeToJavaString(std::string size);
  
};

#endif

/*
  JSmooth: a VM wrapper toolkit for Windows
  Copyright (C) 2003 Rodrigo Reyes <reyes@charabia.net>

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

 */

package net.charabia.jsmoothgen.application;

import java.io.File;

import net.charabia.jsmoothgen.skeleton.SkeletonBean;

/**
 * @author Rodrigo
 */
public class PropertiesBuilder {
    /**
     * Creates a text containing all the relevant properties of a
     * JSmoothModelBean object. The properties are output in the form
     * "key=value".
     * <p/>
     * <p/>
     * Note that all the paths are converted to be made relative to
     * the basedir parameter provided. All the paths converted are
     * expected to be relative to the targetted executable binary
     * (before the conversion is applied, that is).
     */
    static public String makeProperties(File basedir, JSmoothModelBean obj) {
        StringBuffer out = new StringBuffer();

    addPair("arguments", obj.getArguments(), out);
    addPair("mainclassname", obj.getMainClassName(), out);
    addPair("jvmsearch", makePathConc(obj.getJVMSearchPath()), out);
    addPair("minversion", obj.getMinimumVersion(), out);
    addPair("maxversion", obj.getMaximumVersion(), out);

    addPair("currentdir", obj.getCurrentDirectory(), out);

    if (obj.getEmbeddedJar() && (obj.getJarLocation().trim().length() > 0)) {
      addPair("embedjar", "true", out);
    } else {
      addPair("embedjar", "false", out);
    }

    if (obj.getMaximumMemoryHeap() > 1) {
      addPair("maxheap", Integer.toString(obj.getMaximumMemoryHeap()), out);
    }

    if (obj.getInitialMemoryHeap() > 1) {
      addPair("initialheap", Integer.toString(obj.getInitialMemoryHeap()), out);
    }

    if ((obj.getVmParameter() != null) && (!obj.getVmParameter().isEmpty())) {
      addPair("vmparameter", obj.getVmParameter(), out);
    }
    // BundledVM & classpaths are changed to be accessible
    // from the current directory
    File curdir = new File(obj.getExecutableName()).getParentFile();

        if (curdir == null)
            curdir = basedir.getAbsoluteFile();

        if (curdir.isAbsolute() == false) {
            curdir = new File(basedir, curdir.toString());
        }


        //	System.out.println("... curdir1 : " + curdir.toString());

    if (obj.getCurrentDirectory() != null) {
      File newcurdir = new File(obj.getCurrentDirectory());
      // System.out.println("... curdir1.5 : " + obj.getCurrentDirectory());

      if (!"${EXECUTABLEPATH}".equalsIgnoreCase(obj.getCurrentDirectory())) {
        if (newcurdir.isAbsolute() == false) {
          curdir = new File(curdir, newcurdir.toString());
        } else
          curdir = newcurdir;
      }
    }
    // System.out.println("... curdir2 : " + curdir.toString());

    if (obj.getBundledJVMPath() != null) 
		addPair("bundledvm", getRenormalizedPathIfNeeded(obj.getBundledJVMPath(), basedir, curdir), out);

    if (obj.getClassPath() != null) {
      String[] relcp = new String[obj.getClassPath().length];
      for (int i = 0; i < relcp.length; i++) {
        relcp[i] = getRenormalizedPathIfNeeded(obj.getClassPath()[i], basedir, curdir);
      }
      addPair("classpath", makePathConc(relcp), out);
    }

    //
    // Adds all the skeleton-specific properties
    //
    if (obj.getSkeletonProperties() != null) {
      for (int i = 0; i < obj.getSkeletonProperties().length; i++) {
        JSmoothModelBean.Property prop = obj.getSkeletonProperties()[i];
        if (prop.getKey() != null) {
          String val = prop.getValue();
                    if (val == null)
                        val = "";
                    addPair("skel_" + prop.getKey(), val, out);
                }
            }
        }


        //
        // Adds all the java properties. Those properties are
    // typically passed as -Dname=value arguments for the sun's
    // JVM.
    //

    JavaPropertyPair[] javapairs = obj.getJavaProperties();
    if (javapairs != null) {
      addPair("javapropertiescount", new Integer(javapairs.length).toString(), out);
      for (int i = 0; i < javapairs.length; i++) {
        addPair("javaproperty_name_" + i, javapairs[i].getName(), out);
        addPair("javaproperty_value_" + i, javapairs[i].getValue(), out);
      }
    }

    return out.toString();
  }

  /**
   * Create a manifest entry for windows right elevation
   * 
   * @param data
   * @param skel
   * @return the manifest xml
   */
  public static String makeManifest(JSmoothModelBean data, SkeletonBean skel) {
    StringBuilder retVal = new StringBuilder();
    String platform = "x86";
    String shortName = skel.getShortName();
    if (shortName.contains("64")) {
      platform = "ia64";
    }
    retVal.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>").append("\n");
    retVal.append("<assembly xmlns=\"urn:schemas-microsoft-com:asm.v1\" manifestVersion=\"1.0\">").append("\n");
    retVal.append("  <assemblyIdentity type=\"win32\"").append("\n");
    retVal.append("    name=\"").append(shortName).append("\"").append("\n");
    retVal.append("    version=\"1.0.0.0\"").append("\n");
    retVal.append("    processorArchitecture=\"").append(platform).append("\"").append("\n");
    retVal.append("  />").append("\n");
    retVal.append("  <dependency>").append("\n");
    retVal.append("    <dependentAssembly>").append("\n");
    retVal
        .append(
            "      <assemblyIdentity type=\"win32\" name=\"Microsoft.Windows.Common-Controls\" version=\"6.0.0.0\" processorArchitecture=\"*\" publicKeyToken=\"6595b64144ccf1df\" language=\"*\"/>")
        .append("\n");
    retVal.append("    </dependentAssembly>").append("\n");
    retVal.append("  </dependency>").append("\n");
    if (data.isUacRequireAdmin()) {
      retVal.append("  <trustInfo xmlns=\"urn:schemas-microsoft-com:asm.v2\">").append("\n");
      retVal.append("    <security>").append("\n");
      retVal.append("      <requestedPrivileges>").append("\n");
      retVal.append("        <requestedExecutionLevel level=\"requireAdministrator\"/>").append("\n");
      retVal.append("      </requestedPrivileges>").append("\n");
      retVal.append("    </security>").append("\n");
      retVal.append("  </trustInfo>").append("\n");
    }
    retVal.append("</assembly>");
    return retVal.toString();
  }

  /**
   * Converts a path relative to previousbasedir into a path 
   * relative to newbasedir.
   */
  static public String getRenormalizedPathIfNeeded(String value, File previousbasedir, File newbasedir) {
    // File f = new File(value);
    // if (f.isAbsolute())
    // return value;

        if (newbasedir == null)
            return value;

        if (value == null)
            return "";

        File abs = new File(previousbasedir, value).getAbsoluteFile();
        File n = JSmoothModelPersistency.makePathRelativeIfPossible(newbasedir, abs);

        return n.toString();
    }

    static public String escapeString(String str) {
        if (str == null)
            return "";

        StringBuffer out = new StringBuffer();
        for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      switch (c) {
        case '\n':
          out.append("\\n");
          break;
        case '\t':
          out.append("\\t");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\\':
          out.append("\\\\");
          break;
        default:
          out.append(c);
      }
    }
    return out.toString();
  }

  static private void addPair(String name, String value, StringBuffer out) {
    out.append(escapeString(name));
    out.append("=");
    out.append(escapeString(value));
    out.append("\n");
  }

    static public String makePathConc(String[] elements) {
        StringBuffer buf = new StringBuffer();
        if (elements != null)
            for (int i = 0; i < elements.length; i++) {
                buf.append(elements[i]);
                if ((i + 1) < elements.length)
                    buf.append(";");
            }
        return buf.toString();
  }

}

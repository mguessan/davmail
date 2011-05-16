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

/*
 * JSmoothModelBean.java
 *
 * Created on 7 aout 2003, 18:32
 */

package net.charabia.jsmoothgen.application;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

public class JSmoothModelBean {
  private String m_skeletonName;
  private String m_executableName;
  private String m_currentDirectory;

  private String m_iconLocation;
  private boolean m_embedJar = false;
  private String m_jarLocation;
  private String m_mainClassName;
  private String m_arguments;
  private String[] m_classPath;
  private String m_minimumVersion = "";
  private String m_maximumVersion = "";
  private String[] m_jvmSearch = null;

  private int m_maxHeap = -1;
  private int m_initialHeap = -1;

  private String m_vmParamter;

  private int m_uacRequireAdmin;

  private String m_noJvmMessage;
  private String m_noJvmURL;

  private String m_bundledJVM = null;

  private JavaPropertyPair[] m_javaprops = new JavaPropertyPair[0];
  private JSmoothModelBean.Property[] m_skelproperties;

  static public class Property {
    public String Key;
    public String Value;

    public void setKey(String key) {
      this.Key = key;
    }

    public String getKey() {
      return this.Key;
    }

    public void setValue(String val) {
      this.Value = val;
    }

    public String getValue() {
      return this.Value;
    }

    public String toString() {
      return getKey() + "==" + getValue();
    }
  }

  transient Vector m_listeners = new Vector();

  public static interface Listener {
    public void dataChanged();
  }

  transient Vector m_skeletonChangedListener = new Vector();

  public static interface SkeletonChangedListener {
    public void skeletonChanged();
  }

    /**
     * Creates a new instance of JSmoothModelBean
     */
    public JSmoothModelBean() {
  }

  public void addListener(JSmoothModelBean.Listener l) {
    m_listeners.add(l);
  }

  public void removeListener(JSmoothModelBean.Listener l) {
    m_listeners.remove(l);
  }

  public void addSkeletonChangedListener(JSmoothModelBean.SkeletonChangedListener l) {
    m_skeletonChangedListener.add(l);
  }

  public void removeSkeletonChangedListener(JSmoothModelBean.SkeletonChangedListener l) {
    m_skeletonChangedListener.remove(l);
  }


  private void fireChanged() {
    for (Iterator i = m_listeners.iterator(); i.hasNext();) {
      JSmoothModelBean.Listener l = (JSmoothModelBean.Listener)i.next();
      l.dataChanged();
    }
  }

  private void fireSkeletonChanged() {
    for (Iterator i = m_skeletonChangedListener.iterator(); i.hasNext();) {
      JSmoothModelBean.SkeletonChangedListener l = (JSmoothModelBean.SkeletonChangedListener)i.next();
      l.skeletonChanged();
    }
  }

  public void setSkeletonName(String name) {
    if (name != m_skeletonName) {
      m_skeletonName = name;
      fireSkeletonChanged();
      fireChanged();
    }
  }

  public String getSkeletonName() {
    return m_skeletonName;
  }

  public void setExecutableName(String name) {
    if (name != m_executableName) {
      m_executableName = name;
      fireChanged();
    }
  }


  public void setCurrentDirectory(String curdir) {
    if (curdir != m_currentDirectory) {
      m_currentDirectory = curdir;
      fireChanged();
    }
  }

  public String getCurrentDirectory() {
    return m_currentDirectory;
  }

  public String getExecutableName() {
    return m_executableName;
  }

  public void setIconLocation(String name) {
    if (name != m_iconLocation) {
      m_iconLocation = name;
      fireChanged();
    }
  }

  public String getIconLocation() {
    return m_iconLocation;
  }

  public boolean getEmbeddedJar() {
    return m_embedJar;
  }

  public void setEmbeddedJar(boolean b) {
    m_embedJar = b;
    fireChanged();
  }

  public void setJarLocation(String name) {
    if (name != m_jarLocation) {
      m_jarLocation = name;
      fireChanged();
    }
  }

  public String getJarLocation() {
    return m_jarLocation;
  }


  public void setMainClassName(String name) {
    if (name != m_mainClassName) {
      m_mainClassName = name;
      fireChanged();
    }
  }

  public String getMainClassName() {
    return m_mainClassName;
  }

  public void setArguments(String args) {
    m_arguments = args;
    fireChanged();
  }

  public String getArguments() {
    return m_arguments;
  }

  public void setClassPath(String[] cp) {
    m_classPath = cp;
    fireChanged();
  }

  public String[] getClassPath() {
    return m_classPath;
  }

  public void setMaximumVersion(String version) {
    m_maximumVersion = version;
    fireChanged();
  }

  public String getMaximumVersion() {
    return m_maximumVersion;
  }

  public void setMinimumVersion(String version) {
    m_minimumVersion = version;
    fireChanged();
  }

  public String getMinimumVersion() {
    return m_minimumVersion;
  }

  public void setJVMSearchPath(String[] path) {
    m_jvmSearch = path;
    fireChanged();
  }

  public String[] getJVMSearchPath() {
    return m_jvmSearch;
  }

  public void setSkeletonProperties(JSmoothModelBean.Property[] props) {
    // for (int i=0; i<props.length; i++)
    // System.out.println("SET PROPERTY: " + props[i].getIdName() + "=" + props[i].getValue());

    m_skelproperties = props;
    fireChanged();
  }

  public JSmoothModelBean.Property[] getSkeletonProperties() {
    return m_skelproperties;
  }

  public void setNoJvmMessage(String msg) {
    m_noJvmMessage = msg;
    fireChanged();
  }

  public String getNoJvmMessage() {
    return m_noJvmMessage;
  }

  public void setNoJvmURL(String url) {
    m_noJvmURL = url;
    fireChanged();
  }

  public String getNoJvmURL() {
    return m_noJvmURL;
  }

  public String getBundledJVMPath() {
    return m_bundledJVM;
  }

  public void setBundledJVMPath(String path) {
    m_bundledJVM = path;
    fireChanged();
  }

  public void setJavaProperties(JavaPropertyPair[] pairs) {
    m_javaprops = pairs;
  }

  public JavaPropertyPair[] getJavaProperties() {
    return m_javaprops;
  }

  public void setMaximumMemoryHeap(int size) {
    m_maxHeap = size;
  }

  public int getMaximumMemoryHeap() {
    return m_maxHeap;
  }

  public void setInitialMemoryHeap(int size) {
    m_initialHeap = size;
  }

  public int getInitialMemoryHeap() {
    return m_initialHeap;
  }

  public void setVmParameter(String parameter) {
    m_vmParamter = parameter;
  }

  public String getVmParameter() {
    return m_vmParamter;
  }

  public void setUacRequireAdministrator(int parameter) {
    m_uacRequireAdmin = parameter;
  }

  public int getUacRequireAdministrator() {
    return m_uacRequireAdmin;
  }

  public boolean isUacRequireAdmin() {
    return m_uacRequireAdmin > 0;
  }

  public String[] normalizePaths(java.io.File filebase) {
    return normalizePaths(filebase, true);
  }

  public String[] normalizePaths(java.io.File filebase, boolean toRelativePath) {
    // System.out.println("Normalize Path " + filebase + " / " + toRelativePath);
    Vector result = new Vector();

    m_iconLocation = checkRelativePath(filebase, m_iconLocation, result, "Icon location", toRelativePath);
    m_jarLocation = checkRelativePath(filebase, m_jarLocation, result, "Jar location", toRelativePath);
    m_bundledJVM = checkRelativePath(filebase, m_bundledJVM, result, "Bundle JVM location", toRelativePath);
    m_executableName = checkRelativePath(filebase, m_executableName, result, "Executable location", toRelativePath);

    if (m_executableName != null) {
      File exebase = new File(m_executableName);
            if (exebase.isAbsolute() == false)
                exebase = new File(filebase, exebase.toString()).getParentFile();

      // System.out.println("EXE FILEBASE: " + exebase.toString());
      if ((m_currentDirectory != null) && (m_currentDirectory.indexOf("${") >= 0))
        m_currentDirectory = checkRelativePath(exebase, m_currentDirectory, result, "Current directory", toRelativePath);
    }

    if (m_classPath != null) {
      for (int i = 0; i < m_classPath.length; i++) {
        m_classPath[i] = checkRelativePath(filebase, m_classPath[i], result, "Classpath entry (" + i + ")", toRelativePath);
      }
    }

        if (result.size() == 0)
            return null;

    String[] res = new String[result.size()];
    result.toArray(res);

    return res;
  }

  private String checkRelativePath(java.io.File root, String value, java.util.Vector errors, String name, boolean toRelativePath) {
        if (value == null)
            return value;

    if (toRelativePath) {
      File nf = JSmoothModelPersistency.makePathRelativeIfPossible(root, new File(value));
      if (nf.isAbsolute()) {
        errors.add(name);
      }
      return nf.toString();
    } else {
      File nf = new File(value);
      if (nf.isAbsolute() == false) {
        nf = new File(root, value);
        nf = nf.getAbsoluteFile();

        try {
          nf = nf.getCanonicalFile();
          nf = nf.getAbsoluteFile();
        } catch (IOException iox) {
          // do nothing
        }
      }
      return nf.toString();
    }
  }
}

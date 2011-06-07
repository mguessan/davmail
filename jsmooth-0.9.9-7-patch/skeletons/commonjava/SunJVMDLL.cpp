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


#include "SunJVMDLL.h"

#include "JClassProxy.h"
#include "JniSmooth.h"

SunJVMDLL::SunJVMDLL(const std::string& jvmdll, const Version& v)
{
  m_dllpath = jvmdll;
  m_version = v;
  m_statusCode = SunJVMDLL::JVM_NOT_STARTED;
  m_vmlib = NULL;
}

SunJVMDLL::~SunJVMDLL()
{
  if (m_vmlib != NULL)
    {
      FreeLibrary(m_vmlib);
    }
}

bool SunJVMDLL::run(const std::string& mainclass, bool waitDeath)
{
  if (m_statusCode == SunJVMDLL::JVM_NOT_STARTED)
    instanciate();

  if (m_statusCode == SunJVMDLL::JVM_LOADED)
    {
      JClassProxy disp(this, mainclass);
      jstring emptystr = newUTFString(std::string(""));  
      jobjectArray mainargs = newObjectArray(m_arguments.size(), "java.lang.String", emptystr);
      for (int i =0; i<m_arguments.size(); i++)
	  {
	    env()->SetObjectArrayElement(mainargs, i, newUTFString(m_arguments[i]));
	  }
	printf("arguments array = %d\n", mainargs);
	jvalue ma[1];
	ma[0].l = mainargs;
	disp.invokeStatic(std::string("void main(java.lang.String[] args)"), ma);
	if (waitDeath == true)
	  m_javavm->DestroyJavaVM();
	return true;
    }

  return false;
}

void SunJVMDLL::join()
{
  if (m_statusCode == SunJVMDLL::JVM_LOADED)
    {
      m_javavm->DestroyJavaVM();
    }
}

bool SunJVMDLL::instanciate()
{
  m_vmlib = LoadLibrary(m_dllpath.c_str());
  if (m_vmlib == NULL)
    {
      m_statusCode = SunJVMDLL::JVM_DLL_CANT_LOAD;
      return false;
    }
  CreateJavaVM_t CreateJavaVM = (CreateJavaVM_t)GetProcAddress(m_vmlib, "JNI_CreateJavaVM");
  GetDefaultJavaVMInitArgs_t GetDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs_t)GetProcAddress(m_vmlib, "JNI_GetDefaultJavaVMInitArgs");
        
  if ((CreateJavaVM == NULL) || (GetDefaultJavaVMInitArgs == NULL))
    {
      m_statusCode = SunJVMDLL::JVM_CANT_USE_VM;
      return false;
    }
  
  DEBUG("VM Created successfully");
      
  m_javavm = new JavaVM();
  m_javaenv = new JNIEnv();

  DEBUG("DLL Setup on " + m_version.toString());
  bool res;
  if ((m_version.getMajor() == 1)  && (m_version.getMinor() == 1))
    res = setupVM11DLL(CreateJavaVM, GetDefaultJavaVMInitArgs);
  else
    res = setupVM12DLL(CreateJavaVM, GetDefaultJavaVMInitArgs);

  registerJniSmooth();

  DEBUG("Result code on DLL: " + StringUtils::toString(res));
  if (res)
    {
      m_statusCode = SunJVMDLL::JVM_LOADED;
      return true;
    }

  m_statusCode = SunJVMDLL::JVM_CANT_USE_VM;
  return false;
}

bool SunJVMDLL::setupVM12DLL(CreateJavaVM_t CreateJavaVM, GetDefaultJavaVMInitArgs_t GetDefaultJavaVMInitArgs)
{
  vector<string> jpropstrv;
   if (m_vmParameter != "")
    {
      std::vector<std::string> vmParameter = StringUtils::split(m_vmParameter, " ", " ", false);
      for (std::vector<std::string>::iterator i=vmParameter.begin(); i != vmParameter.end(); i++)
      {
        jpropstrv.push_back(*i);
      }
    }

  for (int i=0; i<m_properties.size(); i++)
    if(m_properties[i].getName()[0]=='-') {
        jpropstrv.push_back( StringUtils::requoteForCommandLine(m_properties[i].getName()));
    } else {
        jpropstrv.push_back( StringUtils::requoteForCommandLine("-D" + m_properties[i].getName()) + "=" + StringUtils::requoteForCommandLine(m_properties[i].getValue()));
    }
      
//   DEBUG("MAXHEAP: " + StringUtils::toString(m_maxHeap));
//   DEBUG("INITIALHEAP: " + StringUtils::toString(m_initialHeap));

  if (m_maxHeap > 0)
    {
      jpropstrv.push_back("-Xmx" +StringUtils::toString(m_maxHeap));
    }

  if (m_initialHeap > 0)
    {
      jpropstrv.push_back("-Xms" + StringUtils::toString(m_initialHeap));
    }

  JavaVMInitArgs vm_args;
  GetDefaultJavaVMInitArgs(&vm_args);

  JavaVMOption options[1 + jpropstrv.size()];
  std::string cpoption = "-Djava.class.path=" + StringUtils::join(m_pathElements, ";");

  DEBUG("Classpath: " + cpoption);
  options[0].optionString =  (char*)cpoption.c_str();
  vm_args.version = 0x00010002;
  vm_args.version = JNI_VERSION_1_2;
  vm_args.options = options;
  vm_args.nOptions = 1 + jpropstrv.size();
                
  for (int i=0; i<jpropstrv.size(); i++)
    {
      options[1 + i].optionString = (char*)jpropstrv[i].c_str();
      DEBUG(string("Option added:") + options[1+i].optionString);
    }

  vm_args.ignoreUnrecognized = JNI_TRUE;

  //
  // Create the VM
  if (CreateJavaVM( &m_javavm, &m_javaenv, &vm_args) != 0)
    {
      DEBUG("Can't create VM");
      m_statusCode = SunJVMDLL::JVM_CANT_CREATE_VM;
      return false;
    }

  DEBUG("VM 1.2+ Created successfully !!");
  return true;
}

bool SunJVMDLL::setupVM11DLL(CreateJavaVM_t CreateJavaVM, GetDefaultJavaVMInitArgs_t GetDefaultJavaVMInitArgs)
{
  JDK1_1InitArgs vm_args;
  vm_args.version = 0x00010001;
  GetDefaultJavaVMInitArgs(&vm_args);

  if (m_maxHeap > 0)
    vm_args.maxHeapSize = m_maxHeap;
  if (m_initialHeap > 0)
    vm_args.minHeapSize = m_initialHeap;
  
  //
  // create the properties array
  //
  char  const  * props[m_properties.size()+1];
  vector<string> jpropstrv;

  for (int i=0; i<m_properties.size(); i++)
    jpropstrv[i] = m_properties[i].getName() + "=" + m_properties[i].getValue();

  for (int i=0; i<m_properties.size(); i++)
    props[i] = jpropstrv[i].c_str();
  props[m_properties.size()] = NULL;
  
  vm_args.properties = (char**)props;

  /* Append USER_CLASSPATH to the default system class path */

  std::string classpath = vm_args.classpath;
  classpath += StringUtils::join(m_pathElements, ";");

  DEBUG("Classpath = " + classpath);
  vm_args.classpath = (char*)classpath.c_str();

  //
  // Create the VM
  if (CreateJavaVM( &m_javavm, &m_javaenv, &vm_args) != 0)
    {
      DEBUG("Can't create VM");
      m_statusCode = SunJVMDLL::JVM_CANT_CREATE_VM;
      return false;
    }
  DEBUG("VM 1.1 Created successfully !!");
  return true;
}


jclass SunJVMDLL::findClass(const std::string& clazz)
{
  std::string classname = StringUtils::replace(clazz,".", "/");
  DEBUG("Looking up for class <" + classname + ">");
  jclass cls = env()->FindClass(classname.c_str());
  if (cls == 0)
    DEBUG("Can't find class " + classname + " !");
  return cls;
}

jmethodID SunJVMDLL::findMethod(jclass& cls, const std::string& methodname, const std::string& signature, bool isStatic)
{
  std::string sig = StringUtils::replace(signature, ".", "/");
  
  jmethodID mid;
  if (isStatic)
    mid = env()->GetStaticMethodID(cls, methodname.c_str(), sig.c_str());
  else
    mid = env()->GetMethodID(cls, methodname.c_str(), sig.c_str());

  return mid;
}

JavaVM* SunJVMDLL::getJavaVM()
{
  return m_javavm;
}

void SunJVMDLL::setIntField(jclass cls, jobject obj, const std::string& fieldName, int value)
{
  jfieldID binding = env()->GetFieldID(cls, fieldName.c_str(), "I");
  env()->SetIntField(obj, binding, (jint)value);
}

void SunJVMDLL::setLongField(jclass cls, jobject obj, const std::string& fieldName, jlong value)
{
  jfieldID binding = env()->GetFieldID(cls, fieldName.c_str(), "J");
  env()->SetLongField(obj, binding, (jlong)value);
}

void SunJVMDLL::setObjectField(jclass cls, jobject obj, const std::string& fieldName, const std::string& fieldclass, jobject value)
{
  std::string fc = "L" + StringUtils::replace(fieldclass, "." , "/") + ";";
  jfieldID binding = env()->GetFieldID(cls, fieldName.c_str(), fc.c_str());
  env()->SetObjectField(obj, binding, value);
}

jstring   SunJVMDLL::newUTFString(const std::string& str)
{
  return env()->NewStringUTF(str.c_str());
}

jobject   SunJVMDLL::newObject(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->NewObjectA(clazz, methodid, arguments);
}

jobjectArray  SunJVMDLL::newObjectArray(int size, jclass clazz, jobject initialValue)
{
  return env()->NewObjectArray((jsize)size, clazz, initialValue);
}

jobjectArray  SunJVMDLL::newObjectArray(int size, const std::string& classname, jobject initialValue)
{
  jclass cls = findClass(classname);
  return newObjectArray(size, cls, initialValue);
}

//
// Static method invocation
//

void SunJVMDLL::invokeVoidStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  env()->CallStaticVoidMethodA(clazz, methodid, arguments);
}

jboolean SunJVMDLL::invokeBooleanStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticBooleanMethodA(clazz, methodid, arguments);
}

jbyte SunJVMDLL::invokeByteStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticByteMethodA(clazz, methodid, arguments);
}

jchar SunJVMDLL::invokeCharStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticCharMethodA(clazz, methodid, arguments);
}

jshort SunJVMDLL::invokeShortStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticShortMethodA(clazz, methodid, arguments);
}

jint SunJVMDLL::invokeIntStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticIntMethodA(clazz, methodid, arguments);
}

jlong SunJVMDLL::invokeLongStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticLongMethodA(clazz, methodid, arguments);
}

jfloat SunJVMDLL::invokeFloatStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticFloatMethodA(clazz, methodid, arguments);
}

jdouble SunJVMDLL::invokeDoubleStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticDoubleMethodA(clazz, methodid, arguments);
}

jobject SunJVMDLL::invokeObjectStatic(jclass clazz, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallStaticObjectMethodA(clazz, methodid, arguments);
}


//
//  method invocation
//

void SunJVMDLL::invokeVoid(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  env()->CallVoidMethodA(obj, methodid, arguments);
}

jboolean SunJVMDLL::invokeBoolean(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallBooleanMethodA(obj, methodid, arguments);
}

jbyte SunJVMDLL::invokeByte(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallByteMethodA(obj, methodid, arguments);
}

jchar SunJVMDLL::invokeChar(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallCharMethodA(obj, methodid, arguments);
}

jshort SunJVMDLL::invokeShort(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallShortMethodA(obj, methodid, arguments);
}

jint SunJVMDLL::invokeInt(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallIntMethodA(obj, methodid, arguments);
}

jlong SunJVMDLL::invokeLong(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallLongMethodA(obj, methodid, arguments);
}

jfloat SunJVMDLL::invokeFloat(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallFloatMethodA(obj, methodid, arguments);
}

jdouble SunJVMDLL::invokeDouble(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallDoubleMethodA(obj, methodid, arguments);
}

jobject SunJVMDLL::invokeObject(jobject& obj, jmethodID& methodid, jvalue arguments[])
{
  return env()->CallObjectMethodA(obj, methodid, arguments);
}

bool SunJVMDLL::registerMethod(const std::string& classname, const std::string& methodname, const std::string& signature,
		    void* fn)
{
  jclass cc = this->findClass(classname);
  if (cc == 0)
    return false;
  JNINativeMethod jnm;
  jnm.name = (char*)methodname.c_str();
  jnm.signature = (char*)signature.c_str();
  jnm.fnPtr = fn;
  
  int res = env()->RegisterNatives(cc, &jnm, 1);
  if (res != 0)
    return false;

  return true;
}

bool SunJVMDLL::registerJniSmooth()
{
  registerNativeMethods(this);
  return true;
}

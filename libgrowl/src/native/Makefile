CXX = g++
CXXFLAGS = -W -Wall -c -I/System/Library/Frameworks/JavaVM.framework/Headers -I/System/Library/Frameworks/AppKit.framework/Headers -I/System/Library/Frameworks/Foundation.framework/Headers  -isysroot /Developer/SDKs/MacOSX10.5.sdk -arch i386 -arch ppc -arch x86_64 -mmacosx-version-min=10.4
MMFLAGS = -Wno-protocol -Wundeclared-selector
LDFLAGS = -dynamiclib -isysroot /Developer/SDKs/MacOSX10.5.sdk -arch i386 -arch ppc -arch x86_64 -mmacosx-version-min=10.4 
LDSUFFIXES = -framework JavaVM -framework Foundation -framework AppKit
SO_NAME = libgrowl.jnilib

.SUFFIXES: .o .mm
.mm.o:
	$(CXX) $(MMFLAGS) $(CXXFLAGS) $<

MMSRCS = GrowlJava.mm \
         GrowlJavaCallback.mm

.PHONY: all clean

all: $(SO_NAME)

SO_o = $(MMSRCS:.mm=.o)

.cpp.o:
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(EXTRA_INCLUDES) -o $@ $<

$(SO_NAME): $(SO_o)
	$(CXX) $(LDFLAGS) $(LIBS) $(SO_o) -o $@ $(LDSUFFIXES)

clean:
	rm -f *.jnilib
	rm -f *.o


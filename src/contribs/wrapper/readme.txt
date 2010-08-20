From Dustin Hawkins:

I run two instances of DavMail on my linux desktop to connect to work and university exchange servers. 
In order to accomplish this, I used the Java Service Wrapper.

Thought I would share my configs in case any one else wanted to run multi-instance under the java service wrapper.

http://wrapper.tanukisoftware.org/doc/english/download.jsp


I created  the following directory structure

/opt/davmail
    bin/
        wrapper   <-- this is provided by java service wrapper. platform specific native executable.
        davmail
        davmail_2
    lib/
        <davmail jars>
        <wrapper static objects/dll's for appropriate platform>
    conf/
        wrapper.conf
        wrapper.conf_2
        davmail.properties
        davmail.properties_2
    logs/


The bin/davmail* scripts are the linux start/stop scripts for each instance
the conf/ directory has two files for each instance, a wrapper.conf, and a davmail.properties.

by linking the bin/davmail* scripts into /etc/init.d and /etc/rc.d, I start davmail as a linux service, 
and its easily start/stop/restart-able via the standard linux commands.

If you download the Java Service Wrapper, you can find the appropriate executables and start scripts for 
your platform under <wrapper install dir>/bin
You can find the correct static object or DLL files under the <wrapper install dir>/lib

I have included a ZIP of my wrapper.conf and davmail startup scripts

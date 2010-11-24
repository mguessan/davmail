#!/bin/sh
#
# Usage: davmail [</path/to/davmail.properties>]
#

# Create the $HOME/.davmail.properties if necessary
if [ -r /etc/davmail.properties ]; then
  if [ ! -f ${HOME}/.davmail.properties ]; then
	grep -v ^davmail.logFilePath /etc/davmail.properties | \
	  sed -e 's/^davmail.server=true/davmail.server=false/' > \
	    ${HOME}/.davmail.properties
  fi
fi

# Add our libs into CLASSPATH
for i in /usr/share/davmail/lib/*; do export CLASSPATH=${CLASSPATH}:${i}; done

# Start davmail
java -cp /usr/share/davmail/davmail.jar:${CLASSPATH} davmail.DavGateway $*

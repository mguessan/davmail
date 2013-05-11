#!/bin/sh
# Ubuntu setup instructions :
# install java :
# sudo apt-get install default-jre
# launch davmail
BASE=`dirname $0`
for i in $BASE/lib/*; do export CLASSPATH=$CLASSPATH:$i; done
exec java -Xmx512M -Dsun.net.inetaddr.ttl=60 -cp $BASE/davmail.jar:$CLASSPATH davmail.DavGateway $1

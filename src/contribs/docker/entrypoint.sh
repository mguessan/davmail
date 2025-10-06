#!/bin/bash
#
# davmail.properties is in /davmail.properties
# /home is home directory
#
# set memory and enable DNS expiration

: "${JAVA_OPT_BASE:=-Xmx512M -Dsun.net.inetaddr.ttl=60}"
: "${JAVA_OPT_EXPORTS:=}"
JAVA_OPTS="$JAVA_OPT_BASE $JAVA_OPT_EXPORTS $JAVA_OPT_JAR $JAVA_OPT_USER"

# Determined experimentally
export CLASSPATH=/davmail/davmail.jar:/usr/share/java/commons-logging.jar:/usr/share/java/httpclient.jar:/usr/share/java/httpcore.jar:/usr/share/java/jackrabbit-webdav.jar:/usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-graphics.jar:/usr/share/java/javafx-media.jar:/usr/share/java/javafx-swing.jar:/usr/share/java/javafx-web.jar:/usr/share/java/javax.mail.jar:/usr/share/java/jettison.jar:/usr/share/java/jna.jar:/usr/share/java/log4j-1.2.jar:/usr/share/java/swt4.jar:/usr/share/java/stax2-api.jar:xercesImpl.jar:woodstox-core-asl.jar:commons-codec.jar:htmlcleaner.jar:jdom2.jar:jcifs.jar
export SWT_GTK3=0

if [ -z "$1" ]; then set -- /config/davmail.properties -notray; fi

# shellcheck disable=SC2086
exec "${JAVA:-java}" $JAVA_OPTS davmail.DavGateway "$@"

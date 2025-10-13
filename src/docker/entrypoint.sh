#!/bin/bash
#
# davmail.properties is in /davmail.properties
# /home is home directory
#

: "${DAVMAIL_PROPERTIES:=/config/davmail.properties}"

# enable DNS expiration
: "${JAVA_OPT_BASE:=-Dsun.net.inetaddr.ttl=60}"
: "${JAVA_OPT_EXPORTS:=}"
: "${JAVA_OPT_FX:=--module-path /usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-fxml.jar:/usr/share/java/javafx-graphics.jar:/usr/share/java/javafx-media.jar:/usr/share/java/javafx-swing.jar:/usr/share/java/javafx-web.jar --add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web}"
JAVA_OPTS="$JAVA_OPT_BASE $JAVA_OPT_EXPORTS $JAVA_OPT_FX $JAVA_OPT_JAR $JAVA_OPT_USER"

# Determined experimentally
export CLASSPATH=/usr/share/java/commons-codec.jar:/usr/share/java/commons-logging.jar:/usr/share/java/htmlcleaner.jar:/usr/share/java/httpclient.jar:/usr/share/java/httpcore.jar:/usr/share/java/jackrabbit-webdav.jar:/usr/share/java/jcifs.jar:/usr/share/java/jettison.jar:/usr/share/java/log4j-1.2.jar:/usr/share/java/javax.mail.jar:/usr/share/java/slf4j-api.jar:/usr/share/java/slf4j-log4j12.jar:/usr/share/java/stax2-api.jar:/usr/share/java/woodstox-core.jar:/usr/share/java/stax2-api.jar:/usr/share/java/slf4j-jcl.jar:/usr/share/java/jdom2.jar:/usr/share/java/javax.activation.jar

# default option is notray
if [ -z "$1" ]; then set -- -notray; fi

# shellcheck disable=SC2086
exec "${JAVA:-java}" $JAVA_OPTS davmail.DavGateway "$@"
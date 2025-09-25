FROM ubuntu:24.10 AS davbase
RUN mkdir -m 1777 -p /home; chmod 1777 /home
RUN mkdir /davmail
RUN apt-get update && apt-get -y dist-upgrade
RUN apt-get install -y iputils-ping iproute2 strace git x11-apps default-jre libcommons-codec-java libcommons-logging-java libhtmlcleaner-java libhttpclient-java libjackrabbit-java libjcifs-java libjettison-java libjna-java liblog4j1.2-java libmail-java libopenjfx-java  libservlet-api-java libslf4j-java libstax2-api-java libswt-cairo-gtk-4-jni libswt-gtk-4-java libwoodstox-java

FROM davbase AS davbuild
RUN apt-get install -y debhelper-compat javahelper ant ant-optional


FROM davbase AS davupstream
RUN apt-get install -y davmail


FROM davbase AS davmail
ADD davmail-compile.tar /davmail


EXPOSE 1110 1025 1143 1080 1389
COPY entrypoint /entrypoint
ENTRYPOINT [ "/entrypoint" ]
ENV XAUTHORITY=/.Xauthority
ENV DISPLAY=:0
VOLUME [ "/tmp/.X11-unix" ]
VOLUME [ "/.Xauthority" ]
VOLUME [ "/davmail.properties" ]
VOLUME [ "/etc/passwd:/etc/passwd:ro" ]
VOLUME [ "/etc/group:/etc/group:ro" ]
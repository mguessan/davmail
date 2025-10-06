FROM debian:12 AS base
RUN apt-get update


# First we build the jar file
FROM base AS builder
WORKDIR /davmail

RUN apt-get install -y ant git libopenjfx-java
COPY . .
ENV ANT_OPTS="--module-path /usr/share/java/javafx-base.jar:/usr/share/java/javafx-controls.jar:/usr/share/java/javafx-fxml.jar:/usr/share/java/javafx-graphics.jar:/usr/share/java/javafx-media.jar:/usr/share/java/javafx-swing.jar:/usr/share/java/javafx-web.jar --add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web"
RUN ant -Dfile.encoding=UTF-8 jar


# Then we run the jar file
FROM base AS runner
WORKDIR /davmail

RUN apt-get install -y openjdk-17-jre libcommons-codec-java libcommons-logging-java libhtmlcleaner-java libhttpclient-java libjackrabbit-java libjcifs-java libjettison-java libjna-java liblog4j1.2-java libmail-java libopenjfx-java  libservlet-api-java libslf4j-java libstax2-api-java libswt-cairo-gtk-4-jni libswt-gtk-4-java libwoodstox-java

# Copy jar file
COPY --from=builder /davmail/dist/davmail.jar /davmail/davmail.jar

# Copy default davmail.properties and set tokenFilePath
COPY --from=builder /davmail/src/etc/davmail.properties /config/davmail.properties
RUN sed -i 's/#davmail.oauth.tokenFilePath=/davmail.oauth.tokenFilePath=\/config\/.env.oauth/' /config/davmail.properties

VOLUME [ "/config" ]
EXPOSE 1110 1025 1143 1080 1389

COPY --chmod=0755 --from=builder /davmail/src/docker/entrypoint.sh /davmail/entrypoint.sh
ENTRYPOINT [ "/davmail/entrypoint.sh" ]



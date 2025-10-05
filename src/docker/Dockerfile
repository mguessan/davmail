FROM debian:12 AS base
RUN apt-get update


# First we build the jar file
FROM base AS builder
WORKDIR /davmail

RUN apt-get install -y ant git
COPY . .
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

COPY --from=builder /davmail/src/docker/entrypoint.sh /davmail/entrypoint.sh
ENTRYPOINT [ "/davmail/entrypoint.sh" ]



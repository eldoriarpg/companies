FROM eclipse-temurin:25-jdk AS companies
WORKDIR /build
COPY . .
RUN ./gradlew :companies-paper:shadowJar --no-daemon -x test \
    && cp companies-paper/build/libs/companies-paper-*-all.jar /companies.jar

FROM eclipse-temurin:21-jdk AS plotsquared
ADD --keep-git-dir=true https://github.com/IntellectualSites/PlotSquared.git#18a4a4126126759c5a7d9c443079f1e537d802f4 /build
WORKDIR /build
RUN ./gradlew :plotsquared-bukkit:shadowJar --no-daemon \
    && cp "$(ls Bukkit/build/libs/plotsquared-bukkit-*.jar | grep -v -e sources -e javadoc)" /plotsquared.jar

FROM alpine:3.22 AS plugins
RUN apk add --no-cache curl
COPY demo/node/plugins.txt demo/node/fetch-plugins.sh /
RUN /fetch-plugins.sh /plugins.txt

FROM itzg/minecraft-server:2026.10.1-java25
COPY --from=plugins /demo /demo
COPY --from=companies /companies.jar /demo/hub/plugins/Companies.jar
COPY --from=companies /companies.jar /demo/lands/plugins/Companies.jar
COPY --from=plotsquared /plotsquared.jar /demo/lands/plugins/PlotSquared.jar
COPY demo/node/hub /demo/hub
COPY demo/node/lands /demo/lands
ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=26.3 \
    PAPER_BUILD=235 \
    ONLINE_MODE=FALSE \
    REPLACE_ENV_DURING_SYNC=TRUE \
    REMOVE_OLD_MODS=TRUE \
    COPY_PLUGINS_SRC=/nonexistent \
    COPY_CONFIG_DEST=/data
LABEL org.opencontainers.image.title="Companies Demo Node" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"

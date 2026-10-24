FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build
COPY . .
RUN ./gradlew :web-backend:installDist --no-daemon -x test -x spotbugsMain

FROM eclipse-temurin:25-jre-alpine
COPY --from=build /build/web/backend/build/install/web-backend /app
EXPOSE 8080
LABEL org.opencontainers.image.title="Companies Dashboard Gateway" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"
ENTRYPOINT ["/app/bin/web-backend"]

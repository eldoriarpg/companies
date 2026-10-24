FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build
COPY . .
RUN ./gradlew :web-backend:installDist --no-daemon -x test -x spotbugsMain

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S gateway && adduser -S -G gateway gateway
COPY --from=build /build/web/backend/build/install/web-backend /app
USER gateway
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=6 \
    CMD wget -q -O /dev/null http://localhost:8080/api/v1/health || exit 1
LABEL org.opencontainers.image.title="Companies Dashboard Gateway" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"
ENTRYPOINT ["/app/bin/web-backend"]

FROM alpine:3.22
RUN apk add --no-cache bash coreutils docker-cli flock mariadb-client redis tar tzdata
COPY demo/reset/demo-reset.sh /usr/local/bin/demo-reset.sh
COPY demo/reset/paths /reset/paths
COPY demo/seed /seed
ENTRYPOINT ["demo-reset.sh"]
LABEL org.opencontainers.image.title="Companies Demo Reset" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"

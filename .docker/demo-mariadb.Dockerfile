FROM mariadb:11.8
COPY demo/mariadb/databases.sql /docker-entrypoint-initdb.d/databases.sql
LABEL org.opencontainers.image.title="Companies Demo Database" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"

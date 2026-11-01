FROM itzg/mc-proxy:java25-2026.10.2
COPY demo/velocity /config
ENV TYPE=VELOCITY \
    VELOCITY_VERSION=4.3.1 \
    VELOCITY_BUILD_ID=45
LABEL org.opencontainers.image.title="Companies Demo Proxy" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"

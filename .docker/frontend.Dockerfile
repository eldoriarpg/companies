FROM node:26-alpine AS build
WORKDIR /build
COPY web/frontend/package*.json ./
RUN npm ci --ignore-scripts
COPY web/frontend/ .
RUN npx nuxi prepare && npx nuxi build

FROM node:26-alpine
WORKDIR /app
COPY --from=build /build/.output ./
EXPOSE 4000
ENV NODE_ENV=production
ENV NITRO_PORT=4000
ENV NITRO_HOST=0.0.0.0
LABEL org.opencontainers.image.title="Companies Dashboard Frontend" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.source="https://github.com/eldoriarpg/companies"
USER node
CMD ["node", "server/index.mjs"]

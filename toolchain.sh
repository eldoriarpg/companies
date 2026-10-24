#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE=(docker compose -p companies -f "$ROOT/.docker/compose.dev.yaml")

usage() {
    cat <<'HELP'
Usage: ./toolchain.sh <command> [args]

Backend (Gradle)
  be-build              Everything Java: formatting check, tests, SpotBugs, jars
  be-test [args]        All Java tests, e.g. be-test --tests '*HostedGatewayTest'
  be-format             Applies the Java formatting and license headers (spotlessApply)
  be-gateway            Runs the dashboard gateway from source in self-hosted mode against docker-db

Docker for local testing (.docker/compose.dev.yaml; .docker/compose.prod.yaml is for hosting)
  docker-up             Database, gateway on :8080 and web app on :4000, rebuilt
  docker-db             Only the database, on 127.0.0.1 at DB_PORT (default 5463) for be-gateway
  docker-down           Stops everything; the database starts empty next time
  docker-logs [service] Follows the logs
HELP
}

command="${1:-}"
[ $# -gt 0 ] && shift

case "$command" in
    be-build)    "$ROOT/gradlew" -p "$ROOT" build ;;
    be-test)     "$ROOT/gradlew" -p "$ROOT" test "$@" ;;
    be-format)   "$ROOT/gradlew" -p "$ROOT" spotlessApply ;;
    be-gateway)
        GATEWAY_MODE="${GATEWAY_MODE:-self-hosted}" DB_PORT="${DB_PORT:-5463}" DB_PASSWORD="${DB_PASSWORD:-companies}" \
            "$ROOT/gradlew" -p "$ROOT" :web-backend:run ;;
    docker-up)   "${COMPOSE[@]}" up --build -d ;;
    docker-db)   DB_PORT="${DB_PORT:-5463}" "${COMPOSE[@]}" up -d postgres ;;
    docker-down) "${COMPOSE[@]}" down -v ;;
    docker-logs) "${COMPOSE[@]}" logs -f "$@" ;;
    ""|help|-h|--help) usage ;;
    *) echo "Unknown command: $command" >&2; usage; exit 1 ;;
esac

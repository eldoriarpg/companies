#!/bin/sh
# Writes random values for the secrets the compose file reads from DEMO_* variables into secrets.env, for a local run.
# Values already in secrets.env are kept unless --force.
set -eu
umask 077
cd "$(dirname "$0")"
force=false
for arg in "$@"; do
  case "$arg" in
    --force) force=true ;;
    *) echo "unknown option $arg" >&2; exit 1 ;;
  esac
done
touch secrets.env
for name in DEMO_RCON DEMO_FORWARDING DEMO_DB_ROOT DEMO_DB_PASSWORD DEMO_GATEWAY_DB DEMO_DASHBOARD_KEY; do
  if grep -q "^$name='[^']" secrets.env && [ "$force" = false ]; then
    continue
  fi
  case "$name" in
    DEMO_DASHBOARD_KEY) value="cst_$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')" ;;
    *) value=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9') ;;
  esac
  grep -v "^$name=" secrets.env > secrets.env.tmp || true
  printf "%s='%s'\n" "$name" "$value" >> secrets.env.tmp
  mv secrets.env.tmp secrets.env
done

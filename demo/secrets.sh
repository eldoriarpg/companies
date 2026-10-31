#!/bin/sh
# Fills missing files in secrets/ from the 1Password references in secrets.op, or with random values for a local run
# (--local); --force replaces files that exist.
set -eu
cd "$(dirname "$0")"
local=false
force=false
for arg in "$@"; do
  case "$arg" in
    --local) local=true ;;
    --force) force=true ;;
    *) echo "unknown option $arg" >&2; exit 1 ;;
  esac
done
mkdir -p secrets
chmod 700 secrets
while read -r name reference; do
  [ -z "$name" ] && continue
  if [ -s "secrets/$name" ] && [ "$force" = false ]; then
    continue
  fi
  if [ "$local" = true ]; then
    case "$name" in
      lyna_gateway_secret|dashboard_key) : > "secrets/$name" ;;
      *) head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "secrets/$name" ;;
    esac
  else
    op read "$reference" > "secrets/$name"
  fi
  chmod 644 "secrets/$name"
done < secrets.op

#!/bin/bash
# Resets the demo network to its seed: nightly at RESET_AT ("schedule", the default), at once ("now"), or stores the
# seed paths of the running demo as the new seed ("capture").
set -euo pipefail

SEED=${SEED_DIR:-/seed}
CAPTURE=${CAPTURE_DIR:-/capture}
PATHS=/reset/paths
LOCK=/nodes/hub/.demo-reset.lock
DATABASES=(companies luckperms economy)
NODES=(hub lands)

log() { echo "[reset] $(date '+%F %T') $*"; }

project() {
  docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$(hostname)"
}

container() {
  local id
  id=$(docker ps -aq --filter "label=com.docker.compose.project=$(project)" --filter "label=com.docker.compose.service=$1")
  [ -n "$id" ] || { log "no container for $1"; return 1; }
  echo "$id"
}

running() {
  [ "$(docker inspect -f '{{.State.Running}}' "$(container "$1")")" = true ]
}

announce() {
  for node in "${NODES[@]}"; do
    if running "$node"; then
      docker exec "$(container "$node")" rcon-cli \
        "tellraw @a {\"text\":\"[Demo] $1\",\"color\":\"gold\"}" >/dev/null 2>&1 || true
    fi
  done
}

lock() {
  exec 9>"$LOCK"
  flock -n 9 || { log "another reset or capture is running"; exit 1; }
}

stop_nodes() {
  local lands hub
  lands=$(container lands)
  hub=$(container hub)
  trap start_nodes EXIT
  docker stop -t 60 "$lands" "$hub" >/dev/null
}

start_nodes() {
  local hub=0
  docker start "$(container hub)" >/dev/null
  wait_healthy hub || hub=1
  docker start "$(container lands)" >/dev/null
  trap - EXIT
  wait_healthy lands
  return "$hub"
}

wait_healthy() {
  local id
  id=$(container "$1")
  for _ in $(seq 1 120); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$id")" = healthy ] && return 0
    sleep 5
  done
  log "$1 did not become healthy"
  return 1
}

node_paths() {
  awk -v node="$1" -v kind="$2" '$1 == node && $2 == kind { print $3 }' "$PATHS"
}

sql() {
  MYSQL_PWD="$(cat /run/secrets/db_password)" mariadb -h mariadb -u demo "$@"
}

reset_databases() {
  for db in "${DATABASES[@]}"; do
    sql -e "DROP DATABASE IF EXISTS \`$db\`; CREATE DATABASE \`$db\`"
    if [ -f "$SEED/$db.sql" ]; then
      log "loading $db.sql"
      sql "$db" < "$SEED/$db.sql"
    fi
  done
  redis-cli -h redis FLUSHALL >/dev/null
}

remove_paths() {
  local data=/nodes/$1 path
  while read -r path; do
    rm -rf "${data:?}/$path"
  done < <(node_paths "$1" "$2")
}

reset_worlds() {
  for node in "${NODES[@]}"; do
    remove_paths "$node" seed
    remove_paths "$node" wipe
    if [ -f "$SEED/$node.tar.gz" ]; then
      log "restoring $node.tar.gz"
      tar -xzf "$SEED/$node.tar.gz" -C "/nodes/$node"
      remove_paths "$node" wipe
    fi
    chown -R 1000:1000 "/nodes/$node"
  done
}

reset() {
  lock
  announce "The demo resets now."
  log "reset starts"
  stop_nodes
  reset_databases
  reset_worlds
  start_nodes
  log "reset done"
}

capture() {
  lock
  log "capture starts"
  local node path
  for node in "${NODES[@]}"; do
    while read -r path; do
      [ -e "/nodes/$node/$path" ] || { log "$node has no $path"; exit 1; }
    done < <(node_paths "$node" seed)
  done
  stop_nodes
  for node in "${NODES[@]}"; do
    local seeds=() excludes=()
    mapfile -t seeds < <(node_paths "$node" seed)
    while read -r path; do
      excludes+=("--exclude=./$path" "--exclude=$path")
    done < <(node_paths "$node" wipe)
    tar -czf "$CAPTURE/$node.tar.gz.tmp" "${excludes[@]}" -C "/nodes/$node" "${seeds[@]}"
    mv "$CAPTURE/$node.tar.gz.tmp" "$CAPTURE/$node.tar.gz"
    log "stored $node.tar.gz"
  done
  start_nodes
  log "capture done"
}

sleep_until() {
  local seconds=$(( $1 - $(date +%s) ))
  if [ "$seconds" -gt 0 ]; then sleep "$seconds"; fi
}

warn_at() {
  if [ "$1" -gt "$(date +%s)" ]; then
    sleep_until "$1"
    announce "$2"
  fi
}

next_reset() {
  local at
  at=$(date -d "today $RESET_AT" +%s)
  [ "$at" -gt "$(date +%s)" ] || at=$(date -d "tomorrow $RESET_AT" +%s)
  echo "$at"
}

schedule() {
  date -d "today $RESET_AT" >/dev/null 2>&1 || { log "RESET_AT '$RESET_AT' is not a time of day"; exit 1; }
  while true; do
    local at
    at=$(next_reset)
    log "next reset at $(date -d "@$at" '+%F %T')"
    warn_at $(( at - 600 )) "The demo resets in 10 minutes. Everything goes back to the start."
    warn_at $(( at - 60 )) "The demo resets in 1 minute."
    sleep_until "$at"
    "$0" now || log "reset failed"
  done
}

case "${1:-schedule}" in
  schedule) schedule ;;
  now) reset ;;
  capture) capture ;;
  *) echo "usage: demo-reset.sh [schedule|now|capture]" >&2; exit 1 ;;
esac

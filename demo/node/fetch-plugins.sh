#!/bin/sh
# Downloads the plugins listed in plugins.txt into /demo/<node>/plugins and checks their SHA-256.
set -eu
while read -r nodes sum url; do
  [ -z "$nodes" ] && continue
  file="/tmp/$(basename "$url")"
  curl -fsSL -o "$file" "$url"
  echo "$sum  $file" | sha256sum -c -
  for node in hub lands; do
    if [ "$nodes" = all ] || [ "$nodes" = "$node" ]; then
      mkdir -p "/demo/$node/plugins"
      cp "$file" "/demo/$node/plugins/"
    fi
  done
done < "$1"

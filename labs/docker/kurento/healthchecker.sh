#!/bin/bash

set -o errexit -o nounset

RC="$(curl \
  --silent \
  --no-buffer \
  --connect-timeout 4 \
  --max-time 4 \
  --write-out '%{http_code}' \
  --header "Connection: Upgrade" \
  --header "Upgrade: websocket" \
  --header "Host: 127.0.0.1:$PORT" \
  --header "Origin: 127.0.0.1" \
  http://127.0.0.1:$PORT/kurento)"

if [[ "$RC" == "500" ]]; then
  exit 0
else
  exit 1
fi

#!/usr/bin/env bash
# ===============================================
#  Shiwei HM-DianPing  -  stop all containers
# ===============================================
set -euo pipefail
cd "$(dirname "$0")"

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

echo "Stopping containers ..."
$COMPOSE down
echo
echo "Data volumes kept. To wipe them as well: $COMPOSE down -v"

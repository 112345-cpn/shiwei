#!/usr/bin/env bash
# ===============================================
#  Shiwei HM-DianPing  -  one click start (Linux / macOS / WSL)
# ===============================================
set -euo pipefail
cd "$(dirname "$0")"

echo "=========================================="
echo "  Shiwei  HM-DianPing  -  starting"
echo "=========================================="

command -v docker >/dev/null 2>&1 || { echo "[ERROR] docker not found"; exit 1; }
docker info >/dev/null 2>&1 || { echo "[ERROR] docker engine not running"; exit 1; }

# ---------- pick compose command: v2 plugin first, fall back to v1 ----------
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo "[ERROR] neither 'docker compose' nor 'docker-compose' is available"
    exit 1
fi

# ---------- .env from template on first run ----------
if [ ! -f .env ]; then
    echo "[INFO ] .env not found, creating from .env.example"
    cp .env.example .env
fi

echo "[1/2] building and starting containers"
echo "      first run compiles the jar inside a Maven image, 3-8 min is normal"
$COMPOSE up -d --build

# ---------- wait until the web port answers ----------
echo "[2/2] waiting for http://localhost:8080 ..."
for i in $(seq 1 90); do
    if curl -sf -o /dev/null http://localhost:8080/; then
        break
    fi
    sleep 2
done

echo
$COMPOSE ps
cat <<'EOF'

==========================================
  Front end : http://localhost:8080
  Back end  : http://localhost:8081
  RabbitMQ  : http://localhost:15672   (guest / guest)
  Stop      : ./stop.sh
==========================================
EOF

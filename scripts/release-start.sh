#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
PORT="${SERVER_PORT:-8084}"
echo "Starting BMS 计费管理系统 on http://127.0.0.1:$PORT"
exec java ${JAVA_OPTS:-} -jar bms-backend-1.0.0.jar --server.port="$PORT" 

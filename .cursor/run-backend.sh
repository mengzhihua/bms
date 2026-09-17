#!/usr/bin/env bash
# Start the BMS Spring Boot API on :8080 (H2 file DB, auto-seeds demo data).
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backend"

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export BMS_ADMIN_PASSWORD="${BMS_ADMIN_PASSWORD:-admin123}"
export BMS_OPEN_API_KEY="${BMS_OPEN_API_KEY:-dev-open-key}"
export BMS_AUTH_SECRET="${BMS_AUTH_SECRET:-dev-secret}"
export BMS_H2_CONSOLE="${BMS_H2_CONSOLE:-true}"

exec mvn -q spring-boot:run

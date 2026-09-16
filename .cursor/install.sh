#!/usr/bin/env bash
# BMS 结算管理系统 — Cloud Agent dependency bootstrap.
# Idempotent: safe to run repeatedly and against cached/prebuilt state.
set -euo pipefail

# Resolve the repo root (parent of this .cursor dir) so the script works
# regardless of the caller's working directory.
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# System toolchains the default base image lacks: JDK 17 (backend runtime),
# Maven (backend build), and bc (used by scripts/smoke.sh). These get baked into
# the environment build snapshot on first run, so the guard makes reruns a no-op.
if ! command -v mvn >/dev/null 2>&1 \
  || ! command -v bc >/dev/null 2>&1 \
  || [ ! -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk maven bc
fi

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Backend: resolve dependencies and compile with JDK 17 (tests run separately).
( cd backend && mvn -q -B -DskipTests package )

# Frontend: install locked dependencies.
( cd frontend && npm ci )

echo "BMS install complete."

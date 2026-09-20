#!/usr/bin/env bash
# Start the BMS Vite dev server on :5173 (proxies /api to the backend on :8080).
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/frontend"

exec npm run dev

#!/usr/bin/env bash
#
# Local dev runner: starts the backend (dev profile) and the Angular dev server
# side by side, with prefixed, colour-coded logs. Ctrl-C stops both.
#
# The database is NOT started here — bring it up once with:
#   docker compose up -d testmanagement-db
#
# Usage:
#   ./scripts/dev.sh              # backend + frontend
#   ./scripts/dev.sh backend      # backend only
#   ./scripts/dev.sh frontend     # frontend only
#
# Env overrides:
#   JAVA_HOME     JDK 25 to build with (defaults to whatever `java` resolves to)
#   DB_URL / DB_USERNAME / DB_PASSWORD   passed through to Spring
#   SKIP_DB_CHECK=1                      skip the Postgres reachability probe

set -euo pipefail
set -m   # job control: each background job gets its own process group, so we can
         # kill the whole tree (maven -> java, npx -> ng) on Ctrl-C

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
BACKEND_PORT=8089
FRONTEND_PORT=4200

C_BE=$'\033[36m'   # cyan
C_FE=$'\033[35m'   # magenta
C_WARN=$'\033[33m'
C_ERR=$'\033[31m'
C_OFF=$'\033[0m'

log()  { printf '%s[dev]%s %s\n' "$C_BE" "$C_OFF" "$*"; }
warn() { printf '%s[dev]%s %s\n' "$C_WARN" "$C_OFF" "$*"; }
die()  { printf '%s[dev]%s %s\n' "$C_ERR" "$C_OFF" "$*" >&2; exit 1; }

TARGET="${1:-all}"
case "$TARGET" in
  all|backend|frontend) ;;
  *) die "unknown target '$TARGET' (use: all | backend | frontend)" ;;
esac

# --- preflight ---------------------------------------------------------------

check_java() {
  local java_bin="java"
  [[ -n "${JAVA_HOME:-}" ]] && java_bin="$JAVA_HOME/bin/java"
  command -v "$java_bin" >/dev/null 2>&1 || die "java not found. Install JDK 25 or set JAVA_HOME."
  local ver
  ver="$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
  if [[ "$ver" =~ ^[0-9]+$ ]] && (( ver < 25 )); then
    die "Java $ver found, but the backend needs 25. Set JAVA_HOME to a JDK 25 install."
  fi
}

check_db() {
  [[ "${SKIP_DB_CHECK:-0}" == "1" ]] && return 0
  if command -v pg_isready >/dev/null 2>&1; then
    pg_isready -h "$DB_HOST" -p "$DB_PORT" -q && return 0
  elif command -v nc >/dev/null 2>&1; then
    nc -z "$DB_HOST" "$DB_PORT" 2>/dev/null && return 0
  else
    warn "no pg_isready/nc — skipping DB check"
    return 0
  fi
  die "Postgres not reachable on $DB_HOST:$DB_PORT. Start it with: docker compose up -d testmanagement-db"
}

port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# --- process management ------------------------------------------------------

PIDS=()

cleanup() {
  trap - INT TERM EXIT
  log "shutting down..."
  for pid in "${PIDS[@]:-}"; do
    [[ -n "$pid" ]] && kill -TERM -"$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  log "stopped."
}
trap cleanup INT TERM EXIT

# Runs a command in its own process group, piping output through a coloured prefix.
spawn() {
  local name="$1" colour="$2" dir="$3"; shift 3
  (
    cd "$dir"
    "$@" 2>&1 | while IFS= read -r line; do
      printf '%s[%s]%s %s\n' "$colour" "$name" "$C_OFF" "$line"
    done
  ) &
  PIDS+=("$!")
}

# --- go ----------------------------------------------------------------------

if [[ "$TARGET" == "all" || "$TARGET" == "backend" ]]; then
  check_java
  check_db
  port_busy "$BACKEND_PORT" && die "port $BACKEND_PORT is already in use (backend running?)"
  log "backend  -> http://localhost:$BACKEND_PORT  (profile: dev)"
  spawn "backend" "$C_BE" "$BACKEND" \
    ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev
fi

if [[ "$TARGET" == "all" || "$TARGET" == "frontend" ]]; then
  command -v npm >/dev/null 2>&1 || die "npm not found. Install Node.js 22+."
  if [[ ! -d "$FRONTEND/node_modules" ]]; then
    log "installing frontend dependencies..."
    (cd "$FRONTEND" && npm install)
  fi
  port_busy "$FRONTEND_PORT" && die "port $FRONTEND_PORT is already in use (ng serve running?)"
  log "frontend -> http://localhost:$FRONTEND_PORT  (/api proxied to :$BACKEND_PORT)"
  spawn "frontend" "$C_FE" "$FRONTEND" \
    npx ng serve --port "$FRONTEND_PORT"
fi

if [[ "$TARGET" != "frontend" ]]; then
  log "login: ${ADMIN_EMAIL:-admin@localhost.ch} / ${ADMIN_PASSWORD:-admin}"
fi
log "Ctrl-C to stop"
wait

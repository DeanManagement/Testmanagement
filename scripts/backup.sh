#!/usr/bin/env bash
#
# Instance backup (PRD-039 Phase A): a pg_dump of the compose database, which holds everything,
# screenshots, step images and Allure reports included. The app container is stateless.
#
# Usage:
#   ./scripts/backup.sh [output-dir]      # default: ./backups
#
# Writes testmanagement-<UTC timestamp>-V<schema>.dump in PostgreSQL custom format, for
# ./scripts/restore.sh. The file only gets its final name once the dump has succeeded, so a failed
# or interrupted backup never leaves something that looks usable.
#
# Runs pg_dump inside the database container, so the host needs no PostgreSQL tools. Uses the
# .env next to docker-compose.yml like `docker compose` does.

set -euo pipefail

DB_SERVICE="testmanagement-db"
DB_USER="testmanagement"
DB_NAME="testmanagement"

die() { printf 'backup: %s\n' "$*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-$ROOT/backups}"
mkdir -p "$OUT_DIR" || die "cannot create $OUT_DIR"
# Resolved before the cd below, so a relative path means relative to where you ran the script.
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
cd "$ROOT"

command -v docker >/dev/null 2>&1 || die "docker not found"

psql_value() {
  docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"
}

schema="$(psql_value "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+\$'" \
  2>/dev/null || true)"
[[ "$schema" =~ ^[0-9]+$ ]] \
  || die "could not read the schema version. Is the database running and has the app started on it once? (docker compose up -d $DB_SERVICE)"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
final="$OUT_DIR/testmanagement-$stamp-V$schema.dump"
partial="$OUT_DIR/.testmanagement-$stamp-V$schema.dump.partial"
trap 'rm -f "$partial"' EXIT

docker compose exec -T "$DB_SERVICE" pg_dump -U "$DB_USER" -Fc "$DB_NAME" > "$partial" \
  || die "pg_dump failed; nothing was written"
[[ -s "$partial" ]] || die "pg_dump produced an empty file; nothing was written"
mv "$partial" "$final"
trap - EXIT

printf 'Backup written: %s (%s)\n' "$final" "$(du -h "$final" | cut -f1)"
cat <<'EOF'

Back up your .env as well, separately and just as carefully: the dump does not contain it.
  - Losing APP_ENCRYPTION_KEY means re-entering every stored issue-tracker token,
    build-server token and OIDC client secret.
  - Losing JWT_SECRET signs everyone out.
EOF

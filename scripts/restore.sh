#!/usr/bin/env bash
#
# Instance restore (PRD-039 Phase A) from a dump made by ./scripts/backup.sh.
#
# Usage:
#   ./scripts/restore.sh <file.dump>
#
# Refuses, and writes nothing, unless:
#   - the app container is stopped, so nothing writes during the restore and Flyway runs once
#     afterwards;
#   - the target database is empty. There is no --force: restoring over live data is how backups
#     destroy data. Start from an empty volume (see the message below);
#   - the dump's schema is not newer than this checkout. An older dump is fine: the app migrates
#     it forward when it starts, which is the normal upgrade path. The version is read from the
#     dump's own migration history, not trusted from the file name.
#
# Runs pg_restore inside the database container, so the host needs no PostgreSQL tools.

set -euo pipefail

DB_SERVICE="testmanagement-db"
APP_SERVICE="testmanagement"
DB_USER="testmanagement"
DB_NAME="testmanagement"

die() { printf 'restore: %s\n' "$*" >&2; exit 1; }

[[ $# -eq 1 ]] || die "usage: $0 <file.dump>"
DUMP="$1"
[[ -f "$DUMP" && -s "$DUMP" ]] || die "no such dump, or it is empty: $DUMP"
DUMP="$(cd "$(dirname "$DUMP")" && pwd)/$(basename "$DUMP")"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
command -v docker >/dev/null 2>&1 || die "docker not found"

psql_value() {
  docker compose exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"
}

# --- the app must be stopped ----------------------------------------------------------------------
if docker compose ps --status running --services 2>/dev/null | grep -qx "$APP_SERVICE"; then
  die "the app container is running. Stop it first: docker compose stop $APP_SERVICE"
fi

# --- the database must be up and empty ------------------------------------------------------------
tables="$(psql_value "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" 2>/dev/null || true)"
[[ "$tables" =~ ^[0-9]+$ ]] || die "cannot reach the database. Start it: docker compose up -d $DB_SERVICE"
if [[ "$tables" -ne 0 ]]; then
  die "the database is not empty ($tables tables). Restore only goes into an empty database.
  To start from an empty one, DELETING EVERYTHING currently in it:
    docker compose down -v                      # removes the containers AND the database volume
    docker compose up -d $DB_SERVICE
  then run this again. Take a backup of the current data first if you might need it."
fi

# --- the dump must not be newer than this checkout ------------------------------------------------
# Highest V<n> this checkout knows, across the vendor-neutral and vendor-specific migrations.
local_version="$(find backend/src/main/resources/db/migration backend/src/main/resources/db/specific \
  -name 'V*__*.sql' 2>/dev/null | sed -E 's|.*/V([0-9]+)__.*|\1|' | sort -n | tail -1)"
[[ "$local_version" =~ ^[0-9]+$ ]] || die "cannot find the migrations of this checkout under backend/src/main/resources/db"

# The migration history as the dump holds it: tab-separated COPY rows, version in column 2 and
# success (t/f) in the last.
dump_version="$(docker compose exec -T "$DB_SERVICE" pg_restore --data-only --table=flyway_schema_history --file=- \
  < "$DUMP" 2>/dev/null \
  | awk -F'\t' '/^COPY /{rows=1; next} /^\\\.$/{rows=0} rows && $NF=="t" && $2 ~ /^[0-9]+$/ {print $2}' \
  | sort -n | tail -1 || true)"
[[ "$dump_version" =~ ^[0-9]+$ ]] \
  || die "$DUMP does not look like a backup of this app (no migration history found in it)"
if [[ "$dump_version" -gt "$local_version" ]]; then
  die "the dump is at schema V$dump_version but this checkout only knows up to V$local_version.
  Upgrade the app first (git pull), then restore."
fi

# --- restore ---------------------------------------------------------------------------------------
echo "Restoring $DUMP (schema V$dump_version) ..."
docker compose exec -T "$DB_SERVICE" pg_restore -U "$DB_USER" -d "$DB_NAME" \
  --single-transaction --exit-on-error --no-owner < "$DUMP" \
  || die "pg_restore failed; it ran in one transaction, so the database is still empty"

if [[ "$dump_version" -lt "$local_version" ]]; then
  echo "Restored. The app will migrate it from V$dump_version to V$local_version when it starts."
else
  echo "Restored."
fi
echo "Start the app: docker compose up -d"

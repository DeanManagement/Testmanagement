#!/usr/bin/env bash
#
# Tests for scripts/backup.sh and scripts/restore.sh (PRD-039 Phase A) against a stub `docker`, so
# they run without Docker. The real round trip (backup, wipe, restore, start) is the manual check
# in the PRD's §8.
#
# Usage: ./scripts/tests/backup-restore.test.sh

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/backup-restore-test.XXXXXX")" || { echo "cannot create a work directory" >&2; exit 1; }
[[ -d "$WORK" ]] || { echo "cannot create a work directory" >&2; exit 1; }
trap 'rm -rf "$WORK"' EXIT
LOCAL_VERSION="$(find "$ROOT/backend/src/main/resources/db/migration" "$ROOT/backend/src/main/resources/db/specific" \
  -name 'V*__*.sql' | sed -E 's|.*/V([0-9]+)__.*|\1|' | sort -n | tail -1)"

# The stub answers from environment variables:
#   STUB_SCHEMA          what the schema-version query returns (empty: database unreachable)
#   STUB_TABLES          what the table-count query returns (empty: database unreachable)
#   STUB_RUNNING         services `compose ps --status running --services` lists
#   STUB_DUMP_FAIL=1     pg_dump writes a little, then fails
#   STUB_DUMP_VERSION    highest successful migration in the dump's history
# and records every pg_restore into the database in $WORK/restored.
mkdir -p "$WORK/bin"
cat > "$WORK/bin/docker" <<'STUB'
#!/usr/bin/env bash
args="$*"
case "$args" in
  "compose ps --status running --services") printf '%s\n' ${STUB_RUNNING:-} ;;
  *" psql "*"flyway_schema_history"*) [[ -n "${STUB_SCHEMA:-}" ]] && echo "$STUB_SCHEMA" || exit 2 ;;
  *" psql "*"information_schema.tables"*) [[ -n "${STUB_TABLES:-}" ]] && echo "$STUB_TABLES" || exit 2 ;;
  *" pg_dump "*)
    printf 'PGDMP-partial'
    [[ "${STUB_DUMP_FAIL:-0}" == 1 ]] && exit 1
    printf -- '-complete' ;;
  *" pg_restore "*"--table=flyway_schema_history"*)
    cat > /dev/null
    printf 'COPY public.flyway_schema_history (installed_rank, version, success) FROM stdin;\n'
    printf '1\t1\tcreate projects\tSQL\tV1.sql\t1\tu\t2026-01-01\t5\tt\n'
    printf '2\t%s\tlatest\tSQL\tVn.sql\t1\tu\t2026-01-01\t5\tt\n' "$STUB_DUMP_VERSION"
    printf '3\t%s\tfailed later one\tSQL\tVx.sql\t1\tu\t2026-01-01\t5\tf\n' "$((STUB_DUMP_VERSION + 50))"
    printf '\\.\n' ;;
  *" pg_restore "*" -d "*) cat > /dev/null; echo restored >> "$STUB_LOG" ;;
  *) echo "stub docker: unexpected: $args" >&2; exit 99 ;;
esac
STUB
chmod +x "$WORK/bin/docker"
export PATH="$WORK/bin:$PATH" STUB_LOG="$WORK/restored"

failures=0
pass() { printf 'ok   %s\n' "$1"; }
fail() { printf 'FAIL %s\n     %s\n' "$1" "$2"; failures=$((failures + 1)); }

# run <script> [args...]: sets $status and $output
run() { output="$("$@" 2>&1)"; status=$?; }

reset() {
  unset STUB_SCHEMA STUB_TABLES STUB_RUNNING STUB_DUMP_FAIL STUB_DUMP_VERSION
  rm -rf "$WORK/out" "$STUB_LOG"
  mkdir -p "$WORK/out"
}

dump_file() {
  printf 'PGDMP' > "$WORK/out/$1"
  printf '%s' "$WORK/out/$1"
}

# --- backup ---------------------------------------------------------------------------------------
reset; export STUB_SCHEMA=61
run "$ROOT/scripts/backup.sh" "$WORK/out"
files="$(ls -A "$WORK/out")"
if [[ $status -eq 0 && "$files" =~ ^testmanagement-[0-9]{8}T[0-9]{6}Z-V61\.dump$ \
      && "$(cat "$WORK/out/$files")" == "PGDMP-partial-complete" && "$output" == *".env"* ]]; then
  pass "backup writes a timestamped, versioned dump and reminds about .env"
else
  fail "backup writes a timestamped, versioned dump and reminds about .env" "status=$status files=[$files] $output"
fi

reset; export STUB_SCHEMA=61 STUB_DUMP_FAIL=1
run "$ROOT/scripts/backup.sh" "$WORK/out"
files="$(ls -A "$WORK/out")"
if [[ $status -ne 0 && -z "$files" ]]; then
  pass "a failed pg_dump leaves no file behind, not even the partial one"
else
  fail "a failed pg_dump leaves no file behind, not even the partial one" "status=$status files=[$files]"
fi

reset
run "$ROOT/scripts/backup.sh" "$WORK/out"
if [[ $status -ne 0 && "$output" == *"docker compose up -d testmanagement-db"* && -z "$(ls -A "$WORK/out")" ]]; then
  pass "backup refuses when it cannot read the schema version"
else
  fail "backup refuses when it cannot read the schema version" "status=$status $output"
fi

# --- restore --------------------------------------------------------------------------------------
reset; export STUB_TABLES=0 STUB_DUMP_VERSION=$((LOCAL_VERSION - 3))
dump="$(dump_file old.dump)"
run "$ROOT/scripts/restore.sh" "$dump"
if [[ $status -eq 0 && -f "$STUB_LOG" && "$output" == *"migrate it from V$((LOCAL_VERSION - 3)) to V$LOCAL_VERSION"* ]]; then
  pass "restore puts an older dump into an empty database and says it will be migrated"
else
  fail "restore puts an older dump into an empty database and says it will be migrated" "status=$status $output"
fi

reset; export STUB_TABLES=0 STUB_DUMP_VERSION=$LOCAL_VERSION
run "$ROOT/scripts/restore.sh" "$(dump_file same.dump)"
if [[ $status -eq 0 && -f "$STUB_LOG" ]]; then
  pass "restore accepts a dump at exactly this checkout's schema, ignoring failed migrations in its history"
else
  fail "restore accepts a dump at exactly this checkout's schema, ignoring failed migrations in its history" "status=$status $output"
fi

reset; export STUB_TABLES=87 STUB_DUMP_VERSION=$LOCAL_VERSION
run "$ROOT/scripts/restore.sh" "$(dump_file x.dump)"
if [[ $status -ne 0 && ! -f "$STUB_LOG" && "$output" == *"not empty (87 tables)"* && "$output" == *"down -v"* ]]; then
  pass "restore refuses a database that is not empty, and says how to start from an empty one"
else
  fail "restore refuses a database that is not empty, and says how to start from an empty one" "status=$status $output"
fi

reset; export STUB_TABLES=0 STUB_DUMP_VERSION=$((LOCAL_VERSION + 1))
run "$ROOT/scripts/restore.sh" "$(dump_file testmanagement-20260101T000000Z-V1.dump)"
if [[ $status -ne 0 && ! -f "$STUB_LOG" && "$output" == *"Upgrade the app first"* ]]; then
  pass "restore refuses a dump newer than this checkout, whatever its file name claims"
else
  fail "restore refuses a dump newer than this checkout, whatever its file name claims" "status=$status $output"
fi

reset; export STUB_TABLES=0 STUB_DUMP_VERSION=$LOCAL_VERSION STUB_RUNNING="testmanagement-db testmanagement"
run "$ROOT/scripts/restore.sh" "$(dump_file x.dump)"
if [[ $status -ne 0 && ! -f "$STUB_LOG" && "$output" == *"docker compose stop testmanagement"* ]]; then
  pass "restore refuses while the app container is running"
else
  fail "restore refuses while the app container is running" "status=$status $output"
fi

reset; export STUB_DUMP_VERSION=$LOCAL_VERSION
run "$ROOT/scripts/restore.sh" "$(dump_file x.dump)"
if [[ $status -ne 0 && ! -f "$STUB_LOG" && "$output" == *"cannot reach the database"* ]]; then
  pass "restore refuses when the database is not reachable"
else
  fail "restore refuses when the database is not reachable" "status=$status $output"
fi

reset
run "$ROOT/scripts/restore.sh" "$WORK/out/missing.dump"
if [[ $status -ne 0 && ! -f "$STUB_LOG" && "$output" == *"no such dump"* ]]; then
  pass "restore refuses a file that does not exist"
else
  fail "restore refuses a file that does not exist" "status=$status $output"
fi

echo
if [[ $failures -eq 0 ]]; then
  echo "All backup/restore checks passed."
else
  echo "$failures check(s) failed."
  exit 1
fi

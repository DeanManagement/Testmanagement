#!/usr/bin/env bash
# PRD-017 release check: image URLs must NOT be served to unauthenticated callers
# through the full nginx -> backend stack (guards against a shared proxy cache
# reappearing in nginx.conf).
#
# Usage: ./scripts/smoke-media-auth.sh [BASE_URL] [SCREENSHOT_ID] [TOKEN]
#   BASE_URL      default http://localhost:8012 (compose frontend)
#   SCREENSHOT_ID id of any existing screenshot (omit to only check headers shape)
#   TOKEN         a valid JWT; with both set, the script warms the path first,
#                 then asserts the unauthenticated request is rejected.
set -euo pipefail

BASE_URL="${1:-http://localhost:8012}"
ID="${2:-}"
TOKEN="${3:-}"

fail() { echo "SMOKE FAIL: $1" >&2; exit 1; }

if [[ -n "$ID" && -n "$TOKEN" ]]; then
  # Warm: authorized fetch (would populate a shared cache if one existed).
  auth_status=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/screenshots/$ID")
  [[ "$auth_status" == "200" ]] || fail "authorized fetch returned $auth_status (expected 200) — check ID/TOKEN"

  cache_control=$(curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/screenshots/$ID" | tr -d '\r' | grep -i '^cache-control:' || true)
  echo "$cache_control" | grep -qi 'private' || fail "Cache-Control is not private: '$cache_control'"
  echo "$cache_control" | grep -qiv 'public' || fail "Cache-Control contains public: '$cache_control'"

  # The actual bypass check: same URI, no credentials, must be rejected.
  anon_status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/screenshots/$ID")
  [[ "$anon_status" == "401" || "$anon_status" == "403" ]] \
    || fail "unauthenticated fetch returned $anon_status (expected 401/403) — shared cache bypass?"
  echo "OK: warmed path still rejects unauthenticated access ($anon_status); Cache-Control private."
else
  # Header-only mode: any random UUID must be rejected, and no X-Cache-Status header may appear.
  headers=$(curl -s -D - -o /dev/null "$BASE_URL/api/screenshots/00000000-0000-0000-0000-000000000000" | tr -d '\r')
  status=$(echo "$headers" | head -1 | awk '{print $2}')
  [[ "$status" == "401" || "$status" == "403" ]] || fail "unauthenticated fetch returned $status (expected 401/403)"
  echo "$headers" | grep -qi '^x-cache-status:' && fail "nginx proxy cache is active on /api (X-Cache-Status present)"
  echo "OK: unauthenticated access rejected ($status); no proxy-cache header present."
fi

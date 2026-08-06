#!/usr/bin/env bash
# Asserts things about the PRODUCTION frontend build that no unit test can see, because they only
# exist in the built output. Both CI pipelines call this so the two cannot drift apart.
#
# Every check here corresponds to a bug that reached a running deployment:
#
#   1. index.html referenced fonts.googleapis.com, so Material Icons were blocked by the app's own
#      CSP (font-src 'self') and every icon rendered as its ligature text. The app is also meant to
#      run air-gapped, which an external font quietly breaks.
#   2. Angular's inlineCritical optimisation deferred the whole stylesheet behind
#      media="print" onload="this.media='all'". The CSP blocks inline handlers, so the app rendered
#      with critical CSS only — with no error anywhere.
#   3. The SPA has to actually be in the build, or the jar serves nothing.
#
# Usage: ./scripts/check-frontend-bundle.sh [DIST_DIR]
#   DIST_DIR  default frontend/dist/frontend/browser
set -euo pipefail

DIST="${1:-frontend/dist/frontend/browser}"
INDEX="$DIST/index.html"
fail=0

# All diagnostics go to stderr, so a failure and its details stay in order rather than
# interleaving across two streams in the CI log.
note() { printf '    %s\n' "$*" >&2; }
bad()  { printf '✗ %s\n' "$*" >&2; fail=1; }
ok()   { printf '✓ %s\n' "$*" >&2; }

[ -f "$INDEX" ] || { bad "no index.html in $DIST — did the build run?"; exit 1; }

# 1. No external references. Anything not served by us is blocked by default-src 'self'.
if grep -qoE 'https?://[a-zA-Z0-9.-]+' "$INDEX"; then
  bad "index.html references an external host — the CSP allows 'self' only:"
  grep -oE 'https?://[a-zA-Z0-9.-]+' "$INDEX" | sort -u | while read -r host; do note "$host"; done
else
  ok "no external hosts in index.html"
fi

# 2. No inline event handlers. script-src-attr is not permitted, so these silently never run.
if grep -qoE '\son[a-z]+="' "$INDEX"; then
  bad "index.html contains an inline event handler, which the CSP blocks:"
  grep -oE '\son[a-z]+="[^"]*"' "$INDEX" | sort -u | while read -r handler; do note "$handler"; done
  note "if this is Angular's inlineCritical, keep optimization.styles.inlineCritical=false"
else
  ok "no inline event handlers in index.html"
fi

# 3. The app root and a stylesheet are actually present.
grep -qi '<app-root' "$INDEX" || bad "index.html has no <app-root> — this is not the SPA shell"
grep -qi '<link[^>]*rel="stylesheet"' "$INDEX" || bad "index.html links no stylesheet"

# 4. The icon font ships with us rather than being fetched at runtime.
if find "$DIST" -name 'material-icons*.woff2' -print -quit | grep -q .; then
  ok "Material Icons font is bundled"
else
  bad "no bundled Material Icons woff2 — the icon font must not come from a CDN"
fi

[ "$fail" -eq 0 ] || { echo "frontend bundle checks failed" >&2; exit 1; }
echo "frontend bundle checks passed"

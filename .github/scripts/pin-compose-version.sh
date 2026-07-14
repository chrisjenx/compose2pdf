#!/usr/bin/env bash
#
# Pin the Compose Multiplatform + Kotlin versions in a Gradle version catalog.
#
# Used by the compatibility workflow's `nextdriver-guard` job to build the library
# against a frozen reshaped CMP release (so the reflective NextDriver path is exercised).
# Parameterised and path-injectable so it can be run and tested locally against a
# throwaway copy without touching the real catalog:
#
#   cp gradle/libs.versions.toml /tmp/libs.toml
#   .github/scripts/pin-compose-version.sh 1.12.0-beta01 2.4.0 /tmp/libs.toml
#   grep -E '^(kotlin|compose-multiplatform) = ' /tmp/libs.toml
#
set -euo pipefail

usage() {
  echo "usage: $(basename "$0") <compose-version> <kotlin-version> [libs-versions-toml]" >&2
  exit 2
}

COMPOSE="${1:-}"
KOTLIN="${2:-}"
LIBS="${3:-gradle/libs.versions.toml}"

[ -n "$COMPOSE" ] && [ -n "$KOTLIN" ] || usage
[ -f "$LIBS" ] || { echo "ERROR: version catalog not found: $LIBS" >&2; exit 1; }

perl -i -pe "
  s/^kotlin = \".*\"/kotlin = \"$KOTLIN\"/;
  s/^compose-multiplatform = \".*\"/compose-multiplatform = \"$COMPOSE\"/;
" "$LIBS"

# Fixed-string, whole-line checks so a malformed edit fails loudly instead of
# silently building against the wrong runtime.
grep -qxF "kotlin = \"$KOTLIN\"" "$LIBS" \
  || { echo "ERROR: failed to pin kotlin to $KOTLIN in $LIBS" >&2; exit 1; }
grep -qxF "compose-multiplatform = \"$COMPOSE\"" "$LIBS" \
  || { echo "ERROR: failed to pin compose-multiplatform to $COMPOSE in $LIBS" >&2; exit 1; }

grep -E '^(kotlin|compose-multiplatform) = ' "$LIBS"

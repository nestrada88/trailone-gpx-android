#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
kotlinc \
  app/src/main/java/com/trailone/gpx/core/GpxEngine.kt \
  app/src/main/java/com/trailone/gpx/ui/GuidedSession.kt \
  tools/GuidedSessionSmoke.kt \
  -include-runtime -d "$TEMP_DIR/guided-smoke.jar"
java -jar "$TEMP_DIR/guided-smoke.jar"

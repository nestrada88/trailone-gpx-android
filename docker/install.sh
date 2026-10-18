#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
APK="$ROOT/dist/TrailOneGPX-debug.apk"
command -v adb >/dev/null || { echo 'Install Android platform-tools (adb) first.' >&2; exit 127; }
[[ -s "$APK" ]] || { echo "Missing APK: $APK. Build first with bash docker/build.sh" >&2; exit 1; }
CONNECTED="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count+0 }')"
[[ "$CONNECTED" -eq 1 ]] || { echo "Expected exactly one authorized Android device, found $CONNECTED. Run adb devices." >&2; exit 1; }
echo "Installing $(basename "$APK") via ADB; existing app data will be kept if signatures match."
adb install -r "$APK"

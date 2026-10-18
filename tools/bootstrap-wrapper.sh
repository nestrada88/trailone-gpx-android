#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle 8.13 is required to generate the official Gradle wrapper. Install Gradle or use Android Studio.' >&2
  exit 1
fi
gradle wrapper --gradle-version 8.13 --distribution-type bin
printf 'Official Gradle wrapper generated. Run ./gradlew :app:assembleDebug\n'

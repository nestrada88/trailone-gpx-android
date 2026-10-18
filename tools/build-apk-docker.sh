#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$PROJECT_DIR"

if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed on the host." >&2
    exit 127
fi

mkdir -p "$PROJECT_DIR/dist"
# A failed Docker build still writes a diagnostic log on the host.
set -o pipefail
docker buildx build \
    --progress=plain \
    --file Dockerfile.android \
    --target apk \
    --output "type=local,dest=$PROJECT_DIR/dist" \
    . 2>&1 | tee "$PROJECT_DIR/android-build.log"

if [[ ! -s "$PROJECT_DIR/dist/TrailOneGPX-debug.apk" ]]; then
    echo "Error: Gradle completed but the expected APK was not produced." >&2
    exit 1
fi
printf 'APK built: %s\n' "$PROJECT_DIR/dist/TrailOneGPX-debug.apk"

#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
    echo 'ERROR: Docker CLI is required on the Linux host.' >&2
    exit 127
fi
if ! docker buildx version >/dev/null 2>&1; then
    echo 'ERROR: Docker Buildx plugin is required (docker-buildx-plugin).' >&2
    exit 127
fi
if ! docker info >/dev/null 2>&1; then
    echo 'ERROR: Docker daemon inaccessible. Check service and user permissions.' >&2
    exit 1
fi

# Prevent accidental source-control inclusion of the private key, even with git add -A.
mkdir -p .local/signing dist logs
chmod 700 .local .local/signing
if [[ ! -f .local/.gitignore ]]; then
    printf '*\n!.gitignore\n' > .local/.gitignore
fi
KEY="$ROOT/.local/signing/trailone-internal-debug.keystore"
BASE_IMAGE='gradle:8.13-jdk17-noble@sha256:ec973fb6579488ecba8cd24f4728cd819eda45d6b26f7f44a81682e1c034898f'

if [[ ! -s "$KEY" ]]; then
    echo 'Generating ONE stable signing key for this internal device.'
    echo 'Back up .local/signing/trailone-internal-debug.keystore; losing it prevents in-place updates.'
    docker run --rm --user "$(id -u):$(id -g)" \
        --mount "type=bind,src=$ROOT/.local/signing,dst=/signing" \
        "$BASE_IMAGE" keytool -genkeypair -noprompt \
        -keystore /signing/trailone-internal-debug.keystore \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=TrailONE 505 Internal Debug,O=TrailONE 505,C=NI'
    chmod 600 "$KEY"
fi

# Check key and alias before spending time resolving Android dependencies.
docker run --rm --user "$(id -u):$(id -g)" \
    --mount "type=bind,src=$ROOT/.local/signing,dst=/signing,readonly" \
    "$BASE_IMAGE" keytool -list \
    -keystore /signing/trailone-internal-debug.keystore \
    -storepass android -alias androiddebugkey >/dev/null

# Cached Docker builds with a rotated key must NOT reuse a previously signed APK.
KEY_SHA="$(sha256sum "$KEY" | awk '{print $1}')"
DATE="$(date -u +%Y%m%dT%H%M%SZ)"
LOG="$ROOT/logs/docker-build-$DATE.log"
printf '%s\n' "$LOG" > "$ROOT/logs/latest-log-path.txt"
# Avoid delivering a stale APK if the following build fails.
rm -f "$ROOT/dist/TrailOneGPX-debug.apk" "$ROOT/dist/TrailOneGPX-debug.apk.sha256"

echo "Building TrailOne GPX (Android 16, API 36). Log: $LOG"
echo 'Docker image resolves all Gradle, Kotlin, Android SDK, test and APK tasks.'
set +e
docker buildx build \
    --progress=plain \
    --file docker/Dockerfile \
    --target artifacts \
    --secret "id=trailone_debug_keystore,src=$KEY" \
    --build-arg "SIGNING_KEY_SHA256=$KEY_SHA" \
    --output "type=local,dest=$ROOT/dist" \
    . 2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}
set -e
if (( STATUS != 0 )); then
    echo "BUILD FAILED (Docker exit status $STATUS). Full log: $LOG" >&2
    grep -nE -m 30 '(^|[[:space:]])e:|error:|FAILURE:|failed to build|ERROR:' "$LOG" > "$ROOT/logs/last-errors.txt" || true
    exit "$STATUS"
fi
if [[ ! -s "$ROOT/dist/TrailOneGPX-debug.apk" ]]; then
    echo "ERROR: Docker exited successfully but the APK is missing. Log: $LOG" >&2
    exit 1
fi
(
  cd "$ROOT/dist"
  sha256sum TrailOneGPX-debug.apk > TrailOneGPX-debug.apk.sha256
  sha256sum -c TrailOneGPX-debug.apk.sha256
)
printf '\nSUCCESS: %s\n' "$ROOT/dist/TrailOneGPX-debug.apk"
printf 'JUnit XML: %s\n' "$ROOT/dist/reports/testDebugUnitTest"
printf 'Full build log: %s\n' "$LOG"
echo 'To install: adb install -r dist/TrailOneGPX-debug.apk'

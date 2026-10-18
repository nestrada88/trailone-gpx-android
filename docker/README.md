# TrailONE 505 — Android Docker build environment

**Status:** proposed local build environment, not a verified compiled APK or public release. This directory was added without altering the approved five-stage UI source, existing GPX engine, Gradle configuration, brand resources, or original Dockerfile. Do not update source-lock SHA values for this addition.

## Host prerequisites

- Linux with Docker Engine, Buildx (`docker buildx version`), network access to Docker Hub, Google Android SDK, Google's Maven repository, Maven Central, and Gradle Plugin Portal.
- Enough disk space for the SDK, Gradle caches, and build intermediates (recommend **at least 15 GiB available**), and sufficient RAM for Java/Kotlin compilation (recommend **at least 6 GiB available**).
- No host Java, Kotlin, Gradle, Android SDK or Android Studio required to build. Python 3 is optional for integrity checks.
- For installation only: a host `adb` command and a USB-authorized target Redmi Note 13 Pro+ 5G.

## Build

From **the project root**, run:

```bash
bash docker/build.sh
```

The command will:

1. Create one persistent device-internal **debug-signing key** in `.local/signing/` if missing (not exported or copied into the Docker image).
2. Build using Docker BuildKit, Gradle 8.13 / JDK 17, AGP 8.11.1, Kotlin 2.2.21, Android SDK API 36, Android build tools 35.0.0, official command-line tools revision 15859902.
3. Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `apksigner verify` **inside Docker**.
4. Export `dist/TrailOneGPX-debug.apk`, its `.sha256`, and `dist/reports/testDebugUnitTest/` to the host.
5. Write all stdout/stderr to `logs/docker-build-YYYYMMDDTHHMMSSZ.log`; `logs/latest-log-path.txt` points to it. On failure, `logs/last-errors.txt` contains likely compiler diagnostics. Log files remain on the host after failure; no Docker log volume required.

**No stale-APK fallback:** the script removes the previous APK before starting. A failed build leaves no installable APK in `dist/`.

### Rebuilding and caches

BuildKit caches the installed SDK image layers and Maven/Gradle downloads. Future runs use the same signing key; Gradle does not generate a new, incompatible certificate per Docker build. Modifying the key changes the cache argument and forces a fresh signing/build step.

**Do not delete** `.local/signing/trailone-internal-debug.keystore`. Keep a secure backup outside the project. If you lose it and regenerate another, you cannot upgrade an installed APK signed with the former certificate without removing that installation (losing its private app data). The `android` alias/password is the conventional development keystore identity; this is **not hardened production signing**. Do not distribute this key, commit it, or publish this APK as a production release.

The project-local `.local/.gitignore` and `docker/Dockerfile.dockerignore` prevent normal Git/Docker inclusion of the private key. These are defense-in-depth guards, not substitutes for secure backup and access controls.

### Device deployment (optional)

```bash
bash docker/inspect-device.sh
bash docker/install.sh
```

If ADB rejects the installation due to an existing app signed with a different debug certificate (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), **do not delete data automatically**. Either retrieve the original signing key or deliberately uninstall the old app after backing up required data, then install. Do not disable Android Advanced Protection just for installation.

### Diagnostics

```bash
cat logs/latest-log-path.txt
cat logs/last-errors.txt
```

On failed builds, attach the full timestamped log; the first `e:` Kotlin diagnostic is usually more useful than the stack-trace tail.

### Limits and integrity

- Only Java/Kotlin/Android application build and test; no Android emulator or instrumented/on-device tests are run inside the container.
- The pinning includes the Gradle base image's observed digest. Android SDK zip and resolved Maven artifacts are version-selected but **not content-hash locked**. Therefore this is **repeatable but not fully hermetic/bit-for-bit reproducible**.
- Debug APK signatures have a persistent *local* identity. The resulting package is internal/test-only and should not be treated as a released or formally approved TrailOne artifact.
- Current UI is **Android Views**, not Compose; target and compile SDK are 36; this is a Java/Kotlin platform-independent APK for the supported SDK rather than a compiled ARM binary (no native `.so` libraries are required by the app).
- Both the brand-context repository and Python reference project remain external and unchanged.

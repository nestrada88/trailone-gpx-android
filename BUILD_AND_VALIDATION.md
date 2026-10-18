# TrailOne GPX Android — Build and validation record

**Baseline:** Android 16 (API 36), Xiaomi Redmi Note 13 Pro+ 5G (23090RA98G).
**Reference:** `nestrada88/gpx-wpt-tag-revgeo` at `caa6e1644687e60a9e3598c527189227db0ec4ae`.

## Corrected build matrix

| Component | Pin | Rationale |
|---|---|---|
| Android Gradle Plugin | 8.11.1 | Supports API 36; Kotlin 2.2.21's published test matrix includes AGP 8.11.1 |
| Gradle | 8.13 | Android Gradle Plugin 8.11 minimum |
| Kotlin Gradle Plugin | 2.2.21 | Tested with Gradle 8.13 and AGP 8.11.1 |
| Java | 17 | AGP 8.11 JDK requirement |
| Android SDK Platform | 36 | Target device Android 16 |
| Android Build Tools | 35.0.0 | AGP 8.11 default |
| UI | Android framework Views | Eliminates Compose and AndroidX build/runtime requirements |

References: https://developer.android.com/build/releases/agp-8-11-0-release-notes and https://kotlinlang.org/docs/gradle-configure-project.html

## Build on a Docker host

From the extracted `TrailOneGPXAndroid` directory:

```bash
bash tools/build-apk-docker.sh
```

**On successful Docker compilation:** `dist/TrailOneGPX-debug.apk`.

**On failure:** `android-build.log` is created in the project root and contains the original Gradle or SDK failure (the build script uses `tee` and `pipefail`). No APK is claimed unless the build produces it.

Inspect the first actionable diagnostic with:

```bash
grep -n -A 15 -E 'What went wrong:|Execution failed for task|^e: |error:|Could not resolve' android-build.log | head -100
```

The image uses the published Google Android SDK command-line tools, installs `platforms;android-36` and `build-tools;35.0.0`, tests the JVM unit suite, and builds the debug APK.

## What was verified in this environment

- Source completeness and Gradle configuration reviewed.
- `bash -n tools/build-apk-docker.sh` — passed.
- `bash tools/test-core.sh` — 6/6 GPX regression cases passed using local Kotlin/JDK.
- All three Kotlin application files compiled against *local Android API placeholders*. This is a Kotlin syntax/type check, **not** an Android SDK compilation and does not establish compatibility of every actual Android method.
- ZIP packaging is checked for integrity and contents when produced.

## What remains unverified

There is no Docker daemon or Android SDK available in the authoring environment, and its shell cannot resolve external package repositories. Therefore **no APK has been compiled or installed here**. Real Android Gradle compilation and operation on HyperOS 3 require execution on a Docker host/device.

## Acceptance checklist

1. Docker build finishes with `BUILD SUCCESSFUL` and produces a nonempty `dist/TrailOneGPX-debug.apk`.
2. Install with `adb install -r dist/TrailOneGPX-debug.apk`.
3. Import Cerro Mogotón GPX or another GPX 1.1 track; select 3-letter prefix and integer interval.
4. Verify statistics, TH/TE/LE/HE/MP/KM marker names, and no cross-segment distance jumps.
5. Export GPX and validate that original track points and metadata are retained by default.
6. Repeat with offline mode, malformed GPX, and unavailable geocoding endpoint.

Only after these pass is the app acceptable as a working Android release candidate.

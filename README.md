# TrailOne GPX — Android 16 native Kotlin application

A standalone Android application that ports the waypoint-enrichment workflow from
[`nestrada88/gpx-wpt-tag-revgeo`](https://github.com/nestrada88/gpx-wpt-tag-revgeo)
(commit `caa6e1644687e60a9e3598c527189227db0ec4ae`).

**Important:** This archive provides a native Kotlin Android *source project*. The local GPX engine regression tests passed, but a full Android Gradle compilation and APK installation have **not** been verified. See [BUILD_AND_VALIDATION.md](BUILD_AND_VALIDATION.md).

## Functions

- Open GPX 1.1 files using Android's system document picker without broad storage permissions.
- Validate input structure, coordinates, elevation and limits (25 MiB, 200,000 track points).
- Analyze multiple tracks/segments without connecting gaps; calculate ascent, descent, extrema and approximate 3D distance.
- Select AUTO / WGS84 geodesic / haversine distances.
- Produce ordered named semantic markers `TH`, `TE`, `LE`, `HE`, `MP`, and integer kilometer markers `KM001`..`KM999` at 1–100 km intervals.
- Reverse geocode optional waypoint descriptions through HTTPS Nominatim-compatible provider, with retries and local persistent cache.
- Preserve source metadata, routes, tracks and original waypoints by default; optionally replace attribution with legacy Trail One metadata.
- Export a GPX 1.1 document using Android's system save picker, not replacing the imported file.

The app UI uses **Android framework Views** written in Kotlin: no Jetpack Compose, AndroidX Activity or coroutines dependencies are needed. This reduces unnecessary Gradle/Kotlin plugin conflicts for a small processing app. The computational core remains separate and Android-independent.

## Build using Docker

Requires Docker with Buildx and access to Google SDK, Maven and Gradle repositories:

```bash
bash tools/build-apk-docker.sh
```

Expected result **only after a successful build**:

```text
dist/TrailOneGPX-debug.apk
```

If the build fails, its complete log is written to `android-build.log` in the **project root** even though `dist/` remains empty.

For direct invocation:

```bash
docker buildx build --progress=plain -f Dockerfile.android --target apk \
  --output "type=local,dest=$(pwd)/dist" .
```

The toolchain is AGP 8.11.1, Gradle 8.13, Kotlin 2.2.21, JDK 17, compile/target SDK 36, Android Build Tools 35.0.0.

## Install and operate

```bash
adb install -r dist/TrailOneGPX-debug.apk
```

On the device, select a GPX 1.1 file, enter exactly three uppercase prefix letters (for example `MGT`), choose whole-kilometer marker interval and distance method, optionally enable reverse geocoding, tap **Generate enriched GPX**, then **Save GPX 1.1 document**.

Reverse geocoding sends waypoint coordinates to the configured service. Public Nominatim's usage limit is aggregate, not per device. The app defaults to offline mode; use a managed or self-hosted provider for general distribution. See https://operations.osmfoundation.org/policies/nominatim/ .

## Development and testing

Local Kotlin-only regression tests need the `kotlinc` command and a JDK:

```bash
bash tools/test-core.sh
```

Six regression cases validate GPX import/export, metadata preservation, waypoint markers, WGS84 distance, segment gaps, input rejection, and missing-elevation semantics. Additional JUnit tests are in `app/src/test`. Docker runs them before assembling the APK.

See [PARITY.md](PARITY.md) for deviations from Python (no GPX 1.0 conversion, different geodesic algorithm and metadata-preservation default).

## Source layout

```text
app/src/main/java/com/trailone/gpx/
  core/GpxEngine.kt             Pure Kotlin geospatial processing
  network/NominatimGeocoder.kt  Optional Nominatim HTTP provider
  ui/MainActivity.kt            Native Android document/UI workflow
app/src/test/java/com/trailone/gpx/core/GpxEngineTest.kt
Dockerfile.android
tools/build-apk-docker.sh
tools/test-core.sh
BUILD_AND_VALIDATION.md
PARITY.md
```

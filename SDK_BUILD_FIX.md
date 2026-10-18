# Android SDK build fix — 2026-10-16

## Observed error

`Warning: Failed to find package 'platforms;android-37'` during Docker layer `sdkmanager` install.

## Root cause

API 37 packages use minor versions in their SDK paths. `platforms;android-37` is not a published package ID.

## Corrections

- `Dockerfile.android`: use `platforms;android-37.0`.
- `app/build.gradle.kts`: use `compileSdk { version = release(37) { minorApiLevel = 0 } }`.
- Retain `targetSdk = 36` for Android 16 and `build-tools;36.0.0`.

## Verify

From the extracted project root:

```bash
bash tools/build-apk-docker.sh
```

Expected successful output: `dist/TrailOneGPX-debug.apk`.

## Validation boundary

Static patch confirmed. Docker/Android SDK unavailable in this execution environment; image build and APK generation not executed.

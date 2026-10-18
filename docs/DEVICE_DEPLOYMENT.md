# TrailOne GPX — Primary device and internal deployment

**Working context:** v0.1.1, 2026-10-18. Internal TrailONE 505, **one primary supported test device**.

## Target information

The owner reported Xiaomi Redmi Note 13 Pro+ 5G (`23090RA98G`), HyperOS 3 `3.0.302.0.WNOMIXM.C07`, Android 16/API 36, security patch 2026-08-01. Full device report (including SoC, kernel, RAM, storage and baseband) is machine readable in `governance/DEVICE_PROFILE.json`. `arm64-v8a` remains *expected*, not independently verified.

## Deployment method

Direct tap-to-install from the file manager was blocked by Advanced Protection in screenshots. Official Google guidance allows ADB deployment for developers when Advanced Protection is active: https://support.google.com/accounts/answer/9764949. Whether this **particular** phone and build accept the APK by ADB must be tested. No Play Store, AAB, public listing or app-store credentials are required for the current scope.

From the extracted Android app root on an authorized Linux host:

```bash
bash tools/build-apk-docker.sh
ls -lh dist/TrailOneGPX-debug.apk
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.build.fingerprint
adb install -r dist/TrailOneGPX-debug.apk
```

Do not claim success until output reads `Success`. The first build command needs Docker/network; ADB is separate and requires USB debugging authorization. Do not put signing secrets into this repository or share them in artifacts.

**Upgrade risk:** If the installed APK was signed using a different debug keystore, `adb install -r` may fail due to signature mismatch. Reconcile the signing certificate rather than blindly uninstalling; uninstalling erases app data. A stable privately managed signing identity is a next engineering task and is not configured by this document.

## Evidence to record per device run

- `adb devices -l` connection authorized and model/API/ABI output.
- Full build log, APK SHA-256, signing certificate fingerprint, app version and signing continuity.
- APK installation result, full guided workflow, `.gpx` source read-only behavior, exported document opened and validated.
- Rotation, screen insets, Android 16 back behavior, SAF access/cancellation and provider failures.
- Flight-mode offline operation; network consent and geocoding failure behavior when enabled.
- Source file invariance, memory consumption for 25 MiB/200,000 points, basic TalkBack/text scaling.

## Scope boundaries

The application does not acquire live GPS despite GNSS hardware. Do not request location permissions merely for device availability. Physical storage capacity and virtual RAM do not remove file-size constraints or heap limits. Device-oriented testing is mandatory, broad handset compatibility testing is optional and out of scope.

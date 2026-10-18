# Validation plan and honest acceptance gates

## Evidence currently available (2026-10-18)

| Gate | Command or activity | Status | Notes |
|---|---|---|---|
| Source ZIP integrity | `unzip -t ...implementation.zip` | PASS | Archive integrity only |
| Context/source lock | `python3 tools/verify-app-context.py` | To run after integration | Checks source files and context metadata, not app correctness |
| Pure Kotlin GPX regression | `bash tools/test-core.sh` | **PASS 6/6** | Re-run during context preparation using local `kotlinc` |
| Pure Kotlin guided workflow regression | `bash tools/test-guided.sh` | **PASS 12/12** | Re-run during context preparation using local `kotlinc` |
| Gradle JVM tests | Docker `:app:testDebugUnitTest` | **NOT VERIFIED** | Previously described local JUnit-compatible test harness is not Gradle/JUnit execution |
| Android APK assembly | Docker `:app:assembleDebug` | **NOT VERIFIED** | No APK binary supplied in this context |
| Emulator / device | Install and test on Android 16 / HyperOS | **NOT VERIFIED** | No device run under this implementation |
| Python/Kotlin behavioral parity | Golden GPX fixtures | **NOT VERIFIED** | Compare parsed semantic outputs and relevant original GPX structure |
| Security & accessibility | Device and code review | **NOT VERIFIED** | XML hardening, permissions, SAF, TalkBack, font scaling, contrast |
| Release | Signed release + review | **NOT VERIFIED** | Debug signing is not production release authentication |

## Required checks by scenario

### Import
Valid GPX 1.1; incorrect extension; malformed/non-UTF-8 XML; forbidden DOCTYPE/external entity; no tracks; empty segments; fewer than two points; no elevation; coordinates outside valid ranges; file >25 MiB; >200,000 points; denied/missing URI permission; unsupported provider.

### Configure
Prefix empty, lowercase, >3 letters and invalid character; interval 0, 101 and noninteger; algorithm choices; metadata modes; offline default; invalid HTTP/credential-bearing geocoding URL; editable controls with soft keyboard and TalkBack.

### Generate
Correct TH/TE/LE/HE/MP/KM names, positions, deterministic ordering and elevation/distance statistics; no artificial distance added between track segments; repeated runs not duplicating original XML DOM waypoints; invalidated stale async completion; geocoding timeout/failure/rate limit; cancellation/back behavior.

### Review
All statistics are computed rather than simulated; exact marker count matches output file; warnings accurate; parameter edits require regeneration; no export until review accepted.

### Export
SAF Save As and success; cancellation; provider refusal; zero/partial write; new filename collision; write/close failure; successful output is valid GPX 1.1; source not overwritten; preserve result after failed save; duplicate save if intended.

### Platform
Fresh install and update signing compatibility; portrait and landscape; process death and rotation; available memory at limits; foreground/background handling; Android 16 edge-to-edge/insets; tap targets ≥48 dp, large text, contrast, screen-reader traversal.

## Commands

From the Android project root:

```bash
python3 tools/verify-app-context.py
bash tools/test-core.sh
bash tools/test-guided.sh
bash tools/build-apk-docker.sh
```

Expected successful Docker output path: `dist/TrailOneGPX-debug.apk`. Full host diagnostics: `android-build.log`. If Gradle fails, **capture and fix the first compiler diagnostic**, never infer success from `BUILD FAILED` stacktrace tails. With authorization on the device, install via `adb install -r dist/TrailOneGPX-debug.apk` and record observed results.

## Release acceptance

No release-ready claim without a passing real Android build, tested binary and documented device or emulator execution. No brand approval claim beyond the exact approved palette and workflow. Maintain reproducibility provenance including source checksums, dependency versions, APK hash, signing certificate and test records.

## Internal deployment profile (context v0.1.1)

- **Primary device:** owner-reported Redmi Note 13 Pro+ 5G `23090RA98G`, HyperOS 3 `3.0.302.0.WNOMIXM.C07`, Android 16/API 36. Verify actual API/ABI via `adb shell getprop`; do not mark ABI verified from owner expectation alone.
- **Required release output:** an installable, internally signed APK; Play Store/AAB/distribution-catalog checks are **not required**.
- **Install evidence:** build success, exact APK hash, package signature fingerprint, `adb install -r ...` result and device model/build. Advanced Protection blocked file-manager sideloading; ADB remains to be verified on the physical device.
- **Internal quality gate:** GPX input/output fidelity, stage flow, error/cancellation recovery, Android 16 system interaction, SAF provider behavior, offline default, explicit geocoding and signing continuity.
- **Not implied:** GPS capture/navigation tests, Compose-framework tests or wide Android-device matrix; they are not current app capabilities.

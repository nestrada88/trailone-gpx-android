# TrailOne GPX — Development Context (Internal TrailONE 505)

**Context ID:** `TRAILONE-GPX-ANDROID-DEVCTX`  
**Context version:** `0.1.1` (`WORKING_CONTEXT_NOT_RELEASE`)  
**Date:** 2026-10-18  
**Product scope:** Internal utility for TrailONE 505; **not intended for mass-market release**  
**Target hardware:** Xiaomi Redmi Note 13 Pro+ 5G, model `23090RA98G`, owner-reported Android 16 / HyperOS 3.

## 1. Product and deployment definition

TrailOne GPX is an offline-first native Kotlin Android utility used **internally by TrailONE 505** for validating imported GPX tracks, generating semantic/distance-based waypoints, reviewing generated data and exporting a new GPX 1.1 document. The application is not a public consumer product. Its reference functional behavior originates from `nestrada88/gpx-wpt-tag-revgeo`.

**Deployment constraint:** one known Android 16 phone is the primary acceptance target. APK installation and iterative update through authorized **ADB** is the intended workflow. Google Play publication, store listing, AAB production, marketing onboarding, analytics SDKs, multi-device compatibility certification and consumer account flows are **out of scope** unless explicitly requested. This does not remove requirements for correctness, integrity, usability, security, or regression tests.

The app is **not** live GPS navigation, GPS recording, terrain mapping, route planning, hazard assessment, or cloud synchronization. GNSS hardware is available according to the owner, but current application behavior does not use live device location. No location permission should be added merely because the phone has GNSS.

## 2. Authorities and approved baselines

| Concern | Baseline | Authority / status |
|---|---|---|
| Intended user and distribution | Internal TrailONE 505, one primary device, APK/ADB | Owner-reported product constraint in this conversation |
| Guided operation | Import → Configure → Generate → Review → Export | **Owner-approved** UX implementation baseline |
| Brand visual assets | `nestrada88/trailone-brand-context` at observed `247863bf557bd422ed20c0b972209a8f9b7b2e60` | Canonical palette v1.0.0 **approved unsigned**; no silent changes |
| Python behavior reference | `nestrada88/gpx-wpt-tag-revgeo` at observed `caa6e1644687e60a9e3598c527189227db0ec4ae` | Behavioral reference, not proof of Kotlin parity |
| Native Kotlin implementation | `TrailOneGPXAndroid-Five-Stage-Guided-v1.0.0-implementation.zip` | Source candidate; Android APK build still unverified |
| Trail-cover spec | Trail-cover production manifest | Approved only within its own cover-render scope, not app UX |

Source archive lock, brand SHA-256 and source code boundary are unchanged from context v0.1.0. This v0.1.1 release changes **documentation/context only**; no Kotlin code, Dockerfile or Gradle modification is authorized or made here.

## 3. Primary device — owner-reported target

The **authoritative record of this report within the app context** is `governance/DEVICE_PROFILE.json` (not independently measured by ADB).

| Category | Reported value |
|---|---|
| Manufacturer / model | Xiaomi Redmi Note 13 Pro+ 5G; `23090RA98G` |
| SoC / CPU | MediaTek Dimensity 7200-Ultra; 64-bit ARM; octa-core; up to 2.8 GHz |
| ABI | `arm64-v8a` **expected**, pending `getprop` verification |
| Physical RAM / extended RAM | 12 GB physical + 12 GB virtual (not 24 GB physical) |
| Storage | 512 GB UFS 3.1; at time of report 126 GB used / 386 GB available |
| OS | Xiaomi HyperOS 3 `3.0.302.0.WNOMIXM.C07` |
| Android | Android 16; API level 36; build `BP2A.250605.031.A3` |
| Security patch | 2026-08-01 |
| Kernel | `5.15.180`, branch `android13-8`, build `00021-g46a5565a0982-ab13743836` |
| Baseband | `MOLY.NR16.R1.MP3P5.TC8.PR1.SP.V1.P107` |
| Development | Native Android/Kotlin; Compose compatible, **app currently uses Views** |
| GNSS | Present per device report; no active location requirement for current app |
| Packaging | APK primary; AAB possible but not needed for this deployment |

This one-phone target informs **testing priorities**, not arbitrary CPU-specific optimizations, unsupported ABI assumptions, or additional features. Screen dimensions, active locale, font scaling, available memory at runtime and `ro.product.cpu.abilist` require observation on device.

## 4. Five-stage workflow and contracts

`IMPORT → CONFIGURE → GENERATE → REVIEW → EXPORT`

1. **Import:** SAF chooses an existing `.gpx`; read/validate source; maintain read-only original and analysis snapshot.
2. **Configure:** Three-letter uppercase prefix (default `HIK`), kilometre interval `[1,100]`, `AUTO`/`GEODESIC`/`HAVERSINE`, optional geocoding (OFF), and metadata policy.
3. **Generate:** Reparse original source bytes on each attempt; compute distance/elevation and waypoint enrichment; reject stale async completion.
4. **Review:** Display computed source and output statistics and generated markers; invalidate review if any upstream input or option changes.
5. **Export:** Use SAF Create Document to save GPX 1.1 to a user-chosen destination; preserve source and generated bytes if save is cancelled or fails.

Original source is read-only by application intent. See `docs/UX_CONTRACT.md` and `docs/ARCHITECTURE.md` for state transitions.

## 5. Functional limits

- Current Kotlin source accepts GPX 1.1 official namespace, up to 25 MiB and 200,000 track points; requires at least two valid track points and at least one elevation value.
- Generated names: configured prefix + `TH`, `TE`, `LE`, `HE`, `MP`, `KMnnn` where applicable.
- Interval `[1,100]` whole kilometres; defaults to `1`. Horizontal distance choice is `AUTO`, `GEODESIC` or `HAVERSINE` and vertical delta contributes to 3D distance.
- Segment boundaries must not be bridged as travelled distance. Original XML is not promised to remain byte-identical when serialized.
- Metadata preservation is the default; optional `TRAILONE_LEGACY`. Geocoding is opt-in and shares coordinates with configured HTTPS provider when enabled.
- Failure/retry should preserve source and configuration, without exporting invalid or stale output.

## 6. Technical toolchain and packaging

| Item | Working Kotlin project configuration |
|---|---|
| Source language / UI | Kotlin 2.2.21; native `Activity` + Android Views, **not Compose** |
| Gradle / AGP | Gradle 8.13 / Android Gradle Plugin 8.11.1 |
| JDK / JVM target | Java 17 |
| SDK / build tools | `compileSdk=36`, `targetSdk=36`, `minSdk=26`, build tools `35.0.0` |
| App ID | `com.trailone.gpx` |
| Build | Docker multi-stage, `tools/build-apk-docker.sh` |
| Target | **Single primary device** `23090RA98G`, Android 16 / API 36 |
| Deliverable | Debug APK during iteration; stable signing identity to be established for repeated internal updates |

Do **not** change the Gradle/SDK stack merely because installation is blocked by a device security policy. AAB and public-app release workflows are not current goals. The build is not certified fully hermetic.

## 7. Distribution and device-security considerations

Earlier screenshots from this device showed file-manager APK installation blocked by Google Advanced Protection. Google describes ADB as a supported installation path for developer-built apps in the Advanced Protection context (https://support.google.com/accounts/answer/9764949). **Actual successful ADB installation for this revised application has not been observed.**

- Use an authorized Linux host and `adb devices -l` to establish access; enable/authorize USB debugging on the handset for testing.
- Install internal build via `adb install -r dist/TrailOneGPX-debug.apk` after successful Docker compilation.
- Preserve a **stable signing identity** to allow in-place updates. Do not distribute a private signing key through chat, ZIPs, Git, or the brand repo.
- Record APK SHA-256, signing-certificate fingerprint, installed package version and exact device build for QA evidence.
- Do not disable device security as a default workaround. If ADB fails, capture the package-manager diagnostic and treat it as a separate deployment gate.
- No Play Store release, public developer enrollment, telemetry or cloud infrastructure is required for the current scope.

See `docs/DEVICE_DEPLOYMENT.md`.

## 8. Validation and known limitations

**Previously verified:** source archive integrity, source lock, six GPX regression checks and twelve guided-workflow smoke checks.

**Not yet verified:** Android Gradle unit test/assembly; physical device import/configure/generate/review/export; ADB installation under Advanced Protection; Android 16 activity recreation, permissions, document provider reliability, edge-to-edge, long GPX performance, accessibility and Python/Kotlin parity.

**Known source constraints:** Activity retains original/output bytes in memory and uses a serial executor; Activity recreation discards output; cancelling geocoding may not stop an in-flight request; fresh Docker debug signing may prevent replacement of an earlier APK.

Only release gates relevant to an **internal utility** should be imposed: reproducible build evidence, functional correctness on target device, safe file handling, privacy, a maintained signing identity, regression tests and documented rollback. No mass-market rollout is planned.

## 9. Next engineering tasks

1. Obtain an actual Android SDK `testDebugUnitTest` and `assembleDebug` PASS from Docker; capture diagnostics.
2. Confirm the exact device model/ABI/API and Advanced Protection/ADB installation on the physical handset.
3. Test five stages with actual GPX inputs, large-file limits and SAF cancellation/errors.
4. Validate output against Python golden cases and confirm update signing works across two builds.
5. Review usability (Android 16 insets, accessible 48dp targets) and optional app launcher asset without inventing a new logo.
6. Store baseline context and Android source in an explicitly designated internal version-control location; **not performed** by this documentation update.

**Approval scope:** User has established the internal deployment constraint and provided device specifications. This document is an updated *working development context*, not a signed app release or verified device certificate.

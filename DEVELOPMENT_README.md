# TrailOne GPX — Internal Android Development Context

**Working context version:** `0.1.1` (2026-10-18)  
**Purpose:** Native Android GPX waypoint enrichment used internally by TrailONE 505.  
**Target:** Owner-reported Xiaomi Redmi Note 13 Pro+ 5G (`23090RA98G`), Android 16/API 36/HyperOS 3.

This is a documentation-only update of v0.1.0. The **original Android source, build files, Kotlin GPX engine, guided UX contract and authoritative brand assets remain unchanged**. App build/device testing remains pending.

Read `AGENTS.md`, `APP_CONTEXT.md`, `APP_STATE.json`, `governance/DEVICE_PROFILE.json`, `docs/DEVICE_DEPLOYMENT.md`, then the existing `docs/ARCHITECTURE.md`, `docs/UX_CONTRACT.md`, `docs/VALIDATION_PLAN.md`, `docs/CHANGE_CONTROL.md`. The existing `governance/SOURCE_LOCK.json` continues to lock the exact unchanged source files.

Recommended validation in integrated app root:

```bash
python3 tools/verify-app-context.py
bash tools/test-core.sh
bash tools/test-guided.sh
bash tools/build-apk-docker.sh
```

For device setup and ADB install, follow `docs/DEVICE_DEPLOYMENT.md`. ADB installation is the intended path; do not disable Android Advanced Protection by default. No Play Store/AAB release is required.

**Authorities:** Five-stage workflow was approved by the owner as implementation baseline. Brand palette and logo originate from the separate approved `nestrada88/trailone-brand-context` repository; do not modify approved artifacts. Android implementation is a source candidate, not a verified APK.

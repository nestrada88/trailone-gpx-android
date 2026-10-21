# Approved launcher icon integration — implementation hold

## Approved source
- Brand repository: `nestrada88/trailone-brand-context` (private)
- Path: `brand/visual-identity/iconography/applications/trailone-gpx/TRAILONE_GPX_APP_ICON_APPROVED_v1.0.0.png`
- Identity: `TRAILONE-GPX-APP-ICON-001 v1.0.0`
- Expected SHA-256: `be19ff29cc63cf4e3615fb9613668334f086cb9a2d879a4bc004eaeefad7be38`
- PNG: 1254 × 1254 RGB, 1,314,647 bytes
- Asset approval is not approval of adaptive-icon derivatives.

## Changes remaining (DO NOT merge this PR until completed)
1. Obtain the exact binary source from an authorized checkout of the private brand repository; do not substitute generated or approximate artwork.
2. Verify `sha256sum` matches the expected hash.
3. Copy byte-identically to `app/src/main/res/mipmap-nodpi/ic_launcher.png`.
4. Add `android:icon="@mipmap/ic_launcher"` to the existing `<application>` in `app/src/main/AndroidManifest.xml`.
5. Record hashes for changed manifest and new PNG in `governance/SOURCE_LOCK.json`; document the approved icon in `APP_STATE.json` without changing historical provenance.
6. Run `python3 tools/verify-app-context.py`, `bash tools/test-core.sh`, `bash tools/test-guided.sh`, Android Gradle tests and an APK build.
7. Verify launcher visual rendering on the Redmi Note 13 Pro+ 5G and signing compatibility with installed APK.

## Scope restrictions
No `MainActivity.kt`, `GuidedSession.kt`, `GpxEngine.kt`, `NominatimGeocoder.kt`, existing palette or toolchain modifications. No adaptive/monochrome-derived icon resources in this baseline.

## Delivery blocker
The connected GitHub file reader rejects non-UTF-8 PNG blobs, and GitHub does not accept a tree referencing a blob that exists only in another repository. As a result this branch currently contains **documentation only**, not the launcher integration. Do not mark CI, Android build, or device testing as passed.

## Publication
The Android repository is public and the brand repository private; the exact approved PNG becomes public when committed to the Android branch. Confirm that this use is within owner authorization before adding it.

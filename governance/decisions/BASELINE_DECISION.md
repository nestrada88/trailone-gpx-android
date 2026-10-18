# TrailOne GPX — Accepted Functional Baseline

**Record:** `TRAILONE-GPX-APP-BASELINE-001`  
**Record version:** `1.0.0`  
**Effective date:** 2026-10-18  
**Status:** `ACCEPTED_FUNCTIONAL_BASELINE_BINARY_UNPINNED`  
**Authority:** Explicit TrailONE 505 application-owner approval in the BRAND project conversation.

## Owner acceptance evidence

The owner confirmed (1) successful installation on the target Redmi, (2) observed application behavior met expectations, (3) generated output met requirements, then explicitly approved using the current installed release as the application baseline.

This decision establishes functional acceptance for future development; it is not itself a cryptographic signature or evidence of exhaustive testing.

## Scope preserved

- **Audience / distribution:** internal TrailONE 505, no mass-market release.
- **Target device:** Xiaomi Redmi Note 13 Pro+ 5G, model `23090RA98G`, reported Android 16 / HyperOS 3 (API 36).
- **UX:** IMPORT → CONFIGURE → GENERATE → REVIEW → EXPORT (owner-approved five-stage workflow).
- **Function:** Preserve the successfully tested GPX enrichment behavior, semantic and distance-marker handling, output integrity, and original-file preservation.
- **Brand:** TrailOne canonical palette v1.0.0, authoritative from the private brand-context repository, without silent substitutions.
- **Signing:** Maintain the installed app's signing identity for compatible in-place upgrades.

## Related source candidate — not attested as the installed APK

- Delivered archive: `TrailOneGPXAndroid-Internal-Docker-Builder-v0.1.3-EditText-Fixed.zip`
- Archive SHA-256: `2744999ede11ff5b7b08433d6d17d55c11e1ccf111bde9d247588ead561279ae`
- ZIP CRC check: PASS
- Android `app/build.gradle.kts` in the candidate declares `versionName = "1.0.0"` and `versionCode = 1`, but that version was **not verified from the installed APK**.

## Outstanding release identity evidence

1. APK SHA-256 from the host's `dist/TrailOneGPX-debug.apk`.
2. APK signing-certificate SHA-256 from `apksigner verify --print-certs` (or the build's saved verification output).
3. Source revision and successful Docker build run associated with that APK.
4. Immutable copy of the accepted APK and signing keystore, held privately.

Do not replace missing values with the archive hash; archive and APK identify different objects.

## Rules for subsequent work

1. Preserve the accepted application behavior as the regression baseline.
2. Make changes in separate candidates; do not overwrite approved or archived artifacts.
3. Document changes, affected stages, regression coverage, and rollback procedure.
4. Only advance the accepted baseline after owner approval and successful device validation.
5. No changes have been made to the Android code, installed APK, or brand GitHub repository by this record.

# Development change control (proposed local policy)

This document governs the **Android application development context**, not the authoritative TrailOne brand repository itself.

## Classification

- **APPROVED:** explicitly approved by the owner within the stated scope. Currently includes the five-stage guided workflow *as implementation baseline* and the externally governed brand palette.
- **IMPLEMENTED_NOT_VERIFIED:** Android source exists, but has not cleared Android build/device verification.
- **PROPOSED:** new UI components, icons, algorithms, API contracts, platform changes or additions not yet accepted.
- **MISSING_EVIDENCE:** runtime signing, release artifact, external service conditions or tests not observed.

## For each development change

1. Record requirement, reason, affected files, prior behavior and expected behavior.
2. Consult `AGENTS.md`, `APP_STATE.json`, current brand repository and Python reference when applicable.
3. Isolate scope: UI changes must not mutate engine, network or build configuration accidentally.
4. Implement on a branch/working copy. Preserve earlier approved artifacts; never modify immutable brand originals.
5. Add/adjust unit and contract tests for changed behavior and integration/device checks where needed.
6. Report file hashes/diffs, actual test output and outstanding gates.
7. Obtain explicit owner review before changing an approved UX contract, brand rule or application release baseline.
8. Version the context/state record together with the accepted new implementation, retaining previous baselines for audit and rollback.

## Repositories

The current authoritative **brand** repository is `nestrada88/trailone-brand-context`; its governance controls brand changes. The currently observed **functional Python** repository is `nestrada88/gpx-wpt-tag-revgeo`; it should not be silently overwritten with the Android port. The Android project is currently a delivered source archive: **no dedicated Android GitHub repository is confirmed by the inspected sources**. Publishing this context under version control is the recommended persistence step, but is **not performed** in this context setup.

## Conflicts

If a saved chat summary, context JSON, local implementation, upstream Python project and current approved brand spec disagree, report the specific conflict, origin and affected behavior rather than silently reconciling them. The nearest authoritative **owner decision for the scope** takes precedence; cross-repository changes require explicit scope approval.

## Device-specific scope amendment v0.1.1

The owner clarified an internal TrailONE 505-only application and an exact primary Redmi handset. This version changes development documentation and QA/deployment scope only. Previous source integrity hashes, approved UX and brand assets remain unchanged. Device hardware data is owner-reported and requires later ADB confirmation. No public-distribution workflow is implied.

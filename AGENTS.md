# TrailOne GPX — Android development operating policy

## Session startup (required)

1. Read `APP_CONTEXT.md`, `APP_STATE.json` and the task-relevant files in `docs/`.
2. Verify the current source revision and integrity against `governance/SOURCE_LOCK.json` using `python3 tools/verify-app-context.py`.
3. Fetch the **current** `AGENTS.md`, `CONTEXT.md`, `STATE.json` and task-relevant approved artifacts from `nestrada88/trailone-brand-context`; do not rely on memory of the palette or accept approximate values.
4. Fetch the relevant current files from `nestrada88/gpx-wpt-tag-revgeo` if the change touches GPX semantics, naming, numerical algorithms or output compatibility.
5. Report the active baselines and any divergence (especially remote revisions or context/documentation staleness) before implementing changes. Do not silently treat updated upstream files as already adopted.

## Authority and scope

- Explicit owner decisions and approved UX contracts govern the intended workflow.
- `trailone-brand-context` controls **brand tokens and approved assets**. Its approved artifacts are immutable; brand revisions require its separate change-control process.
- The Python GPX repository is a **behavioral reference**, not automatic proof of Kotlin parity.
- The local Kotlin package is the **current implementation candidate**, not a compiled or released application.
- Proposal mockups, generated imagery, previous conversations and unchecked markdown are not independently approved assets.

## Engineering constraints

- Maintain native Kotlin Android architecture and the five sequential stages: `IMPORT → CONFIGURE → GENERATE → REVIEW → EXPORT`.
- Keep the GPX processing engine and network provider unchanged unless a separately scoped, tested and approved change requires otherwise.
- Maintain source/output separation; reparse original GPX bytes for regeneration and reject stale asynchronous results.
- Preserve documented marker identifiers and distance/elevation semantics. Do not invent new output attributes or silently alter metadata.
- Reverse geocoding defaults to OFF. Network calls must be explicit, privacy-disclosed and failure-tolerant. Never claim geocoding success when descriptions are missing.
- Use approved palette tokens exactly. Screen components, launcher icon treatments and typography require their own review; they are **not** automatically approved by matching the palette.
- UI progress and preview data must never be presented as real GPX processing results until backed by the engine.
- Do not introduce navigation, route tracking, mapping or unrelated product functions without a requirement and design decision.

## Validation and reporting

- Run source-integrity verification, engine regression checks, guided state tests, Gradle unit tests, Android APK build, and physical-device tests as separately reportable gates.
- Never claim a successful Docker or Android build without its actual output and diagnostics.
- Do not alter build tool versions as a guess when the compiler reports a source error.
- Preserve upstream and approved files byte-for-byte where not within scope; publish an explicit before/after diff.
- Before release, identify unverified accessibility, lifecycle/SAF, network-provider, signing and installation behavior.
- New code or documentation is a candidate until reviewed; a commit or ZIP does not create approval.

## Internal TrailONE 505 distribution target (owner-declared 2026-10-18)

- Do **not** propose a Play Store, mass-market, public beta, AAB publication, cloud onboarding or compatibility matrix as a prerequisite. The current product is for **internal TrailONE 505 use**, primarily on Xiaomi Redmi Note 13 Pro+ 5G `23090RA98G`, owner-reported Android 16/API 36/HyperOS 3.
- Treat `governance/DEVICE_PROFILE.json` as an **owner-reported working profile** until confirmed with physical-device ADB properties. `arm64-v8a` is expected, not yet verified. Never conflate 12 GB virtual RAM with physical RAM.
- Native Android Views are the existing UI implementation; device Compose compatibility is not authorization to migrate frameworks. GNSS hardware availability does not imply live tracking or need for GPS permissions.
- Primary delivery is a locally built APK through authorized ADB. Preserve a stable signing certificate, do not ship keys, and report Advanced Protection installation diagnostics without weakening phone security by default.
- Evidence standards remain strict for internal use: correct GPX output, regression tests, device testing, privacy controls, reliable import/export and explicit validation status.
- App source and brand baseline stay unchanged unless separately scoped. Treat documentation/device-context updates as distinct from code or signing changes.

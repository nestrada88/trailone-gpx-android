# Android implementation architecture and data carriage

## Layering (current source)

```text
Android OS / system document providers
      │ ACTION_OPEN_DOCUMENT / ACTION_CREATE_DOCUMENT
      ▼
MainActivity.kt                         [native Views, UI and Activity lifecycle]
      │ actions / rendering             [single-thread background executor]
      ▼
GuidedSession.kt                        [pure Kotlin stage guards, source/options revisions]
      │ immutable input bytes snapshot / options snapshot / run ID
      ├───────────────┐
      ▼               ▼
GpxEngine.kt          NominatimGeocoder.kt (optional, online only)
      │               │
      └─────── generated GPX bytes + waypoint list + warnings
                      │
                      ▼
                Review → SAF export
```

## Data ownership

- **Imported input:** source `content://` URI, display name, original byte-array copy, parsed statistics and source SHA-256. UI reads input through `ContentResolver` and calls `GpxEngine.analyze` before unlocking Configure.
- **Options:** prefix, interval, method, geocode flag, HTTPS endpoint and metadata mode. Changes increment an options revision.
- **Processing:** `GuidedSession.beginGeneration()` records a run identifier. UI reparses original bytes and generates the GPX on its worker. Only a completion whose source hash, options revision and run identifier remain current may be accepted.
- **Result:** generated GPX bytes, computed metrics/ordered waypoint list, proposed output filename and warnings, retained until upstream data changes or user resets.
- **Export:** reviewer must explicitly continue from Review. The Android Create Document picker selects a destination. A write is successful only after output stream use completes. Cancellation does not invalidate output.

## Resource limits and concurrency

- Engine limits input to 25 MiB and 200,000 track points; the UI currently holds original and output bytes in memory, so stress-test peak heap use.
- The Activity uses one serial executor; cancellation prevents stale result acceptance but does not guarantee interruption of synchronous I/O/network operations.
- Worker and export job lifetimes are tied to the Activity. Current code saves URI and options for Activity recreation, not a durable background work queue.
- Document read permissions may require reauthorization if a provider does not grant persistence.

## Non-goals of the current implementation

No live GPS tracking, offline base-map downloads, GPX editing canvas, route planning, terrain hazard analysis, user accounts or cloud synchronization. These are not implicitly authorized by the TrailOne brand scope.

## Architecture boundary requiring attention

This is currently a **single-Activity app with a pure Kotlin state model**, not a ViewModel/repository/WorkManager architecture. Do not write the development context as if those components already exist. If lifecycle testing reveals actual defects, propose the smallest warranted refactor and provide regression coverage.

# Python-to-Kotlin functional parity assessment

Reference: `nestrada88/gpx-wpt-tag-revgeo`, commit `caa6e1644687e60a9e3598c527189227db0ec4ae`.

| Behavior | Kotlin result | Notes |
|---|---|---|
| GPX 1.1 input with `<trk>/<trkseg>` | Implemented | Direct-child extraction and strict non-empty segments |
| GPX 1.0 normalization | **Not implemented** | Explicitly rejected, rather than partially/incorrectly converting |
| 200k track-point limit | Implemented | Additionally bounded by 25 MiB input limit |
| Finite lat/lon and elevation validation | Implemented | Latitude ±90, longitude ±180, elevation -1000..9000 m |
| Multi-track, multi-segment accumulation | Implemented | No distance across track or segment boundaries |
| Geodesic horizontal distance | Near-equivalent | Kotlin uses Vincenty inverse on WGS84; Python uses geopy/GeographicLib; antipodal fallback to haversine |
| Haversine distance | Implemented | Radius 6,371,000 m; clamp avoids floating-point domain errors |
| 3D distance approximation | Implemented | sqrt(horizontal² + deltaElevation²); zero vertical component if either elevation absent |
| `auto` point threshold | Implemented | >50,000 => haversine, otherwise WGS84 |
| Ascent/descent and elevation extrema | Implemented | No filtering, no artificial cross-segment height delta |
| Waypoint names and tie ordering | Implemented | TH, LE, HE, MP, KM, TE; 3-letter prefix; 1–100 whole km steps |
| 999-km maximum marker name | Implemented | Markers above 999 km omitted, endpoints still generated |
| Straight coordinate interpolation | Implemented | Shortest-wrap antimeridian longitude; elevation only if both endpoints provided |
| Nominatim reverse geocoding | Optional | Off by default for offline-first operation; HTTPS provider user-configurable |
| 3 retries, backoff, timeouts, throttling | Implemented | Persistent cache instead of original in-memory cache |
| Existing tracks, routes and waypoints | Preserved | DOM import and insertion; source attributes and XML extensions retained |
| Source GPX attribution | **Intentional change** | Original metadata preserved by default; optional explicit legacy replacement |
| GPX 1.1 serialization | Implemented | Valid namespace, schema element ordering assumed from valid source |
| XSD validation | **Not implemented** | XML parser checks well-formedness and GPX root contract, not XSD |
| Write file atomically with fsync/replace | **Different** | Android Storage Access Framework creates a chosen document; output write depends on provider |
| CLI and Docker wrapper | **Replaced** | Native Android Views UI and Android storage; Docker is build-only |
| Input file immutability | Implemented | Imported URI opened for reading only |
| Existing generated waypoint reconciliation | **Not implemented** | Mirrors Python's append-only behavior; reprocessing adds markers |
| Identical output bytes | **Not guaranteed** | Metadata, formatting, floating point, service results and timestamps differ |

## Validation claims

- **Confirmed:** Kotlin core compiles with local `kotlinc` 1.9 and passes supplied smoke test.
- **Not confirmed:** Android Gradle sync/build, APK runtime behavior, device SAF provider interoperability,
  end-to-end public Nominatim connectivity, GPX XSD conformance, and parity over the upstream Cerro Mogotón fixture.

## Geo-specific caveats

- The WGS84 inverse implementation uses Vincenty's iterative algorithm. It is accurate for ordinary
  hiking trail legs but, unlike GeographicLib, can fail near antipodal pairs; the code falls back to haversine.
- Fractional interpolation between GPX points is linear in latitude/longitude coordinates rather than
  following the geodesic, as in the reference implementation.
- GPS elevation noise is not filtered. Ascent and descent therefore reflect sampled values, not a
  surveyed terrain surface or a claimed physical exertion metric.
- Reverse-geocoded descriptions indicate nearby named geographic entities, not terrain risk or route safety.

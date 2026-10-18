# Android API 36 XMLConstants compilation correction

Reported build failure in `:app:compileDebugKotlin`:

- `GpxEngine.kt:86` and `:277`: `Unresolved reference 'ACCESS_EXTERNAL_DTD'`
- `GpxEngine.kt:87`: `Unresolved reference 'ACCESS_EXTERNAL_SCHEMA'`
- `GpxEngine.kt:278`: `Unresolved reference 'ACCESS_EXTERNAL_STYLESHEET'`

The Android API 36 `javax.xml.XMLConstants` class does not expose these Java SE members in the Android compile SDK. The Android API supports the class itself, but not these property-name constants. No Gradle, Kotlin, Android SDK, or Docker version change is necessary for this correction.

The four usages were changed to private string constants containing the standard JAXP property names:

- `http://javax.xml.XMLConstants/property/accessExternalDTD`
- `http://javax.xml.XMLConstants/property/accessExternalSchema`
- `http://javax.xml.XMLConstants/property/accessExternalStylesheet`

The `runCatching` blocks are retained because individual Android XML providers may not recognize these optional attributes. Input DTDs are prohibited before parsing, and an entity resolver rejects external entity requests independently of these optional properties.

**Verified here:** core Kotlin compilation and six engine regression smoke tests (`tools/test-core.sh`).

**Not verified here:** Android Gradle build, APK assembly, instrumented runtime behavior, or installation; Docker and the Android SDK are not available in this execution environment. This correction addresses the *four exact compiler diagnostics* supplied by the device developer, not other possible errors.

To rebuild from the project root:

```bash
bash tools/build-apk-docker.sh
```

On success, the output will be `dist/TrailOneGPX-debug.apk`.

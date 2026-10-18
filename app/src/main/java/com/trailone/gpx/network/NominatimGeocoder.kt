package com.trailone.gpx.network

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Optional, user-triggered reverse geocoding. Run exclusively from Dispatchers.IO.
 * The local limit cannot enforce Nominatim's aggregate 1 req/s across all app installs.
 * Distribution requires a shared proxy or dedicated provider with server-side limiting.
 */
class NominatimGeocoder(context: Context, endpoint: String) {
    private val cache = context.applicationContext.getSharedPreferences("reverse_geocode_cache_v1", Context.MODE_PRIVATE)
    private val base = endpoint.trim().also {
        require(it.startsWith("https://") && URL(it).protocol == "https") {
            "Geocoding endpoint must be a valid HTTPS URL."
        }
    }
    private val agent = "TrailOne-GPX-Android/1.0 (https://github.com/nestrada88/gpx-wpt-tag-revgeo)"

    companion object {
        private val lock = Any()
        private var lastRequestMs = 0L
    }

    fun reverse(latitude: Double, longitude: Double): String? {
        val lat = "%.7f".format(Locale.US, latitude)
        val lon = "%.7f".format(Locale.US, longitude)
        val key = "${base.hashCode()}:$lat,$lon"
        if (cache.contains(key)) return cache.getString(key, null)?.ifBlank { null }
        for (attempt in 0 until 3) {
            try {
                // Serialize calls within this process, including the HTTP request itself.
                val result = synchronized(lock) {
                    val elapsed = SystemClock.elapsedRealtime() - lastRequestMs
                    if (lastRequestMs > 0 && elapsed < 1_100L) Thread.sleep(1_100L - elapsed)
                    val response = try { performRequest(lat, lon) }
                    finally { lastRequestMs = SystemClock.elapsedRealtime() }
                    response
                }
                // Persist results to avoid repeating queries between app runs.
                cache.edit().putString(key, result).apply()
                return result.ifBlank { null }
            } catch (ex: Exception) {
                if (ex is InterruptedException) { Thread.currentThread().interrupt(); return null }
                if (attempt < 2) Thread.sleep((1L shl attempt) * 1_000L)
            }
        }
        return null // Geocoding failure never invalidates a GPX output.
    }

    private fun performRequest(latitude: String, longitude: String): String {
        val separator = if (base.contains('?')) "&" else "?"
        val fullUrl = "$base${separator}format=jsonv2&addressdetails=0&lat=$latitude&lon=$longitude"
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", agent)
            conn.setRequestProperty("Accept", "application/json")
            val status = conn.responseCode
            if (status !in 200..299) error("Reverse-geocoding HTTP $status")
            val bytes = conn.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                val result = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    require(result.size() + read <= 64 * 1024) { "Geocoder response exceeds limit." }
                    result.write(buffer, 0, read)
                }
                result.toString(Charsets.UTF_8.name())
            }
            return JSONObject(bytes).optString("display_name", "").trim()
        } finally { conn.disconnect() }
    }
}

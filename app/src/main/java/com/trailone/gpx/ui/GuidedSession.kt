package com.trailone.gpx.ui

import com.trailone.gpx.core.GpxEngine
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Testable, Android-independent workflow contract. All data derived from a source
 * and options revision is invalidated when either dependency changes.
 * The engine remains the authority for GPX validation and transformation.
 */
class GuidedSession {
    enum class Stage { IMPORT, CONFIGURE, GENERATE, REVIEW, EXPORT }
    data class Options(
        val prefix: String = "HIK",
        val intervalKm: Int = 1,
        val method: GpxEngine.DistanceMethod = GpxEngine.DistanceMethod.AUTO,
        val onlineGeocoding: Boolean = false,
        val endpoint: String = "https://nominatim.openstreetmap.org/reverse",
        val metadataMode: GpxEngine.MetadataMode = GpxEngine.MetadataMode.PRESERVE_SOURCE,
    ) {
        fun validate() {
            GpxEngine.validateOptions(prefix, intervalKm)
            if (onlineGeocoding) {
                val parsed = runCatching { URI(endpoint.trim()) }.getOrNull()
                require(parsed != null && parsed.scheme.equals("https", ignoreCase = true)
                    && !parsed.host.isNullOrBlank() && parsed.userInfo == null) {
                    "Enter a valid HTTPS geocoding endpoint."
                }
            }
        }
    }
    class Source internal constructor(
        val uri: String,
        val name: String,
        val bytes: ByteArray,
        val stats: GpxEngine.Statistics,
        val sha256: String,
    )
    class Output internal constructor(
        val result: GpxEngine.Result,
        val filename: String,
        val sourceSha256: String,
        val optionsRevision: Long,
        val runId: Long,
        val warnings: List<String>,
    )

    var stage: Stage = Stage.IMPORT
        private set
    var source: Source? = null
        private set
    var options: Options = Options()
        private set
    var output: Output? = null
        private set
    var optionsRevision: Long = 0
        private set
    var activeRunId: Long = 0
        private set
    var running: Boolean = false
        private set
    var reviewed: Boolean = false
        private set
    var exportedUri: String? = null
        private set

    fun importValidated(uri: String, name: String, bytes: ByteArray, statistics: GpxEngine.Statistics) {
        require(uri.isNotBlank()) { "Input URI is missing." }
        require(name.lowercase(Locale.ROOT).endsWith(".gpx")) { "Select a file ending in .gpx." }
        require(bytes.isNotEmpty() && bytes.size <= GpxEngine.MAX_INPUT_BYTES) { "GPX size must be 1 byte to 25 MiB." }
        invalidate()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        source = Source(uri, name, bytes.copyOf(), statistics, digest)
        stage = Stage.IMPORT
    }

    fun updateOptions(next: Options) {
        if (next == options) return
        options = next
        optionsRevision++
        invalidate()
    }

    fun navigate(next: Stage): Boolean {
        if (running || next.ordinal > stage.ordinal + 1) return false
        val can = when (next) {
            Stage.IMPORT -> true
            Stage.CONFIGURE -> source != null
            Stage.GENERATE -> source != null && runCatching { options.validate() }.isSuccess
            Stage.REVIEW -> isCurrentOutput()
            Stage.EXPORT -> isCurrentOutput() && reviewed
        }
        if (can) stage = next
        return can
    }

    fun beginGeneration(): Long {
        require(stage == Stage.GENERATE && source != null) { "Import a valid GPX before generating." }
        options.validate()
        check(!running) { "Generation already in progress." }
        invalidate()
        running = true
        activeRunId++
        return activeRunId
    }

    fun generationFailed(runId: Long): Boolean {
        if (runId != activeRunId || !running) return false
        running = false
        return true
    }

    fun finishGeneration(runId: Long, sourceSha: String, version: Long, result: GpxEngine.Result,
                         warnings: List<String> = emptyList()): Boolean {
        if (!running || runId != activeRunId || source?.sha256 != sourceSha || version != optionsRevision) return false
        val name = source!!.name.substringBeforeLast('.') + "_${options.intervalKm}.0_wpt.gpx"
        output = Output(result, name, sourceSha, version, runId, warnings.toList())
        reviewed = false
        exportedUri = null
        running = false
        return true
    }

    fun isCurrentOutput(): Boolean = output?.let {
        !running && it.sourceSha256 == source?.sha256 && it.optionsRevision == optionsRevision
    } ?: false

    fun acceptReview(): Boolean {
        if (stage != Stage.REVIEW || !isCurrentOutput()) return false
        reviewed = true
        return navigate(Stage.EXPORT)
    }

    fun exportSucceeded(uri: String): Boolean {
        if (stage != Stage.EXPORT || !reviewed || !isCurrentOutput() || uri.isBlank()) return false
        exportedUri = uri
        return true
    }

    fun cancelGeneration() {
        if (running) {
            activeRunId++ // Discard any asynchronous completion, even if execution cannot be interrupted.
            running = false
        }
        invalidate()
    }

    fun newActivity() {
        invalidate()
        source = null
        stage = Stage.IMPORT
    }

    private fun invalidate() {
        activeRunId++
        running = false
        output = null
        reviewed = false
        exportedUri = null
    }
}

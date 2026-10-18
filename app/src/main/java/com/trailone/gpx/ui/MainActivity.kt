package com.trailone.gpx.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.trailone.gpx.core.GpxEngine
import com.trailone.gpx.network.NominatimGeocoder
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TrailOne GPX five-stage guided workflow, owner-approved UX direction.
 * UI component styling is a proposed mapping of approved palette v1.0.0.
 * The GPX engine and network provider are not modified by this implementation.
 */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_IMPORT = 201
        private const val REQUEST_EXPORT = 202
        private val FOREST = Color.rgb(4, 41, 27)          // #04291B
        private val MOUNTAIN = Color.rgb(77, 108, 90)      // #4D6C5A
        private val MOUNTAIN_DARK = Color.rgb(73, 104, 86) // #496856
        private val SAGE = Color.rgb(131, 148, 105)       // #839469
        private val OCHRE = Color.rgb(212, 155, 57)       // #D49B39
        private val SANDSTONE = Color.rgb(234, 207, 154)  // #EACF9A
        private val IVORY = Color.rgb(251, 239, 215)      // #FBEFD7
        private val CANVAS = Color.rgb(254, 254, 254)     // #FEFEFE
    }

    private val session = GuidedSession()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var container: LinearLayout
    private var importBusy = false
    private var exportBusy = false
    private var importSerial = 0L
    private var importError: String? = null
    private var generationError: String? = null
    private var exportError: String? = null
    private var exportState: String? = null
    private var advancedVisible = false
    private var pendingOutput: GuidedSession.Output? = null
    private var systemBackCallback: android.window.OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = FOREST
        @Suppress("DEPRECATION")
        window.navigationBarColor = FOREST
        window.decorView.systemUiVisibility = 0
        if (savedInstanceState != null) {
            val method = runCatching {
                GpxEngine.DistanceMethod.valueOf(savedInstanceState.getString("method") ?: "AUTO")
            }.getOrDefault(GpxEngine.DistanceMethod.AUTO)
            val metadata = if (savedInstanceState.getBoolean("legacy", false))
                GpxEngine.MetadataMode.TRAILONE_LEGACY else GpxEngine.MetadataMode.PRESERVE_SOURCE
            session.updateOptions(GuidedSession.Options(
                prefix = savedInstanceState.getString("prefix") ?: "HIK",
                intervalKm = savedInstanceState.getInt("interval", 1),
                method = method,
                onlineGeocoding = savedInstanceState.getBoolean("geocode", false),
                endpoint = savedInstanceState.getString("endpoint") ?: "https://nominatim.openstreetmap.org/reverse",
                metadataMode = metadata,
            ))
            advancedVisible = savedInstanceState.getBoolean("advanced", false)
        }
        initializeLayout()
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback {
                if (session.stage == GuidedSession.Stage.IMPORT) finish() else goBack()
            }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
            systemBackCallback = callback
        }
        render()
        // Android may recreate the Activity; revalidate from the persisted source URI.
        // Derived GPX output is deliberately not serialized into a Binder Bundle.
        val originalUri = savedInstanceState?.getString("sourceUri")
        if (originalUri != null) {
            val requested = savedInstanceState.getInt("stage", 0)
            val name = savedInstanceState.getString("sourceName") ?: "source.gpx"
            importSource(Uri.parse(originalUri), name, restoreToConfigure = requested > 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val o = session.options
        outState.putString("sourceUri", session.source?.uri)
        outState.putString("sourceName", session.source?.name)
        outState.putInt("stage", session.stage.ordinal)
        outState.putString("prefix", o.prefix)
        outState.putInt("interval", o.intervalKm)
        outState.putString("method", o.method.name)
        outState.putBoolean("geocode", o.onlineGeocoding)
        outState.putString("endpoint", o.endpoint)
        outState.putBoolean("legacy", o.metadataMode == GpxEngine.MetadataMode.TRAILONE_LEGACY)
        outState.putBoolean("advanced", advancedVisible)
    }

    private fun initializeLayout() {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CANVAS)
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { v, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(0, bars.top, 0, bars.bottom)
                    insets
                }
            } else {
                @Suppress("DEPRECATION")
                setOnApplyWindowInsetsListener { v, insets ->
                    v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                    insets
                }
            }
        }
        setContentView(container)
    }

    private fun render() {
        if (isFinishing || isDestroyed) return
        container.removeAllViews()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FOREST)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        top.addView(textView("TRAILONE  /  GPX", 12f, SANDSTONE, bold = true), block(0, 7))
        val stage = session.stage
        val stageNumber = stage.ordinal + 1
        top.addView(textView("${stageNumber.toString().padStart(2, '0')}  ${stageTitle(stage)}", 23f, IVORY, bold = true), block(0, 6))
        top.addView(textView("Step $stageNumber of 5  ·  ${stageSubtitle(stage)}", 12f, IVORY), block(0, 12))
        val progressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(5) { i ->
            val strip = View(this).apply { background = round(if (i <= stage.ordinal) OCHRE else MOUNTAIN_DARK, 3) }
            progressRow.addView(strip, LinearLayout.LayoutParams(0, dp(5), 1f).apply { if (i > 0) marginStart = dp(5) })
        }
        top.addView(progressRow)
        container.addView(top)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(20), dp(17), dp(28))
        }
        scroll.addView(body)
        container.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        when (stage) {
            GuidedSession.Stage.IMPORT -> renderImport(body)
            GuidedSession.Stage.CONFIGURE -> renderConfigure(body)
            GuidedSession.Stage.GENERATE -> renderGenerate(body)
            GuidedSession.Stage.REVIEW -> renderReview(body)
            GuidedSession.Stage.EXPORT -> renderExport(body)
        }
        renderActions(stage)
    }

    private fun stageTitle(stage: GuidedSession.Stage): String = when (stage) {
        GuidedSession.Stage.IMPORT -> "Import GPX"
        GuidedSession.Stage.CONFIGURE -> "Configure"
        GuidedSession.Stage.GENERATE -> "Generate"
        GuidedSession.Stage.REVIEW -> "Review"
        GuidedSession.Stage.EXPORT -> "Export"
    }

    private fun stageSubtitle(stage: GuidedSession.Stage): String = when (stage) {
        GuidedSession.Stage.IMPORT -> "Validate an activity document"
        GuidedSession.Stage.CONFIGURE -> "Set waypoint generation rules"
        GuidedSession.Stage.GENERATE -> "Process the original track"
        GuidedSession.Stage.REVIEW -> "Inspect generated GPX"
        GuidedSession.Stage.EXPORT -> "Save the resulting document"
    }

    private fun renderImport(body: LinearLayout) {
        body.addView(sectionTitle("Source activity"))
        body.addView(description("Select a GPX 1.1 track. The original file is read-only and will not be overwritten."), block(0, 14))
        val importButton = if (session.source == null) primaryButton("Choose GPX file") { chooseDocument() }
                           else secondaryButton("Replace GPX file") { chooseDocument() }
        body.addView(importButton, block(0, 14))
        if (importBusy) {
            body.addView(progressRow("Validating GPX structure and geometry…"), block(0, 12))
        }
        importError?.let { body.addView(notice("Cannot import: $it", caution = true), block(0, 12)) }
        session.source?.let { s ->
            val panel = panel()
            panel.addView(overline("VALIDATED SOURCE"))
            panel.addView(textView(s.name, 18f, FOREST, bold = true), block(5, 8))
            stat(panel, "Track points", "${s.stats.points}")
            stat(panel, "Tracks / segments", "${s.stats.tracks} / ${s.stats.segments}")
            stat(panel, "Distance (analysis)", "${number(s.stats.distanceMeters / 1000.0)} km")
            stat(panel, "Elevation range", "${number(s.stats.minimumElevation)}–${number(s.stats.maximumElevation)} m")
            body.addView(panel, block(0, 15))
            body.addView(notice("Validation passed. You may continue to configuration."), block(0, 9))
        } ?: run {
            body.addView(notice("Accepted: GPX 1.1 · up to 25 MiB · up to 200,000 track points. At least two points and usable elevation data are required."), block(0, 12))
        }
    }

    private fun renderConfigure(body: LinearLayout) {
        val source = session.source ?: return
        body.addView(overline("INPUT · ${source.name}"), block(0, 9))
        body.addView(sectionTitle("Waypoint parameters"), block(0, 6))
        body.addView(description("Required parameters determine the waypoint names and kilometer markers."), block(0, 16))
        val options = session.options
        body.addView(fieldLabel("Prefix  ·  exactly 3 uppercase letters"))
        val prefix = input(options.prefix, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        prefix.contentDescription = "Waypoint prefix, three uppercase letters"
        prefix.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(3))
        prefix.addTextChangedListener(changeListener { raw ->
            session.updateOptions(session.options.copy(prefix = raw.uppercase(Locale.ROOT)))
        })
        body.addView(prefix, block(5, 15))
        body.addView(fieldLabel("Kilometer marker interval  ·  1–100 km"))
        val km = input(options.intervalKm.toString(), InputType.TYPE_CLASS_NUMBER)
        km.contentDescription = "Marker interval, whole kilometers from 1 to 100"
        km.addTextChangedListener(changeListener { raw ->
            session.updateOptions(session.options.copy(intervalKm = raw.toIntOrNull() ?: 0))
        })
        body.addView(km, block(5, 15))
        val expandable = secondaryButton(if (advancedVisible) "Advanced settings  −" else "Advanced settings  +") {
            advancedVisible = !advancedVisible
            render()
        }
        body.addView(expandable, block(0, 12))
        if (advancedVisible) {
            val advanced = panel()
            advanced.addView(fieldLabel("Horizontal distance algorithm"), block(0, 7))
            val values = GpxEngine.DistanceMethod.entries.toList()
            val spinner = Spinner(this).apply {
                contentDescription = "Horizontal distance algorithm"
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values.map { it.name })
                setSelection(values.indexOf(options.method).coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        session.updateOptions(session.options.copy(method = values[position]))
                    }
                }
                minimumHeight = dp(50)
            }
            advanced.addView(spinner, block(0, 12))
            val geocode = toggle("Reverse-geocode waypoint coordinates (online)", options.onlineGeocoding)
            advanced.addView(geocode)
            advanced.addView(description("Disabled by default. Enabling it sends waypoint coordinates to the selected HTTPS provider."), block(3, 11))
            val endpointContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            endpointContainer.addView(fieldLabel("HTTPS endpoint"))
            val endpoint = input(options.endpoint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            endpoint.contentDescription = "HTTPS reverse-geocoding endpoint"
            endpoint.addTextChangedListener(changeListener { raw ->
                session.updateOptions(session.options.copy(endpoint = raw.trim()))
            })
            endpointContainer.addView(endpoint, block(5, 7))
            endpointContainer.addView(description("Public Nominatim usage is subject to rate limits. © OpenStreetMap contributors."))
            endpointContainer.visibility = if (options.onlineGeocoding) View.VISIBLE else View.GONE
            advanced.addView(endpointContainer, block(3, 10))
            geocode.setOnCheckedChangeListener { _, selected ->
                session.updateOptions(session.options.copy(onlineGeocoding = selected))
                endpointContainer.visibility = if (selected) View.VISIBLE else View.GONE
            }
            val legacy = toggle("Replace original source attribution (legacy)", options.metadataMode == GpxEngine.MetadataMode.TRAILONE_LEGACY)
            legacy.setOnCheckedChangeListener { _, selected ->
                session.updateOptions(session.options.copy(metadataMode = if (selected)
                    GpxEngine.MetadataMode.TRAILONE_LEGACY else GpxEngine.MetadataMode.PRESERVE_SOURCE))
            }
            advanced.addView(legacy, block(0, 4))
            advanced.addView(description("Recommended: preserve source metadata. Output creator identifies the converter."))
            body.addView(advanced, block(0, 13))
        }
        body.addView(notice("Generated names include ${options.prefix}_TH, ${options.prefix}_TE, elevation extrema, midpoint and KM markers."), block(0, 10))
    }

    private fun renderGenerate(body: LinearLayout) {
        val src = session.source ?: return
        body.addView(sectionTitle("Ready for processing"))
        body.addView(description("Source and settings are frozen for each generation. Processing creates a new GPX document; it never writes to the source."), block(0, 15))
        val panel = panel()
        stat(panel, "Input", src.name)
        stat(panel, "Prefix", session.options.prefix)
        stat(panel, "Interval", "${session.options.intervalKm} km")
        stat(panel, "Method", session.options.method.name)
        stat(panel, "Geocoding", if (session.options.onlineGeocoding) "Enabled · online" else "Off · offline")
        body.addView(panel, block(0, 15))
        when {
            session.running -> body.addView(progressRow("Generating markers and GPX output…"))
            generationError != null -> body.addView(notice("Generation failed: $generationError. Settings and source are retained for retry.", caution = true))
            session.isCurrentOutput() -> body.addView(notice("Generation completed: ${session.output!!.result.waypoints.size} waypoints. Continue to review."))
            else -> body.addView(notice("Select Generate waypoints to begin. Online geocoding may take additional time."))
        }
    }

    private fun renderReview(body: LinearLayout) {
        val current = session.output?.takeIf { session.isCurrentOutput() } ?: return
        val r = current.result
        body.addView(overline("COMPUTED RESULT · GPX 1.1"), block(0, 7))
        body.addView(sectionTitle("Activity summary"), block(0, 12))
        val panel = panel()
        stat(panel, "Distance", "${number(r.statistics.distanceMeters / 1000.0)} km")
        stat(panel, "Ascent / descent", "${number(r.statistics.ascentMeters)} / ${number(r.statistics.descentMeters)} m")
        stat(panel, "Elevation min / max", "${number(r.statistics.minimumElevation)} / ${number(r.statistics.maximumElevation)} m")
        stat(panel, "Tracks / segments / points", "${r.statistics.tracks} / ${r.statistics.segments} / ${r.statistics.points}")
        stat(panel, "Generated waypoints", "${r.waypoints.size}")
        body.addView(panel, block(0, 13))
        current.warnings.forEach { body.addView(notice(it, caution = true), block(0, 10)) }
        body.addView(sectionTitle("Generated waypoint identifiers"), block(0, 10))
        val markers = panel()
        r.waypoints.forEachIndexed { index, wp ->
            markers.addView(textView("${index + 1}. ${wp.name}   ·   ${number(wp.distanceMeters / 1000.0)} km", 14f, FOREST), block(0, 9))
        }
        body.addView(markers)
        body.addView(description("Changing parameters invalidates this output and requires regeneration."), block(12, 10))
        body.addView(secondaryButton("Adjust waypoint parameters") {
            session.navigate(GuidedSession.Stage.CONFIGURE)
            render()
        }, block(0, 0))
    }

    private fun renderExport(body: LinearLayout) {
        val current = session.output?.takeIf { session.isCurrentOutput() && session.reviewed } ?: return
        body.addView(overline("REVIEWED GPX DOCUMENT"), block(0, 8))
        body.addView(sectionTitle("Save generated GPX"), block(0, 9))
        body.addView(description("Android will ask where to save this new document. The source file remains untouched."), block(0, 15))
        val panel = panel()
        stat(panel, "Suggested filename", current.filename)
        stat(panel, "Generated waypoints", "${current.result.waypoints.size}")
        stat(panel, "File size", "${current.result.output.size} bytes")
        body.addView(panel, block(0, 14))
        when {
            exportBusy -> body.addView(progressRow("Writing document to the selected location…"))
            exportError != null -> body.addView(notice("Save failed: $exportError. You may retry.", caution = true))
            session.exportedUri != null -> body.addView(notice("Saved successfully. You may start a new activity or save another copy."))
            exportState != null -> body.addView(notice(exportState!!))
            else -> body.addView(notice("Select Save GPX document to choose a destination."))
        }
        if (session.exportedUri != null) body.addView(secondaryButton("Start new activity") {
            importSerial++
            session.newActivity()
            generationError = null
            exportError = null
            exportState = null
            render()
        }, block(16, 0))
    }

    private fun renderActions(stage: GuidedSession.Stage) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(IVORY)
            setPadding(dp(17), dp(11), dp(17), dp(15))
        }
        if (stage != GuidedSession.Stage.IMPORT) {
            val back = secondaryButton(if (stage == GuidedSession.Stage.GENERATE && session.running)
                "Cancel & back" else "Back") { goBack() }
            back.isEnabled = !importBusy && !exportBusy
            row.addView(back, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        }
        val isBusy = importBusy || exportBusy || session.running
        val primaryText = when (stage) {
            GuidedSession.Stage.IMPORT -> "Continue"
            GuidedSession.Stage.CONFIGURE -> "Continue"
            GuidedSession.Stage.GENERATE -> if (session.isCurrentOutput()) "Review results" else if (session.running) "Processing…" else "Generate waypoints"
            GuidedSession.Stage.REVIEW -> "Continue to export"
            GuidedSession.Stage.EXPORT -> "Save GPX document"
        }
        val button = primaryButton(primaryText) {
            when (stage) {
                GuidedSession.Stage.IMPORT -> if (session.navigate(GuidedSession.Stage.CONFIGURE)) render()
                GuidedSession.Stage.CONFIGURE -> {
                    val valid = runCatching { session.options.validate() }
                    if (valid.isFailure) {
                        android.widget.Toast.makeText(this, valid.exceptionOrNull()?.message ?: "Invalid settings.", android.widget.Toast.LENGTH_LONG).show()
                    } else if (session.navigate(GuidedSession.Stage.GENERATE)) { generationError = null; render() }
                }
                GuidedSession.Stage.GENERATE -> if (session.isCurrentOutput()) {
                    if (session.navigate(GuidedSession.Stage.REVIEW)) render()
                } else generate()
                GuidedSession.Stage.REVIEW -> if (session.acceptReview()) render()
                GuidedSession.Stage.EXPORT -> saveDocument()
            }
        }
        button.isEnabled = !isBusy && when (stage) {
            GuidedSession.Stage.IMPORT -> session.source != null
            GuidedSession.Stage.CONFIGURE -> session.source != null
            GuidedSession.Stage.GENERATE -> session.source != null
            GuidedSession.Stage.REVIEW -> session.isCurrentOutput()
            GuidedSession.Stage.EXPORT -> session.isCurrentOutput() && session.reviewed
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(52), if (stage == GuidedSession.Stage.IMPORT) 1f else 2f))
        container.addView(row)
    }

    private fun goBack() {
        if (importBusy || exportBusy) return
        if (session.running) {
            session.cancelGeneration() // old asynchronous completion will be ignored
        }
        val current = session.stage.ordinal
        if (current > 0) session.navigate(GuidedSession.Stage.entries[current - 1])
        render()
    }

    @Deprecated("Back dispatch retained for native Activity and hardware back button")
    override fun onBackPressed() {
        if (session.stage != GuidedSession.Stage.IMPORT) goBack() else super.onBackPressed()
    }

    private fun chooseDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Framework Activity callback for Android Storage Access Framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_EXPORT) { exportState = "Save cancelled. Generated GPX retained."; render() }
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_IMPORT -> {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                importSource(uri, getDisplayName(uri))
            }
            REQUEST_EXPORT -> writeOutput(uri)
        }
    }

    private fun importSource(uri: Uri, name: String, restoreToConfigure: Boolean = false) {
        if (importBusy || exportBusy || session.running) return
        importBusy = true
        importError = null
        // Replacing a document invalidates the previous source and all derived output.
        session.newActivity()
        pendingOutput = null
        val ticket = ++importSerial
        render()
        executor.execute {
            val result = runCatching {
                require(name.endsWith(".gpx", ignoreCase = true)) { "Filename must end with .gpx." }
                val data = readBounded(uri, GpxEngine.MAX_INPUT_BYTES)
                val stats = GpxEngine.analyze(data).statistics
                data to stats
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || ticket != importSerial) return@runOnUiThread
                importBusy = false
                result.onSuccess { (bytes, stats) ->
                    session.importValidated(uri.toString(), name, bytes, stats)
                    if (restoreToConfigure) session.navigate(GuidedSession.Stage.CONFIGURE)
                    generationError = null
                    exportError = null
                    exportState = if (restoreToConfigure) "Previous result must be regenerated after Android recreated the screen." else null
                }.onFailure { error -> importError = error.message ?: "Unable to parse GPX file." }
                render()
            }
        }
    }

    private fun generate() {
        val source = session.source ?: return
        val options = session.options
        val revision = session.optionsRevision
        val token = runCatching { session.beginGeneration() }.getOrElse {
            generationError = it.message ?: "Invalid generation request."
            render()
            return
        }
        generationError = null
        render()
        executor.execute {
            val generated = runCatching {
                // Reparse original bytes for every run. Never enrich a previously modified DOM.
                val analysis = GpxEngine.analyze(source.bytes)
                val markers = GpxEngine.generateWaypoints(analysis, options.prefix, options.intervalKm, options.method)
                val geocoder = if (options.onlineGeocoding) NominatimGeocoder(applicationContext, options.endpoint) else null
                val result = GpxEngine.enrich(analysis, markers, options.metadataMode) { lat, lon ->
                    geocoder?.reverse(lat, lon)
                }
                val unresolved = if (options.onlineGeocoding) result.waypoints.count { it.description.isNullOrBlank() } else 0
                val warnings = if (unresolved > 0) listOf("$unresolved of ${result.waypoints.size} waypoints have no geocoded description. The GPX is still valid; provider availability may vary.") else emptyList()
                result to warnings
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                generated.onSuccess { (result, warnings) ->
                    session.finishGeneration(token, source.sha256, revision, result, warnings)
                }.onFailure { error ->
                    if (session.generationFailed(token)) generationError = error.message ?: error.javaClass.simpleName
                }
                render()
            }
        }
    }

    private fun saveDocument() {
        if (!session.isCurrentOutput() || !session.reviewed || exportBusy) return
        pendingOutput = session.output
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, pendingOutput!!.filename)
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun writeOutput(destination: Uri) {
        val frozen = pendingOutput
        if (frozen == null || !session.isCurrentOutput() || session.output !== frozen) {
            exportError = "Output changed before save; regenerate and review."
            render()
            return
        }
        exportBusy = true
        exportState = null
        exportError = null
        render()
        executor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(destination, "w")?.use { stream ->
                    stream.write(frozen.result.output)
                    stream.flush()
                } ?: error("Cannot open the selected destination for writing.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                exportBusy = false
                result.onSuccess {
                    if (session.output === frozen && session.exportSucceeded(destination.toString())) exportState = "GPX saved."
                    else exportError = "Source or settings changed; saved file requires verification."
                }.onFailure { exportError = it.message ?: "Could not save document; check selected location." }
                render()
            }
        }
    }

    private fun readBounded(uri: Uri, maximum: Int): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("Cannot open input GPX document.")
        return input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            val sink = ByteArrayOutputStream()
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                require(sink.size() + read <= maximum) { "GPX exceeds 25 MiB input limit." }
                sink.write(buffer, 0, read)
            }
            sink.toByteArray()
        }
    }

    private fun getDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "source.gpx"
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(IVORY, 15, SANDSTONE)
        setPadding(dp(16), dp(16), dp(16), dp(15))
    }
    private fun stat(parent: LinearLayout, label: String, value: String) {
        parent.addView(textView(label.uppercase(Locale.ROOT), 11f, MOUNTAIN_DARK, true), block(3, 4))
        parent.addView(textView(value, 15f, FOREST, true), block(0, 12))
    }
    private fun sectionTitle(label: String) = textView(label, 22f, FOREST, true)
    private fun fieldLabel(label: String) = textView(label, 13f, MOUNTAIN_DARK, true)
    private fun overline(label: String) = textView(label, 11f, MOUNTAIN_DARK, true)
    private fun description(label: String) = textView(label, 14f, MOUNTAIN)
    private fun notice(message: String, caution: Boolean = false) = textView(message, 13f, FOREST).apply {
        background = round(if (caution) SANDSTONE else IVORY, 12)
        setPadding(dp(13), dp(13), dp(13), dp(13))
    }
    private fun progressRow(message: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val progress = ProgressBar(this@MainActivity).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(OCHRE)
        }
        addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        addView(textView(message, 14f, FOREST), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun input(value: String, flags: Int) = EditText(this).apply {
        inputType = flags
        setSingleLine(true)
        setText(value)
        textSize = 16f
        minHeight = dp(54)
        setTextColor(FOREST)
        setHintTextColor(MOUNTAIN)
        background = round(CANVAS, 11, SAGE)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }
    private fun toggle(label: String, checked: Boolean) = CheckBox(this).apply {
        text = label
        isChecked = checked
        textSize = 14f
        minHeight = dp(48)
        setTextColor(FOREST)
        buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(FOREST, MOUNTAIN))
    }
    private fun textView(label: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun round(color: Int, radius: Int, border: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border != null) setStroke(dp(1), border)
    }
    private fun primaryButton(label: String, click: () -> Unit) = button(label, FOREST, IVORY, click)
    private fun secondaryButton(label: String, click: () -> Unit) = button(label, SANDSTONE, FOREST, click)
    private fun button(label: String, backgroundColor: Int, foregroundColor: Int, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(50)
        setTextColor(foregroundColor)
        backgroundTintList = null
        background = round(backgroundColor, 11)
        setOnClickListener { click() }
    }
    private fun changeListener(change: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            change(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
    private fun block(top: Int, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun number(value: Double) = String.format(Locale.US, "%.2f", value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) systemBackCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        importSerial++
        executor.shutdownNow()
        super.onDestroy()
    }
}

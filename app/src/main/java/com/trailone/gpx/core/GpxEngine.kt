package com.trailone.gpx.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.xml.sax.SAXException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.*

/** Kotlin port of nestrada88/gpx-wpt-tag-revgeo's *processing behavior*.
 * Uses JDK APIs supported on Android; no Android dependencies in the engine.
 */
object GpxEngine {
    const val NS = "http://www.topografix.com/GPX/1/1"
    const val MAX_POINTS = 200_000
    const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    private const val AUTO_HAVERSINE_THRESHOLD = 50_000
    private const val MAX_KM_MARKER = 999
    private const val EARTH_RADIUS = 6_371_000.0
    // Android API 36 does not expose the Java SE XMLConstants.ACCESS_EXTERNAL_*
    // fields, although some XML providers accept their standard property URIs.
    // These are best-effort hardening properties; external entity resolution is
    // independently prohibited by the parser features and entity resolver below.
    private const val ACCESS_EXTERNAL_DTD_URI =
        "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA_URI =
        "http://javax.xml.XMLConstants/property/accessExternalSchema"
    private const val ACCESS_EXTERNAL_STYLESHEET_URI =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"

    enum class DistanceMethod { AUTO, GEODESIC, HAVERSINE }
    enum class MetadataMode { PRESERVE_SOURCE, TRAILONE_LEGACY }

    data class Point(val latitude: Double, val longitude: Double, val elevation: Double?)
    data class Statistics(
        val tracks: Int,
        val segments: Int,
        val points: Int,
        val distanceMeters: Double,
        val ascentMeters: Double,
        val descentMeters: Double,
        val minimumElevation: Double,
        val maximumElevation: Double,
    ) {
        val elevationRange: Double get() = maximumElevation - minimumElevation
    }
    data class Waypoint(
        val point: Point,
        val name: String,
        val comment: String,
        var description: String? = null,
        val sourceOrder: Double,
        val distanceMeters: Double,
        val tieRank: Int,
        val sequence: Int,
    )
    data class Analysis internal constructor(
        val segments: List<List<Point>>,
        val document: Document,
        val statistics: Statistics,
    )
    data class Result(val output: ByteArray, val statistics: Statistics, val waypoints: List<Waypoint>)

    /** Strict external-entity-free parser. The file size is checked before parsing. */
    fun analyze(input: ByteArray): Analysis {
        require(input.isNotEmpty()) { "GPX input is empty." }
        require(input.size <= MAX_INPUT_BYTES) { "GPX file exceeds the 25 MiB input limit." }
        // Android's DocumentBuilderFactory does not implement FEATURE_SECURE_PROCESSING.
        // Independently reject DTDs and non-UTF-8 data before the DOM parser sees them.
        val xmlText = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(input)).toString()
        require("<!DOCTYPE" !in xmlText) { "DOCTYPE declarations are prohibited." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setExpandEntityReferences(false)
            // Optional parser features differ across Android implementations.
            runCatching { setXIncludeAware(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD_URI, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA_URI, "") }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External entity resolution is prohibited.") }
        val doc = builder.parse(ByteArrayInputStream(input))
        val root = doc.documentElement
        require(root.localName == "gpx" && root.namespaceURI == NS && root.getAttribute("version") == "1.1") {
            "Only GPX 1.1 files in the official namespace are supported."
        }

        val tracks = children(root, "trk")
        require(tracks.isNotEmpty()) { "GPX contains no <trk> elements." }
        val segments = mutableListOf<List<Point>>()
        var count = 0
        tracks.forEachIndexed { ti, trk ->
            val trackSegments = children(trk, "trkseg")
            require(trackSegments.isNotEmpty()) { "Track ${ti + 1} has no segments." }
            trackSegments.forEachIndexed { si, seg ->
                val nodes = children(seg, "trkpt")
                require(nodes.isNotEmpty()) { "Track ${ti + 1}, segment ${si + 1} is empty." }
                count += nodes.size
                require(count <= MAX_POINTS) { "GPX exceeds $MAX_POINTS track points." }
                segments += nodes.mapIndexed { index, el ->
                    val lat = coordinate(el, "lat", -90.0, 90.0)
                    val lon = coordinate(el, "lon", -180.0, 180.0)
                    val elevation = children(el, "ele").firstOrNull()?.textContent?.trim()?.toDoubleOrNull()
                    if (children(el, "ele").isNotEmpty()) {
                        require(elevation != null && elevation.isFinite() && elevation in -1000.0..9000.0) {
                            "Invalid elevation at track ${ti + 1}, segment ${si + 1}, point ${index + 1}."
                        }
                    }
                    Point(lat, lon, elevation)
                }
            }
        }
        require(count >= 2) { "GPX must contain at least two track points." }
        var min: Double? = null
        var max: Double? = null
        var ascent = 0.0
        var descent = 0.0
        var distance = 0.0
        for (segment in segments) {
            var previousElevation: Double? = null
            segment.forEachIndexed { index, point ->
                point.elevation?.let { ele ->
                    min = if (min == null) ele else minOf(min!!, ele)
                    max = if (max == null) ele else maxOf(max!!, ele)
                    previousElevation?.let { previous ->
                        val difference = ele - previous
                        if (difference > 0.0) ascent += difference else descent -= difference
                    }
                    previousElevation = ele
                }
                if (index > 0) distance += distance3d(segment[index - 1], point, DistanceMethod.AUTO, count)
            }
        }
        require(min != null && max != null) { "GPX has no usable elevation measurements." }
        return Analysis(
            segments,
            doc,
            Statistics(tracks.size, segments.size, count, distance, ascent, descent, min!!, max!!)
        )
    }

    fun validateOptions(prefix: String, intervalKm: Int) {
        require(Regex("[A-Z]{3}").matches(prefix)) { "Prefix must contain exactly three uppercase letters." }
        require(intervalKm in 1..100) { "Interval must be a whole number from 1 to 100 km." }
    }

    fun generateWaypoints(
        analysis: Analysis,
        prefix: String,
        intervalKm: Int,
        method: DistanceMethod = DistanceMethod.AUTO,
    ): List<Waypoint> {
        validateOptions(prefix, intervalKm)
        val segments = analysis.segments
        val pointCount = analysis.statistics.points
        val positions = mutableListOf<List<Double>>()
        val orders = mutableListOf<List<Int>>()
        val lengths = mutableListOf<List<Double>>()
        var runningDistance = 0.0
        var runningIndex = 0
        for (segment in segments) {
            val currentOrders = segment.map { runningIndex++ }
            val currentPositions = mutableListOf(runningDistance)
            val currentLengths = mutableListOf<Double>()
            for (i in 1 until segment.size) {
                val leg = distance3d(segment[i-1], segment[i], method, pointCount)
                currentLengths += leg
                runningDistance += leg
                currentPositions += runningDistance
            }
            positions += currentPositions
            orders += currentOrders
            lengths += currentLengths
        }

        val all = mutableListOf<Waypoint>()
        fun add(point: Point, kind: String, nameSuffix: String, label: String, position: Double, distance: Double) {
            val priority = mapOf("TH" to 0, "LE" to 1, "HE" to 2, "MP" to 3, "KM" to 4, "TE" to 5)
            all += Waypoint(point, "${prefix}_$nameSuffix", label, sourceOrder = position,
                distanceMeters = distance, tieRank = priority.getValue(kind), sequence = all.size)
        }
        val first = segments.first().first()
        val last = segments.last().last()
        add(first, "TH", "TH", "Trail Head", 0.0, 0.0)
        var lowest: Triple<Point, Int, Double>? = null
        var highest: Triple<Point, Int, Double>? = null
        segments.forEachIndexed { si, segment ->
            segment.forEachIndexed { pi, point ->
                if (point.elevation != null) {
                    if (lowest == null || point.elevation < lowest!!.first.elevation!!) {
                        lowest = Triple(point, orders[si][pi], positions[si][pi])
                    }
                    if (highest == null || point.elevation > highest!!.first.elevation!!) {
                        highest = Triple(point, orders[si][pi], positions[si][pi])
                    }
                }
            }
        }
        lowest?.let { add(it.first, "LE", "LE", "Lowest Elevation Point", it.second.toDouble(), it.third) }
        highest?.let { add(it.first, "HE", "HE", "Highest Elevation Point", it.second.toDouble(), it.third) }

        val half = runningDistance / 2.0
        var nextKilometer = intervalKm
        var halfwayAdded = false
        var cumulative = 0.0
        for (si in segments.indices) {
            val segment = segments[si]
            for (j in 1 until segment.size) {
                val leg = lengths[si][j-1]
                val endDistance = cumulative + leg
                val order = orders[si][j-1]
                while (nextKilometer <= MAX_KM_MARKER && endDistance >= nextKilometer * 1000.0) {
                    if (leg == 0.0) break
                    val milestone = nextKilometer * 1000.0
                    val frac = ((milestone-cumulative)/leg).coerceIn(0.0, 1.0)
                    add(interpolate(segment[j-1], segment[j], frac), "KM", "KM%03d".format(java.util.Locale.US, nextKilometer),
                        "$nextKilometer km Marker", order + frac, milestone)
                    nextKilometer += intervalKm
                }
                if (!halfwayAdded && endDistance >= half && leg > 0.0) {
                    val frac = ((half-cumulative)/leg).coerceIn(0.0, 1.0)
                    add(interpolate(segment[j-1], segment[j], frac), "MP", "MP", "Halfway Point", order + frac, half)
                    halfwayAdded = true
                }
                cumulative = endDistance
            }
        }
        add(last, "TE", "TE", "Trail End", (pointCount - 1).toDouble(), runningDistance)
        val names = all.map { it.name }
        require(names.size == names.toSet().size) { "Duplicate generated waypoint names." }
        return all.sortedWith(compareBy<Waypoint>({it.sourceOrder}, {it.distanceMeters}, {it.tieRank}, {it.sequence}))
    }

    /** Geocoding is a callback: the engine works fully offline. */
    fun enrich(
        analysis: Analysis,
        waypoints: List<Waypoint>,
        metadataMode: MetadataMode = MetadataMode.PRESERVE_SOURCE,
        reverseGeocode: (latitude: Double, longitude: Double) -> String? = {_,_ -> null},
    ): Result {
        // Network failures are handled by the provider. XML generation will still succeed offline.
        waypoints.forEach { wp -> wp.description = reverseGeocode(wp.point.latitude, wp.point.longitude)?.takeIf { it.isNotBlank() } }
        val bytes = serialize(analysis.document, waypoints, metadataMode)
        val total = waypoints.lastOrNull { it.name.endsWith("_TE") }?.distanceMeters
            ?: analysis.statistics.distanceMeters
        return Result(bytes, analysis.statistics.copy(distanceMeters = total), waypoints)
    }

    private fun serialize(document: Document, waypoints: List<Waypoint>, mode: MetadataMode): ByteArray {
        val root = document.documentElement
        // Original wpt/rte/trk/extensions nodes are retained rather than parsed and regenerated.
        // This preserves track-point extensions and source attribution by default.
        root.setAttribute("creator", "TrailOne GPX Android 1.0")
        if (mode == MetadataMode.TRAILONE_LEGACY) replaceMetadata(document, root, waypoints)
        val anchor = children(root).firstOrNull { it.localName == "rte" || it.localName == "trk" || it.localName == "extensions" }
        waypoints.forEach { wp ->
            val wpt = document.createElementNS(NS, "wpt")
            wpt.setAttribute("lat", wp.point.latitude.toString())
            wpt.setAttribute("lon", wp.point.longitude.toString())
            wp.point.elevation?.let { addText(document, wpt, "ele", it.toString()) }
            addText(document, wpt, "name", wp.name)
            addText(document, wpt, "cmt", wp.comment)
            wp.description?.let { addText(document, wpt, "desc", it) }
            if (anchor != null) root.insertBefore(wpt, anchor) else root.appendChild(wpt)
        }
        val transformerFactory = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD_URI, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_STYLESHEET_URI, "") }
        }
        val transformer = transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.VERSION, "1.0")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        val output = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun replaceMetadata(document: Document, root: Element, generated: List<Waypoint>) {
        val current = children(root, "metadata").firstOrNull()
        val meta = document.createElementNS(NS, "metadata")
        addText(document, meta, "name", "Trail One - Waypoint-Enriched GPX")
        addText(document, meta, "desc", "GPX trail export generated by Trail One; includes navigation waypoint markers.")
        val author = document.createElementNS(NS, "author")
        addText(document, author, "name", "Trail One")
        val link = document.createElementNS(NS, "link")
        link.setAttribute("href", "https://www.wikiloc.com/wikiloc/user.do?id=18125352")
        addText(document, link, "text", "Trail One on Wikiloc")
        author.appendChild(link)
        meta.appendChild(author)
        val copyright = document.createElementNS(NS, "copyright")
        copyright.setAttribute("author", "Trail One | Solo Hiking")
        addText(document, copyright, "year", Instant.now().atOffset(ZoneOffset.UTC).year.toString())
        meta.appendChild(copyright)
        val metaLink = document.createElementNS(NS, "link")
        metaLink.setAttribute("href", "https://www.wikiloc.com/wikiloc/user.do?id=18125352")
        addText(document, metaLink, "text", "Trail One on Wikiloc")
        meta.appendChild(metaLink)
        addText(document, meta, "time", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        addText(document, meta, "keywords", "Trail One, GPX, hiking, waypoint generation, navigation, elevation")
        val existingCoords = mutableListOf<Point>()
        fun collect(node: Node) {
            if (node is Element && node.namespaceURI == NS && node.localName in listOf("wpt", "rtept", "trkpt")) {
                val lat = node.getAttribute("lat").toDoubleOrNull()
                val lon = node.getAttribute("lon").toDoubleOrNull()
                if (lat != null && lon != null && lat.isFinite() && lon.isFinite()
                    && lat in -90.0..90.0 && lon in -180.0..180.0)
                    existingCoords += Point(lat, lon, null)
            }
            val kids = node.childNodes
            for (i in 0 until kids.length) collect(kids.item(i))
        }
        collect(root)
        val coords = existingCoords + generated.map { it.point }
        if (coords.isNotEmpty()) {
            val bounds = document.createElementNS(NS, "bounds")
            bounds.setAttribute("minlat", coords.minOf { it.latitude }.toString())
            bounds.setAttribute("minlon", coords.minOf { it.longitude }.toString())
            bounds.setAttribute("maxlat", coords.maxOf { it.latitude }.toString())
            bounds.setAttribute("maxlon", coords.maxOf { it.longitude }.toString())
            meta.appendChild(bounds)
        }
        // Preserve the original metadata extensions when replacing other fields.
        current?.let { existing -> children(existing, "extensions").forEach { meta.appendChild(it.cloneNode(true)) } }
        if (current != null) root.replaceChild(meta, current)
        else root.insertBefore(meta, root.firstChild)
    }

    private fun addText(doc: Document, parent: Element, tag: String, value: String) {
        val el = doc.createElementNS(NS, tag)
        el.textContent = value
        parent.appendChild(el)
    }
    private fun children(parent: Element, tag: String? = null): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.namespaceURI == NS && (tag == null || el.localName == tag)) result += el
        }
        return result
    }
    private fun coordinate(element: Element, name: String, min: Double, max: Double): Double {
        val parsed = element.getAttribute(name).toDoubleOrNull()
        require(parsed != null && parsed.isFinite() && parsed in min..max) { "Invalid $name coordinate." }
        return parsed
    }

    private fun interpolate(start: Point, end: Point, fraction: Double): Point {
        val rawLon = end.longitude - start.longitude
        val delta = if (abs(rawLon) > 180.0) ((rawLon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0 else rawLon
        var lon = start.longitude + fraction * delta
        if (lon > 180) lon -= 360.0 else if (lon < -180) lon += 360.0
        val elevation = if (start.elevation != null && end.elevation != null)
            start.elevation + fraction * (end.elevation - start.elevation) else null
        return Point(start.latitude + fraction * (end.latitude - start.latitude), lon, elevation)
    }

    fun distance3d(a: Point, b: Point, method: DistanceMethod, datasetSize: Int = 1): Double {
        validatePoint(a); validatePoint(b)
        val selected = if (method == DistanceMethod.AUTO)
            if (datasetSize > AUTO_HAVERSINE_THRESHOLD) DistanceMethod.HAVERSINE else DistanceMethod.GEODESIC
        else method
        val horizontal = if (selected == DistanceMethod.HAVERSINE) haversine(a, b) else vincentyOrHaversine(a, b)
        val elevation = if (a.elevation != null && b.elevation != null) b.elevation - a.elevation else 0.0
        return hypot(horizontal, elevation)
    }
    private fun validatePoint(p: Point) {
        require(p.latitude.isFinite() && p.latitude in -90.0..90.0) { "Latitude out of bounds." }
        require(p.longitude.isFinite() && p.longitude in -180.0..180.0) { "Longitude out of bounds." }
        require(p.elevation == null || p.elevation.isFinite()) { "Elevation must be finite." }
    }
    private fun haversine(a: Point, b: Point): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat/2).pow(2) + cos(lat1)*cos(lat2)*sin(dLon/2).pow(2)
        return 2.0 * EARTH_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Vincenty inverse on WGS84. If antipodal convergence fails, falls back to haversine. */
    private fun vincentyOrHaversine(a: Point, b: Point): Double {
        if (a.latitude == b.latitude && a.longitude == b.longitude) return 0.0
        val major = 6_378_137.0
        val flattening = 1.0 / 298.257223563
        val minor = (1 - flattening) * major
        val u1 = atan((1 - flattening) * tan(Math.toRadians(a.latitude)))
        val u2 = atan((1 - flattening) * tan(Math.toRadians(b.latitude)))
        val deltaLon = ((b.longitude - a.longitude + 540.0) % 360.0) - 180.0
        val L = Math.toRadians(deltaLon)
        var lambda = L
        var sigma = 0.0
        var sinSigma = 0.0
        var cosSigma = 0.0
        var sinAlpha: Double
        var cosSqAlpha = 0.0
        var cos2SigmaM = 0.0
        var converged = false
        for (i in 0 until 200) {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cos(u2) * sinLambda).pow(2) +
                    (cos(u1) * sin(u2) - sin(u1) * cos(u2) * cosLambda).pow(2))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sin(u1) * sin(u2) + cos(u1) * cos(u2) * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cos(u1) * cos(u2) * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha == 0.0) 0.0 else cosSigma - 2 * sin(u1) * sin(u2) / cosSqAlpha
            val C = flattening / 16 * cosSqAlpha * (4 + flattening * (4 - 3 * cosSqAlpha))
            val old = lambda
            lambda = L + (1 - C) * flattening * sinAlpha *
                    (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2*cos2SigmaM.pow(2))))
            if (abs(lambda - old) < 1e-12) { converged = true; break }
        }
        if (!converged) return haversine(a, b)
        val uSq = cosSqAlpha * (major*major - minor*minor) / (minor*minor)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175*uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47*uSq)))
        val deltaSigma = B * sinSigma * (cos2SigmaM + B/4 *
                (cosSigma * (-1 + 2*cos2SigmaM.pow(2)) - B/6 * cos2SigmaM *
                (-3 + 4*sinSigma.pow(2)) * (-3 + 4*cos2SigmaM.pow(2))))
        return minor * A * (sigma - deltaSigma)
    }
}

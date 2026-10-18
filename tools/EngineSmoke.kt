package com.trailone.gpx.core

import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import kotlin.math.abs

/** Android-independent behavioral regression checks for the GPX engine. */
private fun checkNear(actual: Double, expected: Double, tolerance: Double = 0.005) {
    check(abs(actual - expected) <= tolerance) { "Expected $expected ±$tolerance, got $actual" }
}

private fun gpx(body: String, metadata: String = ""): ByteArray = """
    <?xml version="1.0" encoding="UTF-8"?>
    <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="field-recorder">
    $metadata
    $body
    </gpx>
""".trimIndent().toByteArray(Charsets.UTF_8)

private fun trkpt(lat: Double, lon: Double, ele: String? = "100") =
    "<trkpt lat=\"$lat\" lon=\"$lon\">${ele?.let { "<ele>$it</ele>" } ?: ""}</trkpt>"

private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

fun main() {
    var passed = 0
    fun verify(name: String, test: () -> Unit) {
        test()
        println("PASS $name")
        passed++
    }

    verify("GPX import, elevation, semantic and km markers, export and metadata") {
        val raw = gpx("""
            <trk><trkseg>
              ${trkpt(0.0, 0.0, "100")}
              ${trkpt(0.0, 0.01, "110")}
              ${trkpt(0.0, 0.02, "120")}
            </trkseg></trk>
        """, "<metadata><name>Original field survey</name><author><name>Original creator</name></author></metadata>")
        val analysis = GpxEngine.analyze(raw)
        check(analysis.statistics.tracks == 1 && analysis.statistics.segments == 1)
        check(analysis.statistics.points == 3)
        checkNear(analysis.statistics.ascentMeters, 20.0)
        checkNear(analysis.statistics.descentMeters, 0.0)
        checkNear(analysis.statistics.minimumElevation, 100.0)
        checkNear(analysis.statistics.maximumElevation, 120.0)
        val wpts = GpxEngine.generateWaypoints(analysis, "HIK", 1)
        check(wpts.map { it.name }.toSet() == setOf("HIK_TH", "HIK_LE", "HIK_HE", "HIK_MP", "HIK_KM001", "HIK_KM002", "HIK_TE"))
        check(wpts.first().name == "HIK_TH" && wpts.last().name == "HIK_TE")
        val result = GpxEngine.enrich(analysis, wpts)
        val output = String(result.output)
        check("Original creator" in output && "Original field survey" in output)
        check(Regex("<trkpt\\s").findAll(output).count() == 3)
        val doc = parseXml(result.output)
        check(doc.getElementsByTagNameNS(GpxEngine.NS, "wpt").length == 7)
        check(doc.getElementsByTagNameNS(GpxEngine.NS, "trkpt").length == 3)
    }

    verify("WGS84 inverse distance") {
        val a = GpxEngine.Point(0.0, 0.0, 0.0)
        val b = GpxEngine.Point(0.0, 1.0, 0.0)
        checkNear(GpxEngine.distance3d(a, b, GpxEngine.DistanceMethod.GEODESIC), 111_319.490793, 0.005)
        check(GpxEngine.distance3d(a, b, GpxEngine.DistanceMethod.HAVERSINE) > 111_000.0)
    }

    verify("segment boundary gap does not inflate distance") {
        val raw = gpx("""
            <trk><trkseg>${trkpt(0.0,0.0)}${trkpt(0.0,0.001)}</trkseg>
            <trkseg>${trkpt(45.0,45.0)}${trkpt(45.0,45.001)}</trkseg></trk>
        """)
        val analysis = GpxEngine.analyze(raw)
        check(analysis.statistics.segments == 2)
        check(analysis.statistics.distanceMeters < 300.0)
        val names = GpxEngine.generateWaypoints(analysis, "MGT", 1).map { it.name }
        check(names.none { it.contains("_KM") })
    }

    verify("preserve original waypoints and tracks") {
        val raw = gpx("""
            <wpt lat="0" lon="0"><name>ORIGINAL</name></wpt>
            <trk><trkseg>${trkpt(0.0,0.0)}${trkpt(0.0,0.001)}</trkseg></trk>
        """)
        val a = GpxEngine.analyze(raw)
        val out = GpxEngine.enrich(a, GpxEngine.generateWaypoints(a,"HIK",1))
        check(String(out.output).contains("ORIGINAL"))
        check(parseXml(out.output).getElementsByTagNameNS(GpxEngine.NS,"trkpt").length == 2)
    }

    verify("prefix, interval, invalid coordinate and XXE safeguards") {
        check(runCatching { GpxEngine.validateOptions("FOUR", 1) }.isFailure)
        check(runCatching { GpxEngine.validateOptions("HIK", 0) }.isFailure)
        check(runCatching { GpxEngine.validateOptions("HIK", 101) }.isFailure)
        check(runCatching { GpxEngine.analyze(gpx("<trk><trkseg>${trkpt(91.0,0.0)}${trkpt(0.0,0.1)}</trkseg></trk>")) }.isFailure)
        val unsafe = """<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
             <gpx xmlns="${GpxEngine.NS}" version="1.1"><trk><trkseg>${trkpt(0.0,0.0)}${trkpt(0.0,0.01)}</trkseg></trk></gpx>"""
        check(runCatching { GpxEngine.analyze(unsafe.toByteArray()) }.isFailure)
    }

    verify("unknown elevation stays unknown in interpolated waypoints") {
        val raw = gpx("""
            <trk><trkseg>
            ${trkpt(0.0,0.0,"100")}
            ${trkpt(0.0,0.01,null)}
            ${trkpt(0.0,0.02,"110")}
            </trkseg></trk>
        """)
        val analysis = GpxEngine.analyze(raw)
        val mid = GpxEngine.generateWaypoints(analysis,"HIK",1).first { it.name == "HIK_KM001" }
        check(mid.point.elevation == null)
    }
    println("ENGINE REGRESSION RESULT: $passed/$passed passed")
}

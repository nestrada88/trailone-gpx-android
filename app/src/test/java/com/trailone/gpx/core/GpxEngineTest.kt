package com.trailone.gpx.core

import org.junit.Assert.*
import org.junit.Test

class GpxEngineTest {
    private fun track(body: String, meta: String = "") = """<?xml version="1.0" encoding="UTF-8"?>
        <gpx xmlns="http://www.topografix.com/GPX/1/1" creator="test-source" version="1.1">
        $meta $body</gpx>""".trimIndent().toByteArray()
    private fun pt(lat: Double, lon: Double, ele: String = "100") =
        "<trkpt lat=\"$lat\" lon=\"$lon\"><ele>$ele</ele></trkpt>"

    @Test fun `produces semantic and cumulative markers without changing original geometry`() {
        val data = track("<trk><trkseg>${pt(0.0, 0.0, "100")}${pt(0.0, 0.01, "110")}${pt(0.0, 0.02, "120")}</trkseg></trk>")
        val analysis = GpxEngine.analyze(data)
        assertEquals(3, analysis.statistics.points)
        assertEquals(20.0, analysis.statistics.ascentMeters, 1e-9)
        assertEquals(0.0, analysis.statistics.descentMeters, 1e-9)
        val wpts = GpxEngine.generateWaypoints(analysis, "HIK", 1)
        assertEquals(setOf("HIK_TH", "HIK_LE", "HIK_HE", "HIK_MP", "HIK_KM001", "HIK_KM002", "HIK_TE"), wpts.map { it.name }.toSet())
        assertEquals("HIK_TH", wpts.first().name)
        assertEquals("HIK_TE", wpts.last().name)
        val result = GpxEngine.enrich(analysis, wpts)
        val xml = String(result.output)
        assertTrue(xml.contains("HIK_KM001"))
        assertEquals(3, Regex("<trkpt\\s").findAll(xml).count())
        assertEquals(7, Regex("<wpt\\s").findAll(xml).count())
    }

    @Test fun `does not bridge track segment gaps`() {
        val data = track("""<trk><trkseg>${pt(0.0, 0.0)}${pt(0.0, 0.001)}</trkseg>
            <trkseg>${pt(40.0, 40.0)}${pt(40.0, 40.001)}</trkseg></trk>""")
        val a = GpxEngine.analyze(data)
        assertTrue(a.statistics.distanceMeters < 300.0)
        assertEquals(0, GpxEngine.generateWaypoints(a, "HIK", 1).count { it.name.contains("_KM") })
    }

    @Test fun `preserves source attribution and existing waypoints by default`() {
        val original = track("""<wpt lat="0.0" lon="0.0"><name>OLD</name></wpt>
            <trk><trkseg>${pt(0.0, 0.0)}${pt(0.0, 0.001)}</trkseg></trk>""",
            "<metadata><name>Original survey</name><author><name>Original creator</name></author></metadata>")
        val a = GpxEngine.analyze(original)
        val result = GpxEngine.enrich(a, GpxEngine.generateWaypoints(a, "HIK", 1))
        val xml = String(result.output)
        assertTrue(xml.contains("Original creator"))
        assertTrue(xml.contains("Original survey"))
        assertTrue(xml.contains("<name>OLD</name>"))
    }

    @Test fun `geodesic equatorial degree matches WGS84`() {
        val start = GpxEngine.Point(0.0, 0.0, 0.0)
        val end = GpxEngine.Point(0.0, 1.0, 0.0)
        assertEquals(111_319.490793, GpxEngine.distance3d(start, end, GpxEngine.DistanceMethod.GEODESIC), 0.002)
    }

    @Test fun `rejects invalid prefix and interval`() {
        assertThrows(IllegalArgumentException::class.java) { GpxEngine.validateOptions("FOUR", 1) }
        assertThrows(IllegalArgumentException::class.java) { GpxEngine.validateOptions("HIK", 0) }
    }

    @Test fun `rejects doctype to prevent XXE`() {
        val source = """<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="x">
            <trk><trkseg>${pt(0.0, 0.0)}${pt(0.0, 0.001)}</trkseg></trk></gpx>"""
        assertThrows(Exception::class.java) { GpxEngine.analyze(source.toByteArray()) }
    }
}

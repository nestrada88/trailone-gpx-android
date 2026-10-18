package com.trailone.gpx.ui

import com.trailone.gpx.core.GpxEngine
import org.junit.Assert.*
import org.junit.Test

class GuidedSessionTest {
    private val xml = """<?xml version="1.0"?><gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
        <trk><trkseg><trkpt lat="12.0" lon="-86.0"><ele>100</ele></trkpt>
        <trkpt lat="12.02" lon="-86.02"><ele>150</ele></trkpt></trkseg></trk></gpx>""".toByteArray()

    private fun imported(): GuidedSession = GuidedSession().apply {
        importValidated("content://documents/a", "a.gpx", xml, GpxEngine.analyze(xml).statistics)
    }

    private fun generated(s: GuidedSession) {
        if (s.stage == GuidedSession.Stage.IMPORT) assertTrue(s.navigate(GuidedSession.Stage.CONFIGURE))
        assertTrue(s.navigate(GuidedSession.Stage.GENERATE))
        val id = s.beginGeneration()
        val source = s.source!!
        val analysis = GpxEngine.analyze(source.bytes)
        val waypoints = GpxEngine.generateWaypoints(analysis, s.options.prefix, s.options.intervalKm)
        assertTrue(s.finishGeneration(id, source.sha256, s.optionsRevision, GpxEngine.enrich(analysis, waypoints)))
    }

    @Test fun requireImport() {
        val session = GuidedSession()
        assertFalse(session.navigate(GuidedSession.Stage.CONFIGURE))
        assertFalse(session.navigate(GuidedSession.Stage.EXPORT))
    }

    @Test fun cannotSkipStages() {
        val s = imported()
        assertFalse(s.navigate(GuidedSession.Stage.GENERATE))
        assertTrue(s.navigate(GuidedSession.Stage.CONFIGURE))
        assertFalse(s.navigate(GuidedSession.Stage.REVIEW))
    }

    @Test fun optionsMustValidate() {
        val s = imported()
        assertTrue(s.navigate(GuidedSession.Stage.CONFIGURE))
        s.updateOptions(s.options.copy(prefix = "HI"))
        assertFalse(s.navigate(GuidedSession.Stage.GENERATE))
        s.updateOptions(s.options.copy(prefix = "HIK", onlineGeocoding = true, endpoint = "http://example.com"))
        assertFalse(s.navigate(GuidedSession.Stage.GENERATE))
        s.updateOptions(s.options.copy(endpoint = "https://example.com/reverse"))
        assertTrue(s.navigate(GuidedSession.Stage.GENERATE))
    }

    @Test fun generationToReviewedExport() {
        val s = imported()
        generated(s)
        assertTrue(s.isCurrentOutput())
        assertFalse(s.navigate(GuidedSession.Stage.EXPORT))
        assertTrue(s.navigate(GuidedSession.Stage.REVIEW))
        assertTrue(s.acceptReview())
        assertTrue(s.exportSucceeded("content://documents/enriched"))
        assertNotNull(s.exportedUri)
        assertTrue(s.output!!.result.output.toString(Charsets.UTF_8).contains("HIK_TH"))
    }

    @Test fun editsInvalidateOutputAndExport() {
        val s = imported()
        generated(s)
        assertTrue(s.navigate(GuidedSession.Stage.REVIEW))
        assertTrue(s.acceptReview())
        s.updateOptions(s.options.copy(intervalKm = 2))
        assertFalse(s.isCurrentOutput())
        assertNull(s.output)
        assertFalse(s.reviewed)
        assertNull(s.exportedUri)
        assertFalse(s.navigate(GuidedSession.Stage.EXPORT))
    }

    @Test fun cancelRejectsStaleCompletion() {
        val s = imported()
        assertTrue(s.navigate(GuidedSession.Stage.CONFIGURE))
        assertTrue(s.navigate(GuidedSession.Stage.GENERATE))
        val id = s.beginGeneration()
        val source = s.source!!
        val version = s.optionsRevision
        s.cancelGeneration()
        val analysis = GpxEngine.analyze(source.bytes)
        val waypoints = GpxEngine.generateWaypoints(analysis, "HIK", 1)
        assertFalse(s.finishGeneration(id, source.sha256, version, GpxEngine.enrich(analysis, waypoints)))
        assertNull(s.output)
    }

    @Test fun invalidInputRejected() {
        val s = GuidedSession()
        assertThrows(IllegalArgumentException::class.java) {
            s.importValidated("content://x", "not-gpx.txt", xml, GpxEngine.analyze(xml).statistics)
        }
    }

    @Test fun newActivityResetsSource() {
        val s = imported()
        generated(s)
        s.newActivity()
        assertNull(s.source)
        assertNull(s.output)
        assertEquals(GuidedSession.Stage.IMPORT, s.stage)
    }
}

import com.trailone.gpx.core.GpxEngine
import com.trailone.gpx.ui.GuidedSession

private val input = """<?xml version="1.0" encoding="UTF-8"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="original">
<trk><name>Test activity</name><trkseg>
<trkpt lat="12.000" lon="-86.000"><ele>100</ele></trkpt>
<trkpt lat="12.010" lon="-86.010"><ele>150</ele></trkpt>
<trkpt lat="12.020" lon="-86.020"><ele>130</ele></trkpt>
</trkseg></trk></gpx>""".toByteArray()
private fun fixture() = GuidedSession().apply {
    importValidated("content://documents/activity", "test.gpx", input, GpxEngine.analyze(input).statistics)
}
private fun generate(session: GuidedSession): Long {
    if (session.stage == GuidedSession.Stage.IMPORT) check(session.navigate(GuidedSession.Stage.CONFIGURE))
    check(session.navigate(GuidedSession.Stage.GENERATE))
    val id = session.beginGeneration()
    val src = session.source!!
    val analysis = GpxEngine.analyze(src.bytes)
    val points = GpxEngine.generateWaypoints(analysis, session.options.prefix, session.options.intervalKm)
    check(session.finishGeneration(id, src.sha256, session.optionsRevision, GpxEngine.enrich(analysis, points)))
    return id
}

fun main() {
    var passed = 0
    fun test(label: String, run: () -> Unit) { run(); println("PASS $label"); passed++ }
    test("cannot advance without GPX") {
        val s = GuidedSession()
        check(!s.navigate(GuidedSession.Stage.CONFIGURE))
        check(!s.navigate(GuidedSession.Stage.EXPORT))
    }
    test("import permits configure and blocks export") {
        val s = fixture()
        check(s.source!!.sha256.length == 64)
        check(!s.navigate(GuidedSession.Stage.GENERATE)) // no skipping Configure
        check(s.navigate(GuidedSession.Stage.CONFIGURE))
        check(!s.navigate(GuidedSession.Stage.REVIEW))
    }
    test("invalid source name rejected") {
        check(runCatching { GuidedSession().importValidated("content://x", "not-a-gpx.xml", input, GpxEngine.analyze(input).statistics) }.isFailure)
    }
    test("configuration validation and HTTPS privacy") {
        val s = fixture()
        check(s.navigate(GuidedSession.Stage.CONFIGURE))
        s.updateOptions(s.options.copy(prefix = "AB"))
        check(!s.navigate(GuidedSession.Stage.GENERATE))
        s.updateOptions(s.options.copy(prefix = "ABC", onlineGeocoding = true, endpoint = "http://example.org/reverse"))
        check(!s.navigate(GuidedSession.Stage.GENERATE))
        s.updateOptions(s.options.copy(endpoint = "https://example.org/reverse"))
        check(s.navigate(GuidedSession.Stage.GENERATE))
    }
    test("fresh generation produces actual GPX") {
        val s = fixture()
        generate(s)
        check(s.isCurrentOutput())
        val data = s.output!!.result.output.toString(Charsets.UTF_8)
        check("HIK_TH" in data && "HIK_TE" in data)
        check(s.navigate(GuidedSession.Stage.REVIEW))
        check(s.acceptReview())
        check(s.exportSucceeded("content://export/result"))
    }
    test("no export before review") {
        val s = fixture()
        generate(s)
        check(!s.navigate(GuidedSession.Stage.EXPORT))
        check(!s.exportSucceeded("content://export/result"))
    }
    test("changing options invalidates all generated output") {
        val s = fixture()
        generate(s)
        check(s.navigate(GuidedSession.Stage.REVIEW))
        check(s.acceptReview())
        s.updateOptions(s.options.copy(intervalKm = 2))
        check(!s.isCurrentOutput() && !s.reviewed && s.exportedUri == null)
        check(!s.navigate(GuidedSession.Stage.EXPORT))
    }
    test("same options preserve output") {
        val s = fixture()
        generate(s)
        s.updateOptions(s.options.copy())
        check(s.isCurrentOutput())
    }
    test("source replacement invalidates output") {
        val s = fixture()
        generate(s)
        s.importValidated("content://documents/new", "new.gpx", input, GpxEngine.analyze(input).statistics)
        check(!s.isCurrentOutput() && s.stage == GuidedSession.Stage.IMPORT)
    }
    test("cancel rejects late worker result") {
        val s = fixture()
        check(s.navigate(GuidedSession.Stage.CONFIGURE))
        check(s.navigate(GuidedSession.Stage.GENERATE))
        val id = s.beginGeneration()
        val oldVersion = s.optionsRevision
        val oldSource = s.source!!
        s.cancelGeneration()
        val analysis = GpxEngine.analyze(oldSource.bytes)
        val points = GpxEngine.generateWaypoints(analysis, "HIK", 1)
        check(!s.finishGeneration(id, oldSource.sha256, oldVersion, GpxEngine.enrich(analysis, points)))
    }
    test("failed job may retry") {
        val s = fixture()
        check(s.navigate(GuidedSession.Stage.CONFIGURE))
        check(s.navigate(GuidedSession.Stage.GENERATE))
        val id = s.beginGeneration()
        check(s.generationFailed(id))
        check(!s.generationFailed(id))
        generate(s)
        check(s.isCurrentOutput())
    }
    test("new activity clears everything") {
        val s = fixture()
        generate(s)
        s.newActivity()
        check(s.source == null && s.output == null && s.stage == GuidedSession.Stage.IMPORT)
    }
    println("Guided workflow smoke: $passed/$passed passed")
}

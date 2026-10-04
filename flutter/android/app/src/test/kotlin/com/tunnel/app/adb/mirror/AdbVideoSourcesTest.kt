package com.tunnel.app.adb.mirror

import org.junit.Assert.*
import org.junit.Test

class AdbVideoSourcesTest {
    @Test fun closingOverlaysInEitherOrderRestoresLive() {
        for ((first, second) in listOf("ignore" to "hierarchy", "hierarchy" to "ignore")) {
            var sources = AdbVideoSources().apply("live_on")
            assertEquals(0, sources.mode)
            sources = sources.apply("${first}_on").apply("${second}_on")
            assertEquals(4, sources.mode)
            assertTrue(sources.live)
            sources = sources.apply("${first}_off")
            assertEquals(if (second == "ignore") 1 else 2, sources.mode)
            sources = sources.apply("${second}_off")
            assertEquals(AdbVideoSources(live = true), sources)
            assertEquals(0, sources.mode)
        }
    }

    @Test fun stoppingLiveDoesNotStopOverlaysOrResurrectLiveOnRecovery() {
        var sources = AdbVideoSources(live = true, snapshot = true, hierarchy = true)
        sources = sources.apply("live_off")
        assertEquals(4, sources.mode)
        assertEquals(sources, sources.apply("resume"))
        sources = sources.apply("ignore_off")
        assertEquals(2, sources.mode)
        sources = sources.apply("hierarchy_off")
        assertEquals(3, sources.mode)
        assertEquals(AdbVideoSources(), sources.apply("resume"))
    }

    @Test fun liveRestartAndDuplicateButtonsPreserveIndependentChoices() {
        for (sources in listOf(AdbVideoSources(), AdbVideoSources(snapshot = true),
                AdbVideoSources(hierarchy = true), AdbVideoSources(snapshot = true, hierarchy = true))) {
            assertEquals(sources, sources.apply("resume"))
            val live = sources.apply("live_on").apply("live_on")
            assertEquals(sources.snapshot, live.snapshot)
            assertEquals(sources.hierarchy, live.hierarchy)
            assertEquals(sources, live.apply("live_off").apply("live_off"))
            assertEquals(sources.apply("ignore_on"), sources.apply("ignore_on").apply("ignore_on"))
            assertEquals(sources.apply("hierarchy_on"), sources.apply("hierarchy_on").apply("hierarchy_on"))
        }
    }
}

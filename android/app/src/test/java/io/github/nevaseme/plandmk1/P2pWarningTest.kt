package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.eew.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Synthetic reports: no recorded user or device data. */
class P2pWarningTest {
    private fun report(id: String = "message-1", serial: String = "1", area: String = "京都府南部", test: Boolean = false, cancelled: Boolean = false) = """
        {"id":"$id","code":556,"time":"2026/10/25 12:00:01.000","test":$test,
         "issue":{"eventId":"20261025120000","serial":"$serial","time":"2026/10/25 12:00:00"},
         "cancelled":$cancelled,"areas":[{"pref":"京都府","name":"$area","scaleFrom":45.0,"scaleTo":55.0,"kindCode":"19","arrivalTime":null}],
         "earthquake":{"originTime":"2026/10/25 11:59:55","arrivalTime":"2026/10/25 11:59:57","hypocenter":{"name":"試験震源"}}}
    """.trimIndent()

    @Test fun parsesRegionalRangeWithoutInventingAnExactIntensity() {
        val warning = requireNotNull(P2pWarning.parse(report()))
        assertEquals("20261025120000", warning.eventId)
        assertEquals(1, warning.serial)
        val area = warning.warningFor("京都府南部")!!
        assertEquals(45, area.scaleFrom)
        assertEquals(55, area.scaleTo)
        assertNull(warning.warningFor("京都府北部"))
    }

    @Test fun rejectsDetectionAndMalformedReportsInsteadOfStartingAnIncident() {
        assertNull(P2pWarning.parse("""{"id":"detection","code":554,"type":"Full"}"""))
        assertNull(P2pWarning.parse(report().replace("\"cancelled\":false,", "")))
        assertNull(P2pWarning.parse(report(serial = "not-a-number")))
        assertNull(P2pWarning.parse(report().replace("2026/10/25 12:00:00", "2026/02/30 12:00:00")))
        assertNull(P2pWarning.parse("not json"))
    }

    @Test fun cancellationHasNoEarthquakeOrAreas() {
        val raw = """{"id":"c","code":556,"time":"2026/10/25 12:00:02.000","issue":{"eventId":"20261025120000","serial":"2","time":"2026/10/25 12:00:02"},"cancelled":true}"""
        val warning = requireNotNull(P2pWarning.parse(raw))
        assertTrue(warning.cancelled)
        assertTrue(warning.areas.isEmpty())
    }

    @Test fun doesNotTreatTestUnknownKindOrOtherRegionAsAWarningForKyoto() {
        assertNull(P2pWarning.parse(report(test = true))!!.warningFor("京都府南部"))
        assertNull(P2pWarning.parse(report(area = "東京都２３区"))!!.warningFor("京都府南部"))
        assertNull(P2pWarning.parse(report().replace("\"19\"", "\"00\""))!!.warningFor("京都府南部"))
    }

    @Test fun unknownAndOpenEndedRangesRemainUnknownAndOpenEnded() {
        val warning = P2pWarning.parse(report().replace("45.0", "-1").replace("55.0", "99"))!!
        assertEquals(-1, warning.areas.single().scaleFrom)
        assertEquals(99, warning.areas.single().scaleTo)
    }

    @Test fun savedHistorySurvivesReopeningAndReplaysDoNotDuplicateOrUndoCancellation() {
        val directory = Files.createTempDirectory("pland-eew").toFile()
        try {
            val store = EewReceiptStore(directory)
            assertTrue(store.save(P2pWarning.parse(report())!!, 100))
            assertFalse(store.save(P2pWarning.parse(report())!!, 200))
            assertTrue(store.save(P2pWarning.parse(report("message-cancel", "3", cancelled = true))!!, 300))
            assertTrue(store.save(P2pWarning.parse(report("message-late", "2"))!!, 400))
            val reopened = EewReceiptStore(directory)
            assertEquals(3, reopened.receipts().size)
            assertTrue(reopened.latest("20261025120000")!!.warning.cancelled)
            assertEquals(100L, reopened.receipts().first { it.warning.messageId == "message-1" }.receivedAt)
        } finally { directory.deleteRecursively() }
    }

    @Test fun writesTestMessagesSeparatelyFromRealEarthquakesEvenIfIdsMatch() {
        val directory = Files.createTempDirectory("pland-eew-test").toFile()
        try {
            val store = EewReceiptStore(directory)
            store.save(P2pWarning.parse(report(test = true))!!, 100)
            store.save(P2pWarning.parse(report())!!, 200)
            assertEquals(2, store.receipts().size)
            assertFalse(store.latest("20261025120000")!!.warning.test)
        } finally { directory.deleteRecursively() }
    }
}

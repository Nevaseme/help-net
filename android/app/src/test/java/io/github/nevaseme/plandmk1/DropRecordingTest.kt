package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.drops.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DropRecordingTest {
    @get:Rule val folder = TemporaryFolder()
    private val info = DropSensorInfo("drop_depth", "vivo", 1, 66561, "android.sensor.drop_depth", true, "V2417A", "test")
    private class Source : DropSource {
        lateinit var emit: (DropReading) -> Unit
        var opens = 0
        var closes = 0
        override fun open(receive: (DropReading) -> Unit): DropSubscription {
            opens++; emit = receive
            return DropSubscription { closes++ }
        }
    }
    private fun reading(at: Long = 110) = DropReading(info, at, 120, 1_000, false, 3, listOf(2.29, 0.0))

    @Test fun savesOnlyFreshUniqueEventsAndReadsThemAfterReopeningStore() {
        val directory = folder.newFolder()
        val store = DropEventStore(directory) { 1_000 }
        val recorder = DropRecording({ 100 }, store::save)
        val source = Source()
        recorder.start(1, "group", source)
        source.emit(reading(99)) // Cached notification from before this recording.
        source.emit(reading())
        source.emit(reading()) // Repeated callback must not duplicate a record.
        source.emit(reading(109))
        source.emit(reading(121)) // Impossible future timestamp.
        val saved = DropEventStore(directory) { 1_000 }.records().single()
        assertEquals("group", saved.groupId)
        assertEquals(110L, saved.reading.eventElapsedNanos)
        assertEquals(listOf(2.29, 0.0), saved.reading.values)
        assertFalse(saved.reading.screenInteractive)
        assertEquals(1L, recorder.state.value.saved)
        assertEquals(4L, recorder.state.value.ignored)
    }

    @Test fun stopAndNewSessionRejectLateCallbacksWithoutClosingNewSubscription() {
        val saved = mutableListOf<DropRecord>()
        var now = 100L
        val recorder = DropRecording({ now }, { saved.add(it); Unit })
        val first = Source(); val second = Source()
        recorder.start(1, "old-group", first)
        recorder.start(1, "old-group", first)
        assertEquals(1, first.opens)
        recorder.stop(1)
        first.emit(reading())
        now = 200
        recorder.start(2, "new-group", second)
        recorder.stop(1)
        first.emit(reading(210).copy(receivedElapsedNanos = 220))
        second.emit(reading(210).copy(receivedElapsedNanos = 220))
        assertEquals("new-group", saved.single().groupId)
        assertEquals(1, first.closes)
        assertEquals(0, second.closes)
        recorder.stop(2)
        second.emit(reading(211).copy(receivedElapsedNanos = 220))
        assertEquals(1, saved.size)
        assertEquals(1, second.closes)
        assertFalse(recorder.state.value.active)
    }

    @Test fun saveFailureIsReportedWithoutEscapingCallbackAndNextEventCanBeSaved() {
        var fail = true
        val saved = mutableListOf<DropRecord>()
        val recorder = DropRecording({ 100 }, { if (fail) throw IOException("full"); saved.add(it); Unit })
        val source = Source()
        recorder.start(1, "group", source)
        source.emit(reading())
        assertTrue(recorder.state.value.active)
        assertEquals("storage-error", recorder.state.value.status)
        assertEquals(1L, recorder.state.value.failed)
        fail = false
        source.emit(reading(111))
        assertEquals(111L, saved.single().reading.eventElapsedNanos)
        assertEquals("receiving", recorder.state.value.status)
    }

    @Test fun unsupportedOrRejectedSensorDoesNotThrow() {
        val recorder = DropRecording({ 100 }, {})
        recorder.start(1, "group", object : DropSource {
            override fun open(receive: (DropReading) -> Unit): DropSubscription? = null
        })
        assertFalse(recorder.state.value.active)
        assertEquals("unavailable", recorder.state.value.status)
        recorder.start(2, "group", object : DropSource {
            override fun open(receive: (DropReading) -> Unit): DropSubscription = throw SecurityException()
        })
        assertEquals("registration-error", recorder.state.value.status)
    }

    @Test fun fileStoreKeepsExistingEventAndCannotEscapeItsDirectory() {
        val store = DropEventStore(folder.newFolder()) { 1_000 }
        val record = DropRecord("12345678-1234-1234-1234-123456789abc", "group", reading())
        store.save(record); store.save(record)
        assertEquals(listOf(record), store.records())
        assertThrows(IllegalArgumentException::class.java) { store.save(record.copy(sessionId = "../escape")) }
        assertEquals(listOf(record), store.records())
    }

    @Test fun retentionRemovesOnlyRecordsAtLeastThirtyDaysOldAndPreservesOtherFiles() {
        var now = 1_000L
        val directory = folder.newFolder()
        val store = DropEventStore(directory) { now }
        val record = DropRecord("12345678-1234-1234-1234-123456789abc", "group", reading())
        store.save(record)
        val unrelated = java.io.File(directory, "notes.txt").apply { writeText("keep") }
        now = 2_592_000_999L
        assertEquals(1, store.records().size)
        now = 2_592_001_000L
        store.prune()
        assertTrue(store.records().isEmpty())
        assertTrue(directory.listFiles()!!.none { it.extension == "json" })
        assertEquals("keep", unrelated.readText())
    }

    @Test fun corruptOldFileDoesNotPreventSavingNewEvents() {
        val directory = folder.newFolder()
        val corrupt = java.io.File(directory, "damaged.json").apply { writeText("{") }
        val store = DropEventStore(directory) { 1_000 }
        val record = DropRecord("12345678-1234-1234-1234-123456789abc", "group", reading())
        store.save(record)
        assertEquals(listOf(record), store.records())
        assertEquals("{", corrupt.readText())
    }

    @Test fun unregisterFailureDoesNotEscapePositionStopOrAllowLateEvents() {
        val saved = mutableListOf<DropRecord>()
        val recorder = DropRecording({ 100 }, { saved.add(it); Unit })
        lateinit var emit: (DropReading) -> Unit
        recorder.start(1, "group", DropSource { receive ->
            emit = receive; DropSubscription { throw IllegalStateException("vendor failure") }
        })
        recorder.stop(1)
        emit(reading())
        assertTrue(saved.isEmpty())
        assertFalse(recorder.state.value.active)
        assertEquals("unregister-error", recorder.state.value.status)
    }

    @Test fun abandonedPendingWritesExpireWithoutDeletingFreshPendingWrites() {
        val directory = folder.newFolder()
        val old = java.io.File(directory, "pending-123.tmp").apply { writeText("private partial record"); setLastModified(1_000) }
        val fresh = java.io.File(directory, "pending-456.tmp").apply { writeText("fresh"); setLastModified(2_592_001_000) }
        DropEventStore(directory) { 2_592_001_000 }.prune()
        assertFalse(old.exists())
        assertEquals("fresh", fresh.readText())
    }
}

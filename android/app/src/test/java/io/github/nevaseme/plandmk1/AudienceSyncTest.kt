package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.RecordStore
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.core.Safety
import io.github.nevaseme.plandmk1.core.SyncEngine
import io.github.nevaseme.plandmk1.core.WireMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Production SyncEngine, with a deterministic queued transport and in-memory persistence.
 * Forty PWA profiles are preloaded at Android relays, not modeled as Nearby radio peers.
 * These are convergence/traffic regression checks, not physical radio or latency measurements.
 */
class AudienceSyncTest {
    private class Store : RecordStore {
        private val records = mutableMapOf<Pair<String, String>, Resident>()
        override suspend fun all(groupId: String) = records.values.filter { it.groupId == groupId }
        override suspend fun get(groupId: String, originId: String) = records[groupId to originId]
        override suspend fun merge(record: Resident): Boolean {
            record.validate()
            val key = record.groupId to record.originId
            if ((records[key]?.revision ?: 0) >= record.revision) return false
            records[key] = record
            return true
        }
    }

    private data class Packet(val from: Int, val to: Int, val bytes: ByteArray)

    private class Network {
        val stores = mutableMapOf<Int, Store>()
        val engines = mutableMapOf<Int, SyncEngine>()
        val links = mutableSetOf<Pair<Int, Int>>()
        val errors = mutableListOf<String>()
        private val queue = ArrayDeque<Packet>()
        val sentTypes = mutableMapOf<String, Int>()
        var packets = 0
            private set
        var maxQueue = 0
            private set
        var maxPacketBytes = 0
            private set
        var maxDrainPackets = 0
            private set

        fun edge(a: Int, b: Int) = minOf(a, b) to maxOf(a, b)

        fun add(index: Int, group: String = GROUP) {
            val store = stores.getOrPut(index) { Store() }
            engines[index] = SyncEngine(group, id(index), store, send = { peer, bytes ->
                val to = peer.toInt()
                assertTrue("Send must use an established simulated link", edge(index, to) in links)
                assertTrue("Wire payload exceeds production limit", bytes.size <= 24_000)
                maxPacketBytes = maxOf(maxPacketBytes, bytes.size)
                val type = Json.decodeFromString<WireMessage>(bytes.decodeToString()).type
                sentTypes[type] = (sentTypes[type] ?: 0) + 1
                enqueue(Packet(index, to, bytes))
            }, onError = { errors.add("node $index: $it") })
        }

        fun recreateDisconnectedEngine(index: Int) {
            assertTrue("Recreation requires a settled transport", queue.isEmpty())
            assertTrue("Recreation must not carry old links", links.none { it.first == index || it.second == index })
            val previous = engines.getValue(index)
            val persistedStore = stores.getValue(index)
            add(index)
            assertNotSame(previous, engines.getValue(index))
            assertSame(persistedStore, stores.getValue(index))
        }

        private fun enqueue(packet: Packet) {
            queue.addLast(packet)
            maxQueue = maxOf(maxQueue, queue.size)
            // A broad population-scaled storm bound: two queued frames per record/link/node.
            assertTrue("Unbounded transport backlog: ${queue.size}", queue.size <= QUEUE_BUDGET)
        }

        suspend fun connect(a: Int, b: Int) {
            assertTrue(links.add(edge(a, b)))
            assertDegreeBound()
            engines.getValue(a).connected(b.toString())
            engines.getValue(b).connected(a.toString())
        }

        fun disconnect(a: Int, b: Int) {
            assertTrue(links.remove(edge(a, b)))
            engines.getValue(a).disconnected(b.toString())
            engines.getValue(b).disconnected(a.toString())
        }

        suspend fun ring() {
            // Successor and second-successor edges give exactly four links per Android.
            repeat(ANDROID) { from ->
                for (distance in 1..2) {
                    val to = (from + distance) % ANDROID
                    if (edge(from, to) !in links) connect(from, to)
                }
            }
            assertEquals(ANDROID * LINKS / 2, links.size)
            repeat(ANDROID) { node -> assertEquals(LINKS, links.count { it.first == node || it.second == node }) }
        }

        fun assertDegreeBound() {
            engines.keys.forEach { node ->
                assertTrue("node $node exceeds four links", links.count { it.first == node || it.second == node } <= LINKS)
            }
        }

        suspend fun drain() {
            var delivered = 0
            while (queue.isNotEmpty()) {
                // Summary/request/record/ack plus duplicate in-flight paths have a finite budget.
                assertTrue("Sync did not quiesce within $PACKET_BUDGET frames", ++delivered <= PACKET_BUDGET)
                val packet = queue.removeFirst()
                packets++
                if (edge(packet.from, packet.to) in links) {
                    engines.getValue(packet.to).receive(packet.from.toString(), packet.bytes)
                }
            }
            maxDrainPackets = maxOf(maxDrainPackets, delivered)
            assertTrue("Production SyncEngine errors: $errors", errors.isEmpty())
            assertDegreeBound()
        }

        suspend fun change(node: Int, record: Resident) {
            assertTrue(stores.getValue(node).merge(record))
            engines.getValue(node).changed()
        }

        fun replay(from: Int, to: Int, record: Resident, envelopeGroup: String = record.groupId) {
            assertTrue(edge(from, to) in links)
            enqueue(Packet(from, to, Json.encodeToString(WireMessage(groupId = envelopeGroup, type = "RECORD", record = record)).encodeToByteArray()))
        }

        suspend fun assertRecords(expected: Map<String, Resident>, nodes: Iterable<Int> = 0 until ANDROID) {
            nodes.forEach { node ->
                assertEquals("Record contents diverged at Android $node", expected, stores.getValue(node).all(GROUP).associateBy { it.originId })
            }
            assertTrue(maxPacketBytes in 1..24_000)
            assertTrue(maxQueue in 1..QUEUE_BUDGET)
            assertTrue(maxDrainPackets in 1..PACKET_BUDGET)
        }
    }

    private suspend fun seeded(): Pair<Network, MutableMap<String, Resident>> {
        val network = Network()
        repeat(ANDROID) { network.add(it) }
        val expected = mutableMapOf<String, Resident>()
        repeat(RECORDS) { index ->
            val record = resident(index)
            expected[record.originId] = record
            // Each Android owns its record and relays two different preloaded PWA records.
            assertTrue(network.stores.getValue(index % ANDROID).merge(record))
        }
        network.ring()
        network.drain()
        network.assertRecords(expected)
        return network to expected
    }

    @Test fun twentyAndroidRelaysConvergeSixtyProfilesAndSimultaneousOwnEdits() = runTest {
        val (network, expected) = seeded()
        val before = network.packets
        // Queue every local edit before delivering anything: none sees another's edit first.
        repeat(ANDROID) { index ->
            val edit = resident(index, revision = 2, safety = if (index % 2 == 0) Safety.SOS else Safety.SAFE)
            expected[edit.originId] = edit
            network.change(index, edit)
        }
        network.drain()
        network.assertRecords(expected)
        assertTrue(network.packets - before in 1..PACKET_BUDGET)
        val recordFrames = network.sentTypes["RECORD"] ?: 0
        val settledPackets = network.packets
        network.disconnect(0, 1)
        network.connect(0, 1)
        network.drain()
        assertEquals("Reconnect of equal inventories only exchanges summaries", 2, network.packets - settledPackets)
        assertEquals("Equal inventories must not resend records", recordFrames, network.sentTypes["RECORD"] ?: 0)
        network.assertRecords(expected)
        println("AudienceSync 20 Android / 40 PWA: packets=${network.packets}, maxQueue=${network.maxQueue}, maxFrame=${network.maxPacketBytes}")
    }

    @Test fun partitionedOwnEditsAndDifferentPwaRevisionsConvergeAfterReconnect() = runTest {
        val (network, original) = seeded()
        val cut = network.links.filter { (a, b) -> (a < ANDROID / 2) != (b < ANDROID / 2) }
        assertTrue(cut.isNotEmpty())
        cut.forEach { (a, b) -> network.disconnect(a, b) }
        val left = original.toMutableMap()
        val right = original.toMutableMap()
        repeat(ANDROID) { index ->
            val edit = resident(index, revision = 2, safety = Safety.SOS)
            (if (index < ANDROID / 2) left else right)[edit.originId] = edit
            network.change(index, edit)
        }
        // The same PWA profile reaches separate Android relays at different revisions.
        val earlier = resident(ANDROID, revision = 2, safety = Safety.SOS).copy(updatedAt = 9999)
        val latest = resident(ANDROID, revision = 3, safety = Safety.SAFE).copy(updatedAt = 1)
        left[earlier.originId] = earlier
        right[latest.originId] = latest
        network.change(0, earlier)
        network.change(ANDROID / 2, latest)
        network.drain()
        network.assertRecords(left, 0 until ANDROID / 2)
        network.assertRecords(right, ANDROID / 2 until ANDROID)
        val expected = original.toMutableMap()
        repeat(ANDROID) { index -> expected[id(index)] = resident(index, revision = 2, safety = Safety.SOS) }
        expected[latest.originId] = latest
        cut.forEach { (a, b) -> network.connect(a, b) }
        network.drain()
        network.assertRecords(expected)
        println("AudienceSync partition/rejoin: packets=${network.packets}, maxQueue=${network.maxQueue}")
    }

    @Test fun duplicateAndOldFramesDoNotRollBackOrCreateAnotherPropagationWave() = runTest {
        val (network, expected) = seeded()
        val latest = resident(0, revision = 3, safety = Safety.SAFE)
        expected[latest.originId] = latest
        network.change(0, latest)
        network.drain()
        val summaries = network.sentTypes["SUMMARY"] ?: 0
        val requests = network.sentTypes["REQUEST"] ?: 0
        val acknowledgements = network.sentTypes["ACK"] ?: 0
        repeat(10) {
            network.replay(0, 1, resident(0, revision = 1, safety = Safety.SOS))
            network.replay(0, 1, latest)
        }
        network.drain()
        network.assertRecords(expected)
        assertEquals("Stale/duplicate records must not propagate", summaries, network.sentTypes["SUMMARY"] ?: 0)
        assertEquals(requests, network.sentTypes["REQUEST"] ?: 0)
        assertEquals("Duplicates still acknowledge durable receipt", 20, (network.sentTypes["ACK"] ?: 0) - acknowledgements)
    }

    @Test fun anotherGroupsFramesCannotMixIntoAudienceEvenAcrossAnAccidentalLink() = runTest {
        val (network, expected) = seeded()
        val stranger = ANDROID
        network.add(stranger, "other-group")
        val foreign = resident(999).copy(groupId = "other-group")
        assertTrue(network.stores.getValue(stranger).merge(foreign))
        // Free a slot before introducing the mismatched-group transport peer.
        network.disconnect(0, 2)
        network.connect(0, stranger)
        network.replay(stranger, 0, foreign)
        network.replay(stranger, 0, foreign, envelopeGroup = GROUP)
        network.replay(0, stranger, expected.getValue(id(0)))
        network.drain()
        network.assertRecords(expected)
        assertEquals(listOf(foreign), network.stores.getValue(stranger).all("other-group"))
        assertTrue(network.stores.getValue(stranger).all(GROUP).isEmpty())
        repeat(ANDROID) { assertTrue(network.stores.getValue(it).all("other-group").isEmpty()) }
    }

    @Test fun storedRecordsSurviveRelayEngineRecreationAndReachLaterPeersWithoutTheirAuthor() = runTest {
        val network = Network()
        val a = 0
        val b = 1
        val c = 2
        listOf(a, b, c).forEach { network.add(it) }
        fun assertOnlyLink(from: Int, to: Int) {
            assertEquals(setOf(network.edge(from, to)), network.links)
            assertFalse("A and C must never have a direct link", network.edge(a, c) in network.links)
        }

        val original = resident(a, revision = 1, safety = Safety.SOS)
        network.change(a, original)
        network.connect(a, b)
        assertOnlyLink(a, b)
        network.drain()
        assertEquals(original, network.stores.getValue(b).get(GROUP, original.originId))
        assertTrue(network.stores.getValue(c).all(GROUP).isEmpty())
        network.disconnect(a, b)

        // The fake persistence layer survives, while all SyncEngine peer/pending state is new.
        // This tests the engine/storage boundary, not SQLite durability or a real process kill.
        network.recreateDisconnectedEngine(b)
        network.connect(b, c)
        assertOnlyLink(b, c) // A is absent throughout this forwarding exchange.
        network.drain()
        assertEquals(original, network.stores.getValue(c).get(GROUP, original.originId))

        // Both disconnected components can edit without erasing the relayed original.
        val latest = original.copy(revision = 2, updatedAt = 1, safety = Safety.SAFE)
        val relayOwn = resident(b, revision = 1, safety = Safety.SAFE)
        network.change(a, latest)
        network.change(b, relayOwn)
        network.drain()
        assertEquals(original, network.stores.getValue(c).get(GROUP, original.originId))
        assertEquals(relayOwn, network.stores.getValue(c).get(GROUP, relayOwn.originId))
        assertNull(network.stores.getValue(a).get(GROUP, relayOwn.originId))

        // B visits A, stores its newer revision, then leaves before meeting C again.
        network.disconnect(b, c)
        network.connect(a, b)
        assertOnlyLink(a, b)
        network.drain()
        assertEquals(latest, network.stores.getValue(b).get(GROUP, latest.originId))
        assertEquals(relayOwn, network.stores.getValue(a).get(GROUP, relayOwn.originId))
        network.disconnect(a, b)
        network.recreateDisconnectedEngine(b)
        network.connect(b, c)
        assertOnlyLink(b, c)
        network.drain()
        val expected = mapOf(latest.originId to latest, relayOwn.originId to relayOwn)
        network.assertRecords(expected, listOf(a, b, c))

        // A delayed older payload cannot undo the newer copy after store-and-forward.
        network.replay(b, c, original)
        network.replay(b, c, latest)
        network.drain()
        network.assertRecords(expected, listOf(a, b, c))
        assertOnlyLink(b, c)
    }

    companion object {
        private const val GROUP = "audience-demo"
        private const val ANDROID = 20
        private const val PWA = 40
        private const val RECORDS = ANDROID + PWA
        private const val LINKS = 4
        private const val QUEUE_BUDGET = ANDROID * RECORDS * LINKS * 2
        private const val PACKET_BUDGET = ANDROID * RECORDS * LINKS * 8
        private fun id(index: Int) = "00000000-0000-0000-0000-${(index + 1).toString().padStart(12, '0')}"
        private fun resident(index: Int, revision: Long = 1, safety: Safety = Safety.UNREPORTED) = Resident(
            GROUP, id(index), revision, revision * 100,
            if (index < ANDROID) "架空Android $index" else "架空PWA ${index - ANDROID}",
            35.68, 139.76, "2階", "架空の部屋", safety,
        )
    }
}

package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.RecordStore
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.core.Safety
import io.github.nevaseme.plandmk1.nearby.NearbyPort
import io.github.nevaseme.plandmk1.nearby.NearbyState
import io.github.nevaseme.plandmk1.nearby.Peer
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Real SharingSession actors, including their production Channel(256), with fake radio and storage.
 * One virtual tick is a scheduling device, not a radio/disk latency estimate. The fake database
 * yields on reads and writes so incoming callbacks can overlap a busy session command.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudienceSharingSessionTest {
    private class Store(private val activity: () -> Unit) : RecordStore {
        val rows = mutableMapOf<String, Resident>()
        private suspend fun io() { activity(); delay(1) }
        override suspend fun all(groupId: String): List<Resident> {
            io()
            return rows.values.filter { it.groupId == groupId }
        }
        override suspend fun get(groupId: String, originId: String): Resident? {
            io()
            return rows[originId]?.takeIf { it.groupId == groupId }
        }
        override suspend fun merge(record: Resident): Boolean {
            io()
            record.validate()
            if ((rows[record.originId]?.revision ?: 0) >= record.revision) return false
            rows[record.originId] = record
            return true
        }
        fun local(record: Resident) {
            record.validate()
            check((rows[record.originId]?.revision ?: 0) < record.revision)
            rows[record.originId] = record
            activity()
        }
    }

    private data class Packet(val from: Int, val to: Int, val bytes: ByteArray)

    private class Network(private val scope: TestScope) {
        var activity = 0
        val stores = List(NODES) { Store { activity++ } }
        val ports = List(NODES) { Port(it) }
        val published = MutableList(NODES) { NearbyState() }
        val finished = IntArray(NODES)
        val sessions = List(NODES) { index ->
            SharingSession(GROUP, id(index), ports[index], stores[index], scope.backgroundScope,
                { published[index] = it }, { finished[index]++ }, automatic = true)
        }
        val links = mutableSetOf<Pair<Int, Int>>()
        private val packets = ArrayDeque<Packet>()
        var sent = 0
        var delivered = 0
        var maxTransportQueue = 0
        var maxCallbackBatch = 0
        var steps = 0

        inner class Port(private val index: Int) : NearbyPort {
            override val state = MutableStateFlow(NearbyState())
            override var onConnected: suspend (String) -> Unit = {}
            override var onDisconnected: (String) -> Unit = {}
            override var onBytes: (String, ByteArray) -> Unit = { _, _ -> }
            var starts = 0
            var stops = 0
            override fun start(name: String) { starts++; state.value = state.value.copy(active = true) }
            override fun stop() { stops++; state.value = NearbyState() }
            override fun setVisible(visible: Boolean) = Unit
            override fun connect(peer: Peer) = Unit
            override fun verify(id: String, accept: Boolean) = Unit
            override fun error(message: String) { state.value = state.value.copy(error = message) }
            override fun receipt() { state.value = state.value.copy(receiptCount = state.value.receiptCount + 1) }
            override suspend fun send(peer: String, bytes: ByteArray) {
                val to = peer.toInt()
                assertTrue(bytes.size <= 24_000)
                // Async send completion allows the receiver's actor and other peers to run.
                delay(1)
                assertTrue("Sending over a missing link", edge(index, to) in links)
                packets.addLast(Packet(index, to, bytes.copyOf()))
                sent++; activity++
                maxTransportQueue = maxOf(maxTransportQueue, packets.size)
                assertTrue("Unbounded fake-radio queue", packets.size <= NODES * RECORDS * LINKS)
                assertTrue("Unbounded protocol traffic", sent <= 120_000)
            }
        }

        fun edge(a: Int, b: Int) = minOf(a, b) to maxOf(a, b)
        suspend fun connect(a: Int, b: Int) {
            assertTrue(links.add(edge(a, b)))
            for ((from, to) in listOf(a to b, b to a)) {
                ports[from].state.value = ports[from].state.value.copy(connected = ports[from].state.value.connected + to.toString())
                assertTrue(ports[from].state.value.connected.size <= LINKS)
                ports[from].onConnected(to.toString())
            }
        }
        fun disconnect(a: Int, b: Int) {
            assertTrue(links.remove(edge(a, b)))
            for ((from, to) in listOf(a to b, b to a)) {
                ports[from].state.value = ports[from].state.value.copy(connected = ports[from].state.value.connected - to.toString())
                ports[from].onDisconnected(to.toString())
            }
        }
        fun assertActive() {
            repeat(NODES) { node ->
                assertEquals("Session $node ended: ${published[node].error}", 0, finished[node])
                assertTrue("Session $node inactive: ${published[node].error}", published[node].active)
                assertNull("Session $node transport error", published[node].error)
                assertEquals(0, ports[node].stops)
            }
        }
        fun assertRecords(expected: Map<String, Resident>, nodes: Iterable<Int> = 0 until NODES) {
            nodes.forEach { node -> assertEquals("Android $node records", expected, stores[node].rows) }
        }
        fun update(node: Int, record: Resident) { stores[node].local(record); sessions[node].changed() }

        fun settle() {
            var quiet = 0
            var phaseSteps = 0
            while (quiet < 16) {
                assertTrue("Session actors failed to settle", ++phaseSteps <= 30_000)
                steps++
                val previousActivity = activity
                scope.runCurrent()
                // At most one callback per directed radio link in a tick, preserving FIFO.
                // No synthetic 257-frame callback flood is injected to force queue overflow.
                val used = mutableSetOf<Pair<Int, Int>>()
                val callbacks = IntArray(NODES)
                repeat(packets.size) {
                    val packet = packets.removeFirst()
                    if (!used.add(packet.from to packet.to)) packets.addLast(packet)
                    else if (edge(packet.from, packet.to) in links) {
                        ports[packet.to].onBytes(packet.from.toString(), packet.bytes)
                        callbacks[packet.to]++; delivered++; activity++
                    }
                }
                maxCallbackBatch = maxOf(maxCallbackBatch, callbacks.maxOrNull() ?: 0)
                assertTrue(maxCallbackBatch <= LINKS)
                scope.advanceTimeBy(1)
                scope.runCurrent()
                assertActive()
                quiet = if (activity == previousActivity && packets.isEmpty()) quiet + 1 else 0
            }
        }
        fun stop() {
            sessions.forEach { it.stop() }
            scope.runCurrent()
            repeat(NODES) { assertEquals(1, finished[it]); assertEquals(1, ports[it].stops) }
        }
    }

    @Test fun boundedSessionQueuesKeepTwentyRelaysActiveAcrossSixtyProfilesEditsAndPartitionRepair() = runTest {
        val network = Network(this)
        try {
            val expected = (0 until RECORDS).associate { id(it) to record(it) }.toMutableMap()
            // Forty PWA profiles enter through persisted Android relays, not forty radio peers.
            repeat(RECORDS) { index -> network.stores[index % NODES].local(expected.getValue(id(index))) }
            network.sessions.forEachIndexed { index, session -> session.start("架空Android $index") }
            runCurrent()
            repeat(NODES) { from ->
                for (distance in 1..2) {
                    val to = (from + distance) % NODES
                    if (network.edge(from, to) !in network.links) network.connect(from, to)
                }
            }
            assertEquals(NODES * LINKS / 2, network.links.size)
            network.settle()
            network.assertRecords(expected)
            network.sessions.forEach { it.setForeground(false) }
            runCurrent()

            // All twenty edits are enqueued before the scheduler delivers any of them.
            repeat(NODES) { node ->
                val edit = record(node, 2, Safety.SOS)
                expected[edit.originId] = edit
                network.update(node, edit)
            }
            network.settle()
            network.assertRecords(expected)

            val cut = network.links.filter { (a, b) -> (a < NODES / 2) != (b < NODES / 2) }
            cut.forEach { (a, b) -> network.disconnect(a, b) }
            runCurrent()
            val left = expected.toMutableMap()
            val right = expected.toMutableMap()
            repeat(NODES) { node ->
                val edit = record(node, 3, Safety.SAFE)
                (if (node < NODES / 2) left else right)[edit.originId] = edit
                expected[edit.originId] = edit
                network.update(node, edit)
            }
            val earlierPwa = record(NODES, 2, Safety.SOS)
            val latestPwa = record(NODES, 3, Safety.SAFE).copy(updatedAt = 1)
            left[earlierPwa.originId] = earlierPwa
            right[latestPwa.originId] = latestPwa
            expected[latestPwa.originId] = latestPwa
            network.update(0, earlierPwa)
            network.update(NODES / 2, latestPwa)
            network.settle()
            network.assertRecords(left, 0 until NODES / 2)
            network.assertRecords(right, NODES / 2 until NODES)
            cut.forEach { (a, b) -> network.connect(a, b) }
            network.settle()
            network.assertRecords(expected)
            network.assertActive()
            assertEquals(network.sent, network.delivered)
            assertTrue(network.ports.all { it.starts == 1 })
            assertTrue(network.maxCallbackBatch in 1..LINKS)
            println("AudienceSharingSession: packets=${network.sent}, maxRadioQueue=${network.maxTransportQueue}, maxCallbacksPerNodeTick=${network.maxCallbackBatch}, schedulerSteps=${network.steps}; all 20 sessions active")
        } finally {
            // Stop cancels actor/collector/automatic retry jobs; backgroundScope is also owned by runTest.
            network.stop()
        }
    }

    companion object {
        private const val GROUP = "audience-session"
        private const val NODES = 20
        private const val RECORDS = 60
        private const val LINKS = 4
        private fun id(index: Int) = "00000000-0000-0000-0000-${(index + 1).toString().padStart(12, '0')}"
        private fun record(index: Int, revision: Long = 1, safety: Safety = Safety.UNREPORTED) = Resident(
            GROUP, id(index), revision, revision * 100,
            if (index < NODES) "架空Android $index" else "架空PWA ${index - NODES}",
            35.68, 139.76, "2階", "架空の部屋", safety,
        )
    }
}

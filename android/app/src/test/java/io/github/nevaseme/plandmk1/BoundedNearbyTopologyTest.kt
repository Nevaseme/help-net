package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.nearby.BoundedNearbyTopology
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Uses the production scheduler; the simulated boundary supplies discovery and connection callbacks. */
class BoundedNearbyTopologyTest {
    private fun device(i: Int) = "00000000-0000-0000-0000-${i.toString().padStart(12, '0')}"
    private fun endpoint(i: Int) = "endpoint-$i"
    private fun index(endpoint: String) = endpoint.substringAfterLast('-').toInt()

    private inner class Network(seed: Int) {
        val nodes = mutableMapOf<Int, BoundedNearbyTopology>()
        private val links = mutableMapOf<Pair<Int, Int>, Long>()
        private val random = Random(seed)
        var now = 0L
        var attempts = 0
        var reachable: (Int, Int) -> Boolean = { _, _ -> true }
        fun key(a: Int, b: Int) = minOf(a, b) to maxOf(a, b)
        fun add(ids: Collection<Int>) {
            ids.forEach { nodes[it] = BoundedNearbyTopology(device(it)) }
            discover()
        }
        fun discover() {
            nodes.keys.shuffled(random).forEach { a ->
                nodes.keys.shuffled(random).filter { b -> a != b && reachable(a, b) }.forEach { b ->
                    // Duplicate and shuffled callbacks must not create duplicate requests or restart settling.
                    repeat(3) { nodes.getValue(a).found(endpoint(b), device(b), now) }
                }
            }
        }
        fun remove(ids: Collection<Int>) {
            ids.forEach { gone ->
                nodes.filterKeys { it != gone }.values.forEach { it.lost(endpoint(gone), now) }
                links.keys.filter { it.first == gone || it.second == gone }.toList().forEach { edge ->
                    val other = if (edge.first == gone) edge.second else edge.first
                    nodes[other]?.failed(endpoint(gone), now)
                    links.remove(edge)
                }
                nodes.remove(gone)
            }
        }
        fun partition() {
            reachable = { a, b -> (a <= 50) == (b <= 50) }
            nodes.forEach { (a, node) -> nodes.keys.filter { b -> !reachable(a, b) }.forEach { b -> node.lost(endpoint(b), now) } }
            links.keys.filter { !reachable(it.first, it.second) }.toList().forEach { edge ->
                nodes.getValue(edge.first).failed(endpoint(edge.second), now)
                nodes.getValue(edge.second).failed(endpoint(edge.first), now)
                links.remove(edge)
            }
        }
        fun step() {
            now += BoundedNearbyTopology.POLL_INTERVAL_MS
            links.filterValues { it <= now && it != Long.MAX_VALUE }.keys.toList().forEach { (a, b) ->
                assertTrue(nodes.getValue(a).established(endpoint(b)))
                assertTrue(nodes.getValue(b).established(endpoint(a)))
                links[key(a, b)] = Long.MAX_VALUE
                assertCaps()
            }
            nodes.keys.shuffled(random).forEach { a ->
                val node = nodes.getValue(a)
                val plan = node.poll(now)
                plan.disconnect.forEach { remote ->
                    val b = index(remote)
                    if (links.remove(key(a, b)) != null) nodes[b]?.failed(endpoint(a), now)
                }
                plan.connect?.let { remote ->
                    val b = index(remote)
                    attempts++
                    assertTrue("Duplicate connection attempt", key(a, b) !in links)
                    if (reachable(a, b) && nodes[b]?.accept(endpoint(a), device(a), now) == true) {
                        links[key(a, b)] = now + 500
                    } else node.failed(remote, now)
                }
                assertCaps()
            }
        }
        fun run(seconds: Int = 120) = repeat(seconds * 1_000 / BoundedNearbyTopology.POLL_INTERVAL_MS.toInt()) { step() }
        fun assertCaps() {
            nodes.values.forEach { node ->
                assertTrue(node.connectedEndpoints.size + node.pendingEndpoints.size <= BoundedNearbyTopology.MAX_LINKS)
                assertTrue(node.outgoingEndpoints.size <= 1)
            }
        }
        fun assertConverged(groups: List<Set<Int>> = listOf(nodes.keys.toSet())) {
            nodes.forEach { (_, node) ->
                assertEquals(node.desiredEndpoints(), node.connectedEndpoints)
                assertTrue(node.pendingEndpoints.isEmpty())
            }
            groups.forEach { group ->
                val reached = mutableSetOf(group.first())
                var previous: Int
                do {
                    previous = reached.size
                    reached.toList().forEach { a -> reached.addAll(nodes.getValue(a).connectedEndpoints.map(::index).filter { it in group }) }
                } while (reached.size != previous)
                assertEquals(group, reached)
            }
        }
    }

    @Test fun twentyDeviceTargetAndHundredNodeStressConvergeAcrossDiscoveryOrders() {
        listOf(20, 100).forEach { population -> repeat(5) { seed ->
            val net = Network(seed)
            net.add((1..population).toList())
            net.run(20)
            net.assertConverged()
            assertEquals(population * 2, net.attempts)
            assertEquals(population * 4, net.nodes.values.sumOf { it.connectedEndpoints.size })
            val attempts = net.attempts
            net.discover(); net.run(30)
            assertEquals("Repeated discovery must not restart connections", attempts, net.attempts)
        } }
    }

    @Test fun lateArrivalsDepartureAndReturnConvergeWithoutFullMesh() {
        val net = Network(18)
        net.add((1..70).toList()); net.run(20); net.assertConverged()
        // Interleave arrivals and real scheduling, instead of computing an ideal graph once.
        (71..100).shuffled(Random(2)).forEach { net.add(listOf(it)); net.run(2) }
        net.run(); net.assertConverged()
        val departed = (1..100).filter { it % 7 == 0 }
        net.remove(departed); net.run(); net.assertConverged()
        net.add(departed); net.run(); net.assertConverged()
        assertTrue("Churn should stay far below 4,950 full-mesh attempts: ${net.attempts}", net.attempts < 1_200)
    }

    @Test fun twoPartitionsReconnectAfterDiscoveryReturns() {
        val net = Network(45)
        net.add((1..100).toList()); net.run(20); net.assertConverged()
        net.partition(); net.run()
        net.assertConverged(listOf((1..50).toSet(), (51..100).toSet()))
        net.reachable = { _, _ -> true }; net.discover(); net.run()
        net.assertConverged()
        assertTrue("Partition repair must not dial every pair: ${net.attempts}", net.attempts < 300)
    }

    @Test fun smallGroupsAndEndpointAliasesDoNotWasteSlots() {
        (1..5).forEach { size ->
            val net = Network(size)
            net.add((1..size).toList()); net.run(20); net.assertConverged()
            assertEquals(size * (size - 1) / 2, net.attempts)
        }
        val node = BoundedNearbyTopology(device(1))
        node.found("a", device(2), 0); node.found("b", device(2), 0)
        assertEquals(setOf("a"), node.desiredEndpoints())
        assertEquals("a", node.poll(1_000).connect)
        assertFalse(node.accept("b", device(2), 1_000))
        assertEquals(setOf("a"), node.pendingEndpoints)
    }

    @Test fun failedAndSilentRequestsArePacedAndBoundedUntilRediscovery() {
        val node = BoundedNearbyTopology(device(1))
        node.found("peer", device(2), 0)
        val dialTimes = mutableListOf<Long>()
        for (time in 0L..400_000L step BoundedNearbyTopology.POLL_INTERVAL_MS) {
            val plan = node.poll(time)
            if (plan.connect != null) {
                dialTimes.add(time)
                // First request never calls back, so production timeout must free its slot.
                if (dialTimes.size > 1) node.failed("peer", time)
            }
            assertTrue(node.pendingEndpoints.size <= 1)
        }
        assertTrue("Repeated failures must not create a connection storm", dialTimes.size <= 10)
        assertTrue("Radio recovery must remain possible after five failures", dialTimes.size > 5)
        assertTrue(dialTimes.zipWithNext().all { (a, b) -> b - a >= BoundedNearbyTopology.DIAL_INTERVAL_MS })
        node.lost("peer", 401_000); node.found("peer", device(2), 402_000)
        assertEquals("peer", node.poll(403_000).connect)
    }

    @Test fun incomingRequestsCannotBypassDesiredNeighborsOrFourSlotLimit() {
        val node = BoundedNearbyTopology(device(50))
        (1..100).filter { it != 50 }.forEach { node.found(endpoint(it), device(it), 0) }
        assertFalse(node.accept(endpoint(1), device(1), 1_000))
        assertTrue(node.accept(endpoint(48), device(48), 1_000))
        assertTrue(node.accept(endpoint(49), device(49), 1_000))
        val first = node.poll(1_000).connect!!
        assertEquals(endpoint(51), first)
        assertNull(node.poll(3_000).connect) // one pending outgoing even with a free slot
        assertTrue(node.established(first))
        assertEquals(endpoint(52), node.poll(3_000).connect)
        assertEquals(4, node.connectedEndpoints.size + node.pendingEndpoints.size)
        // A new predecessor becomes desired but cannot take a fifth slot before reconciliation.
        val newcomer = "00000000-0000-0000-0000-00000000004a"
        assertFalse(node.accept("new", newcomer, 3_100))
        val rotated = node.poll(4_100)
        assertTrue(endpoint(48) in rotated.disconnect)
        assertTrue(node.accept("new", newcomer, 4_100))
        assertTrue(node.connectedEndpoints.size + node.pendingEndpoints.size <= 4)
    }

    @Test fun twoDevicesRecoverAfterLongRadioOutageWithoutFreshDiscovery() {
        val left = BoundedNearbyTopology(device(1))
        val right = BoundedNearbyTopology(device(2))
        left.found("right", device(2), 0); right.found("left", device(1), 0)
        var attempts = 0
        for (time in 0L..400_000L step BoundedNearbyTopology.POLL_INTERVAL_MS) {
            left.poll(time).connect?.let {
                attempts++
                if (attempts <= 6) left.failed("right", time)
                else {
                    assertTrue(right.accept("left", device(1), time))
                    assertTrue(left.established("right")); assertTrue(right.established("left"))
                }
            }
        }
        assertEquals(7, attempts)
        assertEquals(setOf("right"), left.connectedEndpoints)
        assertEquals(setOf("left"), right.connectedEndpoints)
    }
}

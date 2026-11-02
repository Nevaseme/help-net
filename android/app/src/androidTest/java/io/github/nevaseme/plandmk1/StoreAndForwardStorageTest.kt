package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.PlanDatabase
import io.github.nevaseme.plandmk1.data.RoomRecordStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StoreAndForwardStorageTest {
    @Test fun relayReopensSqliteAndForwardsWhileOriginalDeviceIsAbsent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "store-forward-${UUID.randomUUID()}"
        val names = (0..2).map { "$prefix-$it.db" }
        fun open(index: Int) = Room.databaseBuilder(context, PlanDatabase::class.java, names[index]).build()
        val databases = (0..2).map(::open).toMutableList()
        val stores = databases.map(::RoomRecordStore).toMutableList()
        val devices = (0..2).map { UUID.randomUUID().toString() }
        data class Packet(val from: Int, val to: Int, val bytes: ByteArray)
        val queue = ArrayDeque<Packet>()
        val links = mutableSetOf<Pair<Int, Int>>()
        val errors = mutableListOf<String>()
        fun edge(a: Int, b: Int) = minOf(a, b) to maxOf(a, b)
        fun engine(index: Int) = SyncEngine("store-forward", devices[index], stores[index],
            send = { peer, bytes ->
                val target = devices.indexOf(peer)
                check(edge(index, target) in links)
                queue.addLast(Packet(index, target, bytes.copyOf()))
            }, onError = errors::add)
        val engines = (0..2).map(::engine).toMutableList()
        suspend fun drain() {
            var delivered = 0
            while (queue.isNotEmpty()) {
                check(++delivered < 1_000) { "Unexpected forwarding loop" }
                val packet = queue.removeFirst()
                if (edge(packet.from, packet.to) in links)
                    engines[packet.to].receive(devices[packet.from], packet.bytes)
            }
            assertTrue(errors.toString(), errors.isEmpty())
        }
        suspend fun connect(a: Int, b: Int) {
            assertTrue("Only one contact at a time", links.isEmpty())
            assertNotEquals("No A-to-C radio path", 0 to 2, edge(a, b))
            links.add(edge(a, b))
            engines[a].connected(devices[b]); engines[b].connected(devices[a]); drain()
        }
        fun disconnect(a: Int, b: Int) {
            links.remove(edge(a, b))
            engines[a].disconnected(devices[b]); engines[b].disconnected(devices[a])
            assertTrue(queue.isEmpty())
        }
        fun reopen(index: Int) {
            check(links.none { index == it.first || index == it.second })
            databases[index].close()
            databases[index] = open(index)
            stores[index] = RoomRecordStore(databases[index])
            engines[index] = engine(index)
        }
        try {
            val original = Resident("store-forward", devices[0], 1, 100, "架空A", 35.0, 139.0, "2階", "北", Safety.SOS)
            assertTrue(stores[0].merge(original))
            connect(0, 1)
            assertEquals(original, stores[1].get(original.groupId, original.originId))
            disconnect(0, 1)
            reopen(1) // All B's connections and pending acknowledgements are discarded.
            connect(1, 2) // A remains absent; B reads the record from its reopened SQLite file.
            assertEquals(original, stores[2].get(original.groupId, original.originId))
            disconnect(1, 2)
            reopen(2)
            assertEquals(original, stores[2].get(original.groupId, original.originId))

            val latest = original.copy(revision = 2, updatedAt = 50, safety = Safety.SAFE)
            assertTrue(stores[0].merge(latest))
            connect(0, 1); disconnect(0, 1); reopen(1)
            connect(1, 2); disconnect(1, 2); reopen(2)
            assertEquals(latest, stores[2].get(latest.groupId, latest.originId))
            assertFalse(stores[2].merge(original))
            assertEquals(listOf(latest), stores[2].all(latest.groupId))
        } finally {
            databases.forEach { it.close() }
            names.forEach(context::deleteDatabase)
        }
    }
}

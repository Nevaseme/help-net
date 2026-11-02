package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Real adapter with a controllable Play Services boundary; no radio assumptions. */
class NearbyLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun idle() { instrumentation.waitForIdleSync(); Thread.sleep(100); instrumentation.waitForIdleSync() }
    private class Client {
        val calls = mutableListOf<Pair<String, String?>>()
        lateinit var lifecycle: ConnectionLifecycleCallback
        lateinit var discovery: EndpointDiscoveryCallback
        lateinit var receiver: PayloadCallback
        var requested: ConnectionLifecycleCallback? = null
        var advertiseGate: TaskCompletionSource<Void>? = null
        val value = Proxy.newProxyInstance(ConnectionsClient::class.java.classLoader, arrayOf(ConnectionsClient::class.java)) { _, method, args ->
            calls.add(method.name to (args?.firstOrNull() as? String))
            if (method.name == "startDiscovery") discovery=args!![1] as EndpointDiscoveryCallback
            if (method.name == "requestConnection") requested=args!![2] as ConnectionLifecycleCallback
            if (method.name == "acceptConnection") receiver=args!![1] as PayloadCallback
            if (method.name == "startAdvertising") {
                lifecycle = args!![2] as ConnectionLifecycleCallback
                advertiseGate?.task ?: Tasks.forResult<Void>(null)
            } else if (Task::class.java.isAssignableFrom(method.returnType)) Tasks.forResult<Void>(null)
            else null
        } as ConnectionsClient
    }
    @Test fun automaticDiscoversAndAuthenticatesInBackgroundWithoutConfirmationDialog() {
        val client=Client();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val left=GroupLink("g","00000000-0000-0000-0000-000000000001","ab".repeat(32))
        val right=GroupLink("g","00000000-0000-0000-0000-000000000002","ab".repeat(32))
        lateinit var p:NearbyTransport;var joins=0
        main {
            p=NearbyTransport(instrumentation.targetContext,scope,client.value){true}
            p.configureAutomatic(left);p.onConnected={joins++};p.start("A");p.setVisible(false)
        };idle()
        main {
            client.discovery.onEndpointFound("B",DiscoveredEndpointInfo(left.serviceId,right.endpoint("B")))
        }
        Thread.sleep(1_500); idle()
        main {
            assertTrue(client.calls.any{it.first=="requestConnection"})
            client.requested!!.onConnectionInitiated("B",ConnectionInfo(right.endpoint("B"),"connection-token",false))
            assertTrue(p.state.value.verifications.isEmpty())
            client.requested!!.onConnectionResult("B",ConnectionResolution(Status.RESULT_SUCCESS))
            assertTrue(p.state.value.connected.isEmpty());assertEquals(0,joins)
            client.receiver.onPayloadReceived("B",Payload.fromBytes(right.proof("connection-token".toByteArray(),right.device,left.device)))
        };idle()
        main {
            assertEquals(setOf("B"),p.state.value.connected);assertEquals(1,joins)
            assertTrue(p.state.value.discovering)
            client.requested!!.onDisconnected("B")
            assertTrue(p.state.value.connected.isEmpty());assertTrue(p.state.value.active)
            p.stop();scope.cancel()
        }
    }
    @Test fun wrongGroupProofNeverReleasesRecordsOrCountsAsConnected() {
        val client=Client();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val left=GroupLink("g","00000000-0000-0000-0000-000000000002","ab".repeat(32))
        val other=GroupLink("g","00000000-0000-0000-0000-000000000001","cd".repeat(32))
        lateinit var p:NearbyTransport;var joins=0;var records=0
        main{p=NearbyTransport(instrumentation.targetContext,scope,client.value){true};p.configureAutomatic(left);p.onConnected={joins++};p.onBytes={_,_->records++};p.start("B")};idle()
        main {
            client.lifecycle.onConnectionInitiated("A",ConnectionInfo(other.endpoint("A"),"token",true))
            client.lifecycle.onConnectionResult("A",ConnectionResolution(Status.RESULT_SUCCESS))
            client.receiver.onPayloadReceived("A",Payload.fromBytes(other.proof("token".toByteArray(),other.device,left.device)))
            client.receiver.onPayloadReceived("A",Payload.fromBytes("fake record".toByteArray()))
        };idle()
        main {assertEquals(0,joins);assertEquals(0,records);assertTrue(p.state.value.connected.isEmpty());assertTrue(client.calls.contains("disconnectFromEndpoint" to "A"));p.stop();scope.cancel()}
    }
    @Test fun twentyDiscoveredDevicesCannotExceedFourPendingLinksOrBypassRingPolicy() {
        val client = Client(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        fun link(i: Int) = GroupLink("g", "00000000-0000-0000-0000-${i.toString().padStart(12, '0')}", "ab".repeat(32))
        val local = link(19)
        lateinit var p: NearbyTransport
        main { p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }; p.configureAutomatic(local); p.start("local") }
        idle()
        main {
            (1..20).filter { it != 19 }.forEach { i ->
                client.discovery.onEndpointFound("p$i", DiscoveredEndpointInfo(local.serviceId, link(i).endpoint("p$i")))
            }
            listOf(1, 17, 18).forEach { i ->
                client.lifecycle.onConnectionInitiated("p$i", ConnectionInfo(link(i).endpoint("p$i"), "token-$i", true))
            }
            client.lifecycle.onConnectionInitiated("p2", ConnectionInfo(link(2).endpoint("p2"), "token-2", true))
            assertTrue(client.calls.contains("rejectConnection" to "p2"))
            assertEquals(3, p.state.value.connecting.size)
        }
        Thread.sleep(1_500); idle()
        main {
            assertEquals(setOf("p1", "p17", "p18", "p20"), p.state.value.connecting)
            assertEquals(1, client.calls.count { it.first == "requestConnection" })
            // Endpoint 0 changes the ring but cannot obtain a fifth reservation.
            client.lifecycle.onConnectionInitiated("p0", ConnectionInfo(link(0).endpoint("p0"), "token-0", true))
            assertTrue(client.calls.contains("rejectConnection" to "p0"))
            assertEquals(4, p.state.value.connecting.size)
            assertTrue(p.state.value.verifications.isEmpty())
            p.stop(); scope.cancel()
        }
    }
    @Test fun backgroundStopsDiscoveryCancelsPendingAndKeepsApprovedConnections() {
        val client = Client(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main { p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }; p.start("test") }
        idle()
        main {
            client.lifecycle.onConnectionInitiated("A", ConnectionInfo("A", "1234", true))
            p.verify("A", true)
            client.lifecycle.onConnectionResult("A", ConnectionResolution(Status.RESULT_SUCCESS))
            client.lifecycle.onConnectionInitiated("pending", ConnectionInfo("pending", "5678", true))
            p.setVisible(false)
            assertEquals(setOf("A"), p.state.value.connected)
            assertTrue(p.state.value.verifications.isEmpty()); assertTrue(p.state.value.connecting.isEmpty())
            assertTrue(client.calls.contains("stopAdvertising" to null)); assertTrue(client.calls.contains("stopDiscovery" to null))
            assertTrue(client.calls.contains("rejectConnection" to "pending")); assertTrue(client.calls.contains("disconnectFromEndpoint" to "pending"))
            assertFalse(client.calls.any { it.first == "stopAllEndpoints" })
            client.lifecycle.onConnectionResult("pending", ConnectionResolution(Status.RESULT_SUCCESS))
            assertEquals(setOf("A"), p.state.value.connected)
            p.setVisible(true)
        }
        idle()
        main {
            assertEquals(2, client.calls.count { it.first == "startAdvertising" })
            assertEquals(2, client.calls.count { it.first == "startDiscovery" })
            p.stop(); scope.cancel()
        }
    }
    @Test fun completionAfterStopCleansUpButCannotStopNewSession() {
        val old = Client().apply { advertiseGate = TaskCompletionSource() }
        val next = Client()
        val oldScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var a: NearbyTransport; lateinit var b: NearbyTransport
        main {
            a = NearbyTransport(instrumentation.targetContext, oldScope, old.value) { true }; a.start("old"); a.stop(); oldScope.cancel()
            b = NearbyTransport(instrumentation.targetContext, newScope, next.value) { true }; b.start("new")
        }
        idle()
        val before = old.calls.count { it.first == "stopAdvertising" }
        main { old.advertiseGate!!.setResult(null) }; idle()
        assertEquals(before, old.calls.count { it.first == "stopAdvertising" })
        assertTrue(b.state.value.active)
        main { b.stop(); newScope.cancel() }
    }
    @Test fun cancelledAttemptCallbacksCannotReplaceOrDisconnectNewAttempt() {
        val client = Client(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main { p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }; p.start("test") }; idle()
        val old = client.lifecycle
        main {
            old.onConnectionInitiated("B", ConnectionInfo("old", "1234", true))
            p.setVisible(false); p.setVisible(true)
        }; idle()
        main {
            old.onConnectionInitiated("B", ConnectionInfo("cancelled", "1234", true))
            assertTrue("Cancelled verification reappeared", p.state.value.verifications.isEmpty())
            client.lifecycle.onConnectionInitiated("B", ConnectionInfo("new", "5678", true))
            old.onDisconnected("B")
            assertEquals("new", p.state.value.verifications.single().name)
            p.verify("B", true)
            old.onConnectionResult("B", ConnectionResolution(Status.RESULT_SUCCESS))
            assertTrue(p.state.value.connected.isEmpty())
            client.lifecycle.onConnectionResult("B", ConnectionResolution(Status.RESULT_SUCCESS))
            assertEquals(setOf("B"), p.state.value.connected)
            p.stop(); scope.cancel()
        }
    }
    @Test fun failedSearchPublishesReasonBeforeSessionStops() {
        val client = Client().apply { advertiseGate = TaskCompletionSource() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = object : RecordStore {
            override suspend fun all(groupId: String) = emptyList<Resident>()
            override suspend fun get(groupId: String, originId: String): Resident? = null
            override suspend fun merge(record: Resident) = false
        }
        var published = NearbyState(); var ended = false
        main {
            val p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }
            SharingSession("demo", "local", p, store, scope, { published = it }, { ended = true }).start("test")
            client.advertiseGate!!.setException(IllegalStateException("radio rejected"))
        }; idle()
        assertTrue(ended); assertFalse(published.active)
        assertTrue("Missing failure explanation", published.error?.contains("radio rejected") == true)
        main { scope.cancel() }
    }
    @Test fun delayedAdvertisingIsStoppedBeforeRapidForegroundReturnRestartsIt() {
        val client = Client().apply { advertiseGate = TaskCompletionSource() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main {
            p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }
            p.start("test"); p.setVisible(false); p.setVisible(true)
        }
        val gate = client.advertiseGate!!
        main { client.advertiseGate = null; gate.setResult(null) }; idle()
        main {
            val names = client.calls.map { it.first }
            assertEquals(2, names.count { it == "startAdvertising" })
            assertEquals("stopAdvertising", names[names.lastIndexOf("startAdvertising") - 1])
            assertEquals(1, names.count { it == "startDiscovery" })
            p.stop(); scope.cancel()
        }
    }
}

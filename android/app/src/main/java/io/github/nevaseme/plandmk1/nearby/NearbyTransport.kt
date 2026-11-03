package io.github.nevaseme.plandmk1.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Peer(val id: String, val name: String, val deviceId: String? = null)
data class Verification(val id: String, val name: String, val code: String)
data class NearbyState(
    val active: Boolean = false,
    val starting: Boolean = false,
    val discovering: Boolean = false,
    val peers: List<Peer> = emptyList(),
    val connected: Set<String> = emptySet(),
    val connecting: Set<String> = emptySet(),
    val verifications: List<Verification> = emptyList(),
    val error: String? = null,
    val receiptCount: Int = 0,
    val showPlayServicesSettings: Boolean = false,
)

class NearbyTransport(
    private val context: Context, private val scope: CoroutineScope,
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context),
    private val available: () -> Boolean = { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS },
) : NearbyPort {
    private val mutable = MutableStateFlow(NearbyState())
    override val state = mutable.asStateFlow()
    private var generation = 0
    private var endpointName = "PlanD"
    private var visible = true
    private var searchEpoch = 0
    private var searchJob: Job? = null
    private val accepted = mutableSetOf<String>()
    private val connectionOwners = mutableMapOf<String, Long>()
    private var nextListener = 0L
    private var nativeOwner = 0L
    private var automatic: GroupLink? = null
    private var topology: BoundedNearbyTopology? = null
    private var retryJob: Job? = null
    private var nextSearchAt = 0L
    private var searchBackoff = 5_000L
    private var discoveryAllowed = true
    private val devices = mutableMapOf<String, String>()
    private val challenges = mutableMapOf<String, ByteArray>()
    private fun now() = android.os.SystemClock.elapsedRealtime()
    private val canSearch get() = discoveryAllowed && (visible || automatic != null)
    override fun configureAutomatic(link: GroupLink) { check(!mutable.value.active); automatic = link }
    override var onConnected: suspend (String) -> Unit = {}
    override var onDisconnected: (String) -> Unit = {}
    override var onBytes: (String, ByteArray) -> Unit = { _, _ -> }

    override fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    override fun receipt() { mutable.update { it.copy(receiptCount = it.receiptCount + 1) } }

    override fun start(name: String) {
        if (mutable.value.active) return
        if (!available()) {
            error("Google Play開発者サービスを更新・有効化してください"); return
        }
        endpointName = automatic?.endpoint(name) ?: name.take(60)
        topology = automatic?.let { BoundedNearbyTopology(it.device) }
        nativeOwner = ++latestOwner
        ++generation
        nextSearchAt = 0L; searchBackoff = 5_000L
        mutable.value = NearbyState(active = true)
        resumeSearch()
        retryJob?.cancel()
        if (automatic != null) retryJob = scope.launch {
            while (isActive && mutable.value.active) {
                delay(BoundedNearbyTopology.POLL_INTERVAL_MS)
                if (!mutable.value.discovering && now() >= nextSearchAt) resumeSearch()
                reconcileAutomatic()
            }
        }
    }

    private fun validSearch(token: Int, epoch: Int) = token == generation && epoch == searchEpoch && canSearch && mutable.value.active

    private fun resumeSearch() {
        if (!mutable.value.active || !canSearch || mutable.value.discovering) return
        val token = generation
        val epoch = ++searchEpoch
        val owner = nativeOwner
        val previous = searchJob
        mutable.update { it.copy(discovering = true) }
        searchJob = scope.launch {
            previous?.join()
            if (!validSearch(token, epoch)) return@launch
            try {
                client.startAdvertising(endpointName, automatic?.serviceId ?: SERVICE_ID, lifecycle(token), AdvertisingOptions.Builder()
                    .setStrategy(Strategy.P2P_CLUSTER).setConnectionType(ConnectionType.NON_DISRUPTIVE).build())
                    .addOnSuccessListener { if (owner == latestOwner && !validSearch(token, epoch)) client.stopAdvertising() }.awaitResult()
                if (!validSearch(token, epoch)) return@launch
                client.startDiscovery(automatic?.serviceId ?: SERVICE_ID, discovery(token, epoch), DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                    .addOnSuccessListener { if (owner == latestOwner && !validSearch(token, epoch)) client.stopDiscovery() }.awaitResult()
                if (validSearch(token, epoch)) {
                    searchBackoff = 5_000L
                    mutable.update { it.copy(error = null, showPlayServicesSettings = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (validSearch(token, epoch)) {
                    val permission = Manifest.permission.BLUETOOTH_ADVERTISE
                    val ownGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    val gmsGranted = if (Build.VERSION.SDK_INT >= 31) runCatching {
                        context.packageManager.getApplicationInfo("com.google.android.gms", 0)
                        context.packageManager.checkPermission(permission, "com.google.android.gms") == PackageManager.PERMISSION_GRANTED
                    }.getOrNull() else null
                    val failure = sharingFailure((e as? ApiException)?.statusCode, ownGranted, gmsGranted, e.localizedMessage.orEmpty())
                    if (automatic == null) stopWithReason(failure.message, failure.showPlayServicesSettings)
                    else {
                        // Advertising/discovery can fail while a Wi-Fi data link is still usable.
                        // Keep the session generation and payload listeners until the SDK disconnects.
                        pauseSearch()
                        nextSearchAt = now() + searchBackoff
                        searchBackoff = (searchBackoff * 2).coerceAtMost(60_000L)
                        mutable.update { it.copy(error = failure.message, showPlayServicesSettings = failure.showPlayServicesSettings) }
                    }
                }
            }
        }
    }

    private fun pauseSearch() {
        searchEpoch++
        runCatching { client.stopAdvertising() }; runCatching { client.stopDiscovery() }
        // Discovery callbacks from the old search will not deliver endpoint-lost events.
        mutable.value.peers.forEach { topology?.lost(it.id, now()) }
        mutable.update { s -> s.copy(discovering = false, peers = s.peers.filter { it.id in s.connected || it.id in s.connecting }) }
    }

    override fun refreshDiscovery() {
        if (automatic == null || !mutable.value.active) return
        pauseSearch()
        searchBackoff = 5_000L; nextSearchAt = now() + 1_000L
    }

    override fun setDiscoveryAllowed(allowed: Boolean) {
        if (discoveryAllowed == allowed) return
        discoveryAllowed = allowed
        if (!mutable.value.active) return
        if (!allowed) pauseSearch()
        else { searchBackoff = 5_000L; nextSearchAt = 0L; resumeSearch() }
    }

    override fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (automatic != null) return
        if (visible) { resumeSearch(); return }
        searchEpoch++
        client.stopAdvertising(); client.stopDiscovery()
        val pending = mutable.value.connecting + mutable.value.verifications.map { it.id }
        pending.filterNot { it in mutable.value.connected }.forEach {
            accepted.remove(it); connectionOwners.remove(it); client.rejectConnection(it); client.disconnectFromEndpoint(it)
        }
        mutable.update { s -> s.copy(discovering = false, connecting = emptySet(), verifications = emptyList(), peers = s.peers.filter { it.id in s.connected }) }
    }

    override fun stop() = stopWithReason(null)
    private fun stopWithReason(reason: String?, showPlayServicesSettings: Boolean = false) {
        generation++
        retryJob?.cancel(); retryJob = null
        devices.clear(); challenges.clear(); topology = null
        searchEpoch++; accepted.clear(); connectionOwners.clear()
        runCatching { client.stopAdvertising() }; runCatching { client.stopDiscovery() }; runCatching { client.stopAllEndpoints() }
        mutable.value.connected.forEach(onDisconnected)
        mutable.value = NearbyState(error = reason, showPlayServicesSettings = showPlayServicesSettings)
    }

    override fun connect(peer: Peer) {
        if (automatic != null) { reconcileAutomatic(); return }
        requestConnection(peer)
    }

    private fun requestConnection(peer: Peer) {
        if (!canSearch || !mutable.value.active || peer.id in mutable.value.connecting || peer.id in mutable.value.connected) return
        val token = generation
        mutable.update { it.copy(connecting = it.connecting + peer.id) }
        val callback = lifecycle(token, peer.id)
        val owner = connectionOwners[peer.id]
        client.requestConnection(endpointName, peer.id, callback, ConnectionOptions.Builder()
            .setConnectionType(ConnectionType.NON_DISRUPTIVE).build()).addOnFailureListener {
            if (generation == token && connectionOwners[peer.id] == owner) {
                if (automatic != null) { failAutomatic(peer.id); return@addOnFailureListener }
                connectionOwners.remove(peer.id)
                mutable.update { s -> s.copy(connecting = s.connecting - peer.id) }
                error("接続できませんでした。相手の共有状態を確認して再試行してください")
            }
        }
    }

    override fun verify(id: String, accept: Boolean) {
        if (!visible || mutable.value.verifications.none { it.id == id }) return
        val token = generation
        val owner = connectionOwners[id] ?: return
        mutable.update { it.copy(verifications = it.verifications.filterNot { v -> v.id == id }) }
        if (accept) {
            accepted.add(id)
            client.acceptConnection(id, payload(token, owner)).addOnFailureListener {
            if (token == generation && connectionOwners[id] == owner) {
                connectionOwners.remove(id)
                accepted.remove(id)
                client.disconnectFromEndpoint(id)
                mutable.update { s -> s.copy(connecting = s.connecting - id) }
                error("接続の確認に失敗しました。再接続してください")
            }
            }
        } else {
            accepted.remove(id)
            connectionOwners.remove(id)
            client.rejectConnection(id)
            mutable.update { it.copy(connecting = it.connecting - id) }
        }
    }

    override suspend fun send(peer: String, bytes: ByteArray) {
        require(bytes.size <= 24_000)
        check(peer in mutable.value.connected) { "接続が切れました" }
        client.sendPayload(peer, Payload.fromBytes(bytes)).awaitResult()
    }

    private fun discovery(token: Int, epoch: Int) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (!validSearch(token, epoch)) return
            val identity = automatic?.peer(info.endpointName)
            if (automatic != null && identity == null) return
            identity?.let { devices[id] = it.first; topology?.found(id, it.first, now()) }
            val peer = Peer(id, identity?.second ?: info.endpointName.take(60), identity?.first)
            mutable.update { it.copy(peers = it.peers.filterNot { p -> p.id == id } + peer) }
            reconcileAutomatic()
        }
        override fun onEndpointLost(id: String) {
            if (!validSearch(token, epoch)) return
            topology?.lost(id, now())
            mutable.update { it.copy(peers = it.peers.filterNot { p -> p.id == id && id !in it.connected && id !in it.connecting }) }
            if (id !in mutable.value.connected && id !in mutable.value.connecting) devices.remove(id)
        }
    }

    private fun lifecycle(token: Int, requestedId: String? = null): ConnectionLifecycleCallback {
      val epoch = searchEpoch
      val owner = ++nextListener
      if (requestedId != null) connectionOwners[requestedId] = owner
      return object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (token != generation) return
            if (requestedId != null && connectionOwners[id] != owner) return
            if (!validSearch(token, epoch)) {
                if (connectionOwners[id] == null) client.rejectConnection(id)
                return
            }
            if (id in mutable.value.connected || (connectionOwners[id] != null && connectionOwners[id] != owner)) return
            val link = automatic
            if (link != null) {
                val identity = link.peer(info.endpointName)
                val raw = info.rawAuthenticationToken
                if (identity == null || raw == null || raw.isEmpty()) { client.rejectConnection(id); return }
                devices[id] = identity.first
                mutable.update { it.copy(peers = it.peers.filterNot { p -> p.id == id } + Peer(id, identity.second, identity.first)) }
                if (topology?.accept(id, identity.first, now()) != true) {
                    client.rejectConnection(id)
                    if (id in mutable.value.connecting) failAutomatic(id)
                    return
                }
                devices[id] = identity.first; challenges[id] = raw
                connectionOwners[id] = owner; accepted.add(id)
                mutable.update { it.copy(peers=it.peers.filterNot { p->p.id==id }+Peer(id,identity.second,identity.first),connecting=it.connecting+id) }
                client.acceptConnection(id,payload(token,owner)).addOnFailureListener {
                    if (generation==token && connectionOwners[id]==owner) failAutomatic(id)
                }
                return
            }
            connectionOwners[id] = owner
            mutable.update { s -> s.copy(
                peers = s.peers.filterNot { it.id == id } + Peer(id, info.endpointName.take(60)),
                connecting = s.connecting + id,
                verifications = s.verifications.filterNot { it.id == id } + Verification(id, info.endpointName.take(60), info.authenticationDigits),
            ) }
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (token != generation || connectionOwners[id] != owner) return
            if (result.status.isSuccess && (!canSearch || id !in accepted)) {
                client.disconnectFromEndpoint(id)
                connectionOwners.remove(id); accepted.remove(id)
                mutable.update { it.copy(connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
                return
            }
            if (automatic != null) {
                if (!result.status.isSuccess) { failAutomatic(id); return }
                val raw=challenges[id] ?: return failAutomatic(id)
                val remote=devices[id] ?: return failAutomatic(id)
                val proof=automatic!!.proof(raw,automatic!!.device,remote)
                client.sendPayload(id,Payload.fromBytes(proof)).addOnFailureListener {
                    if (generation==token && connectionOwners[id]==owner) failAutomatic(id)
                }
                return
            }
            mutable.update { it.copy(connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
            if (result.status.isSuccess) {
                mutable.update { it.copy(connected = it.connected + id) }
                scope.launch { if (token == generation && connectionOwners[id] == owner) onConnected(id) }
            } else {
                connectionOwners.remove(id); accepted.remove(id)
                error("接続が成立しませんでした。双方でコードを確認して再接続してください")
            }
        }
        override fun onDisconnected(id: String) {
            if (token != generation || connectionOwners[id] != owner) return
            if (automatic != null) { failAutomatic(id); return }
            connectionOwners.remove(id)
            accepted.remove(id)
            mutable.update { it.copy(connected = it.connected - id, connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
            this@NearbyTransport.onDisconnected(id)
        }
    }
    }

    private fun payload(token: Int, owner: Long) = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (generation == token && connectionOwners[id] == owner && automatic != null && id !in mutable.value.connected) {
                val bytes=payload.asBytes()
                val raw=challenges[id];val remote=devices[id]
                if (bytes==null || raw==null || remote==null || !automatic!!.verifies(raw,remote,bytes)) { failAutomatic(id); return }
                if (topology?.established(id) != true) { failAutomatic(id); return }
                challenges.remove(id)
                mutable.update { it.copy(connected=it.connected+id,connecting=it.connecting-id,error=null) }
                scope.launch { if (generation==token && connectionOwners[id]==owner) onConnected(id) }
                return
            }
            if (generation == token && connectionOwners[id] == owner && id in mutable.value.connected && payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { if (it.size <= 24_000) onBytes(id, it) }
            }
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            if (generation == token && connectionOwners[id] == owner && update.status == PayloadTransferUpdate.Status.FAILURE) error("転送が中断しました。共有を再接続すると再送されます")
        }
    }

    private fun reconcileAutomatic() {
        if (!mutable.value.active || !canSearch) return
        val plan = topology?.poll(now()) ?: return
        plan.disconnect.forEach { dropAutomatic(it) }
        plan.connect?.let { id ->
            val peer = mutable.value.peers.firstOrNull { it.id == id }
            if (peer != null) requestConnection(peer) else topology?.failed(id, now())
        }
    }
    private fun failAutomatic(id: String) {
        topology?.failed(id, now())
        dropAutomatic(id)
    }
    private fun dropAutomatic(id: String) {
        val wasConnected=id in mutable.value.connected
        connectionOwners.remove(id);accepted.remove(id);challenges.remove(id)
        client.disconnectFromEndpoint(id)
        mutable.update { it.copy(connected=it.connected-id,connecting=it.connecting-id,
            peers=if (topology?.isVisible(id) == true) it.peers else it.peers.filterNot { p -> p.id == id },
            verifications=it.verifications.filterNot { v->v.id==id }) }
        if (topology?.isVisible(id) != true) devices.remove(id)
        if (wasConnected) onDisconnected(id)
    }

    companion object {
        // Main-thread ownership prevents a retired client's late task completion from stopping a new session.
        private var latestOwner = 0L
        const val SERVICE_ID = "io.github.nevaseme.plandmk1.sync.v1"
        fun permissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT <= 30) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }.toTypedArray()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}


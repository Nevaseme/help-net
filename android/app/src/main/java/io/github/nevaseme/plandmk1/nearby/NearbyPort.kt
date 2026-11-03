package io.github.nevaseme.plandmk1.nearby

import kotlinx.coroutines.flow.StateFlow

interface NearbyPort {
    val state: StateFlow<NearbyState>
    var onConnected: suspend (String) -> Unit
    var onDisconnected: (String) -> Unit
    var onBytes: (String, ByteArray) -> Unit
    fun start(name: String)
    fun configureAutomatic(link: GroupLink) { }
    /** Retry discovery after a radio change without closing authenticated links. */
    fun refreshDiscovery() { }
    fun setVisible(visible: Boolean)
    fun stop()
    fun connect(peer: Peer)
    fun verify(id: String, accept: Boolean)
    suspend fun send(peer: String, bytes: ByteArray)
    fun error(message: String)
    fun receipt()
}

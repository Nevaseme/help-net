package io.github.nevaseme.plandmk1.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Observe Android's validated default network; Nearby and sync progress are independent. */
@Composable
fun internetAvailable(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun available() = manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    var connected by remember(manager) { mutableStateOf(available()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { connected = available() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { connected = available() }
            override fun onLost(network: Network) { connected = available() }
        }
        manager.registerDefaultNetworkCallback(callback)
        connected = available()
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return connected
}

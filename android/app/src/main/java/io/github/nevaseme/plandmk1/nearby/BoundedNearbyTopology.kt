package io.github.nevaseme.plandmk1.nearby

/**
 * Radio-independent connection scheduler, also used by the population simulation.
 * With a common discovery view, two neighbours on each side of the UUID ring give
 * at most four links per device and a connected graph (2N edges for N >= 5).
 * Local discovery views need not agree: this is not a radio-range guarantee.
 */
class BoundedNearbyTopology(private val localDevice: String) {
    data class Plan(val disconnect: Set<String> = emptySet(), val connect: String? = null)
    private data class Retry(val failures: Int, val after: Long)
    private val identities = mutableMapOf<String, String>()
    private val visible = mutableSetOf<String>()
    private val connected = mutableSetOf<String>()
    private val pending = mutableMapOf<String, Long>()
    private val outgoing = mutableSetOf<String>()
    private val retries = mutableMapOf<String, Retry>()
    private var settleUntil = 0L
    private var nextDial = 0L
    private var previousDesired = emptySet<String>()

    val connectedEndpoints: Set<String> get() = connected.toSet()
    val pendingEndpoints: Set<String> get() = pending.keys.toSet()
    val outgoingEndpoints: Set<String> get() = outgoing.toSet()
    fun isVisible(endpoint: String) = endpoint in visible

    fun found(endpoint: String, device: String, now: Long) {
        if (device == localDevice) return
        val changed = identities[endpoint] != device || endpoint !in visible
        identities[endpoint] = device
        visible.add(endpoint)
        if (changed) settleUntil = now + DISCOVERY_SETTLE_MS
    }

    fun lost(endpoint: String, now: Long) {
        if (!visible.remove(endpoint)) return
        if (endpoint !in connected && endpoint !in pending) forget(endpoint)
        settleUntil = now + DISCOVERY_SETTLE_MS
    }

    /** Keep authenticated links when discovery alone loses an endpoint. */
    fun desiredEndpoints(): Set<String> {
        val byDevice = identities.entries.groupBy({ it.value }, { it.key })
        val ring = (byDevice.keys + localDevice).sorted()
        val index = ring.indexOf(localDevice)
        return listOf(-2, -1, 1, 2).map { ring[Math.floorMod(index + it, ring.size)] }
            .filter { it != localDevice }.distinct().mapTo(linkedSetOf()) { device ->
                // Multiple discovery callbacks/endpoint aliases cannot consume two slots.
                val endpoints = byDevice.getValue(device)
                endpoints.filter { it in connected || it in pending }.minOrNull() ?: endpoints.min()
            }
    }

    /** Atomically reserve incoming slots before the platform accept call. */
    fun accept(endpoint: String, device: String, now: Long): Boolean {
        found(endpoint, device, now)
        if (endpoint !in desiredEndpoints() || endpoint in connected) return false
        if (endpoint in pending) return true
        // Only the lower UUID dials; the other side accepts.
        if (device >= localDevice || connected.size + pending.size >= MAX_LINKS) return false
        pending[endpoint] = now + CONNECTION_TIMEOUT_MS
        return true
    }

    fun established(endpoint: String): Boolean {
        if (pending.remove(endpoint) == null) return false
        outgoing.remove(endpoint)
        connected.add(endpoint)
        retries.remove(endpoint)
        return true
    }

    fun failed(endpoint: String, now: Long) {
        release(endpoint)
        if (endpoint !in identities) return
        val failures = ((retries[endpoint]?.failures ?: 0) + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
        retries[endpoint] = Retry(failures, now + (2_000L shl failures) + jitter(endpoint))
    }

    /** Releases obsolete links before reserving a replacement; never exceeds four slots. */
    fun poll(now: Long): Plan {
        val disconnect = pending.filterValues { it <= now }.keys.toMutableSet()
        disconnect.forEach { failed(it, now) }
        if (now < settleUntil) return Plan(disconnect)
        val desired = desiredEndpoints()
        (desired - previousDesired).forEach { retries.remove(it) }
        previousDesired = desired
        val obsolete = (connected + pending.keys) - desired
        obsolete.forEach { release(it) }
        disconnect.addAll(obsolete)
        if (outgoing.isNotEmpty() || connected.size + pending.size >= MAX_LINKS || now < nextDial) return Plan(disconnect)
        val candidate = desired.sortedWith(compareBy({ identities[it] }, { it })).firstOrNull {
            it !in connected && it !in pending && localDevice < identities.getValue(it) &&
                (retries[it]?.let { retry -> now >= retry.after } ?: true)
        } ?: return Plan(disconnect)
        pending[candidate] = now + CONNECTION_TIMEOUT_MS
        outgoing.add(candidate)
        nextDial = now + DIAL_INTERVAL_MS + jitter(candidate)
        return Plan(disconnect, candidate)
    }

    private fun release(endpoint: String) {
        connected.remove(endpoint)
        pending.remove(endpoint)
        outgoing.remove(endpoint)
        if (endpoint !in visible) forget(endpoint)
    }

    private fun forget(endpoint: String) { identities.remove(endpoint); retries.remove(endpoint) }
    private fun jitter(endpoint: String) = ((localDevice + identities[endpoint]).hashCode().toLong() and 0x7fffffff) % 501

    companion object {
        const val MAX_LINKS = 4
        // Cap retry rate, not lifetime attempts: a radio that returns must recover automatically.
        const val MAX_BACKOFF_EXPONENT = 5
        const val POLL_INTERVAL_MS = 250L
        const val DISCOVERY_SETTLE_MS = 1_000L
        const val DIAL_INTERVAL_MS = 750L
        const val CONNECTION_TIMEOUT_MS = 30_000L
    }
}

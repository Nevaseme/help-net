package io.github.nevaseme.plandmk1.drops

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class DropSensorInfo(
    val name: String, val vendor: String, val version: Int, val type: Int,
    val stringType: String, val wakeUp: Boolean, val model: String, val firmware: String,
)
@Serializable data class DropReading(
    val sensor: DropSensorInfo, val eventElapsedNanos: Long, val receivedElapsedNanos: Long,
    val receivedAt: Long, val screenInteractive: Boolean, val accuracy: Int, val values: List<Double?>,
)
@Serializable data class DropRecord(
    val sessionId: String, val groupId: String, val reading: DropReading,
    val schema: String = "pland-vendor-drop-v1",
    val interpretation: String = "vendor-drop-notification; units-unknown; not-a-collapse-diagnosis",
)
class DropSubscription(val close: () -> Unit)
fun interface DropSource { fun open(receive: (DropReading) -> Unit): DropSubscription? }
data class DropState(
    val active: Boolean = false, val status: String = "stopped",
    val saved: Long = 0, val ignored: Long = 0, val failed: Long = 0,
)

/** Owns exactly one location-recording session. Android callbacks run on a dedicated thread. */
class DropRecording(private val elapsedClock: () -> Long, private val save: (DropRecord) -> Unit) {
    private val mutable = MutableStateFlow(DropState())
    val state = mutable.asStateFlow()
    private data class Session(val token: Long, val id: String, val group: String, val started: Long, var last: Long = -1)
    private var session: Session? = null
    private var subscription: DropSubscription? = null

    @Synchronized fun start(token: Long, group: String, source: DropSource) {
        if (session?.token == token) return
        session?.let { stop(it.token) }
        val current = Session(token, UUID.randomUUID().toString(), group, elapsedClock())
        session = current
        mutable.value = DropState(status = "starting")
        try {
            subscription = source.open { receive(current, it) }
            mutable.value = mutable.value.copy(active = subscription != null, status = if (subscription == null) "unavailable" else "receiving")
        } catch (_: Exception) {
            subscription = null
            mutable.value = mutable.value.copy(active = false, status = "registration-error")
        }
    }

    @Synchronized private fun receive(current: Session, reading: DropReading) {
        if (session !== current) return
        if (reading.eventElapsedNanos < current.started || reading.eventElapsedNanos <= current.last ||
            reading.eventElapsedNanos > reading.receivedElapsedNanos || reading.eventElapsedNanos <= 0) {
            mutable.value = mutable.value.copy(ignored = mutable.value.ignored + 1)
            return
        }
        try {
            save(DropRecord(current.id, current.group, reading))
            current.last = reading.eventElapsedNanos
            mutable.value = mutable.value.copy(saved = mutable.value.saved + 1, status = "receiving")
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(failed = mutable.value.failed + 1, status = "storage-error")
        }
    }

    @Synchronized fun stop(token: Long) {
        if (session?.token != token) return
        session = null // Fence queued callbacks before unregistering.
        val previous = subscription; subscription = null
        var status = "stopped"
        try { previous?.close?.invoke() }
        catch (_: Exception) { status = "unregister-error" }
        finally { mutable.value = mutable.value.copy(active = false, status = status) }
    }
}

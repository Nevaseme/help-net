package io.github.nevaseme.plandmk1.impact

import io.github.nevaseme.plandmk1.motion.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class ImpactMonitoringStatus { STOPPED, STARTING, OBSERVING, INTERRUPTED, SETTINGS_REQUIRED, UNAVAILABLE, FAILED }
data class ImpactMonitoringState(val status: ImpactMonitoringStatus = ImpactMonitoringStatus.STOPPED)
@Serializable data class ImpactMonitoringSnapshot(
    val groupId: String = "", val startedElapsedNanos: Long = 0,
    val sensor: MotionSensorInfo? = null, val sampleCount: Long = 0,
    val lastSampleElapsedNanos: Long? = null, val lastReceivedElapsedNanos: Long? = null,
    val maxSampleGapNanos: Long = 0, val maxDeliveryDelayNanos: Long = 0,
    val rejectedSamples: Long = 0,
)

/** Observation only. No impact threshold or continuous waveform persistence. */
class ImpactMonitoring(private val elapsedClock: () -> Long) {
    private val mutable = MutableStateFlow(ImpactMonitoringState())
    val state = mutable.asStateFlow()
    private var token: Long? = null
    private var subscription: MotionSubscription? = null
    private var stats = ImpactMonitoringSnapshot()

    @Synchronized fun start(sessionToken: Long, group: String, source: MotionSource) {
        if (token == sessionToken && stats.groupId == group && subscription != null) return
        stopCurrent()
        token = sessionToken
        stats = ImpactMonitoringSnapshot(groupId = group, startedElapsedNanos = elapsedClock())
        mutable.value = ImpactMonitoringState(ImpactMonitoringStatus.STARTING)
        try {
            subscription = source.open { sample ->
                synchronized(this) {
                    if (token == sessionToken && stats.groupId == group) receive(sample)
                }
            }
            if (subscription == null) {
                token = null
                mutable.value = ImpactMonitoringState(ImpactMonitoringStatus.UNAVAILABLE)
            } else stats = stats.copy(sensor = subscription!!.sensor)
        } catch (error: Exception) {
            stopCurrent()
            mutable.value = ImpactMonitoringState(if (error is ObservationSettingsRequired)
                ImpactMonitoringStatus.SETTINGS_REQUIRED else ImpactMonitoringStatus.FAILED)
        }
    }

    private fun receive(sample: AccelerationSample) {
        val now = elapsedClock()
        val previous = stats.lastSampleElapsedNanos
        if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite() ||
            sample.elapsedNanos < stats.startedElapsedNanos || sample.elapsedNanos > now ||
            (previous != null && sample.elapsedNanos <= previous)) {
            stats = stats.copy(rejectedSamples = stats.rejectedSamples + 1)
            return
        }
        stats = stats.copy(sampleCount = stats.sampleCount + 1,
            lastSampleElapsedNanos = sample.elapsedNanos, lastReceivedElapsedNanos = now,
            maxSampleGapNanos = maxOf(stats.maxSampleGapNanos, previous?.let { sample.elapsedNanos - it } ?: 0),
            maxDeliveryDelayNanos = maxOf(stats.maxDeliveryDelayNanos, now - sample.elapsedNanos))
        mutable.value = ImpactMonitoringState(if (now - sample.elapsedNanos < SILENCE_NANOS)
            ImpactMonitoringStatus.OBSERVING else ImpactMonitoringStatus.INTERRUPTED)
    }

    @Synchronized fun checkHealth() {
        if (subscription != null && elapsedClock() - (stats.lastSampleElapsedNanos ?: stats.startedElapsedNanos) >= SILENCE_NANOS)
            mutable.value = ImpactMonitoringState(ImpactMonitoringStatus.INTERRUPTED)
    }
    @Synchronized fun snapshot(): ImpactMonitoringSnapshot = stats
    @Synchronized fun stop(sessionToken: Long) { if (token == sessionToken) stopCurrent() }
    private fun stopCurrent() {
        token = null
        val previous = subscription; subscription = null
        val released = runCatching { previous?.close?.invoke() }.isSuccess
        mutable.value = ImpactMonitoringState(if (released) ImpactMonitoringStatus.STOPPED else ImpactMonitoringStatus.FAILED)
    }
    companion object {
        // Health indicator only, NOT an acceptable gap for detecting a short impact.
        private const val SILENCE_NANOS = 1_000_000_000L
    }
}

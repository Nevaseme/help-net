package io.github.nevaseme.plandmk1.impact

import io.github.nevaseme.plandmk1.motion.*

class ObservationSettingsRequired : IllegalStateException("Battery optimization exemption required")

/** One subscription owns one CPU lease; wake-up sensors need no continuous lease. */
class WakeProtectedMotionSource(
    private val source: MotionSource,
    private val exempt: () -> Boolean,
    private val acquire: () -> AutoCloseable,
) : MotionSource {
    override fun open(listener: (AccelerationSample) -> Unit): MotionSubscription? {
        val subscription = source.open(listener) ?: return null
        var lease: AutoCloseable? = null
        try {
            if (!subscription.sensor.wakeUp) {
                if (!exempt()) throw ObservationSettingsRequired()
                lease = acquire()
            }
            return MotionSubscription(subscription.sensor) {
                try { subscription.close() } finally { lease?.close() }
            }
        } catch (error: Exception) {
            runCatching { subscription.close() }
            lease?.close()
            throw error
        }
    }
}

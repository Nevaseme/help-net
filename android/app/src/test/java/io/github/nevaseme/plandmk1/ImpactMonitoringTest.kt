package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.impact.*
import io.github.nevaseme.plandmk1.motion.*
import org.junit.Assert.*
import org.junit.Test

class ImpactMonitoringTest {
    private class Source(val wakeUp: Boolean = false) : MotionSource {
        lateinit var emit: (AccelerationSample) -> Unit
        var closed = 0
        override fun open(listener: (AccelerationSample) -> Unit): MotionSubscription {
            emit = listener
            return MotionSubscription(MotionSensorInfo("test", "test", 1, 156.0, 0.01, 0.24, wakeUp, 10_000)) { closed++ }
        }
    }
    private var now = 1_000_000_000L
    private fun sample(time: Long) = AccelerationSample(time, 0.0, 0.0, 9.81)

    @Test fun registrationIsNotObservationAndSilenceIsReported() {
        val source = Source()
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", source)
        assertEquals(ImpactMonitoringStatus.STARTING, monitor.state.value.status)
        source.emit(sample(now))
        assertEquals(ImpactMonitoringStatus.OBSERVING, monitor.state.value.status)
        now += 2_000_000_000L
        monitor.checkHealth()
        assertEquals(ImpactMonitoringStatus.INTERRUPTED, monitor.state.value.status)
        source.emit(sample(now))
        assertEquals(ImpactMonitoringStatus.OBSERVING, monitor.state.value.status)
        assertEquals(2_000_000_000L, monitor.snapshot().maxSampleGapNanos)
    }

    @Test fun groupChangeAndStopRejectOldCallbacksAndReleaseSubscription() {
        val first = Source(); val second = Source()
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", first)
        first.emit(sample(now))
        monitor.start(2, "b", second)
        first.emit(sample(now + 10_000_000))
        second.emit(sample(now))
        monitor.stop(1)
        assertEquals(1, first.closed)
        assertEquals(1L, monitor.snapshot().sampleCount)
        assertEquals("b", monitor.snapshot().groupId)
        monitor.stop(2)
        second.emit(sample(now + 20_000_000))
        assertEquals(1, second.closed)
        assertEquals(1L, monitor.snapshot().sampleCount)
        assertEquals(ImpactMonitoringStatus.STOPPED, monitor.state.value.status)
    }

    @Test fun invalidAndDuplicateSamplesCannotPretendMonitoringIsWorking() {
        val source = Source(); val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", source)
        source.emit(AccelerationSample(now, Double.NaN, 0.0, 0.0))
        source.emit(sample(now - 1))
        assertEquals(0L, monitor.snapshot().sampleCount)
        source.emit(sample(now))
        source.emit(sample(now))
        assertEquals(1L, monitor.snapshot().sampleCount)
        assertEquals(3L, monitor.snapshot().rejectedSamples)
    }

    @Test fun missingSensorDoesNotClaimObservation() {
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", object : MotionSource {
            override fun open(listener: (AccelerationSample) -> Unit): MotionSubscription? = null
        })
        assertEquals(ImpactMonitoringStatus.UNAVAILABLE, monitor.state.value.status)
    }

    @Test fun nonWakeSensorNeedsExemptionAndDoesNotLeakSubscription() {
        val source = Source(); var held = false
        val protected = WakeProtectedMotionSource(source, { false }) {
            held = true; AutoCloseable { held = false }
        }
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", protected)
        assertEquals(ImpactMonitoringStatus.SETTINGS_REQUIRED, monitor.state.value.status)
        assertFalse(held)
        assertEquals(1, source.closed)
    }

    @Test fun wakeLeaseLivesExactlyAsLongAsNonWakeSubscription() {
        val source = Source(); var held = false
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", WakeProtectedMotionSource(source, { true }) {
            held = true; AutoCloseable { held = false }
        })
        assertTrue(held)
        monitor.stop(1)
        assertFalse(held)
        assertEquals(1, source.closed)
    }

    @Test fun wakeSensorDoesNotAcquireContinuousCpuLease() {
        val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", WakeProtectedMotionSource(Source(true), { false }) {
            error("wake-up sensor must not need a continuous lease")
        })
        assertEquals(ImpactMonitoringStatus.STARTING, monitor.state.value.status)
    }

    @Test fun leaseFailureClosesSensorAndLeavesMonitorFailed() {
        val source = Source(); val monitor = ImpactMonitoring { now }
        monitor.start(1, "a", WakeProtectedMotionSource(source, { true }) { error("permission denied") })
        assertEquals(ImpactMonitoringStatus.FAILED, monitor.state.value.status)
        assertEquals(1, source.closed)
    }
}

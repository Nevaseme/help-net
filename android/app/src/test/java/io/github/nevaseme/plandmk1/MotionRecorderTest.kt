package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.motion.*
import org.junit.Assert.*
import org.junit.Test

class MotionRecorderTest {
    private class Source: MotionSource {
        lateinit var emit: (AccelerationSample)->Unit
        var closed=0
        var opened=0
        override fun open(listener: (AccelerationSample)->Unit): MotionSubscription {
            emit=listener;opened++
            return MotionSubscription(MotionSensorInfo("test","test",1,100.0,0.01,0.1,true,10000)) { closed++ }
        }
    }
    @Test fun followsRecordingSessionAndRejectsLateCallbacks() {
        val source=Source()
        val recorder=MotionRecorder({1000},{1_000_000_000})
        recorder.start(1,"group-a",source)
        recorder.start(1,"group-a",source)
        assertEquals(1,source.opened)
        source.emit(AccelerationSample(1_010_000_000,0.0,0.0,9.81))
        recorder.stop(1)
        source.emit(AccelerationSample(1_020_000_000,0.0,0.0,30.0))
        assertEquals(1,source.closed)
        assertFalse(recorder.state.value.active)
        assertEquals(1,recorder.snapshot("group-a").trace.samples.size)
    }
    @Test fun newGroupHasOwnTraceAndOldStopCannotStopNewSession() {
        val first=Source();val second=Source()
        val recorder=MotionRecorder({1000},{1_000_000_000})
        recorder.start(1,"group-a",first)
        recorder.start(2,"group-b",second)
        first.emit(AccelerationSample(1_010_000_000,0.0,0.0,50.0))
        second.emit(AccelerationSample(1_020_000_000,0.0,0.0,9.81))
        recorder.stop(1)
        assertTrue(recorder.state.value.active)
        assertEquals(9.81,recorder.snapshot("group-b").trace.peakTotalAccelerationMps2!!,1e-8)
        try { recorder.snapshot("group-a"); fail("group mismatch accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun absentSensorDoesNotClaimObservationOrFailLocationSession() {
        val recorder=MotionRecorder({1000},{1_000_000_000})
        recorder.start(1,"group-a",object:MotionSource { override fun open(listener:(AccelerationSample)->Unit):MotionSubscription?=null })
        assertFalse(recorder.state.value.active)
        assertEquals("unavailable",recorder.state.value.status)
    }
    @Test fun sensorReleaseFailureDoesNotPreventPositionStopOrNextSession() {
        val recorder=MotionRecorder({1000},{1_000_000_000})
        recorder.start(1,"group-a",object:MotionSource {
            override fun open(listener:(AccelerationSample)->Unit)=MotionSubscription(
                MotionSensorInfo("test","test",1,100.0,0.01,0.1,false,10000)
            ) { throw IllegalStateException("release failed") }
        })
        recorder.stop(1)
        assertFalse(recorder.state.value.active)
        assertEquals("stop-failed",recorder.state.value.status)
        val next=Source()
        recorder.start(2,"group-a",next)
        assertTrue(recorder.state.value.active)
        assertEquals(1,next.opened)
    }
}

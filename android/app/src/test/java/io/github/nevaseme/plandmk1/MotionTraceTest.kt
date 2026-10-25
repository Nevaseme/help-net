package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.motion.*
import org.junit.Assert.*
import org.junit.Test

class MotionTraceTest {
    @Test fun tracksThreeAxesIncludingGravityWithoutDeclaringAnImpact() {
        val trace=MotionTrace(1000L,1_000_000_000L,capacity=10)
        assertTrue(trace.add(AccelerationSample(1_010_000_000L,0.0,0.0,9.80665)))
        assertTrue(trace.add(AccelerationSample(1_020_000_000L,3.0,4.0,0.0)))
        val snapshot=trace.snapshot()
        assertEquals(1010L,snapshot.samples.first().observedAt)
        assertEquals(9.80665,snapshot.peakTotalAccelerationMps2!!,1e-8)
        assertEquals(5.0,snapshot.samples.last().totalAccelerationMps2,1e-8)
        assertEquals("unconfigured",snapshot.impactDecision)
    }

    @Test fun rejectsNonfiniteAndOutOfOrderDataAndBoundsMemory() {
        val trace=MotionTrace(1000L,1_000_000_000L,capacity=2)
        assertFalse(trace.add(AccelerationSample(999_000_000L,0.0,0.0,9.0)))
        assertFalse(trace.add(AccelerationSample(1_010_000_000L,Double.NaN,0.0,0.0)))
        assertTrue(trace.add(AccelerationSample(1_010_000_000L,1.0,0.0,0.0)))
        assertFalse(trace.add(AccelerationSample(1_010_000_000L,99.0,0.0,0.0)))
        assertTrue(trace.add(AccelerationSample(1_020_000_000L,2.0,0.0,0.0)))
        val original=trace.snapshot()
        assertTrue(trace.add(AccelerationSample(1_030_000_000L,3.0,0.0,0.0)))
        assertEquals(listOf(2.0,3.0),trace.snapshot().samples.map { it.x })
        assertEquals(listOf(1.0,2.0),original.samples.map { it.x })
        assertEquals(1L,trace.snapshot().evictedSamples)
        assertEquals(3L,trace.snapshot().rejectedSamples)
    }

    @Test fun gapsAreRecordedInsteadOfAssumingContinuousObservation() {
        val trace=MotionTrace(1000L,1_000_000_000L,capacity=10)
        trace.add(AccelerationSample(1_010_000_000L,0.0,0.0,9.0))
        trace.add(AccelerationSample(5_010_000_000L,0.0,0.0,9.0))
        assertEquals(4_000_000_000L,trace.snapshot().maxGapNanos)
        assertEquals(1_010L,trace.snapshot().samples.first().observedAt)
        assertEquals(5_010L,trace.snapshot().samples.last().observedAt)
    }
}

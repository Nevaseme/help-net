package io.github.nevaseme.plandmk1.motion

import kotlinx.serialization.Serializable
import java.util.ArrayDeque
import kotlin.math.hypot

data class AccelerationSample(val elapsedNanos: Long,val x: Double,val y: Double,val z: Double)
@Serializable data class MeasuredAcceleration(val elapsedNanos: Long,val observedAt: Long,val x: Double,val y: Double,val z: Double,val totalAccelerationMps2: Double)
@Serializable data class MotionSnapshot(
    val methodId: String="accelerometer-total-calibration-v1",
    val impactDecision: String="unconfigured",
    val includesGravity: Boolean=true,
    val samples: List<MeasuredAcceleration>,
    val peakTotalAccelerationMps2: Double?,
    val rejectedSamples: Long,
    val evictedSamples: Long,
    val maxGapNanos: Long,
)

/** Bounded calibration memory only. No unapproved threshold, SOS or collapse inference. */
class MotionTrace(private val startedAt: Long,private val startedElapsedNanos: Long,private val capacity: Int=4096) {
    init { require(startedAt>=0 && startedElapsedNanos>=0 && capacity>0) }
    private val samples=ArrayDeque<MeasuredAcceleration>()
    private var lastNanos: Long?=null
    private var rejected=0L
    private var evicted=0L
    private var maxGap=0L
    @Synchronized fun add(sample: AccelerationSample): Boolean {
        val magnitude=hypot(hypot(sample.x,sample.y),sample.z)
        if(!magnitude.isFinite() || sample.elapsedNanos<startedElapsedNanos || lastNanos?.let { sample.elapsedNanos<=it }==true) { rejected++;return false }
        val deltaMs=(sample.elapsedNanos-startedElapsedNanos)/1_000_000
        if(deltaMs>Long.MAX_VALUE-startedAt) { rejected++;return false }
        lastNanos?.let { maxGap=maxOf(maxGap,sample.elapsedNanos-it) }
        lastNanos=sample.elapsedNanos
        if(samples.size==capacity) { samples.removeFirst();evicted++ }
        samples.addLast(MeasuredAcceleration(sample.elapsedNanos,startedAt+deltaMs,sample.x,sample.y,sample.z,magnitude))
        return true
    }
    @Synchronized fun snapshot(): MotionSnapshot = MotionSnapshot(samples=samples.toList(),
        peakTotalAccelerationMps2=samples.maxOfOrNull { it.totalAccelerationMps2 },
        rejectedSamples=rejected,evictedSamples=evicted,maxGapNanos=maxGap)
}

package io.github.nevaseme.plandmk1.motion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable data class MotionSensorInfo(val name: String,val vendor: String,val version: Int,
    val maximumRangeMps2: Double,val resolutionMps2: Double,val powerMa: Double,val wakeUp: Boolean,val requestedPeriodUs: Int)
class MotionSubscription(val sensor: MotionSensorInfo,val close: ()->Unit)
interface MotionSource { fun open(listener:(AccelerationSample)->Unit):MotionSubscription? }
data class MotionState(val active: Boolean=false,val status: String="stopped",val sensor: MotionSensorInfo?=null)
@Serializable data class MotionCalibration(val groupId: String,val sensor: MotionSensorInfo,val trace: MotionSnapshot)

class MotionRecorder(private val wallClock:()->Long,private val elapsedClock:()->Long) {
    private val mutable=MutableStateFlow(MotionState())
    val state=mutable.asStateFlow()
    private var token: Long?=null
    private var group: String?=null
    private var subscription: MotionSubscription?=null
    private var trace: MotionTrace?=null
    private var sensor: MotionSensorInfo?=null

    @Synchronized fun start(sessionToken: Long,groupId: String,source: MotionSource) {
        if(token==sessionToken && group==groupId) return
        stopCurrent()
        token=sessionToken;group=groupId;sensor=null
        val currentTrace=MotionTrace(wallClock(),elapsedClock());trace=currentTrace
        try {
            subscription=source.open { sample ->
                synchronized(this) {
                    if(token==sessionToken && group==groupId) currentTrace.add(sample)
                }
            }
            sensor=subscription?.sensor
            mutable.value=if(subscription==null) MotionState(status="unavailable") else MotionState(true,"calibration-only",sensor)
            if(subscription==null) token=null
        } catch (_: Exception) {
            stopCurrent();mutable.value=MotionState(status="failed")
        }
    }
    @Synchronized fun stop(sessionToken: Long) { if(token==sessionToken) stopCurrent() }
    private fun stopCurrent() {
        token=null
        val previous=subscription;subscription=null
        mutable.value=try { previous?.close?.invoke(); MotionState() }
        catch (_: Exception) { MotionState(status="stop-failed") }
    }
    @Synchronized fun snapshot(groupId: String): MotionCalibration {
        require(group==groupId) { "観測したグループが異なります" }
        val info=checkNotNull(sensor) { "加速度センサーを利用できません" }
        val snapshot=checkNotNull(trace).snapshot()
        check(snapshot.samples.isNotEmpty()) { "加速度の測定値がまだありません" }
        return MotionCalibration(groupId,info,snapshot)
    }
}

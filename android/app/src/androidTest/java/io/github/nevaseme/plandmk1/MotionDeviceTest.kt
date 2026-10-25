package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.location.PositionFix
import io.github.nevaseme.plandmk1.motion.*
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.AtomicFile
import android.os.Bundle
import java.io.File
import java.util.UUID

class MotionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PlanDApplication
    private lateinit var previousSource: () -> MotionSource
    private lateinit var previousPosition: () -> (suspend () -> PositionFix)
    private lateinit var previousGroup: String
    private var previousCalibrationEnabled=false
    @Before fun prepare() {
        previousSource=app.motionSourceFactory; previousPosition=app.positionSourceFactory
        previousGroup=app.installation.group.value
        previousCalibrationEnabled=app.motionCalibrationEnabled
        app.motionCalibrationEnabled=true
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(app.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33) automation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnIdle { app.position.stop() }
        // Keep tests from writing a synthetic location into the user's records.
        app.positionSourceFactory={ { delay(Long.MAX_VALUE); error("cancelled before acquisition") } }
        compose.waitUntil(10_000) { app.position.foreground }
    }
    @After fun restore() {
        compose.runOnIdle {
            app.position.stop(); app.motionSourceFactory=previousSource
            app.motionCalibrationEnabled=previousCalibrationEnabled
            app.positionSourceFactory=previousPosition; app.installation.setGroup(previousGroup)
        }
    }
    private class Source: MotionSource {
        val opened=AtomicInteger(); val closed=AtomicInteger()
        lateinit var emit: (AccelerationSample)->Unit
        override fun open(listener:(AccelerationSample)->Unit): MotionSubscription {
            emit=listener; opened.incrementAndGet()
            return MotionSubscription(MotionSensorInfo("injected","test",1,100.0,0.01,0.1,true,10000)) {closed.incrementAndGet()}
        }
    }
    @Test fun normalPositionRecordingObservesWithoutStartingCalibration() {
        app.motionCalibrationEnabled=false
        val source=Source();app.motionSourceFactory={source}
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {app.position.state.value.message=="位置を取得しています"}
        compose.waitUntil(10_000) {source.opened.get()==1}
        source.emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(),0.0,0.0,9.81))
        assertEquals(1L,app.impactMonitoring.snapshot().sampleCount)
        assertFalse(app.motion.state.value.active)
    }
    @Test fun recordingRecreationAndNotificationStopControlMotion() {
        val source=Source(); app.motionSourceFactory={source}
        compose.runOnIdle { app.position.start() }
        compose.waitUntil(10_000) { app.motion.state.value.active }
        source.emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(),0.0,0.0,9.81))
        compose.activityRule.scenario.recreate()
        assertTrue(app.motion.state.value.active)
        assertEquals(1,source.opened.get())
        app.getSystemService(NotificationManager::class.java).activeNotifications.first {it.id==51}
            .notification.actions.single().actionIntent.send()
        compose.waitUntil(10_000) {!app.motion.state.value.active && !app.position.state.value.active}
        source.emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(),0.0,0.0,100.0))
        assertEquals(1,source.closed.get())
        assertEquals(1,app.motion.snapshot(previousGroup).trace.samples.size)
    }
    @Test fun groupChangeStopsMotionAndUnavailableSensorLeavesLocationActive() {
        val source=Source();app.motionSourceFactory={source}
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {app.motion.state.value.active}
        compose.runOnIdle {app.installation.setGroup("motion-test-${SystemClock.elapsedRealtime()}")}
        compose.waitUntil(10_000) {!app.position.state.value.active && !app.motion.state.value.active}
        assertEquals(1,source.closed.get())
        app.motionSourceFactory={object:MotionSource {
            override fun open(listener:(AccelerationSample)->Unit):MotionSubscription?=null
        }}
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {app.motion.state.value.status=="unavailable"}
        assertTrue(app.position.state.value.active)
    }
    @Test fun platformAccelerometerProducesTimestampedReadings() {
        app.motionSourceFactory={AndroidMotionSource(app)}
        val startedAfter=SystemClock.elapsedRealtimeNanos()
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {measurementReady(startedAfter,10)}
        val capture=app.motion.snapshot(previousGroup)
        assertTrue(capture.sensor.maximumRangeMps2>0)
        assertTrue(capture.trace.includesGravity)
        assertEquals("unconfigured",capture.trace.impactDecision)
        assertTrue(capture.trace.samples.all {it.totalAccelerationMps2.isFinite() && it.elapsedNanos>0})
    }
    private fun measurementReady(startedAfter:Long, minimumSamples:Int=1):Boolean =
        app.motion.state.value.active && runCatching {
            val samples=app.motion.snapshot(previousGroup).trace.samples
            samples.size>=minimumSamples && samples.first().elapsedNanos>=startedAfter
        }.getOrDefault(false)

    @Test fun stoppedTraceCannotSatisfyNewMeasurementReadiness() {
        val source=Source();app.motionSourceFactory={source}
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {app.motion.state.value.active}
        source.emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(),0.0,0.0,9.81))
        compose.runOnIdle {app.position.stop()}
        assertFalse(measurementReady(SystemClock.elapsedRealtimeNanos()))
    }
    // Explicitly invoked manual measurement only; skipped in unattended suites.
    @Test fun captureCalibrationOnRequest() {
        val args=InstrumentationRegistry.getArguments()
        val seconds=args.getString("motionCaptureSeconds")?.toIntOrNull()
        Assume.assumeTrue("manual calibration not requested",seconds!=null)
        require(seconds!! in 1..20)
        val label=requireNotNull(args.getString("motionCaptureLabel"))
        require(label.length in 1..120)
        app.motionSourceFactory={AndroidMotionSource(app)}
        val startedAfter=SystemClock.elapsedRealtimeNanos()
        compose.runOnIdle {app.position.start()}
        compose.waitUntil(10_000) {measurementReady(startedAfter)}
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("motionCapture","started: $label ($seconds seconds)")})
        SystemClock.sleep(seconds*1000L)
        compose.runOnIdle {app.position.stop()}
        val capture=CalibrationFile(label,Build.MANUFACTURER,Build.MODEL,Build.VERSION.SDK_INT,
            System.currentTimeMillis(),app.motion.snapshot(previousGroup))
        val directory=File(app.filesDir,"motion-calibration").apply {check(isDirectory || mkdirs())}
        val file=AtomicFile(File(directory,"${UUID.randomUUID()}.json"))
        val stream=file.startWrite()
        try {stream.write(Json {encodeDefaults=true}.encodeToString(capture).toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
        catch(error:Exception) {file.failWrite(stream);throw error}
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("motionCaptureFile",file.baseFile.absolutePath)})
    }
    @Serializable private data class CalibrationFile(val label:String,val manufacturer:String,val model:String,
        val sdk:Int,val exportedAt:Long,val calibration:MotionCalibration)
}

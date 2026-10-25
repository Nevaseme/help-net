package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.impact.ImpactMonitoringStatus
import io.github.nevaseme.plandmk1.motion.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ImpactMonitoringDeviceTest {
    @Test fun normalServiceKeepsObservationAcrossBackgroundAndRecreationThenReleasesIt() = runBlocking {
        val test = InstrumentationRegistry.getInstrumentation()
        val app = test.targetContext.applicationContext as PlanDApplication
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).forEach {
            test.uiAutomation.grantRuntimePermission(app.packageName, it)
        }
        if (Build.VERSION.SDK_INT >= 33) test.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val previous = app.motionSourceFactory; val previousPosition = app.positionSourceFactory
        val calibration = app.motionCalibrationEnabled
        var closed = 0; var opened = 0
        lateinit var emit: (AccelerationSample) -> Unit
        test.runOnMainSync {
            app.position.stop(); app.motionCalibrationEnabled = false
            app.positionSourceFactory = { { delay(Long.MAX_VALUE); error("cancelled") } }
            app.motionSourceFactory = { object : MotionSource {
                override fun open(listener: (AccelerationSample) -> Unit): MotionSubscription {
                    opened++; emit = listener
                    return MotionSubscription(MotionSensorInfo("test", "test", 1, 100.0, 0.01, 0.1, true, 10_000)) { closed++ }
                }
            } }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                withTimeout(10_000) { while (!app.position.foreground) delay(50) }
                test.runOnMainSync { app.position.start() }
                withTimeout(10_000) { while (opened == 0) delay(50) }
                // The manual source has emitted nothing; a slow emulator may already report silence.
                assertNotEquals(ImpactMonitoringStatus.OBSERVING, app.impactMonitoring.state.value.status)
                assertEquals(0L, app.impactMonitoring.snapshot().sampleCount)
                emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(), 0.0, 0.0, 9.81))
                assertEquals(ImpactMonitoringStatus.OBSERVING, app.impactMonitoring.state.value.status)
                scenario.recreate()
                scenario.moveToState(Lifecycle.State.CREATED)
                withTimeout(10_000) { while (app.position.foreground) delay(50) }
                assertFalse(app.position.foreground)
                emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(), 0.0, 0.0, 9.81))
                assertEquals(ImpactMonitoringStatus.OBSERVING, app.impactMonitoring.state.value.status)
                assertEquals(1, opened)
                assertEquals(2L, app.impactMonitoring.snapshot().sampleCount)
                assertTrue(app.position.state.value.active)
                app.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id == 51 }
                    .notification.actions.first { it.title.toString() == "位置記録を停止" }.actionIntent.send()
                withTimeout(10_000) { while (app.position.state.value.active) delay(50) }
                assertEquals(1, closed)
                assertEquals(ImpactMonitoringStatus.STOPPED, app.impactMonitoring.state.value.status)
                emit(AccelerationSample(SystemClock.elapsedRealtimeNanos(), 0.0, 0.0, 100.0))
                assertEquals(2L, app.impactMonitoring.snapshot().sampleCount)
            }
        } finally {
            test.runOnMainSync {
                app.position.stop(); app.motionSourceFactory = previous
                app.positionSourceFactory = previousPosition; app.motionCalibrationEnabled = calibration
            }
        }
    }
}

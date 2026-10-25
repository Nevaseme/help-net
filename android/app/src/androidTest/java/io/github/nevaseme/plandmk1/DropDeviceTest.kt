package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.drops.*
import io.github.nevaseme.plandmk1.location.PositionFix
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*

/** Automated lifecycle tests use injected events on an emulator, not physical drops. */
class DropDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PlanDApplication
    private lateinit var oldSource: () -> DropSource
    private lateinit var oldPosition: () -> (suspend () -> PositionFix)
    private lateinit var oldGroup: String
    private class Source : DropSource {
        lateinit var emit: (DropReading) -> Unit
        var opened = 0; var closed = 0
        override fun open(receive: (DropReading) -> Unit): DropSubscription {
            emit = receive; opened++
            return DropSubscription { closed++ }
        }
    }
    @Before fun prepare() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        oldSource = app.dropSourceFactory; oldPosition = app.positionSourceFactory
        oldGroup = app.installation.group.value
        compose.runOnIdle { app.position.stop() }
        app.positionSourceFactory = { { delay(Long.MAX_VALUE); error("cancelled") } }
        compose.waitUntil(10_000) { app.position.foreground }
    }
    @After fun restore() {
        compose.runOnIdle {
            app.position.stop(); app.dropSourceFactory = oldSource
            app.positionSourceFactory = oldPosition; app.installation.setGroup(oldGroup)
        }
    }
    @Test fun normalServicePersistsDropAcrossRecreationAndNotificationStopClosesReceiver() {
        val source = Source(); app.dropSourceFactory = { source }
        val before = app.dropEvents.records().size
        compose.runOnIdle { app.position.start() }
        compose.waitUntil(10_000) { app.drops.state.value.active }
        compose.activityRule.scenario.recreate()
        assertEquals(1, source.opened)
        val now = SystemClock.elapsedRealtimeNanos()
        val reading = DropReading(DropSensorInfo("synthetic-test", "test", 1, 66561, "android.sensor.drop_depth", true, "emulator", "test"),
            now, now, System.currentTimeMillis(), false, 3, listOf(1.25))
        source.emit(reading)
        assertEquals(before + 1, app.dropEvents.records().size)
        assertEquals(reading, app.dropEvents.records().last().reading)
        app.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id == 51 }
            .notification.actions.single().actionIntent.send()
        compose.waitUntil(10_000) { !app.position.state.value.active && !app.drops.state.value.active }
        source.emit(reading.copy(eventElapsedNanos = now + 1, receivedElapsedNanos = now + 1))
        assertEquals(before + 1, app.dropEvents.records().size)
        assertEquals(1, source.closed)
    }
    @Test fun groupChangeClosesReceiverAndMissingSensorLeavesPositionActive() {
        val source = Source(); app.dropSourceFactory = { source }
        compose.runOnIdle { app.position.start() }
        compose.waitUntil(10_000) { app.drops.state.value.active }
        compose.runOnIdle { app.installation.setGroup("drop-test-${SystemClock.elapsedRealtime()}") }
        compose.waitUntil(10_000) { !app.position.state.value.active && !app.drops.state.value.active }
        assertEquals(1, source.closed)
        app.dropSourceFactory = { DropSource { null } }
        compose.runOnIdle { app.position.start() }
        compose.waitUntil(10_000) { app.drops.state.value.status == "unavailable" }
        assertTrue(app.position.state.value.active)
    }
}

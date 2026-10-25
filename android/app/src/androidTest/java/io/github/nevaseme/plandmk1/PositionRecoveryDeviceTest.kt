package io.github.nevaseme.plandmk1

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PositionRecoveryDeviceTest {
    @Test fun registeredAppStartsAutomaticallyAndBootEntryRestoresInBackground() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PlanDApplication
        val previousGroup = app.installation.group.value
        val source = app.positionSourceFactory
        val group = "recovery-${UUID.randomUUID().toString().take(8)}"
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, it)
        }
        if (Build.VERSION.SDK_INT >= 29) instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        var acquired = System.currentTimeMillis()
        instrumentation.runOnMainSync {
            app.position.stop()
            app.positionSourceFactory = { { PositionFix(35.0, 135.7, 8.0, ++acquired) } }
            app.installation.setGroup(group)
        }
        app.store.updateOwn(group, app.installation.localId) {
            Resident(group, app.installation.localId, 1, 0, "自動復帰試験", 35.0, 135.7, "1階", "試験室")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                withTimeout(20_000) { while (app.store.positions(group).isEmpty()) delay(100) }
                assertTrue(app.position.state.value.active)
                assertEquals(group, PositionPreferences(app).group)
                val first = app.store.positions(group).single()
                scenario.moveToState(Lifecycle.State.CREATED)
                withTimeout(5_000) { while (app.position.foreground) delay(100) }
                instrumentation.runOnMainSync { app.position.refresh() }
                withTimeout(10_000) { while (app.store.positions(group).single().revision <= first.revision) delay(100) }
                instrumentation.runOnMainSync { app.position.stop() }
                withTimeout(5_000) { while (app.position.state.value.active) delay(100) }
                val beforeBoot = app.store.positions(group).single()
                // Exercise the OS entry point, not a new visible Activity.
                instrumentation.runOnMainSync { PositionResumeReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED)) }
                withTimeout(15_000) { while (app.store.positions(group).single().revision <= beforeBoot.revision) delay(100) }
                assertTrue(app.position.state.value.active)
                assertFalse(app.position.foreground)
                assertEquals(beforeBoot.latitude, app.store.positions(group).single().latitude, 0.0)
            }
        } finally {
            instrumentation.runOnMainSync {
                app.positionSourceFactory = source
                val keepForReboot = InstrumentationRegistry.getArguments().getString("keepRecoveryFixture") == "true"
                if (keepForReboot) check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
                else {
                    app.position.stop()
                    app.installation.setGroup(previousGroup)
                }
            }
        }
    }
}

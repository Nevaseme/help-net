package io.github.nevaseme.plandmk1.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import io.github.nevaseme.plandmk1.PlanDApplication
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

/** Shell-only evidence capture. No wake lock, alarm, sensor registration or foreground service. */
class MotionObservationProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PlanDApplication
        if (intent.action == STOP) { job?.cancel(); job = null; return }
        if (intent.action != START) return
        val seconds = intent.getIntExtra("seconds", 300)
        require(seconds in 10..3600)
        val label = intent.getStringExtra("label") ?: "observation"
        require(label.matches(Regex("[a-zA-Z0-9_-]{1,60}")))
        job?.cancel()
        val file = File(app.filesDir, "motion-observation/$label-${System.currentTimeMillis()}.jsonl")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        job = scope.launch {
            val power = app.getSystemService(PowerManager::class.java)
            val end = SystemClock.elapsedRealtime() + seconds * 1000L
            fun record(phase: String) {
                val snapshot = app.impactMonitoring.snapshot()
                val row = buildJsonObject {
                    put("phase", phase); put("wallTime", System.currentTimeMillis())
                    put("elapsedNanos", SystemClock.elapsedRealtimeNanos())
                    put("interactive", power.isInteractive); put("deviceIdle", power.isDeviceIdleMode)
                    put("powerSave", power.isPowerSaveMode)
                    put("optimizationExempt", power.isIgnoringBatteryOptimizations(app.packageName))
                    put("status", app.impactMonitoring.state.value.status.name)
                    put("positionActive", app.position.state.value.active)
                    // No group IDs, locations, waveform, or user information in diagnostic output.
                    put("observation", Json.encodeToJsonElement(snapshot.copy(groupId = "")))
                }
                file.appendText(row.toString() + "\n")
            }
            try {
                record("started")
                while (SystemClock.elapsedRealtime() < end) { delay(5_000); record("sample") }
                record("completed")
            } finally {
                // A final record cannot repair a gap: analysis uses every adjacent timestamp.
                record("ended")
            }
        }
        resultCode = 0
        resultData = file.name
    }
    companion object {
        const val START = "io.github.nevaseme.plandmk1.PROBE_MOTION_START"
        const val STOP = "io.github.nevaseme.plandmk1.PROBE_MOTION_STOP"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null
    }
}

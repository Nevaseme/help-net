package io.github.nevaseme.plandmk1.impact

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import io.github.nevaseme.plandmk1.motion.MotionSource

class AndroidImpactWakePolicy(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    fun needsSettings(): Boolean {
        val manager = context.getSystemService(SensorManager::class.java) ?: return false
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        return !sensor.isWakeUpSensor && !power.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun protect(source: MotionSource): MotionSource = WakeProtectedMotionSource(source,
        { power.isIgnoringBatteryOptimizations(context.packageName) }, ::acquire)

    @SuppressLint("WakelockTimeout") // Owned by PositionService; released on recording stop/group change/destroy.
    private fun acquire(): AutoCloseable {
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlanD:impact-observation")
        lock.setReferenceCounted(false)
        lock.acquire()
        return AutoCloseable { if (lock.isHeld) lock.release() }
    }
}

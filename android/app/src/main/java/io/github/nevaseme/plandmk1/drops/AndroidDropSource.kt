package io.github.nevaseme.plandmk1.drops

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.*
import io.github.nevaseme.plandmk1.drops.com.vivo.sensor.SDC.DropNotificationListener
import java.util.concurrent.atomic.AtomicBoolean

/** Restricted to the tested device family. No accelerometer or allowlist mutations. */
class AndroidDropSource(private val context: Context) : DropSource {
    override fun open(receive: (DropReading) -> Unit): DropSubscription? {
        if (Build.MODEL != "V2417A" || Build.VERSION.SDK_INT != 36) return null
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
            it.stringType == "android.sensor.drop_depth" && it.type == 66561 &&
                it.vendor.equals("vivo", ignoreCase = true) && it.isWakeUpSensor && it.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE
        } ?: return null
        val info = DropSensorInfo(sensor.name, sensor.vendor, sensor.version, sensor.type,
            sensor.stringType, sensor.isWakeUpSensor, Build.MODEL, Build.DISPLAY)
        val power = context.getSystemService(PowerManager::class.java)
        val thread = HandlerThread("PlanD-drop-notification").apply { start() }
        val closed = AtomicBoolean(false)
        val listener = DropNotificationListener { event ->
            if (!closed.get()) receive(DropReading(info, event.timestamp, SystemClock.elapsedRealtimeNanos(),
                System.currentTimeMillis(), power.isInteractive, event.accuracy,
                event.values.map { value -> if (value.isFinite()) value.toDouble() else null }))
        }
        fun close() {
            if (closed.compareAndSet(false, true)) {
                try { manager.unregisterListener(listener) } finally { thread.quitSafely() }
            }
        }
        return try {
            if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(thread.looper))) DropSubscription(::close)
            else { close(); null }
        } catch (error: Exception) { close(); throw error }
    }
}

package io.github.nevaseme.plandmk1.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

/** The requested rate is calibration configuration, not an impact threshold. */
class AndroidMotionSource(private val context: Context) : MotionSource {
    override fun open(listener: (AccelerationSample) -> Unit): MotionSubscription? {
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
        val info = MotionSensorInfo(sensor.name, sensor.vendor, sensor.version,
            sensor.maximumRange.toDouble(), sensor.resolution.toDouble(), sensor.power.toDouble(),
            sensor.isWakeUpSensor, PERIOD_US)
        val thread = HandlerThread("PlanD-motion").apply { start() }
        val closed = AtomicBoolean(false)
        val receiver = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!closed.get() && event.values.size >= 3) {
                    listener(AccelerationSample(event.timestamp, event.values[0].toDouble(),
                        event.values[1].toDouble(), event.values[2].toDouble()))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun close() {
            if (closed.compareAndSet(false, true)) {
                try { manager.unregisterListener(receiver) } finally { thread.quitSafely() }
            }
        }
        return try {
            if (manager.registerListener(receiver, sensor, PERIOD_US, Handler(thread.looper))) {
                MotionSubscription(info, ::close)
            } else { close(); null }
        } catch (error: Exception) {
            close()
            throw error
        }
    }
    companion object { private const val PERIOD_US = 10_000 }
}

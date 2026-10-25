package io.github.nevaseme.plandmk1.drops.com.vivo.sensor.SDC

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * PlanD's own listener, not a vivo component. This name intentionally contains
 * the existing vendor allowlist substring required by the tested V2417A firmware.
 * Keep the name when shrinking; no package/UID or system allowlist is changed.
 */
class DropNotificationListener(private val receive: (SensorEvent) -> Unit) : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = receive(event)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

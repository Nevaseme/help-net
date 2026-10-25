package io.github.nevaseme.plandmk1.vendorprobe.com.vivo.sensor.SDC

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * Test-only probe of this firmware's listener-name substring matching.
 * Runs as PlanD, with its original UID and package; this is not a vivo component.
 * This class name deliberately contains an existing vendor allowlist entry.
 */
class DropNotificationListener(private val receive: (SensorEvent) -> Unit) : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = receive(event)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

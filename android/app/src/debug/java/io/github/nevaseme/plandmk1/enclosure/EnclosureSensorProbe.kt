package io.github.nevaseme.plandmk1.enclosure

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/** Explicit debug-only capture. The caller keeps the app visible and supplies the experiment label.
 * No permission requests, service, microphone recording, playback, file writes, or network access.
 * Sensor power here is the manufacturer's advertised value, never measured battery consumption.
 */
object EnclosureSensorProbe {
    private data class Channel(val name: String, val type: Int, val dimensions: Int, val unit: String)
    private data class Sample(val time: Long, val values: DoubleArray, val accuracy: Int)
    private val channels = listOf(
        Channel("light", Sensor.TYPE_LIGHT, 1, "lux"),
        Channel("proximity", Sensor.TYPE_PROXIMITY, 1, "reported_cm_or_binary"),
        Channel("acceleration", Sensor.TYPE_ACCELEROMETER, 3, "m/s2_including_gravity"),
        Channel("linearAcceleration", Sensor.TYPE_LINEAR_ACCELERATION, 3, "m/s2"),
        Channel("gyroscope", Sensor.TYPE_GYROSCOPE, 3, "rad/s"),
        Channel("magneticField", Sensor.TYPE_MAGNETIC_FIELD, 3, "microtesla"),
        Channel("pressure", Sensor.TYPE_PRESSURE, 1, "hPa"),
    )

    suspend fun capture(context: Context, label: String, durationMillis: Long,
        samplingPeriodUs: Int = 20_000, capacityPerSensor: Int = 8192, includeSensorSamples: Boolean = false): JSONObject {
        require(label.isNotBlank() && label.length <= 120)
        require(durationMillis in 100..120_000 && samplingPeriodUs >= 10_000 && capacityPerSensor in 1..20_000)
        val manager = checkNotNull(context.getSystemService(SensorManager::class.java))
        val audio = context.getSystemService(AudioManager::class.java)
        val started = SystemClock.elapsedRealtimeNanos()
        val wallTime = System.currentTimeMillis()
        val sensors = channels.associateWith { manager.getDefaultSensor(it.type) }
        val records = channels.associateWith { ArrayDeque<Sample>() }
        val evictions = channels.associateWith { 0 }.toMutableMap()
        val states = channels.associateWith { if (sensors[it] == null) "unavailable" else "not-registered" }.toMutableMap()
        val lock = Any()
        var accepting = true
        val thread = HandlerThread("enclosure-calibration").also { it.start() }
        val receiver = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = synchronized(lock) {
                if (!accepting) return@synchronized
                val channel = channels.firstOrNull { it.type == event.sensor.type } ?: return@synchronized
                if (event.values.size < channel.dimensions) return@synchronized
                val list = records.getValue(channel)
                if (list.size == capacityPerSensor) { list.removeFirst(); evictions[channel] = evictions.getValue(channel) + 1 }
                list.addLast(Sample(event.timestamp, DoubleArray(channel.dimensions) { event.values[it].toDouble() }, event.accuracy))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        try {
            sensors.forEach { (channel, sensor) ->
                if (sensor != null) states[channel] = try {
                    if (manager.registerListener(receiver, sensor, samplingPeriodUs, Handler(thread.looper))) "registered" else "registration-failed"
                } catch (_: SecurityException) { "permission-denied" }
            }
            delay(durationMillis)
        } finally {
            synchronized(lock) { accepting = false }
            manager.unregisterListener(receiver)
            thread.quitSafely()
        }
        val ended = SystemClock.elapsedRealtimeNanos()
        return JSONObject().put("method", "enclosure-observation-only-v1").put("decision", "not-configured")
            .put("label", label).put("startedAt", wallTime).put("startedElapsedNanos", started).put("endedElapsedNanos", ended)
            .put("requestedDurationMillis", durationMillis).put("requestedSamplingPeriodUs", samplingPeriodUs)
            .put("unprocessedAudioAdvertised", audio?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) ?: JSONObject.NULL)
            .put("microphoneRecorded", false).put("channels", JSONArray(channels.map { channel ->
                val sensor = sensors[channel]
                val observations = records.getValue(channel).toList()
                JSONObject().put("channel", channel.name).put("unit", channel.unit).put("state", states[channel])
                    .put("evicted", evictions.getValue(channel)).put("accuracyCodes", JSONArray(observations.map { it.accuracy }.distinct()))
                    .put("sensor", sensor?.let { JSONObject().put("name", it.name).put("vendor", it.vendor).put("version", it.version)
                        .put("type", it.type).put("maximumRange", it.maximumRange).put("resolution", it.resolution)
                        .put("reportedPowerMilliamp", it.power).put("wakeUp", it.isWakeUpSensor).put("reportingMode", it.reportingMode) } ?: JSONObject.NULL)
                    .put("features", if (channel.dimensions == 1) scalarJson(EnclosureFeatures.scalar(observations.map { ScalarObservation(it.time, it.values[0]) }))
                        else EnclosureFeatures.vector(observations.map { VectorObservation(it.time, it.values[0], it.values[1], it.values[2]) }).let {
                            JSONObject().put("magnitude", scalarJson(it.magnitude)).put("centeredVectorRms", it.centeredVectorRms ?: JSONObject.NULL)
                        })
                    .also { item -> if (includeSensorSamples) item.put("samples", JSONArray(observations.map {
                        JSONObject().put("elapsedNanos", it.time).put("values", JSONArray(it.values.toList())).put("accuracy", it.accuracy)
                    })) }
            }))
    }

    private fun scalarJson(value: ScalarFeatures) = JSONObject().put("count", value.count).put("rejected", value.rejected)
        .put("minimum", value.minimum ?: JSONObject.NULL).put("maximum", value.maximum ?: JSONObject.NULL)
        .put("mean", value.mean ?: JSONObject.NULL).put("populationSd", value.populationSd ?: JSONObject.NULL)
        .put("spanNanos", value.spanNanos).put("maxGapNanos", value.maxGapNanos)
}

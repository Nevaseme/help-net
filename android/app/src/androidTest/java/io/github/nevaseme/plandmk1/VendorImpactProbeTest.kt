package io.github.nevaseme.plandmk1

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** Explicit capability probe only. Vendor values are not interpreted as impacts. */
class VendorImpactProbeTest {
    @Test fun inspectDropNotificationRegistration() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("vendorImpactProbe")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as PlanDApplication
        val withPositionService=InstrumentationRegistry.getArguments().getString("probePositionService")=="true"
        val previousSource=app.positionSourceFactory
        val previousCalibration=app.motionCalibrationEnabled
        if(withPositionService) check(!app.position.state.value.active) {"Existing position recording must not be replaced by a probe"}
        try {
            if(withPositionService) {
                // Exercise the existing foreground service without collecting or writing locations.
                app.positionSourceFactory={ {delay(Long.MAX_VALUE); error("probe location acquisition cancelled")} }
                app.motionCalibrationEnabled=false
            }
            inspect(withPositionService)
        } finally {
            if(withPositionService) {
                instrumentation.runOnMainSync {app.position.stop()}
                app.positionSourceFactory=previousSource
                app.motionCalibrationEnabled=previousCalibration
            }
        }
    }

    private fun inspect(withPositionService:Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val vendorListenerName=InstrumentationRegistry.getArguments().getString("probeVendorListenerName")=="true"
        val manualStop=InstrumentationRegistry.getArguments().getString("probeManualStop")=="true"
        val waitMillis=(InstrumentationRegistry.getArguments().getString("probeWaitSeconds")?.toLongOrNull() ?: 5L)
            .coerceIn(1L,180L)*1_000L
        val directory=File(context.filesDir,"vendor-impact-probe").apply {check(isDirectory || mkdirs())}
        val runId=UUID.randomUUID().toString()
        val liveFile=File(directory,"$runId.events.jsonl")
        val stopFile=File(directory,"$runId.stop")
        if(InstrumentationRegistry.getArguments().getString("probeForeground")=="true") {
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(2_000)
        }
        val app=context.applicationContext as PlanDApplication
        if(withPositionService) {
            instrumentation.runOnMainSync { app.position.start() }
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(!app.position.state.value.active && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
            check(app.position.state.value.active) {"Position foreground service did not become active"}
        }
        val process=ActivityManager.RunningAppProcessInfo().also {ActivityManager.getMyMemoryState(it)}
        val manager=context.getSystemService(SensorManager::class.java)
        val sensors=manager.getSensorList(Sensor.TYPE_ALL).filter {it.stringType=="android.sensor.drop_depth"}
        val report=buildJsonObject {
            put("schema","vendor-impact-probe-v1")
            put("processImportance",process.importance)
            put("requestedWaitMillis",waitMillis)
            put("manualStop",manualStop)
            put("positionServiceRequested",withPositionService)
            put("vendorListenerName",vendorListenerName)
            put("liveEventsFile",liveFile.absolutePath)
            put("interpretation","unverified-vendor-notification; not an impact or collapse diagnosis")
            putJsonArray("candidates") {
                for(sensor in sensors) {
                    val samples=ConcurrentLinkedQueue<JsonObject>()
                    val thread=HandlerThread("vendor-impact-probe").apply {start()}
                    val active=AtomicBoolean(true)
                    val startedAt=SystemClock.elapsedRealtimeNanos()
                    val collector=object:SensorEventListener {
                        override fun onSensorChanged(event:SensorEvent) {
                            if(active.get() && samples.size<32) {
                                val sample=buildJsonObject {
                                put("elapsedNanos",event.timestamp)
                                put("receivedElapsedNanos",SystemClock.elapsedRealtimeNanos())
                                put("receivedWallTimeMillis",System.currentTimeMillis())
                                put("beforeRegistration",event.timestamp<startedAt)
                                put("accuracy",event.accuracy)
                                putJsonArray("values") {event.values.forEach {add(if(it.isFinite()) JsonPrimitive(it) else JsonNull)}}
                                }
                                samples.add(sample)
                                liveFile.appendText(sample.toString()+"\n",Charsets.UTF_8)
                                instrumentation.sendStatus(0,Bundle().apply {putString("vendorImpactProbeEvent",sample.toString())})
                            }
                        }
                        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
                    }
                    val listener:SensorEventListener=if(vendorListenerName)
                        io.github.nevaseme.plandmk1.vendorprobe.com.vivo.sensor.SDC.DropNotificationListener(collector::onSensorChanged)
                        else collector
                    var registered=false
                    var failure:String?=null
                    try {
                        registered=manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_NORMAL,Handler(thread.looper))
                        instrumentation.sendStatus(0,Bundle().apply {
                            putString("vendorImpactProbeReady",registered.toString())
                            putString("vendorImpactProbeLiveFile",liveFile.absolutePath)
                            putString("vendorImpactProbeStopFile",stopFile.absolutePath)
                        })
                        if(registered) {
                            if(manualStop) {
                                while(!stopFile.exists()) {
                                    val state=ActivityManager.RunningAppProcessInfo().also {ActivityManager.getMyMemoryState(it)}
                                    instrumentation.sendStatus(0,Bundle().apply {
                                        putString("vendorImpactProbeHeartbeat",buildJsonObject {
                                            put("wallTimeMillis",System.currentTimeMillis())
                                            put("processImportance",state.importance)
                                            put("positionActive",app.position.state.value.active)
                                            put("events",samples.size)
                                        }.toString())
                                    })
                                    // The operator explicitly ends this run after reviewing receipt evidence.
                                    // No timeout, event receipt, or ADB disconnect ends manual observation.
                                    repeat(20) {if(!stopFile.exists()) SystemClock.sleep(500)}
                                }
                            } else SystemClock.sleep(waitMillis)
                        }
                    } catch(error:Exception) {failure=error.javaClass.simpleName}
                    finally {
                        active.set(false)
                        try {manager.unregisterListener(listener)} finally {thread.quitSafely();thread.join(2_000)}
                    }
                    add(buildJsonObject {
                        put("name",sensor.name);put("vendor",sensor.vendor);put("type",sensor.type)
                        put("stringType",sensor.stringType);put("version",sensor.version)
                        put("reportingMode",sensor.reportingMode);put("wakeUp",sensor.isWakeUpSensor)
                        put("maximumRange",sensor.maximumRange);put("resolution",sensor.resolution)
                        put("registered",registered);put("failure",failure?.let(::JsonPrimitive) ?: JsonNull)
                        put("listenerClass",listener.javaClass.name)
                        put("startedElapsedNanos",startedAt);put("events",JsonArray(samples.toList()))
                    })
                }
            }
            val control=manager.getDefaultSensor(Sensor.TYPE_LIGHT)
            val controlListener=object:SensorEventListener {
                override fun onSensorChanged(event:SensorEvent)=Unit
                override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
            }
            put("lightControlRegistered",try {
                control!=null && manager.registerListener(controlListener,control,SensorManager.SENSOR_DELAY_NORMAL)
            } finally {manager.unregisterListener(controlListener)})
        }
        val file=File(directory,"$runId.json")
        file.writeText(report.toString(),Charsets.UTF_8)
        instrumentation.sendStatus(0,Bundle().apply {putString("vendorImpactProbeFile",file.absolutePath);putString("vendorImpactProbeReport",report.toString())})
    }
}

package io.github.nevaseme.plandmk1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.buildings.BuildingRiskOverlay
import io.github.nevaseme.plandmk1.diagnostics.BuildingMapBenchmarkActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Direct instrumentation: avoids the Compose test framework's UI automation startup on vivo. */
class BuildingRiskDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun await(timeout:Long=30_000, condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+timeout
        while(!condition()) { check(SystemClock.elapsedRealtime()<end){"Map measurement timed out"};SystemClock.sleep(20) }
    }
    private fun maps(v:View):List<MapView> = if(v is MapView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {maps(v.getChildAt(it))} else emptyList()
    @Test fun measureProductionMap() {
        val context=instrumentation.targetContext
        android.util.Log.i("PlanD-map-device","starting direct activity")
        val activity=if(InstrumentationRegistry.getArguments().getString("externalLaunch")=="true") {
            val ref=AtomicReference<BuildingMapBenchmarkActivity>()
            await(90_000) {
                main {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<BuildingMapBenchmarkActivity>().firstOrNull()?.let(ref::set)}
                ref.get()!=null
            }
            ref.get()
        } else instrumentation.startActivitySync(Intent(context,BuildingMapBenchmarkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BuildingMapBenchmarkActivity
        try {
            val mapRef=AtomicReference<MapLibreMap>();val viewRef=AtomicReference<MapView>()
            await {
                main {if(viewRef.get()==null)maps(activity.window.decorView).firstOrNull()?.let {viewRef.set(it);it.getMapAsync(mapRef::set)}}
                mapRef.get()!=null
            }
            val map=mapRef.get();val view=viewRef.get()
            fun features(layer:String):List<org.maplibre.geojson.Feature> {
                var result=emptyList<org.maplibre.geojson.Feature>()
                main {result=map.queryRenderedFeatures(if(layer==BuildingRiskOverlay.OVERVIEW)RectF(view.width*.45f,view.height*.45f,view.width*.55f,view.height*.55f) else RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),layer)}
                return result
            }
            await(90_000){features(BuildingRiskOverlay.BUILDINGS).isNotEmpty()}
            val initialMs=SystemClock.elapsedRealtime()-activity.createdAt
            val first=features(BuildingRiskOverlay.BUILDINGS)
            val samples=mutableListOf<Double>();var last=0L;var collecting=false
            val listener=MapView.OnDidFinishRenderingFrameListener {_,_,_->
                // Listener and control state are both on the main thread.
                if(collecting) {val now=System.nanoTime();if(last!=0L)samples.add((now-last)/1e6);last=now}
            }
            main {view.addOnDidFinishRenderingFrameListener(listener)}
            val phases=JSONArray()
            val path=listOf(LatLng(35.005,135.763),LatLng(35.009,135.753),LatLng(34.988,135.758),LatLng(35.025,135.755),LatLng(35.003,135.759))
            for(enabled in listOf(false,true,true)) {
                main {
                    map.style!!.getLayer(BuildingRiskOverlay.BUILDINGS)!!.setProperties(visibility(if(enabled)Property.VISIBLE else Property.NONE))
                    map.style!!.getLayer(BuildingRiskOverlay.OVERVIEW)!!.setProperties(visibility(if(enabled)Property.VISIBLE else Property.NONE))
                }
                val durations=JSONArray();val frames=mutableListOf<Double>()
                for(point in path) {
                    val done=AtomicBoolean(false);val t=SystemClock.elapsedRealtime()
                    main {
                        samples.clear();last=0;collecting=true
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(point,16.8),1000,object:MapLibreMap.CancelableCallback {
                            override fun onFinish(){done.set(true)}
                            override fun onCancel(){done.set(true)}
                        })
                    }
                    await(30_000){done.get()}
                    main {collecting=false;frames.addAll(samples)}
                    if(enabled)await {features(BuildingRiskOverlay.BUILDINGS).isNotEmpty()}
                    durations.put(SystemClock.elapsedRealtime()-t)
                }
                val sorted=frames.sorted();assertTrue(sorted.isNotEmpty())
                phases.put(JSONObject().put("overlay",enabled).put("animationPlusVisibleMs",durations).put("renderCallbackIntervalMs",JSONArray(frames))
                    .put("p50Ms",sorted[((sorted.size-1)*.5).toInt()]).put("p95Ms",sorted[((sorted.size-1)*.95).toInt()])
                    .put("over32ms",sorted.count {it>32}).put("frames",sorted.size))
                android.util.Log.i("PlanD-map-device","overlay=$enabled frames=${sorted.size} durations=$durations")
            }
            fun capture(name:String) {
                val bitmap=AtomicReference<Bitmap>()
                main {map.snapshot(bitmap::set)}
                await {bitmap.get()!=null}
                File(context.getExternalFilesDir(null),"risk-map-$name.png").outputStream().use {bitmap.get().compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.get().recycle()
            }
            capture("buildings")
            val t=SystemClock.elapsedRealtime()
            main {map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.04,135.75),10.3))}
            await {features(BuildingRiskOverlay.OVERVIEW).isNotEmpty()}
            val wideMs=SystemClock.elapsedRealtime()-t
            val wide=features(BuildingRiskOverlay.OVERVIEW)
            assertTrue(wide.all {it.hasProperty("p")&&!it.hasProperty("n")&&!it.hasProperty("f12")})
            capture("overview")
            val widePhases=JSONArray()
            for(enabled in listOf(false,true)) {
                main {map.style!!.getLayer(BuildingRiskOverlay.OVERVIEW)!!.setProperties(visibility(if(enabled)Property.VISIBLE else Property.NONE))}
                val frames=mutableListOf<Double>();val durations=JSONArray()
                for(point in listOf(LatLng(35.03,135.76),LatLng(35.05,135.75),LatLng(35.04,135.75))) {
                    val done=AtomicBoolean(false);val began=SystemClock.elapsedRealtime()
                    main {
                        samples.clear();last=0;collecting=true
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(point,10.3),1000,object:MapLibreMap.CancelableCallback {
                            override fun onFinish(){done.set(true)}
                            override fun onCancel(){done.set(true)}
                        })
                    }
                    await {done.get()}
                    main {collecting=false;frames.addAll(samples)}
                    durations.put(SystemClock.elapsedRealtime()-began)
                }
                val sorted=frames.sorted();assertTrue(sorted.isNotEmpty())
                widePhases.put(JSONObject().put("overlay",enabled).put("animationMs",durations).put("renderCallbackIntervalMs",JSONArray(frames))
                    .put("p50Ms",sorted[((sorted.size-1)*.5).toInt()]).put("p95Ms",sorted[((sorted.size-1)*.95).toInt()]).put("frames",sorted.size))
                android.util.Log.i("PlanD-map-device","wide overlay=$enabled frames=${sorted.size} durations=$durations")
            }
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            val report=JSONObject().put("device",Build.MODEL).put("sdk",Build.VERSION.SDK_INT).put("initialVisibleMs",initialMs)
                .put("wideVisibleMs",wideMs).put("initialFeatures",first.size).put("overviewFeatures",wide.size)
                .put("totalPssKb",memory.totalPss).put("phases",phases).put("widePhases",widePhases)
                .put("note","debug build; direct instrumentation; render callback intervals during 1-second camera animations; not a Google Maps comparison")
            File(context.getExternalFilesDir(null),"risk-map-performance.json").writeText(report.toString(2))
            main {view.removeOnDidFinishRenderingFrameListener(listener)}
            main {io.github.nevaseme.plandmk1.ui.SituationCamera().also {it.map=map;it.showKyoto()}}
            await {
                var fits=false
                main {
                    val bounds=map.projection.visibleRegion.latLngBounds
                    fits=bounds.contains(LatLng(35.093958,135.840253)) && bounds.contains(LatLng(34.882525,135.652981))
                }
                fits
            }
            // Wait for camera animation and background map requests before the evidence image.
            SystemClock.sleep(1500)
            capture("extent")
            main {
                val time=SystemClock.uptimeMillis()
                MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,view.width*.5f,view.height*.5f,0).also {view.dispatchTouchEvent(it);it.recycle()}
                MotionEvent.obtain(time,time+80,MotionEvent.ACTION_UP,view.width*.5f,view.height*.5f,0).also {view.dispatchTouchEvent(it);it.recycle()}
            }
            await {
                var zoom=0.0
                main {zoom=map.cameraPosition.zoom}
                zoom>=16.7 && features(BuildingRiskOverlay.BUILDINGS).isNotEmpty()
            }
        } finally {main {activity.finish()}}
    }
}

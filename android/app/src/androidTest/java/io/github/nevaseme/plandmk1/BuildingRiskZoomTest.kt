package io.github.nevaseme.plandmk1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.buildings.BuildingRiskOverlay
import io.github.nevaseme.plandmk1.diagnostics.BuildingMapBenchmarkActivity
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Polygon
import org.maplibre.geojson.MultiPolygon
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class BuildingRiskZoomTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = inst.runOnMainSync(block)
    private fun await(timeout: Long = 30_000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { check(SystemClock.elapsedRealtime() < until) { "Zoom test timed out" }; SystemClock.sleep(30) }
    }
    private fun maps(v: View): List<MapView> = when(v) {
        is MapView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { maps(v.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun buildingColorSurvivesMaximumZoom() {
        val context = inst.targetContext
        val ref = AtomicReference<BuildingMapBenchmarkActivity>()
        if (InstrumentationRegistry.getArguments().getString("externalLaunch") != "true") {
            inst.startActivitySync(Intent(context, BuildingMapBenchmarkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        await(90_000) { main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<BuildingMapBenchmarkActivity>().firstOrNull()?.let(ref::set) }; ref.get() != null }
        val activity = ref.get()
        try {
            val mapRef = AtomicReference<MapLibreMap>(); val viewRef = AtomicReference<MapView>()
            await { main { if(viewRef.get()==null) maps(activity.window.decorView).firstOrNull()?.let { viewRef.set(it); it.getMapAsync(mapRef::set) } }; mapRef.get()!=null }
            val map=mapRef.get(); val view=viewRef.get()
            fun features(): List<org.maplibre.geojson.Feature> {
                var found=emptyList<org.maplibre.geojson.Feature>()
                main { found=map.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),BuildingRiskOverlay.BUILDINGS,BuildingRiskOverlay.OVERVIEW) }
                return found
            }
            await(90_000) { features().isNotEmpty() }
            val sample=features().first { it.getStringProperty("p") != "?" }
            val point=when(val shape=sample.geometry()) {
                is Polygon -> shape.coordinates().first().first()
                is MultiPolygon -> shape.coordinates().first().first().first()
                else -> error("Expected footprint")
            }
            val results=JSONArray(); var missing=false
            for(zoom in listOf(12.0,13.0,13.9,14.0,14.1,14.5,15.0,15.5,16.0,16.8,18.0,20.0,22.0,13.9,14.1,16.8)) {
                main { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude(),point.longitude()),zoom)) }
                SystemClock.sleep(1200)
                val until=SystemClock.elapsedRealtime()+10_000
                var found=features()
                while(found.isEmpty() && SystemClock.elapsedRealtime()<until) { SystemClock.sleep(100); found=features() }
                var actual=0.0; var maxLayer=0f
                main { actual=map.cameraPosition.zoom; maxLayer=map.style!!.getLayer(BuildingRiskOverlay.BUILDINGS)!!.maxZoom }
                results.put(JSONObject().put("requestedZoom",zoom).put("actualZoom",actual).put("features",found.size).put("layerMaxZoom",maxLayer.toString()))
                missing=missing || found.isEmpty()
                android.util.Log.i("PlanD-zoom-test","zoom=$actual features=${found.size} layerMax=$maxLayer")
                val image=AtomicReference<Bitmap>()
                main { map.snapshot(image::set) }; await { image.get()!=null }
                File(context.getExternalFilesDir(null),"risk-zoom-$zoom.png").outputStream().use { image.get().compress(Bitmap.CompressFormat.PNG,100,it) }; image.get().recycle()
            }
            File(context.getExternalFilesDir(null),"risk-zoom.json").writeText(results.toString(2))
            assertTrue("Building risk must remain visible at every zoom: $results", !missing)
        } finally { main { activity.finish() } }
    }

    @Test fun exportOwnHistoricalAnrTrace() {
        if(android.os.Build.VERSION.SDK_INT < 30) return
        val context=inst.targetContext
        val manager=context.getSystemService(android.app.ActivityManager::class.java)
        manager.getHistoricalProcessExitReasons(context.packageName,0,20)
            .firstOrNull { it.reason==android.app.ApplicationExitInfo.REASON_ANR }
            ?.traceInputStream?.use { input ->
                File(context.getExternalFilesDir(null),"historical-anr.txt").outputStream().use { input.copyTo(it) }
            }
    }

    @Test fun recreatingMapReleasesOldView() {
        val context=inst.targetContext
        val ref=AtomicReference<BuildingMapBenchmarkActivity>()
        if(InstrumentationRegistry.getArguments().getString("externalLaunch")!="true") {
            inst.startActivitySync(Intent(context,BuildingMapBenchmarkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        await(90_000) { main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<BuildingMapBenchmarkActivity>().firstOrNull()?.let(ref::set) };ref.get()!=null }
        repeat(3) {
        val activity=ref.get();val viewRef=AtomicReference<MapView>()
        main { viewRef.set(maps(activity.window.decorView).first()) }
        val detached=AtomicBoolean(false);val destroyedBeforeDetach=AtomicBoolean(false)
        main {
            viewRef.get().addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v:View) {}
                override fun onViewDetachedFromWindow(v:View) {
                    destroyedBeforeDetach.set((v as MapView).isDestroyed)
                    detached.set(true)
                }
            })
        }
        SystemClock.sleep(2_000)
        android.os.Handler(android.os.Looper.getMainLooper()).post { activity.recreate() }
        await(10_000) { detached.get() }
        assertTrue("Old map must be destroyed by detach completion",destroyedBeforeDetach.get())
        val responsive=AtomicBoolean(false)
        android.os.Handler(android.os.Looper.getMainLooper()).post { responsive.set(true) }
        await(5_000) { responsive.get() }
        await(30_000) {
            main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<BuildingMapBenchmarkActivity>().firstOrNull { it !== activity }?.let(ref::set) }
            ref.get() !== activity
        }
        val recreatedMap=AtomicReference<MapLibreMap>();val recreatedView=AtomicReference<MapView>()
        await {
            main { if(recreatedView.get()==null) maps(ref.get().window.decorView).firstOrNull()?.let { recreatedView.set(it);it.getMapAsync(recreatedMap::set) } }
            recreatedMap.get()!=null
        }
        await(30_000) {
            var count=0
            main { val v=recreatedView.get();count=recreatedMap.get().queryRenderedFeatures(RectF(0f,0f,v.width.toFloat(),v.height.toFloat()),BuildingRiskOverlay.BUILDINGS,BuildingRiskOverlay.OVERVIEW).size }
            count>0
        }
        }
        main { ref.get().finish() }
    }
}

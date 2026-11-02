package io.github.nevaseme.plandmk1

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.buildings.*
import io.github.nevaseme.plandmk1.diagnostics.BuildingMapBenchmarkActivity
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.Property
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class BuildingRiskMapTest {
    @get:Rule val compose=createAndroidComposeRule<BuildingMapBenchmarkActivity>()
    private fun views(v:View):List<MapView> = if(v is MapView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
    @Test fun allAreaTilesRenderAndMeasure() {
        val started=compose.activity.createdAt
        var map:MapLibreMap?=null;var view:MapView?=null
        compose.waitUntil(30_000) {
            compose.runOnIdle {if(view==null)views(compose.activity.window.decorView).firstOrNull()?.let {view=it;it.getMapAsync {m->map=m}}}
            map!=null
        }
        fun features(layer:String):List<org.maplibre.geojson.Feature> {
            var result=emptyList<org.maplibre.geojson.Feature>()
            compose.runOnIdle {result=map!!.queryRenderedFeatures(if(layer==BuildingRiskOverlay.OVERVIEW)RectF(view!!.width*.45f,view!!.height*.45f,view!!.width*.55f,view!!.height*.55f) else RectF(0f,0f,view!!.width.toFloat(),view!!.height.toFloat()),layer)}
            return result
        }
        compose.waitUntil(90_000) { features(BuildingRiskOverlay.BUILDINGS).isNotEmpty() }
        val initialMs=SystemClock.elapsedRealtime()-started
        val first=features(BuildingRiskOverlay.BUILDINGS)
        assertTrue(first.all {it.hasProperty("bid")&&it.hasProperty("p")})
        if(InstrumentationRegistry.getArguments().getString("probeOnly")=="true") {
            android.util.Log.i("PlanD-map-probe","visibleMs=$initialMs features=${first.size}")
            return
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val model=context.assets.open("kyoto-building-assessment/model.json").bufferedReader().use {BuildingMapModel.parse(it.readText())}
        val sample=first.firstOrNull {it.getStringProperty("p")!="?"}!!
        assertNotNull(model.percent(sample.getStringProperty("p"),6.2))
        // Changing intensity changes the paint expression without rebuilding geometry.
        compose.runOnIdle {
            BuildingRiskOverlay.install(map!!.style!!,model,6.5)
            assertTrue((map!!.style!!.getLayer(BuildingRiskOverlay.BUILDINGS) as org.maplibre.android.style.layers.FillLayer).fillColor.expression.toString().contains("match"))
            BuildingRiskOverlay.install(map!!.style!!,model,6.2)
        }
        val intervals=Collections.synchronizedList(mutableListOf<Double>())
        var last=0L
        val listener=MapView.OnDidFinishRenderingFrameListener { _,_,_->
            val now=System.nanoTime()
            if(last!=0L) intervals.add((now-last)/1e6)
            last=now
        }
        compose.runOnIdle {view!!.addOnDidFinishRenderingFrameListener(listener)}
        val phases=JSONArray()
        val path=listOf(LatLng(35.005,135.763),LatLng(35.009,135.753),LatLng(34.988,135.758),LatLng(35.025,135.755),LatLng(35.003,135.759))
        for(enabled in listOf(false,true,true)) {
            compose.runOnIdle {
                map!!.style!!.getLayer(BuildingRiskOverlay.BUILDINGS)!!.setProperties(visibility(if(enabled)Property.VISIBLE else Property.NONE))
                map!!.style!!.getLayer(BuildingRiskOverlay.OVERVIEW)!!.setProperties(visibility(if(enabled)Property.VISIBLE else Property.NONE))
            }
            val durations=JSONArray();val frameSamples=mutableListOf<Double>()
            for(point in path) {
                intervals.clear();last=0
                val finished=AtomicBoolean(false);val t=SystemClock.elapsedRealtime()
                compose.runOnIdle {map!!.animateCamera(CameraUpdateFactory.newLatLngZoom(point,16.8),1000,object:MapLibreMap.CancelableCallback {
                    override fun onFinish(){finished.set(true)}
                    override fun onCancel(){finished.set(true)}
                })}
                compose.waitUntil(20_000) {finished.get()}
                synchronized(intervals){frameSamples.addAll(intervals)}
                if(enabled)compose.waitUntil(20_000){features(BuildingRiskOverlay.BUILDINGS).isNotEmpty()}
                durations.put(SystemClock.elapsedRealtime()-t)
            }
            val sorted=frameSamples.sorted()
            fun percentile(p:Double)=sorted[((sorted.size-1)*p).toInt()]
            android.util.Log.i("PlanD-map-measure","overlay=$enabled frames=${sorted.size} durations=$durations")
            assertTrue("Map must render animated frames",sorted.isNotEmpty())
            phases.put(JSONObject().put("overlay",enabled).put("animationPlusVisibleMs",durations).put("renderCallbackIntervalMs",JSONArray(frameSamples))
                .put("p50Ms",percentile(.5)).put("p95Ms",percentile(.95)).put("over32ms",sorted.count {it>32}).put("frames",sorted.size))
        }
        fun capture(name:String) {
            var bitmap:Bitmap?=null
            compose.runOnIdle {map!!.snapshot {bitmap=it}}
            compose.waitUntil(30_000){bitmap!=null}
            File(context.getExternalFilesDir(null),"risk-map-$name.png").outputStream().use {bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap!!.recycle()
        }
        capture("buildings")
        val t=SystemClock.elapsedRealtime()
        compose.runOnIdle {map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.04,135.75),10.3))}
        compose.waitUntil(30_000){features(BuildingRiskOverlay.OVERVIEW).isNotEmpty()}
        val wideMs=SystemClock.elapsedRealtime()-t
        val overview=features(BuildingRiskOverlay.OVERVIEW)
        assertTrue(overview.all {it.hasProperty("p")&&!it.hasProperty("n")&&!it.hasProperty("f12")})
        capture("overview")
        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        val report=JSONObject().put("device",Build.MODEL).put("sdk",Build.VERSION.SDK_INT).put("initialVisibleMs",initialMs)
            .put("wideVisibleMs",wideMs).put("initialFeatures",first.size).put("overviewFeatures",overview.size)
            .put("totalPssKb",memory.totalPss).put("phases",phases).put("note","debug build; render callback intervals during 1-second camera animations, not Google Maps comparison")
        File(context.getExternalFilesDir(null),"risk-map-performance.json").writeText(report.toString(2))
        compose.runOnIdle {view!!.removeOnDidFinishRenderingFrameListener(listener)}
        compose.runOnIdle {map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.003,135.759),18.0))}
        compose.waitUntil(30_000){features(BuildingRiskOverlay.BUILDINGS).isNotEmpty()}
        var tap:android.graphics.PointF?=null;var expectedRate:Double?=null
        compose.runOnIdle {
            val candidates=map!!.queryRenderedFeatures(RectF(view!!.width*.2f,view!!.height*.2f,view!!.width*.8f,view!!.height*.8f),BuildingRiskOverlay.BUILDINGS)
            for(f in candidates) {
                val rate=model.percent(f.getStringProperty("p"),6.2) ?: continue
                val rings=(f.geometry() as? org.maplibre.geojson.Polygon)?.coordinates() ?: continue
                val ring=rings.first()
                val lat=(ring.minOf {it.latitude()}+ring.maxOf {it.latitude()})/2
                val lon=(ring.minOf {it.longitude()}+ring.maxOf {it.longitude()})/2
                val point=MapPoint(lat,lon)
                if(!io.github.nevaseme.plandmk1.ui.insideFootprint(point,rings.map {r->r.map {MapPoint(it.latitude(),it.longitude())}}))continue
                val screen=map!!.projection.toScreenLocation(LatLng(lat,lon))
                if(screen.x !in 50f..view!!.width-50f || screen.y !in 50f..view!!.height-50f)continue
                val top=map!!.queryRenderedFeatures(screen,BuildingRiskOverlay.BUILDINGS).firstOrNull() ?: continue
                expectedRate=model.percent(top.getStringProperty("p"),6.2) ?: continue
                tap=screen;break
            }
            assertNotNull("An actual visible building must be tappable",tap)
            val t=SystemClock.uptimeMillis()
            MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,tap!!.x,tap!!.y,0).also {view!!.dispatchTouchEvent(it);it.recycle()}
            MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,tap!!.x,tap!!.y,0).also {view!!.dispatchTouchEvent(it);it.recycle()}
        }
        val expectedText="想定震度 6.2・全壊率 "+String.format(java.util.Locale.JAPAN,"%.1f%%",expectedRate)
        compose.waitUntil(10_000){compose.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(expectedText).assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {image->
            File(context.getExternalFilesDir(null),"risk-map-detail.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
        compose.onNodeWithContentDescription("詳細を閉じる").performClick()
        compose.onNodeWithText("凡例").performClick()
        compose.onNodeWithText("京都の収録範囲を表示").assertExists()
    }
}

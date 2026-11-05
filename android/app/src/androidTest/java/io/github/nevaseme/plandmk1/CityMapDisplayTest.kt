package io.github.nevaseme.plandmk1

import android.view.View
import android.view.ViewGroup
import android.graphics.RectF
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.ui.ResidentMap
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.io.File

/** Run after CityOfflineMapTest. Disables map networking, never device connectivity. */
class CityMapDisplayTest {
 private fun awaitCondition(timeout:Long, predicate:()->Boolean) { val end=android.os.SystemClock.elapsedRealtime()+timeout; while(!predicate()){if(android.os.SystemClock.elapsedRealtime()>end)throw AssertionError("Map rendering timed out");android.os.SystemClock.sleep(100)} }
 private fun maps(v:View):List<MapView> = if(v is MapView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {maps(v.getChildAt(it))} else emptyList()
 @Test fun cityEdgesAndCenterRenderWithoutMapNetworking() {
  val inst=InstrumentationRegistry.getInstrumentation();val points=listOf(35.31 to 135.755,34.884 to 135.727,35.04 to 135.565,35.09 to 135.87,35.0145 to 135.7625)
  inst.runOnMainSync {MapLibre.getInstance(inst.targetContext);MapLibre.setConnected(false)}
  var activity:MainActivity?=null
  if(InstrumentationRegistry.getArguments().getString("externalLaunch")!="true") inst.startActivitySync(android.content.Intent(inst.targetContext,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  awaitCondition(90_000){inst.runOnMainSync {activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()};activity!=null}
  try {
   inst.runOnMainSync {activity!!.setContent {PlanDMk1Theme {ResidentMap(emptyList(),MapPoint(points[0].first,points[0].second),false,{},{},{},Modifier.fillMaxSize())}}}
   var map:MapLibreMap?=null;var view:MapView?=null
   awaitCondition(20_000){inst.runOnMainSync {val a=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first();view=maps(a.window.decorView).firstOrNull();view?.getMapAsync {map=it}};map!=null}
   for((i,p) in points.withIndex()) {
    inst.runOnMainSync {map!!.cameraPosition=CameraPosition.Builder().target(LatLng(p.first,p.second)).zoom(14.0).build()}
    var features=0
    awaitCondition(25_000){inst.runOnMainSync {features=if(map!!.style?.isFullyLoaded==true)map!!.queryRenderedFeatures(RectF(0f,0f,view!!.width.toFloat(),view!!.height.toFloat())).size else 0};features>0}
    android.util.Log.i("PlanD-CityQA","offline edge=$i lat=${p.first} lon=${p.second} features=$features")
    android.os.SystemClock.sleep(600)
    val bitmap=inst.uiAutomation.takeScreenshot();File(inst.targetContext.getExternalFilesDir(null),"city-offline-$i.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
   }
  }finally{inst.runOnMainSync {MapLibre.setConnected(null)};inst.runOnMainSync {activity?.finish()}}
 }
}

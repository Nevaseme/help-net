package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.location.PositionFix
import io.github.nevaseme.plandmk1.ui.drawCurrentMapLocation
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Controlled map fixes, never written to Android's location providers or application records. */
class CurrentMapLocationDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Test fun blueDotAccuracyAndUpdatesNeverMoveCamera(){
  var view:MapView?=null;var map:MapLibreMap?=null;var style:Style?=null
  val fix=PositionFix(35.0145,135.7625,35.0,System.currentTimeMillis())
  shellComposeContent {AndroidView(factory={context->org.maplibre.android.MapLibre.getInstance(context);MapView(context).also {v->view=v;v.onCreate(null);v.onStart();v.onResume();v.getMapAsync {m->map=m;m.cameraPosition=CameraPosition.Builder().target(LatLng(fix.latitude,fix.longitude)).zoom(17.0).build();m.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#edf2f4"}}]}""")){s->style=s;drawCurrentMapLocation(s,fix)}}}})}
  compose.waitUntil(15000){style!=null}
  compose.waitUntil(10000){var count=0;compose.runOnIdle {count=map!!.queryRenderedFeatures(map!!.projection.toScreenLocation(LatLng(fix.latitude,fix.longitude)),"my-location-dot").size};count>0}
  compose.runOnIdle {
   assertNotNull(style!!.getLayer("my-location-accuracy"));assertNotNull(style!!.getLayer("my-location-dot"))
   val before=map!!.cameraPosition
   drawCurrentMapLocation(style!!,fix.copy(latitude=35.0147))
   assertEquals(before,map!!.cameraPosition)
  }
  val inst=InstrumentationRegistry.getInstrumentation();inst.uiAutomation.takeScreenshot()?.let {bitmap->File(inst.targetContext.getExternalFilesDir(null),"current-location-blue-dot.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
  compose.runOnIdle{drawCurrentMapLocation(style!!,null)}
  compose.waitUntil(10000){var count=1;compose.runOnIdle {count=map!!.queryRenderedFeatures(map!!.projection.toScreenLocation(LatLng(35.0147,fix.longitude)),"my-location-dot").size};count==0}
  compose.runOnIdle{view!!.onPause();view!!.onStop();view!!.onDestroy()}
 }
}

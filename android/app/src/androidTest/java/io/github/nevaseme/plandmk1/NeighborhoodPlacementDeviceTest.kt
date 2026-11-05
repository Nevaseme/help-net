package io.github.nevaseme.plandmk1

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.nevaseme.plandmk1.core.CommunityRow
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Assert.*
import org.junit.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.io.File

class NeighborhoodPlacementDeviceTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Before fun initializeMap(){val inst=InstrumentationRegistry.getInstrumentation();inst.runOnMainSync {org.maplibre.android.MapLibre.getInstance(inst.targetContext)}}
 @After fun finish(){InstrumentationRegistry.getInstrumentation().runOnMainSync {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach {it.finish()}}}
 @Test fun neighborhoodRemainsAtItsPointWhileMapMoves(){
  val inst=InstrumentationRegistry.getInstrumentation()
  val center=MapPoint(35.0145,135.7622)
  val regions=listOf(
   MapNeighborhood(CommunityRow("west","御所南西町内会","Gosho South West",sos=1,unreported=2,safe=7,rescued=1),MapPoint(35.0145,135.759),1),
   MapNeighborhood(CommunityRow("east","御所南東町内会","Gosho South East",sos=2,unreported=3,safe=5,rescued=1),MapPoint(35.0145,135.7657),1))
  var selected:String?=null
  shellComposeContent {CompositionLocalProvider(LocalUiLanguage provides "en"){PlanDMk1Theme {
   SituationMap(emptyList(),emptyList(),center,true,System.currentTimeMillis(),false,remember {SituationCamera()},{},{},{},{},
    modifier=Modifier.fillMaxSize(),initialZoom=15.5,neighborhoods=regions,onNeighborhood={selected=it})
  }}}
  fun find(v:View):MapView?=if(v is MapView)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull {find(v.getChildAt(it))} else null
  var map:MapLibreMap?=null;lateinit var view:MapView
  compose.waitUntil(30000){compose.runOnIdle {val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first();find(activity.window.decorView)?.let {view=it;it.getMapAsync {m->map=m}}};map!=null}
  compose.waitUntil(30000){var ready=false;compose.runOnIdle {ready=map!!.markers.size==2};ready}
  val ids=mutableSetOf<Long>()
  compose.runOnIdle {
   ids.addAll(map!!.markers.map {it.id})
   for(marker in map!!.markers){
    val bitmap=marker.icon!!.bitmap
    assertEquals("Lower half provides the bottom anchor",0,android.graphics.Color.alpha(bitmap.getPixel(bitmap.width/2,bitmap.height*3/4)))
   }
  }
  fun verify(){compose.runOnIdle {
   assertEquals(ids,map!!.markers.map {it.id}.toSet())
   for(n in regions)assertTrue("${n.row.nameEn} keeps its geographic anchor",map!!.markers.any {it.position.latitude==n.point!!.latitude&&it.position.longitude==n.point.longitude})
  }}
  repeat(16){i->
   compose.runOnIdle {map!!.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0145+(if(i%2==0).004 else -.004),135.7622+(if(i%3==0).008 else -.003)),if(i%2==0)14.5 else 16.5),350)}
   SystemClock.sleep(180);verify();SystemClock.sleep(220);verify()
  }
  compose.runOnIdle {map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude,center.longitude),14.0))}
  SystemClock.sleep(1500);verify()
  val f=File(inst.targetContext.filesDir,"neighborhood-placement/android-map.png");f.parentFile!!.mkdirs()
  inst.uiAutomation.takeScreenshot().let {b->f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  // Center the target before tapping; its fixed geographic point may legitimately
  // be outside a narrow screen at a closer zoom.
  compose.runOnIdle {map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(regions[0].point!!.latitude,regions[0].point!!.longitude),16.0))}
  SystemClock.sleep(1000);verify()
  // Tap inside the actual bottom-anchored marker, above its fixed map coordinate.
  var x=0f;var y=0f
  compose.runOnIdle {val p=map!!.projection.toScreenLocation(LatLng(regions[0].point!!.latitude,regions[0].point!!.longitude));val offset=IntArray(2);view.getLocationOnScreen(offset);x=p.x+offset[0];y=p.y+offset[1]-50*view.resources.displayMetrics.density}
  val screen=inst.uiAutomation.takeScreenshot()
  try {
   val density=view.resources.displayMetrics.density
   // Inspect the white interior beside the text. The bottom must end at the
   // projected point, not halfway below it (the previous centered rendering).
   val sampleX=(x-65*density).toInt()
   val sampleY=(y+45*density).toInt()
   assertEquals("Visible card ends just above its geographic anchor",0xFFFFFF,screen.getPixel(sampleX,sampleY) and 0xFFFFFF)
  }finally{screen.recycle()}
  val down=SystemClock.uptimeMillis()
  for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP))android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).let {inst.uiAutomation.injectInputEvent(it,true);it.recycle()}
  compose.waitUntil(5000){selected=="west"}
 }
}
